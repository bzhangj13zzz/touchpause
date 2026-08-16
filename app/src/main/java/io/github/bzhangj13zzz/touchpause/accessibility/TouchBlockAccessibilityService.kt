package io.github.bzhangj13zzz.touchpause.accessibility

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import io.github.bzhangj13zzz.touchpause.block.BlockSessionStore
import io.github.bzhangj13zzz.touchpause.block.FeedbackOptions
import io.github.bzhangj13zzz.touchpause.block.ReleaseKey
import io.github.bzhangj13zzz.touchpause.feedback.FeedbackNotifier
import io.github.bzhangj13zzz.touchpause.preferences.UserPreferences
import io.github.bzhangj13zzz.touchpause.tile.TileRefresher

/** Android 14+ backend that withholds direct touch/stylus motion until the release key is used. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class TouchBlockAccessibilityService : AccessibilityService() {
    private val accessibilityManager by lazy {
        getSystemService(AccessibilityManager::class.java)
    }
    private val sessions by lazy { BlockSessionStore(this) }
    private val userPreferences by lazy { UserPreferences(this) }
    private val feedbackNotifier by lazy { FeedbackNotifier(this) }

    private val touchExplorationListener =
        AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            if (enabled && isBlocking) stopBlocking(showFeedback = true)
        }
    private val servicesListener =
        AccessibilityManager.AccessibilityServicesStateChangeListener {
            if (isBlocking && AccessibilityStatus.hasKeyFilterConflict(this)) {
                stopBlocking(showFeedback = true)
            }
        }

    private var isBlocking = false
    private var listenersRegistered = false
    private var previousMotionSources = 0
    private var previousServiceFlags = 0
    private var releaseKeyCode = KeyEvent.KEYCODE_UNKNOWN
    private var releaseKeyAwaitingUp = KeyEvent.KEYCODE_UNKNOWN
    private var feedback = FeedbackOptions()

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (!userPreferences.hasAccessibilityConsent()) {
            if (sessions.clearAccessibilityIfOwned()) TileRefresher.request(this)
            disableSelf()
            return
        }
        connectedService = this
        if (sessions.clearAccessibilityIfOwned()) TileRefresher.request(this)
        accessibilityManager.addTouchExplorationStateChangeListener(touchExplorationListener)
        accessibilityManager.addAccessibilityServicesStateChangeListener(servicesListener)
        listenersRegistered = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        stopBlocking(showFeedback = false)
    }

    /** Android withholds requested motion sources from every other consumer. */
    override fun onMotionEvent(event: MotionEvent) = Unit

    /** Consumes release-key events while filtering remains active so the press does not change volume. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == releaseKeyAwaitingUp) {
            if (event.action == KeyEvent.ACTION_UP) {
                releaseKeyAwaitingUp = KeyEvent.KEYCODE_UNKNOWN
            }
            return true
        }

        if (!isBlocking || event.keyCode != releaseKeyCode) return super.onKeyEvent(event)

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                releaseKeyAwaitingUp = event.keyCode
                stopBlocking(showFeedback = true)
                true
            }
            KeyEvent.ACTION_UP -> {
                stopBlocking(showFeedback = true)
                true
            }
            else -> false
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun toggle(): Boolean {
        if (isBlocking) {
            stopBlocking(showFeedback = true)
            return true
        }

        val selectedKey = userPreferences.releaseKey()
        if (!selectedKey.supportsAccessibility || !captureIsAvailable()) return false
        return startBlocking(selectedKey)
    }

    /** Applies capture first, then publishes ownership only if root did not win the race. */
    @SuppressLint("WrongConstant") // API 34 docs allow SOURCE_TOUCHSCREEN; its lint stub omits it.
    private fun startBlocking(selectedKey: ReleaseKey): Boolean {
        val info = serviceInfo ?: return false
        val captureSources = InputDevice.SOURCE_TOUCHSCREEN or
            InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_BLUETOOTH_STYLUS
        val selectedKeyCode = when (selectedKey) {
            ReleaseKey.VOLUME_DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
            ReleaseKey.VOLUME_UP -> KeyEvent.KEYCODE_VOLUME_UP
            ReleaseKey.POWER -> return false
        }

        previousMotionSources = info.motionEventSources
        previousServiceFlags = info.flags
        // Android may stop key delivery before the prior release gesture's ACTION_UP arrives.
        releaseKeyAwaitingUp = KeyEvent.KEYCODE_UNKNOWN
        releaseKeyCode = selectedKeyCode
        feedback = userPreferences.feedbackOptions()
        info.flags = previousServiceFlags or
            AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        info.setMotionEventSources(previousMotionSources or captureSources)

        val captureApplied = runCatching {
            setServiceInfo(info)
            captureIsAvailable() &&
                serviceInfo.motionEventSources and captureSources == captureSources &&
                serviceInfo.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
        }.getOrDefault(false)

        if (!captureApplied || !sessions.publishAccessibilityActive()) {
            restoreInputConfiguration()
            releaseKeyCode = KeyEvent.KEYCODE_UNKNOWN
            feedback = FeedbackOptions()
            return false
        }

        isBlocking = true
        TileRefresher.request(this)
        feedbackNotifier.showStarted(feedback)
        return true
    }

    /** Restores normal delivery before clearing state or notifying the user. */
    private fun stopBlocking(showFeedback: Boolean) {
        if (!isBlocking) return

        restoreInputConfiguration()
        isBlocking = false
        releaseKeyCode = KeyEvent.KEYCODE_UNKNOWN
        if (sessions.clearAccessibilityIfOwned()) TileRefresher.request(this)
        if (showFeedback) feedbackNotifier.showReleased(feedback)
        feedback = FeedbackOptions()
    }

    /** Stops both touch capture and key filtering before publishing the released state. */
    private fun restoreInputConfiguration() {
        val info = serviceInfo
        if (info == null) {
            disableSelf()
            return
        }

        runCatching {
            info.flags = previousServiceFlags
            info.setMotionEventSources(previousMotionSources)
            setServiceInfo(info)
        }.onFailure {
            // Disabling removes Android's input filter if reconfiguration itself failed.
            disableSelf()
        }
    }

    private fun captureIsAvailable(): Boolean =
        userPreferences.hasAccessibilityConsent() &&
            !sessions.hasPendingRootInvocation() &&
            !AccessibilityStatus.isTouchExplorationEnabled(this) &&
            !AccessibilityStatus.hasKeyFilterConflict(this)

    private fun disconnect() {
        if (isBlocking) stopBlocking(showFeedback = false)
        releaseKeyAwaitingUp = KeyEvent.KEYCODE_UNKNOWN
        if (listenersRegistered) {
            accessibilityManager.removeTouchExplorationStateChangeListener(
                touchExplorationListener
            )
            accessibilityManager.removeAccessibilityServicesStateChangeListener(
                servicesListener
            )
            listenersRegistered = false
        }
        if (connectedService === this) connectedService = null
    }

    companion object {
        @Volatile
        private var connectedService: TouchBlockAccessibilityService? = null

        fun requestToggle(): Boolean = connectedService?.toggle() ?: false

        fun isConnected(): Boolean = connectedService != null

        fun isReady(): Boolean = connectedService?.let { service ->
            service.userPreferences.releaseKey().supportsAccessibility &&
                service.captureIsAvailable()
        } == true
    }
}
