package io.github.bzhangj13zzz.touchquell.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import io.github.bzhangj13zzz.touchquell.R
import io.github.bzhangj13zzz.touchquell.accessibility.AccessibilityStatus
import io.github.bzhangj13zzz.touchquell.accessibility.TouchBlockAccessibilityService
import io.github.bzhangj13zzz.touchquell.block.Backend
import io.github.bzhangj13zzz.touchquell.block.BlockSessionStore
import io.github.bzhangj13zzz.touchquell.feedback.FeedbackNotifier
import io.github.bzhangj13zzz.touchquell.preferences.UserPreferences
import io.github.bzhangj13zzz.touchquell.root.RootBlocker

/** Primary product interface: renders the tile and routes each tap to the safe backend. */
class TouchBlockTileService : TileService() {
    private val sessions by lazy { BlockSessionStore(this) }
    private val userPreferences by lazy { UserPreferences(this) }

    override fun onClick() {
        super.onClick()

        val releaseKey = userPreferences.releaseKey()
        val activeBackend = sessions.activeBackend()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val rootPending = sessions.hasPendingRootInvocation()
            val useAccessibility = activeBackend == Backend.ACCESSIBILITY ||
                (activeBackend == null && releaseKey.supportsAccessibility && !rootPending &&
                    TouchBlockAccessibilityService.isReady())
            val accessibilityConnecting = activeBackend == null &&
                releaseKey.supportsAccessibility && !rootPending &&
                AccessibilityStatus.isEnabled(this) &&
                !TouchBlockAccessibilityService.isConnected()

            if (useAccessibility || accessibilityConnecting) {
                launchAccessibilityToggle()
                return
            }
        }

        if (!RootBlocker(this).toggle(releaseKey, userPreferences.feedbackOptions())) {
            FeedbackNotifier(this).showRootError()
        }
    }

    /** Android 14 requires a PendingIntent when collapsing Quick Settings into an activity. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun launchAccessibilityToggle() {
        val intent = Intent(this, AccessibilityToggleActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pendingIntent)
    }

    override fun onStartListening() {
        super.onStartListening()
        userPreferences.setTileAdded(true)

        val active = sessions.isBlocking()
        qsTile?.apply {
            label = getString(R.string.app_name)
            icon = Icon.createWithResource(this@TouchBlockTileService, R.drawable.ic_touchquell_mark_24)
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(
                    if (active) R.string.tile_state_active else R.string.tile_state_inactive
                )
            }
            updateTile()
        }
    }

    override fun onTileAdded() {
        super.onTileAdded()
        userPreferences.setTileAdded(true)
    }

    override fun onTileRemoved() {
        userPreferences.setTileAdded(false)
        super.onTileRemoved()
    }
}
