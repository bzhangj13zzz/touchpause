package io.github.bzhangj13zzz.touchpause.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bzhangj13zzz.touchpause.block.FeedbackOptions
import io.github.bzhangj13zzz.touchpause.block.ReleaseKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserPreferencesInstrumentedTest {
    private lateinit var context: Context
    private lateinit var rawPreferences: SharedPreferences

    @Before
    fun clearPreferences() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        rawPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        assertTrue(rawPreferences.edit().clear().commit())
    }

    @After
    fun restoreEmptyPreferences() {
        assertTrue(rawPreferences.edit().clear().commit())
    }

    @Test
    fun freshInstallUsesSafeDefaultsWithoutAccessibilityConsent() {
        val preferences = UserPreferences(context)

        assertFalse(preferences.hasAccessibilityConsent())
        assertSame(ReleaseKey.VOLUME_UP, preferences.releaseKey())
        assertEquals(FeedbackOptions(), preferences.feedbackOptions())
        assertFalse(preferences.isTileAdded())
    }

    @Test
    fun accessibilityConsentPersistsAndCanBeRevoked() {
        val preferences = UserPreferences(context)

        assertTrue(preferences.setAccessibilityConsent(true))
        assertTrue(UserPreferences(context).hasAccessibilityConsent())

        assertTrue(preferences.setAccessibilityConsent(false))
        assertFalse(UserPreferences(context).hasAccessibilityConsent())
        assertFalse(
            rawPreferences.contains(UserPreferences.KEY_ACCESSIBILITY_CONSENT_VERSION)
        )
    }

    @Test
    fun unknownAccessibilityDisclosureVersionDoesNotGrantConsent() {
        assertTrue(
            rawPreferences.edit()
                .putInt(UserPreferences.KEY_ACCESSIBILITY_CONSENT_VERSION, Int.MAX_VALUE)
                .commit()
        )

        assertFalse(UserPreferences(context).hasAccessibilityConsent())
    }
}
