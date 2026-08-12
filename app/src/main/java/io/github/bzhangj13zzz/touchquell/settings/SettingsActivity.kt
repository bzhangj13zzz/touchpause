package io.github.bzhangj13zzz.touchquell.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.bzhangj13zzz.touchquell.R

/** Minimal launcher activity; day-to-day operation stays in Quick Settings. */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }
}
