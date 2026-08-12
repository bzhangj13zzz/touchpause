package io.github.bzhangj13zzz.touchquell.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.github.bzhangj13zzz.touchquell.block.FeedbackOptions
import io.github.bzhangj13zzz.touchquell.block.ReleaseKey

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
    }
}
