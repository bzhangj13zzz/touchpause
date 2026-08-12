package io.github.bzhangj13zzz.touchquell.root

import io.github.bzhangj13zzz.touchquell.block.FeedbackOptions
import io.github.bzhangj13zzz.touchquell.block.ReleaseKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootCommandBuilderTest {
    @Test
    fun processUsesOneCompleteShellArgument() {
        val command = RootCommandBuilder.processCommand(request())

        assertEquals(listOf("su", "-c"), command.take(2))
        assertEquals(3, command.size)
        assertTrue(
            command[2].contains(
                "'/data/app/touchquell/lib/${RootCommandBuilder.NATIVE_BINARY_NAME}' '-p'"
            )
        )
        assertTrue(command[2].contains("'-d' '0' '-s' '2' '-v' '1'"))
    }

    @Test
    fun completionBroadcastUsesDynamicApplicationIdentityAndFeedback() {
        val script = RootCommandBuilder.buildShellScript(
            request(
                feedback = FeedbackOptions(
                    showStartMessage = false,
                    vibrateOnStart = false,
                    showStopMessage = true,
                    vibrateOnStop = false
                )
            )
        )

        assertTrue(script.contains("'io.example.touchquell.action.ROOT_BLOCKER_STOPPED'"))
        assertTrue(
            script.contains(
                "'io.example.touchquell/io.example.touchquell.root.RootBlockerStoppedReceiver'"
            )
        )
        assertTrue(script.contains("'SHOW_MESSAGE' 'true'"))
        assertTrue(script.contains("'VIBRATE' 'false'"))
        assertFalse(script.contains("com.nmelihsensoy.snowy"))
    }

    @Test
    fun appOwnedValuesAreShellQuoted() {
        val script = RootCommandBuilder.buildShellScript(
            request(
                nativeLibraryDirectory = "/data/app/it's/lib",
                lockFilePath = "/data/user/0/user's/touch-blocker.lock",
                invocationToken = "owner's-token"
            )
        )
        val escapedQuote = "'\"'\"'"

        assertTrue(script.contains("it${escapedQuote}s/lib"))
        assertTrue(script.contains("user${escapedQuote}s/touch-blocker.lock"))
        assertTrue(script.contains("owner${escapedQuote}s-token"))
    }

    @Test
    fun generatedScriptHasValidShellSyntaxAndPreservesStatus() {
        val script = RootCommandBuilder.buildShellScript(request())
        val shell = ProcessBuilder("sh", "-n").start()

        shell.outputStream.bufferedWriter().use { it.write(script) }
        val error = shell.errorStream.bufferedReader().readText()

        assertEquals(error, 0, shell.waitFor())
        assertTrue(script.contains("blocker_status=\$?"))
        assertTrue(script.endsWith("exit \"\$blocker_status\""))
    }

    private fun request(
        nativeLibraryDirectory: String = "/data/app/touchquell/lib",
        lockFilePath: String = "/data/user/0/touchquell/files/touch-blocker.lock",
        feedback: FeedbackOptions = FeedbackOptions(),
        invocationToken: String = "owner-token"
    ) = RootCommandBuilder.Request(
        nativeLibraryDirectory = nativeLibraryDirectory,
        lockFilePath = lockFilePath,
        releaseKey = ReleaseKey.VOLUME_UP,
        feedback = feedback,
        invocationToken = invocationToken,
        applicationId = "io.example.touchquell",
        receiverClassName = "io.example.touchquell.root.RootBlockerStoppedReceiver"
    )
}
