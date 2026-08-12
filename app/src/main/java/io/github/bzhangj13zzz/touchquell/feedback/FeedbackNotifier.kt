package io.github.bzhangj13zzz.touchquell.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import io.github.bzhangj13zzz.touchquell.R
import io.github.bzhangj13zzz.touchquell.block.FeedbackOptions

/** Shows the user-visible message and vibration feedback for one blocking session. */
class FeedbackNotifier(context: Context) {
    private val appContext = context.applicationContext

    fun showStarted(options: FeedbackOptions) {
        if (options.showStartMessage) {
            Toast.makeText(appContext, R.string.touch_block_started, Toast.LENGTH_SHORT).show()
        }
        if (options.vibrateOnStart) vibrate()
    }

    fun showReleased(options: FeedbackOptions) {
        if (options.showStopMessage) {
            Toast.makeText(appContext, R.string.touch_block_released, Toast.LENGTH_SHORT).show()
        }
        if (options.vibrateOnStop) vibrate()
    }

    fun showRootError() {
        Toast.makeText(appContext, R.string.root_backend_error, Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            vibrator.vibrate(100)
        }
    }
}
