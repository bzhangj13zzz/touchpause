package io.github.bzhangj13zzz.touchpause.root

import io.github.bzhangj13zzz.touchpause.block.FeedbackOptions
import io.github.bzhangj13zzz.touchpause.block.ReleaseKey

/** Builds the single portable shell command passed to different `su` implementations. */
object RootCommandBuilder {
    const val READY_MARKER = "BLOCKER_READY"
    // Android 7's package installer only extracts native payloads named lib*.so.
    const val NATIVE_BINARY_NAME = "libtouchpause-input.so"
    const val LOCK_FILE_NAME = "touch-blocker.lock"

    const val EXTRA_INSTANCE_TOKEN = "INSTANCE_TOKEN"
    const val EXTRA_SHOW_MESSAGE = "SHOW_MESSAGE"
    const val EXTRA_VIBRATE = "VIBRATE"

    private const val STOP_ACTION_SUFFIX = ".action.ROOT_BLOCKER_STOPPED"

    data class Request(
        val nativeLibraryDirectory: String,
        val lockFilePath: String,
        val releaseKey: ReleaseKey,
        val feedback: FeedbackOptions,
        val invocationToken: String,
        val applicationId: String,
        val receiverClassName: String
    )

    /** Returns exactly `su`, `-c`, and one complete shell script argument. */
    fun processCommand(request: Request): List<String> = listOf(
        "su",
        "-c",
        buildShellScript(request)
    )

    /** The manifest uses the same application-id-derived action. */
    fun stoppedAction(applicationId: String): String = "$applicationId$STOP_ACTION_SUFFIX"

    /**
     * Runs the helper, always broadcasts its completion token, then preserves its exit status.
     * The receiver and live process observer race safely because only the token owner can clear an
     * active session and show release feedback.
     */
    internal fun buildShellScript(request: Request): String {
        val blockerPath = "${request.nativeLibraryDirectory}/$NATIVE_BINARY_NAME"
        val blockerCommand = listOf(
            blockerPath,
            "-p",
            request.lockFilePath,
            "-d",
            "0",
            "-s",
            request.releaseKey.nativeValue,
            "-v",
            "1"
        )
        val receiverComponent = "${request.applicationId}/${request.receiverClassName}"
        val stoppedCommand = listOf(
            "am",
            "broadcast",
            "-a",
            stoppedAction(request.applicationId),
            "-n",
            receiverComponent,
            "-f",
            INCLUDE_STOPPED_PACKAGES_FLAG,
            "--es",
            EXTRA_INSTANCE_TOKEN,
            request.invocationToken,
            "--ez",
            EXTRA_SHOW_MESSAGE,
            request.feedback.showStopMessage.toString(),
            "--ez",
            EXTRA_VIBRATE,
            request.feedback.vibrateOnStop.toString()
        )

        return buildString {
            append(shellCommand(listOf("cmd", "statusbar", "collapse"))).append('\n')
            append(shellCommand(blockerCommand)).append('\n')
            append("blocker_status=\$?\n")
            append(shellCommand(stoppedCommand)).append('\n')
            append("exit \"\$blocker_status\"")
        }
    }

    /** Quotes one value for Android's POSIX-compatible root shell. */
    internal fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun shellCommand(arguments: List<String>): String =
        arguments.joinToString(" ") { shellQuote(it) }

    private const val INCLUDE_STOPPED_PACKAGES_FLAG = "0x20"
}
