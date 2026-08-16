package io.github.bzhangj13zzz.touchpause.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi

/** Read-only checks that explain whether Android can provide TouchPause's rootless backend. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
object AccessibilityStatus {
    /** Reads the user-enabled component list, including the short interval before service binding. */
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, TouchBlockAccessibilityService::class.java)
        val enabledComponents = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledComponents.split(':').any { flattenedName ->
            ComponentName.unflattenFromString(flattenedName) == expected
        }
    }

    fun isTouchExplorationEnabled(context: Context): Boolean =
        accessibilityManager(context)?.isTouchExplorationEnabled == true

    /** Key filtering is exclusive, so another requesting service makes release unreliable. */
    fun hasKeyFilterConflict(context: Context): Boolean {
        val manager = accessibilityManager(context) ?: return true
        val expected = ComponentName(context, TouchBlockAccessibilityService::class.java)
        return runCatching {
            manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { info ->
                val service = info.resolveInfo?.serviceInfo ?: return@any false
                val component = ComponentName(service.packageName, service.name)
                component != expected &&
                    info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0
            }
        }.getOrDefault(true)
    }

    private fun accessibilityManager(context: Context): AccessibilityManager? =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
}
