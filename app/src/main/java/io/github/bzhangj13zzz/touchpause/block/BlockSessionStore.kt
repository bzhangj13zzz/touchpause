package io.github.bzhangj13zzz.touchpause.block

import android.content.Context
import android.content.SharedPreferences

/**
 * Shares advisory blocking state between the Accessibility service, settings, and Quick Settings.
 * Accessibility capture cannot outlive this app process, so stale persisted state is cleared once
 * when a new process first constructs the store.
 */
class BlockSessionStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        RUNTIME_PREFERENCES,
        Context.MODE_PRIVATE
    )

    init {
        synchronized(stateLock) {
            if (!processInitialized) {
                preferences.edit().clear().commit()
                processInitialized = true
            }
        }
    }

    fun isBlocking(): Boolean = preferences.getBoolean(KEY_ACCESSIBILITY_ACTIVE, false)

    /** Registers a listener for advisory backend-state changes. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Stops a listener previously registered with [registerListener]. */
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** Publishes state only after Accessibility has successfully applied input capture. */
    fun publishAccessibilityActive(): Boolean = synchronized(stateLock) {
        preferences.edit()
            .putBoolean(KEY_ACCESSIBILITY_ACTIVE, true)
            .commit()
    }

    /** Clears state only when an Accessibility session is currently published. */
    fun clearAccessibilityIfOwned(): Boolean = synchronized(stateLock) {
        if (!preferences.getBoolean(KEY_ACCESSIBILITY_ACTIVE, false)) {
            return@synchronized false
        }

        preferences.edit()
            .remove(KEY_ACCESSIBILITY_ACTIVE)
            .commit()
    }

    companion object {
        const val RUNTIME_PREFERENCES = "touchpause_runtime"

        private const val KEY_ACCESSIBILITY_ACTIVE = "accessibility_active"

        private val stateLock = Any()
        private var processInitialized = false
    }
}
