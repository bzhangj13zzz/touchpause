package io.github.bzhangj13zzz.touchpause.root

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.bzhangj13zzz.touchpause.block.BlockSessionStore
import io.github.bzhangj13zzz.touchpause.block.FeedbackOptions
import io.github.bzhangj13zzz.touchpause.feedback.FeedbackNotifier
import io.github.bzhangj13zzz.touchpause.tile.TileRefresher

/** Reconciles root-helper completion even when Android killed the process that launched it. */
class RootBlockerStoppedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RootCommandBuilder.stoppedAction(context.packageName)) return

        val token = intent.getStringExtra(RootCommandBuilder.EXTRA_INSTANCE_TOKEN) ?: return
        val result = BlockSessionStore(context).clearRootInvocation(token)
        if (result.stateChanged) TileRefresher.request(context)
        if (!result.activeWasCleared) return

        val feedback = FeedbackOptions(
            showStartMessage = false,
            vibrateOnStart = false,
            showStopMessage = intent.getBooleanExtra(
                RootCommandBuilder.EXTRA_SHOW_MESSAGE,
                false
            ),
            vibrateOnStop = intent.getBooleanExtra(RootCommandBuilder.EXTRA_VIBRATE, false)
        )
        FeedbackNotifier(context).showReleased(feedback)
    }
}
