package io.github.bzhangj13zzz.touchpause.settings

import android.os.Bundle
import android.content.res.Configuration
import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import io.github.bzhangj13zzz.touchpause.R

/** Minimal launcher activity; day-to-day operation stays in Quick Settings. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.settings_activity)
        val settingsRoot = findViewById<View>(R.id.settings_root)
        val toolbar = findViewById<MaterialToolbar>(R.id.settings_toolbar)
        val settingsContent = findViewById<View>(R.id.settings)
        val toolbarContentHeight = toolbar.layoutParams.height
        setSupportActionBar(toolbar)
        // API 35+ forces a transparent edge-to-edge navigation area; older releases use black.
        val usesDarkNavigationIcons = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, settingsRoot).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = usesDarkNavigationIcons
        }
        ViewCompat.setOnApplyWindowInsetsListener(settingsRoot) { _, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            // The toolbar paints behind the status bar; preferences own the other safe edges.
            toolbar.layoutParams = toolbar.layoutParams.apply {
                height = toolbarContentHeight + systemBars.top
            }
            toolbar.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            settingsContent.setPadding(
                systemBars.left,
                0,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
        if (savedInstanceState == null) {
            val fragment = SettingsFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(
                        SettingsFragment.ARG_SHOW_ACCESSIBILITY_DISCLOSURE,
                        intent.getBooleanExtra(EXTRA_SHOW_ACCESSIBILITY_DISCLOSURE, false)
                    )
                }
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, fragment)
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    companion object {
        const val EXTRA_SHOW_ACCESSIBILITY_DISCLOSURE =
            "io.github.bzhangj13zzz.touchpause.extra.SHOW_ACCESSIBILITY_DISCLOSURE"
    }
}
