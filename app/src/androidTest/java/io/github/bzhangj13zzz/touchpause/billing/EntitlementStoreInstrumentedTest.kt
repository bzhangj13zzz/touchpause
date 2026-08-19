package io.github.bzhangj13zzz.touchpause.billing

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntitlementStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var store: EntitlementStore

    @Before
    fun setUp() {
        clearPreferences()
        store = EntitlementStore(context)
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun onlyTenSuccessfulSessionsAreAvailable() {
        repeat(EntitlementStore.FREE_SESSION_LIMIT) {
            assertTrue(store.canStartSession())
            store.recordSuccessfulSession()
        }

        assertFalse(store.canStartSession())
        assertEquals(0, store.snapshot().remainingSessions)
        store.recordSuccessfulSession()
        assertEquals(EntitlementStore.FREE_SESSION_LIMIT, store.snapshot().usedSessions)
    }

    @Test
    fun cachedLifetimeEntitlementRestoresAccess() {
        repeat(EntitlementStore.FREE_SESSION_LIMIT) { store.recordSuccessfulSession() }
        assertFalse(store.canStartSession())

        assertTrue(store.setLifetimeUnlocked(true))
        assertTrue(store.canStartSession())
        assertFalse(store.setLifetimeUnlocked(true))
    }

    @Test
    fun reviewAccessRestoresAccessWithoutChangingTrialCount() {
        repeat(EntitlementStore.FREE_SESSION_LIMIT) { store.recordSuccessfulSession() }

        assertTrue(store.grantReviewAccess())
        assertTrue(store.canStartSession())
        store.recordSuccessfulSession()
        assertEquals(EntitlementStore.FREE_SESSION_LIMIT, store.snapshot().usedSessions)
    }

    private fun clearPreferences() {
        context.getSharedPreferences(EntitlementStore.PREFERENCES_NAME, 0)
            .edit()
            .clear()
            .commit()
    }
}
