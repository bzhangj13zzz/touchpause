package io.github.bzhangj13zzz.touchpause.block

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlockSessionStoreInstrumentedTest {
    private lateinit var context: Context
    private lateinit var rawPreferences: SharedPreferences

    @Before
    fun clearRuntimeState() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        rawPreferences = context.getSharedPreferences(
            BlockSessionStore.RUNTIME_PREFERENCES,
            Context.MODE_PRIVATE
        )
        assertTrue(rawPreferences.edit().clear().commit())
    }

    @After
    fun restoreEmptyRuntimeState() {
        assertTrue(rawPreferences.edit().clear().commit())
    }

    @Test
    fun accessibilitySessionIsPublishedAndReleased() {
        val sessions = BlockSessionStore(context)

        assertFalse(sessions.isBlocking())
        assertTrue(sessions.publishAccessibilityActive())
        assertTrue(sessions.isBlocking())
        assertTrue(sessions.clearAccessibilityIfOwned())
        assertFalse(sessions.isBlocking())
        assertFalse(sessions.clearAccessibilityIfOwned())
    }
}
