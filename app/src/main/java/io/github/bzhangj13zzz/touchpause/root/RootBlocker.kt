package io.github.bzhangj13zzz.touchpause.root

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.bzhangj13zzz.touchpause.block.BlockSessionStore
import io.github.bzhangj13zzz.touchpause.block.FeedbackOptions
import io.github.bzhangj13zzz.touchpause.block.ReleaseKey
import io.github.bzhangj13zzz.touchpause.feedback.FeedbackNotifier
import io.github.bzhangj13zzz.touchpause.tile.TileRefresher
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

/** Starts or toggles the native root blocker and reconciles its asynchronous lifecycle. */
class RootBlocker(context: Context) {
    private val appContext = context.applicationContext
    private val sessions = BlockSessionStore(appContext)
    private val feedbackNotifier = FeedbackNotifier(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Starts a root invocation, or safely ignores it when another in-process invocation or
     * Accessibility already owns startup. Returns false only when `su` could not be launched.
     */
    fun toggle(releaseKey: ReleaseKey, feedback: FeedbackOptions): Boolean {
        val invocationToken = UUID.randomUUID().toString()
        if (!sessions.reserveRootInvocation(invocationToken)) {
            Log.w(LOG_TAG, "Root invocation ignored because another backend or request owns it")
            return true
        }

        val request = RootCommandBuilder.Request(
            nativeLibraryDirectory = appContext.applicationInfo.nativeLibraryDir,
            lockFilePath = File(
                appContext.filesDir,
                RootCommandBuilder.LOCK_FILE_NAME
            ).absolutePath,
            releaseKey = releaseKey,
            feedback = feedback,
            invocationToken = invocationToken,
            applicationId = appContext.packageName,
            receiverClassName = RootBlockerStoppedReceiver::class.java.name
        )

        val process = try {
            ProcessBuilder(RootCommandBuilder.processCommand(request))
                .redirectErrorStream(true)
                .start()
        } catch (error: IOException) {
            val cleared = sessions.clearRootInvocation(invocationToken)
            if (cleared.stateChanged) TileRefresher.request(appContext)
            Log.e(LOG_TAG, "Unable to start root command", error)
            return false
        }

        observeProcess(process, invocationToken, feedback)
        return true
    }

    /** Drains output so `su` cannot block, publishes READY on main, and reconciles final exit. */
    private fun observeProcess(
        process: Process,
        invocationToken: String,
        feedback: FeedbackOptions
    ) {
        thread(start = true, isDaemon = true, name = "touchpause-root-output") {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        Log.d(LOG_TAG, line)
                        if (line == RootCommandBuilder.READY_MARKER) {
                            mainHandler.post {
                                if (sessions.publishRootReady(invocationToken)) {
                                    TileRefresher.request(appContext)
                                    feedbackNotifier.showStarted(feedback)
                                }
                            }
                        }
                    }
                }
            }.onFailure { error ->
                Log.w(LOG_TAG, "Unable to read root command output", error)
            }

            val exitCode = runCatching { process.waitFor() }.getOrDefault(1)
            mainHandler.post {
                val cleared = sessions.clearRootInvocation(invocationToken)
                if (cleared.stateChanged) TileRefresher.request(appContext)

                when {
                    exitCode == 0 && cleared.activeWasCleared ->
                        feedbackNotifier.showReleased(feedback)
                    exitCode != 0 && exitCode != NATIVE_TOGGLED_EXIT -> {
                        Log.e(LOG_TAG, "Root blocker exited with status $exitCode")
                        feedbackNotifier.showRootError()
                    }
                }
            }
        }
    }

    private companion object {
        const val LOG_TAG = "TouchPause"
        const val NATIVE_TOGGLED_EXIT = 2
    }
}
