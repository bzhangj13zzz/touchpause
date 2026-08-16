package io.github.bzhangj13zzz.touchpause.billing

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.max

/**
 * Stores the local trial counter and the last Google Play lifetime-entitlement result.
 *
 * A successful block consumes one trial session. Purchase restoration can replace the cached
 * entitlement whenever Google Play is reachable; the cache keeps a paid user working offline.
 */
class EntitlementStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun snapshot(): Snapshot = synchronized(lock) {
        val usedSessions = preferences.getInt(KEY_USED_SESSIONS, 0)
            .coerceIn(0, FREE_SESSION_LIMIT)
        Snapshot(
            lifetimeUnlocked = preferences.getBoolean(KEY_LIFETIME_UNLOCKED, false),
            usedSessions = usedSessions
        )
    }

    fun canStartSession(): Boolean = snapshot().canStartSession

    /** Records one successfully started block and returns the resulting access state. */
    fun recordSuccessfulSession(): Snapshot = synchronized(lock) {
        val current = snapshot()
        if (current.lifetimeUnlocked || current.usedSessions >= FREE_SESSION_LIMIT) {
            return@synchronized current
        }

        val updated = current.usedSessions + 1
        preferences.edit().putInt(KEY_USED_SESSIONS, updated).commit()
        Snapshot(lifetimeUnlocked = false, usedSessions = updated)
    }

    /** Caches the latest successful Google Play ownership query for offline use. */
    fun setLifetimeUnlocked(unlocked: Boolean): Boolean = synchronized(lock) {
        if (preferences.getBoolean(KEY_LIFETIME_UNLOCKED, false) == unlocked) {
            return@synchronized false
        }
        preferences.edit().putBoolean(KEY_LIFETIME_UNLOCKED, unlocked).commit()
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    data class Snapshot(
        val lifetimeUnlocked: Boolean,
        val usedSessions: Int
    ) {
        val remainingSessions: Int = max(0, FREE_SESSION_LIMIT - usedSessions)
        val canStartSession: Boolean = lifetimeUnlocked || remainingSessions > 0
    }

    companion object {
        const val FREE_SESSION_LIMIT = 10
        const val PREFERENCES_NAME = "touchpause_entitlement"

        private const val KEY_USED_SESSIONS = "successful_trial_sessions"
        private const val KEY_LIFETIME_UNLOCKED = "lifetime_unlocked"
        private val lock = Any()
    }
}
