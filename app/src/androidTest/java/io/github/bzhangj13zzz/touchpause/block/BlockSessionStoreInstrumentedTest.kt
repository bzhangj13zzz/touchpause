package io.github.bzhangj13zzz.touchpause.block

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun accessibilityOwnershipBlocksRootUntilReleased() {
        val sessions = BlockSessionStore(context)

        assertEquals("touchpause_runtime", BlockSessionStore.RUNTIME_PREFERENCES)
        assertNull(sessions.activeBackend())
        assertFalse(sessions.isBlocking())
        assertFalse(sessions.hasPendingRootInvocation())

        assertTrue(sessions.publishAccessibilityActive())
        assertEquals(Backend.ACCESSIBILITY, sessions.activeBackend())
        assertTrue(sessions.isBlocking())
        assertFalse(sessions.reserveRootInvocation("root-while-accessibility-is-active"))

        assertTrue(sessions.clearAccessibilityIfOwned())
        assertNull(sessions.activeBackend())
        assertFalse(sessions.isBlocking())
        assertFalse(sessions.clearAccessibilityIfOwned())
    }

    @Test
    fun rootStateChangesOnlyForTheOwningInvocationToken() {
        val sessions = BlockSessionStore(context)
        val ownerToken = "root-owner-${System.nanoTime()}"
        val otherToken = "not-the-owner"

        assertTrue(sessions.reserveRootInvocation(ownerToken))
        assertTrue(sessions.hasPendingRootInvocation())
        assertFalse(sessions.reserveRootInvocation(ownerToken))
        assertFalse(sessions.publishRootReady(otherToken))

        val pendingWrongClear = sessions.clearRootInvocation(otherToken)
        assertFalse(pendingWrongClear.activeWasCleared)
        assertFalse(pendingWrongClear.stateChanged)
        assertTrue(sessions.hasPendingRootInvocation())

        assertTrue(sessions.publishRootReady(ownerToken))
        assertEquals(Backend.ROOT, sessions.activeBackend())
        assertFalse(sessions.hasPendingRootInvocation())
        assertFalse(sessions.publishAccessibilityActive())

        val activeWrongClear = sessions.clearRootInvocation(otherToken)
        assertFalse(activeWrongClear.activeWasCleared)
        assertFalse(activeWrongClear.stateChanged)
        assertEquals(Backend.ROOT, sessions.activeBackend())

        val ownerClear = sessions.clearRootInvocation(ownerToken)
        assertTrue(ownerClear.activeWasCleared)
        assertTrue(ownerClear.stateChanged)
        assertNull(sessions.activeBackend())
        assertFalse(sessions.isBlocking())
    }

    @Test
    fun staleOwnershipIsDiscardedWhenTheBootCountChanges() {
        val currentBoot = Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.BOOT_COUNT
        )
        assertTrue(
            rawPreferences.edit()
                .putInt("boot_count", currentBoot + 1)
                .putString("active_backend", Backend.ROOT.persistedValue)
                .putString("active_token", "owner-from-another-boot")
                .putString("pending_root_token", "pending-from-another-boot")
                .commit()
        )

        val sessions = BlockSessionStore(context)

        assertNull(sessions.activeBackend())
        assertFalse(sessions.hasPendingRootInvocation())
        assertFalse(sessions.isBlocking())
        assertEquals(currentBoot, rawPreferences.getInt("boot_count", -1))
    }
}
