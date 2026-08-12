package io.github.bzhangj13zzz.touchquell

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationIdentityTest {
    @Test
    fun applicationIdMatchesTouchQuellIdentity() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("io.github.bzhangj13zzz.touchquell", appContext.packageName)
    }
}
