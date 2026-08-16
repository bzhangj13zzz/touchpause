package io.github.bzhangj13zzz.touchpause

import android.content.res.Configuration
import android.content.pm.PackageManager
import android.util.Xml
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ApplicationIdentityTest {
    @Test
    fun applicationIdMatchesTouchPauseIdentity() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        assertEquals("io.github.bzhangj13zzz.touchpause", appContext.packageName)
        assertEquals(
            "TouchPause",
            appContext.packageManager.getApplicationLabel(appContext.applicationInfo).toString()
        )
    }

    @Test
    fun packagedNativeHelperUsesTouchPauseIdentity() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val nativeLibraryDirectory = File(appContext.applicationInfo.nativeLibraryDir)

        assertTrue(
            "Expected the installed ABI's TouchPause helper in $nativeLibraryDirectory",
            File(nativeLibraryDirectory, "libtouchpause-input.so").isFile
        )
        assertFalse(File(nativeLibraryDirectory, "touchpause-input.so").exists())
        assertFalse(File(nativeLibraryDirectory, "touchquell-input.so").exists())
    }

    @Test
    fun manifestContainsOnlyExpectedNetworkAndBillingPermissions() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val packageInfo = appContext.packageManager.getPackageInfo(
            appContext.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue("Google Play Billing permission is missing", BILLING_PERMISSION in requested)
        assertTrue("Billing Library network permission is missing", INTERNET_PERMISSION in requested)
        assertTrue(
            "Billing Library network-state permission is missing",
            NETWORK_STATE_PERMISSION in requested
        )
    }

    @Test
    fun appLanguageSelectorHasStableUniqueLocaleTags() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val entries = appContext.resources.getStringArray(R.array.app_language_entries).toList()
        val values = appContext.resources.getStringArray(R.array.app_language_values).toList()
        val expectedValues = listOf(
            "system",
            "en",
            "es",
            "fr",
            "de",
            "pt-BR",
            "ja",
            "ko",
            "zh-Hans",
            "zh-Hant"
        )
        val expectedQuickSettingsHeaders = mapOf(
            "es" to "Ajustes rápidos",
            "fr" to "Réglages rapides",
            "de" to "Schnelleinstellungen",
            "pt-BR" to "Configurações rápidas",
            "ja" to "クイック設定",
            "ko" to "빠른 설정",
            "zh-Hans" to "快捷设置",
            "zh-Hant" to "快速設定"
        )

        assertEquals(entries.size, values.size)
        assertEquals(expectedValues, values)
        assertEquals(values.size, values.distinct().size)

        values.drop(1).forEach { languageTag ->
            val locale = Locale.forLanguageTag(languageTag)
            assertFalse("Invalid locale tag: $languageTag", locale.language.isBlank())

            val configuration = Configuration(appContext.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = appContext.createConfigurationContext(configuration)
            assertEquals("TouchPause", localizedContext.getString(R.string.app_name))
            expectedQuickSettingsHeaders[languageTag]?.let { expectedHeader ->
                assertEquals(
                    "Unexpected resource selection for $languageTag",
                    expectedHeader,
                    localizedContext.getString(R.string.quick_settings_header)
                )
            }
        }
    }

    @Test
    fun accessibilityMetadataIsNotToolAndDoesNotFilterKeysWhileIdle() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val parser = appContext.resources.getXml(R.xml.accessibility_service_config)

        try {
            while (parser.eventType != XmlPullParser.START_TAG &&
                parser.eventType != XmlPullParser.END_DOCUMENT
            ) {
                parser.next()
            }

            assertEquals("accessibility-service", parser.name)
            val attributes = Xml.asAttributeSet(parser)
            assertFalse(
                attributes.getAttributeBooleanValue(ANDROID_NAMESPACE, "isAccessibilityTool", true)
            )
            assertFalse(
                attributes.getAttributeBooleanValue(
                    ANDROID_NAMESPACE,
                    "canRetrieveWindowContent",
                    true
                )
            )
            assertTrue(
                attributes.getAttributeBooleanValue(
                    ANDROID_NAMESPACE,
                    "canRequestFilterKeyEvents",
                    false
                )
            )
            assertNull(attributes.getAttributeValue(ANDROID_NAMESPACE, "accessibilityFlags"))
        } finally {
            parser.close()
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        const val BILLING_PERMISSION = "com.android.vending.BILLING"
        const val INTERNET_PERMISSION = "android.permission.INTERNET"
        const val NETWORK_STATE_PERMISSION = "android.permission.ACCESS_NETWORK_STATE"
    }
}
