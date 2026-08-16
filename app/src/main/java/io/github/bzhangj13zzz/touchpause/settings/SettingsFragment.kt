package io.github.bzhangj13zzz.touchpause.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.bzhangj13zzz.touchpause.R
import io.github.bzhangj13zzz.touchpause.accessibility.AccessibilityStatus
import io.github.bzhangj13zzz.touchpause.accessibility.TouchBlockAccessibilityService
import io.github.bzhangj13zzz.touchpause.billing.EntitlementStore
import io.github.bzhangj13zzz.touchpause.billing.PlayBillingManager
import io.github.bzhangj13zzz.touchpause.block.BlockSessionStore
import io.github.bzhangj13zzz.touchpause.preferences.UserPreferences
import io.github.bzhangj13zzz.touchpause.tile.TileRefresher
import io.github.bzhangj13zzz.touchpause.tile.TouchBlockTileService

/** One-time setup and safety preferences for the Quick Settings-first experience. */
class SettingsFragment : PreferenceFragmentCompat() {
    private lateinit var userPreferences: UserPreferences
    private lateinit var sessions: BlockSessionStore
    private lateinit var entitlements: EntitlementStore
    private lateinit var billingManager: PlayBillingManager
    private var showAccessibilityDisclosureOnResume = false
    private var showPurchaseWhenReady = false
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
    private val entitlementListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateAccessSummary()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        showAccessibilityDisclosureOnResume =
            arguments?.getBoolean(ARG_SHOW_ACCESSIBILITY_DISCLOSURE, false) == true
        showPurchaseWhenReady = arguments?.getBoolean(ARG_SHOW_PURCHASE, false) == true
        arguments?.remove(ARG_SHOW_ACCESSIBILITY_DISCLOSURE)
        arguments?.remove(ARG_SHOW_PURCHASE)
        userPreferences = UserPreferences(requireContext())
        sessions = BlockSessionStore(requireContext())
        entitlements = EntitlementStore(requireContext())
        billingManager = PlayBillingManager(
            requireContext(),
            entitlements,
            object : PlayBillingManager.Listener {
                override fun onBillingStateChanged() {
                    if (!isAdded) return
                    updateAccessSummary()
                    maybeShowRequestedPurchase()
                }

                override fun onPurchaseCompleted() {
                    if (!isAdded) return
                    Toast.makeText(
                        requireContext(),
                        R.string.purchase_completed,
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onBillingUnavailable(message: Int) {
                    if (!isAdded) return
                    showPurchaseWhenReady = false
                    Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                }
            }
        )

        findPreference<Preference>(KEY_TILE_SETUP)?.setOnPreferenceClickListener {
            requestTileSetup()
            true
        }

        findPreference<Preference>(KEY_LIFETIME_ACCESS)?.setOnPreferenceClickListener {
            requestLifetimePurchase()
            true
        }

        val accessibilitySetup = findPreference<Preference>(KEY_ACCESSIBILITY_SETUP)
        accessibilitySetup?.setOnPreferenceClickListener {
            if (userPreferences.hasAccessibilityConsent()) {
                openAccessibilitySettings()
            } else {
                showAccessibilityDisclosure()
            }
            true
        }

        configureLanguagePreference()
        findPreference<Preference>(KEY_ABOUT)?.setOnPreferenceClickListener {
            showAboutDialog()
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
        entitlements.registerListener(entitlementListener)
        updateSummaries()
        billingManager.connect()
        if (showAccessibilityDisclosureOnResume && !userPreferences.hasAccessibilityConsent()) {
            showAccessibilityDisclosureOnResume = false
            view?.post { if (isAdded) showAccessibilityDisclosure() }
        }
        view?.postDelayed(accessibilityStatusRefresh, ACCESSIBILITY_REFRESH_DELAY_MS)
    }

    override fun onPause() {
        view?.removeCallbacks(accessibilityStatusRefresh)
        userPreferences.sharedPreferences.unregisterOnSharedPreferenceChangeListener(
            preferenceListener
        )
        sessions.unregisterListener(sessionListener)
        entitlements.unregisterListener(entitlementListener)
        super.onPause()
    }

    override fun onDestroy() {
        billingManager.close()
        super.onDestroy()
    }

    /** Uses Android's supported user-initiated add-tile prompt. */
    private fun requestTileSetup() {
        val context = requireContext()
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            Toast.makeText(context, R.string.tile_not_added_message, Toast.LENGTH_SHORT).show()
            return
        }

        statusBarManager.requestAddTileService(
            ComponentName(context, TouchBlockTileService::class.java),
            getString(R.string.app_name),
            Icon.createWithResource(context, R.drawable.ic_touchpause_mark_24),
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
        updateLanguagePreference()
        updateAccessSummary()
    }

    private fun updateAccessSummary() {
        val preference = findPreference<Preference>(KEY_LIFETIME_ACCESS) ?: return
        val access = entitlements.snapshot()
        when {
            access.lifetimeUnlocked -> {
                preference.setTitle(R.string.lifetime_access_title)
                preference.setSummary(R.string.lifetime_access_unlocked)
                preference.isSelectable = false
            }
            billingManager.formattedPrice != null -> {
                preference.setTitle(R.string.unlock_lifetime_title)
                preference.summary = resources.getQuantityString(
                    R.plurals.trial_sessions_with_price,
                    access.remainingSessions,
                    access.remainingSessions,
                    billingManager.formattedPrice
                )
                preference.isSelectable = true
            }
            billingManager.productQueryComplete -> {
                preference.setTitle(R.string.unlock_lifetime_title)
                preference.summary = resources.getQuantityString(
                    R.plurals.trial_sessions_purchase_unavailable,
                    access.remainingSessions,
                    access.remainingSessions
                )
                preference.isSelectable = true
            }
            else -> {
                preference.setTitle(R.string.unlock_lifetime_title)
                preference.summary = resources.getQuantityString(
                    R.plurals.trial_sessions_checking_play,
                    access.remainingSessions,
                    access.remainingSessions
                )
                preference.isSelectable = true
            }
        }
    }

    /** Explains the trial and price before handing control to Google Play's purchase UI. */
    private fun requestLifetimePurchase() {
        if (entitlements.snapshot().lifetimeUnlocked) return
        val price = billingManager.formattedPrice
        if (price == null) {
            showPurchaseWhenReady = true
            billingManager.connect()
            if (billingManager.productQueryComplete) {
                showPurchaseWhenReady = false
                Toast.makeText(requireContext(), R.string.billing_unavailable, Toast.LENGTH_LONG)
                    .show()
            }
            return
        }

        showPurchaseWhenReady = false
        val remaining = entitlements.snapshot().remainingSessions
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.unlock_dialog_title)
            .setMessage(
                resources.getQuantityString(
                    R.plurals.unlock_dialog_message,
                    remaining,
                    remaining,
                    price
                )
            )
            .setNegativeButton(R.string.not_now, null)
            .setPositiveButton(R.string.unlock_for_price) { _, _ ->
                if (!billingManager.launchLifetimePurchase(requireActivity())) {
                    Toast.makeText(
                        requireContext(),
                        R.string.billing_unavailable,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }

    private fun maybeShowRequestedPurchase() {
        if (!showPurchaseWhenReady) return
        when {
            entitlements.snapshot().lifetimeUnlocked -> showPurchaseWhenReady = false
            billingManager.formattedPrice != null -> requestLifetimePurchase()
            billingManager.productQueryComplete -> {
                showPurchaseWhenReady = false
                Toast.makeText(requireContext(), R.string.billing_unavailable, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private fun updateTileSetupSummary() {
        val summary = if (userPreferences.isTileAdded()) {
            R.string.tile_added_summary
        } else {
            R.string.tile_setup_summary
        }
        findPreference<Preference>(KEY_TILE_SETUP)?.setSummary(summary)
    }

    private fun updateAccessibilitySummary() {
        val preference = findPreference<Preference>(KEY_ACCESSIBILITY_SETUP) ?: return
        val summary = when {
            !userPreferences.hasAccessibilityConsent() ->
                R.string.accessibility_consent_required_summary
            !AccessibilityStatus.isEnabled(requireContext()) ->
                R.string.accessibility_setup_summary
            AccessibilityStatus.isTouchExplorationEnabled(requireContext()) ->
                R.string.accessibility_touch_exploration_summary
            AccessibilityStatus.hasKeyFilterConflict(requireContext()) ->
                R.string.accessibility_key_filter_conflict_summary
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

    /** Shows Play's required standalone disclosure immediately before system settings. */
    private fun showAccessibilityDisclosure() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.accessibility_disclosure_title)
            .setMessage(R.string.accessibility_disclosure_message)
            .setPositiveButton(R.string.accessibility_disclosure_agree) { _, _ ->
                if (userPreferences.setAccessibilityConsent(true)) {
                    openAccessibilitySettings()
                }
            }
            .setNegativeButton(R.string.accessibility_disclosure_decline, null)
            .show()
    }

    /** Keeps the in-app picker and Android 13+ App Languages setting synchronized. */
    private fun configureLanguagePreference() {
        findPreference<ListPreference>(KEY_APP_LANGUAGE)?.setOnPreferenceChangeListener { _, value ->
            val languageTag = value as? String ?: return@setOnPreferenceChangeListener false
            val locales = if (languageTag == SYSTEM_LANGUAGE_VALUE) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(languageTag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
            TileRefresher.request(requireContext())
            true
        }
    }

    private fun updateLanguagePreference() {
        val preference = findPreference<ListPreference>(KEY_APP_LANGUAGE) ?: return
        val activeTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        preference.value = activeTags.ifEmpty { SYSTEM_LANGUAGE_VALUE }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.about_dialog_title)
            .setMessage(R.string.about_dialog_message)
            .setNeutralButton(R.string.open_privacy_policy) { _, _ ->
                val privacyUri = Uri.parse(getString(R.string.privacy_policy_url))
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, privacyUri))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(
                        requireContext(),
                        R.string.no_browser_available,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setPositiveButton(R.string.close, null)
            .show()
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    companion object {
        const val ARG_SHOW_ACCESSIBILITY_DISCLOSURE = "show_accessibility_disclosure"
        const val ARG_SHOW_PURCHASE = "show_purchase"
        private const val KEY_TILE_SETUP = "tile_setup"
        private const val KEY_LIFETIME_ACCESS = "lifetime_access"
        private const val KEY_ACCESSIBILITY_SETUP = "accessibility_setup"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_ABOUT = "about"
        private const val SYSTEM_LANGUAGE_VALUE = "system"
        private const val ACCESSIBILITY_REFRESH_DELAY_MS = 1_000L
    }
}
