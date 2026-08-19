package io.github.bzhangj13zzz.touchpause.settings

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bzhangj13zzz.touchpause.R
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAppearanceInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun clearUserPreferences() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit())
    }

    @After
    fun restoreEmptyUserPreferences() {
        assertTrue(PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit())
    }

    @Test
    fun feedbackSwitchUpdatesWithoutScrolling() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            val title = context.getString(R.string.feedback_start_message_title)
            val switch = allOf(
                withId(androidx.preference.R.id.switchWidget),
                withParent(hasSibling(hasDescendant(withText(title))))
            )

            onView(switch).check(matches(isChecked()))
            onView(withText(title)).perform(click())
            onView(switch).check(matches(isNotChecked()))
        }
    }

    @Test
    fun darkDialogActionsUseMintForContrast() {
        val nightConfiguration = Configuration(context.resources.configuration).apply {
            uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or
                Configuration.UI_MODE_NIGHT_YES
        }
        val nightContext = context.createConfigurationContext(nightConfiguration)
        val dialogContext = ContextThemeWrapper(
            nightContext,
            R.style.ThemeOverlay_TouchPause_MaterialAlertDialog
        )
        val colorValue = TypedValue()

        assertTrue(
            dialogContext.theme.resolveAttribute(
                com.google.android.material.R.attr.colorPrimary,
                colorValue,
                true
            )
        )
        val resolvedColor = if (colorValue.resourceId != 0) {
            ContextCompat.getColor(dialogContext, colorValue.resourceId)
        } else {
            colorValue.data
        }
        assertEquals(ContextCompat.getColor(nightContext, R.color.touchpause_mint), resolvedColor)
    }
}
