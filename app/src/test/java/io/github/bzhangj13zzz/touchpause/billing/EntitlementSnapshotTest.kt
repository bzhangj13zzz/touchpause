package io.github.bzhangj13zzz.touchpause.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementSnapshotTest {
    @Test
    fun freshTrialHasTenSessions() {
        val snapshot = EntitlementStore.Snapshot(
            lifetimeUnlocked = false,
            reviewAccessEnabled = false,
            usedSessions = 0
        )

        assertEquals(10, snapshot.remainingSessions)
        assertTrue(snapshot.canStartSession)
    }

    @Test
    fun tenthSessionExhaustsTrial() {
        val snapshot = EntitlementStore.Snapshot(
            lifetimeUnlocked = false,
            reviewAccessEnabled = false,
            usedSessions = 10
        )

        assertEquals(0, snapshot.remainingSessions)
        assertFalse(snapshot.canStartSession)
    }

    @Test
    fun lifetimePurchaseOverridesExhaustedTrial() {
        val snapshot = EntitlementStore.Snapshot(
            lifetimeUnlocked = true,
            reviewAccessEnabled = false,
            usedSessions = 10
        )

        assertEquals(0, snapshot.remainingSessions)
        assertTrue(snapshot.canStartSession)
    }

    @Test
    fun reviewAccessOverridesExhaustedTrial() {
        val snapshot = EntitlementStore.Snapshot(
            lifetimeUnlocked = false,
            reviewAccessEnabled = true,
            usedSessions = 10
        )

        assertTrue(snapshot.canStartSession)
    }
}
