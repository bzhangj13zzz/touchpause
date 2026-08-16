package io.github.bzhangj13zzz.touchpause.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import io.github.bzhangj13zzz.touchpause.R
import io.github.bzhangj13zzz.touchpause.accessibility.AccessibilityStatus
import io.github.bzhangj13zzz.touchpause.accessibility.TouchBlockAccessibilityService
import io.github.bzhangj13zzz.touchpause.billing.EntitlementStore
import io.github.bzhangj13zzz.touchpause.block.BlockSessionStore
import io.github.bzhangj13zzz.touchpause.preferences.UserPreferences
import io.github.bzhangj13zzz.touchpause.settings.SettingsActivity

/** Primary product interface: renders the tile and routes each tap to Accessibility. */
class TouchBlockTileService : TileService() {
    private val sessions by lazy { BlockSessionStore(this) }
    private val userPreferences by lazy { UserPreferences(this) }
    private val entitlements by lazy { EntitlementStore(this) }

    override fun onClick() {
        super.onClick()

        val active = sessions.isBlocking()
        if (!active && !entitlements.canStartSession()) {
            launchPurchaseSetup()
            return
        }

        if (!active && !userPreferences.hasAccessibilityConsent()) {
            launchAccessibilitySetup(showDisclosure = true)
            return
        }

        val enabledButConnecting = AccessibilityStatus.isEnabled(this) &&
            !TouchBlockAccessibilityService.isConnected()
        if (active || TouchBlockAccessibilityService.isReady() || enabledButConnecting) {
            launchAccessibilityToggle()
        } else {
            launchAccessibilitySetup(showDisclosure = false)
        }
    }

    /** Android 14 requires a PendingIntent when collapsing Quick Settings into an activity. */
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

    /** Opens setup, optionally showing the required standalone disclosure immediately. */
    private fun launchAccessibilitySetup(showDisclosure: Boolean) {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_SHOW_ACCESSIBILITY_DISCLOSURE, showDisclosure)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            ACCESSIBILITY_SETUP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        startActivityAndCollapse(pendingIntent)
    }

    /** Opens lifetime access without ever interfering with an already active block. */
    private fun launchPurchaseSetup() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            putExtra(SettingsActivity.EXTRA_SHOW_PURCHASE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            PURCHASE_SETUP_REQUEST_CODE,
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
            label = ContextCompat.getString(this@TouchBlockTileService, R.string.app_name)
            icon = Icon.createWithResource(this@TouchBlockTileService, R.drawable.ic_touchpause_mark_24)
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = ContextCompat.getString(
                this@TouchBlockTileService,
                if (active) R.string.tile_state_active else R.string.tile_state_inactive
            )
            stateDescription = ContextCompat.getString(
                this@TouchBlockTileService,
                if (active) {
                    R.string.tile_state_description_active
                } else {
                    R.string.tile_state_description_inactive
                }
            )
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

    private companion object {
        const val ACCESSIBILITY_SETUP_REQUEST_CODE = 1
        const val PURCHASE_SETUP_REQUEST_CODE = 2
    }
}
