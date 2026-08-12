package io.github.bzhangj13zzz.touchquell.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.bzhangj13zzz.touchquell.R
import io.github.bzhangj13zzz.touchquell.accessibility.AccessibilityStatus
import io.github.bzhangj13zzz.touchquell.accessibility.TouchBlockAccessibilityService
import io.github.bzhangj13zzz.touchquell.block.BlockSessionStore
import io.github.bzhangj13zzz.touchquell.preferences.UserPreferences
import io.github.bzhangj13zzz.touchquell.tile.TouchBlockTileService

/** One-time setup and safety preferences for the Quick Settings-first experience. */
class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var userPreferences: UserPreferences
    private lateinit var sessions: BlockSessionStore
    private val accessibilityStatusRefresh = Runnable {
        if (isAdded) updateAccessibilitySummary()
    }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            UserPreferences.KEY_RELEASE_KEY -> {
                updateReleaseKeySummary()
                updateAccessibilitySummary()
            }
            UserPreferences.KEY_TILE_ADDED -> updateTileSetupSummary()
        }
    }
    private val sessionListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateReleaseKeySummary()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        userPreferences = UserPreferences(requireContext())
        sessions = BlockSessionStore(requireContext())

        findPreference<Preference>(KEY_TILE_SETUP)?.setOnPreferenceClickListener {
            requestTileSetup()
            true
        }

        val accessibilitySetup = findPreference<Preference>(KEY_ACCESSIBILITY_SETUP)
        accessibilitySetup?.isVisible =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        accessibilitySetup?.setOnPreferenceClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                AccessibilityStatus.isEnabled(requireContext())
            ) {
                openAccessibilitySettings()
            } else {
                showAccessibilitySetupDialog()
            }
            true
        }

        updateSummaries()
    }

    override fun onResume() {
        super.onResume()
        userPreferences.sharedPreferences.registerOnSharedPreferenceChangeListener(
            preferenceListener
        )
        sessions.registerListener(sessionListener)
        updateSummaries()
        view?.postDelayed(accessibilityStatusRefresh, ACCESSIBILITY_REFRESH_DELAY_MS)
    }

    override fun onPause() {
        view?.removeCallbacks(accessibilityStatusRefresh)
        userPreferences.sharedPreferences.unregisterOnSharedPreferenceChangeListener(
            preferenceListener
        )
        sessions.unregisterListener(sessionListener)
        super.onPause()
    }

    /** Uses Android 13's supported add-tile prompt, with manual guidance on older releases. */
    private fun requestTileSetup() {
        val context = requireContext()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.tile_setup_manual, Toast.LENGTH_LONG).show()
            return
        }

        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            Toast.makeText(context, R.string.tile_not_added_message, Toast.LENGTH_SHORT).show()
            return
        }

        statusBarManager.requestAddTileService(
            ComponentName(context, TouchBlockTileService::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(context, R.drawable.ic_touchquell_mark_24),
            context.mainExecutor
        ) { result ->
            when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                    markTileAdded()
                    Toast.makeText(context, R.string.tile_added_message, Toast.LENGTH_SHORT).show()
                }
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                    markTileAdded()
                    Toast.makeText(
                        context,
                        R.string.tile_already_added_message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> Toast.makeText(
                    context,
                    R.string.tile_not_added_message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun markTileAdded() {
        userPreferences.setTileAdded(true)
        updateTileSetupSummary()
    }

    private fun updateSummaries() {
        updateTileSetupSummary()
        updateAccessibilitySummary()
        updateReleaseKeySummary()
    }

    private fun updateTileSetupSummary() {
        val summary = when {
            userPreferences.isTileAdded() -> R.string.tile_added_summary
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> R.string.tile_setup_manual
            else -> R.string.tile_setup_summary
        }
        findPreference<Preference>(KEY_TILE_SETUP)?.setSummary(summary)
    }

    private fun updateAccessibilitySummary() {
        val preference = findPreference<Preference>(KEY_ACCESSIBILITY_SETUP) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            preference.setSummary(R.string.accessibility_unsupported_summary)
            return
        }

        val summary = when {
            !AccessibilityStatus.isEnabled(requireContext()) ->
                R.string.accessibility_setup_summary
            AccessibilityStatus.isTouchExplorationEnabled(requireContext()) ->
                R.string.accessibility_touch_exploration_summary
            AccessibilityStatus.hasKeyFilterConflict(requireContext()) ->
                R.string.accessibility_key_filter_conflict_summary
            !userPreferences.releaseKey().supportsAccessibility ->
                R.string.accessibility_power_fallback_summary
            TouchBlockAccessibilityService.isReady() ->
                R.string.accessibility_enabled_summary
            else -> R.string.accessibility_waiting_summary
        }
        preference.setSummary(summary)
    }

    /** An active session snapshots its key, so settings changes explicitly apply next time. */
    private fun updateReleaseKeySummary() {
        val preference = findPreference<ListPreference>(UserPreferences.KEY_RELEASE_KEY) ?: return
        preference.summaryProvider = null
        val selectedEntry = preference.entry ?: getString(R.string.release_volume_up)
        preference.summary = if (sessions.isBlocking()) {
            getString(R.string.release_key_next_session_summary, selectedEntry)
        } else {
            selectedEntry
        }
    }

    private fun showAccessibilitySetupDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.accessibility_setup_dialog_title)
            .setMessage(R.string.accessibility_setup_dialog_message)
            .setPositiveButton(R.string.open_app_info) { _, _ ->
                val packageUri = Uri.parse("package:${requireContext().packageName}")
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
            }
            .setNegativeButton(R.string.open_accessibility_settings) { _, _ ->
                openAccessibilitySettings()
            }
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private companion object {
        const val KEY_TILE_SETUP = "tile_setup"
        const val KEY_ACCESSIBILITY_SETUP = "accessibility_setup"
        const val ACCESSIBILITY_REFRESH_DELAY_MS = 1_000L
    }
}
