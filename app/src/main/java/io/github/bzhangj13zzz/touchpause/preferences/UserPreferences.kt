package io.github.bzhangj13zzz.touchpause.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.github.bzhangj13zzz.touchpause.block.FeedbackOptions
import io.github.bzhangj13zzz.touchpause.block.ReleaseKey

/** Typed access to user choices stored by the settings preference screen. */
class UserPreferences(context: Context) {
    val sharedPreferences: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun releaseKey(): ReleaseKey =
        ReleaseKey.fromPreference(sharedPreferences.getString(KEY_RELEASE_KEY, null))

    fun feedbackOptions(): FeedbackOptions = FeedbackOptions(
        showStartMessage = sharedPreferences.getBoolean(KEY_START_MESSAGE, true),
        vibrateOnStart = sharedPreferences.getBoolean(KEY_START_VIBRATION, true),
        showStopMessage = sharedPreferences.getBoolean(KEY_STOP_MESSAGE, true),
        vibrateOnStop = sharedPreferences.getBoolean(KEY_STOP_VIBRATION, true)
    )

    /** True only after the user accepted the separate Accessibility API disclosure. */
    fun hasAccessibilityConsent(): Boolean =
        sharedPreferences.getInt(KEY_ACCESSIBILITY_CONSENT_VERSION, 0) ==
            CURRENT_ACCESSIBILITY_CONSENT_VERSION

    /** Persists consent before Android's Accessibility settings can enable the service. */
    fun setAccessibilityConsent(accepted: Boolean): Boolean =
        sharedPreferences.edit().apply {
            if (accepted) {
                putInt(
                    KEY_ACCESSIBILITY_CONSENT_VERSION,
                    CURRENT_ACCESSIBILITY_CONSENT_VERSION
                )
            } else {
                remove(KEY_ACCESSIBILITY_CONSENT_VERSION)
            }
        }.commit()

    fun isTileAdded(): Boolean = sharedPreferences.getBoolean(KEY_TILE_ADDED, false)

    fun setTileAdded(added: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_TILE_ADDED, added).apply()
    }

    companion object {
        const val KEY_RELEASE_KEY = "release_key"
        const val KEY_START_MESSAGE = "feedback_start_message"
        const val KEY_START_VIBRATION = "feedback_start_vibration"
        const val KEY_STOP_MESSAGE = "feedback_stop_message"
        const val KEY_STOP_VIBRATION = "feedback_stop_vibration"
        const val KEY_TILE_ADDED = "tile_added"
        const val KEY_ACCESSIBILITY_CONSENT_VERSION = "accessibility_disclosure_version"

        // Version 2 adds explicit stylus-motion access to the standalone disclosure.
        private const val CURRENT_ACCESSIBILITY_CONSENT_VERSION = 2
    }
}
