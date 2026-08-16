package io.github.bzhangj13zzz.touchpause.feedback

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.github.bzhangj13zzz.touchpause.R
import io.github.bzhangj13zzz.touchpause.block.FeedbackOptions

/** Shows the user-visible message and vibration feedback for one blocking session. */
class FeedbackNotifier(context: Context) {
    private val appContext = context.applicationContext

    fun showStarted(options: FeedbackOptions) {
        if (options.showStartMessage) {
            Toast.makeText(
                appContext,
                ContextCompat.getString(appContext, R.string.touch_block_started),
                Toast.LENGTH_SHORT
            ).show()
        }
        if (options.vibrateOnStart) vibrate()
    }

    fun showReleased(options: FeedbackOptions) {
        if (options.showStopMessage) {
            Toast.makeText(
                appContext,
                ContextCompat.getString(appContext, R.string.touch_block_released),
                Toast.LENGTH_SHORT
            ).show()
        }
        if (options.vibrateOnStop) vibrate()
    }

    private fun vibrate() {
        val vibrator = appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
            ?: return
        vibrator.vibrate(
            VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
}
