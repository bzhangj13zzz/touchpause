package io.github.bzhangj13zzz.touchquell.block

import android.content.Context
import android.content.SharedPreferences

/**
 * Owns persisted advisory state for the active blocker and pending root invocation.
 *
 * The accessibility service and native root helper remain the sources of truth for actual input
 * capture. Synchronous writes are intentional: the app process can die while the root command
 * continues, so its ownership token must be durable before the child starts or reports ready.
 */
class BlockSessionStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        RUNTIME_PREFERENCES,
        Context.MODE_PRIVATE
    )

    fun activeBackend(): Backend? = Backend.fromPersistedValue(
        preferences.getString(KEY_ACTIVE_BACKEND, null)
    )

    fun isBlocking(): Boolean = activeBackend() != null

    fun hasPendingRootInvocation(): Boolean = preferences.contains(KEY_PENDING_ROOT_TOKEN)

    /** Registers a listener for advisory backend-state changes. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    /** Stops a listener previously registered with [registerListener]. */
    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** Claims advisory ownership for Accessibility unless root already owns or is starting. */
    fun publishAccessibilityActive(): Boolean = synchronized(stateLock) {
        val activeBackend = Backend.fromPersistedValue(
            preferences.getString(KEY_ACTIVE_BACKEND, null)
        )
        if (activeBackend == Backend.ROOT || preferences.contains(KEY_PENDING_ROOT_TOKEN)) {
            return@synchronized false
        }

        preferences.edit()
            .putString(KEY_ACTIVE_BACKEND, Backend.ACCESSIBILITY.persistedValue)
            .remove(KEY_ACTIVE_TOKEN)
            .commit()
    }

    /** Clears state only when Accessibility still owns it. */
    fun clearAccessibilityIfOwned(): Boolean = synchronized(stateLock) {
        if (preferences.getString(KEY_ACTIVE_BACKEND, null) !=
            Backend.ACCESSIBILITY.persistedValue
        ) {
            return@synchronized false
        }

        preferences.edit()
            .remove(KEY_ACTIVE_BACKEND)
            .remove(KEY_ACTIVE_TOKEN)
            .commit()
    }

    /**
     * Reserves root startup unless Accessibility owns blocking or this process already has an
     * invocation in flight. A persisted token from an older process is replaceable, allowing a
     * new tile tap to reconcile a root helper that survived process death.
     */
    fun reserveRootInvocation(token: String): Boolean = synchronized(stateLock) {
        if (preferences.getString(KEY_ACTIVE_BACKEND, null) ==
            Backend.ACCESSIBILITY.persistedValue
        ) {
            return@synchronized false
        }

        val persistedToken = preferences.getString(KEY_PENDING_ROOT_TOKEN, null)
        if (persistedToken != null && persistedToken == processPendingRootToken) {
            return@synchronized false
        }

        val committed = preferences.edit().putString(KEY_PENDING_ROOT_TOKEN, token).commit()
        if (committed) processPendingRootToken = token
        committed
    }

    /** Promotes only the root invocation that this process reserved and that is still pending. */
    fun publishRootReady(token: String): Boolean = synchronized(stateLock) {
        if (processPendingRootToken != token ||
            preferences.getString(KEY_PENDING_ROOT_TOKEN, null) != token ||
            preferences.getString(KEY_ACTIVE_BACKEND, null) ==
            Backend.ACCESSIBILITY.persistedValue
        ) {
            return@synchronized false
        }

        val committed = preferences.edit()
            .putString(KEY_ACTIVE_BACKEND, Backend.ROOT.persistedValue)
            .putString(KEY_ACTIVE_TOKEN, token)
            .remove(KEY_PENDING_ROOT_TOKEN)
            .commit()
        if (committed) processPendingRootToken = null
        committed
    }

    /**
     * Clears pending and active state only for [token].
     *
     * [RootClearResult.activeWasCleared] elects exactly one observer to show stop feedback, while
     * [RootClearResult.stateChanged] also reports a pending-token cleanup that needs a tile refresh.
     */
    fun clearRootInvocation(token: String): RootClearResult {
        var stateChanged = false
        val activeWasCleared = synchronized(stateLock) {
            val ownsActive = preferences.getString(KEY_ACTIVE_BACKEND, null) ==
                Backend.ROOT.persistedValue &&
                preferences.getString(KEY_ACTIVE_TOKEN, null) == token
            val ownsPending = preferences.getString(KEY_PENDING_ROOT_TOKEN, null) == token

            if (ownsActive || ownsPending) {
                val editor = preferences.edit()
                if (ownsActive) {
                    editor.remove(KEY_ACTIVE_BACKEND).remove(KEY_ACTIVE_TOKEN)
                }
                if (ownsPending) editor.remove(KEY_PENDING_ROOT_TOKEN)
                stateChanged = editor.commit()
            }

            if (processPendingRootToken == token && (!ownsPending || stateChanged)) {
                processPendingRootToken = null
            }
            ownsActive && stateChanged
        }
        return RootClearResult(activeWasCleared, stateChanged)
    }

    data class RootClearResult(
        val activeWasCleared: Boolean,
        val stateChanged: Boolean
    )

    companion object {
        const val RUNTIME_PREFERENCES = "touchquell_runtime"

        private const val KEY_ACTIVE_BACKEND = "active_backend"
        private const val KEY_ACTIVE_TOKEN = "active_token"
        private const val KEY_PENDING_ROOT_TOKEN = "pending_root_token"

        private val stateLock = Any()
        private var processPendingRootToken: String? = null
    }
}
