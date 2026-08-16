package io.github.bzhangj13zzz.touchpause.tile

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import io.github.bzhangj13zzz.touchpause.R
import io.github.bzhangj13zzz.touchpause.accessibility.TouchBlockAccessibilityService
import io.github.bzhangj13zzz.touchpause.block.BlockSessionStore

/** Transparent trampoline that lets Quick Settings collapse before touch/stylus capture begins. */
class AccessibilityToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val handled = TouchBlockAccessibilityService.requestToggle()
        if (!handled) {
            val sessions = BlockSessionStore(this)
            if (sessions.clearAccessibilityIfOwned()) {
                TileRefresher.request(this)
            }
            Toast.makeText(this, R.string.accessibility_not_ready, Toast.LENGTH_SHORT).show()
        }

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
