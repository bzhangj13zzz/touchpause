package io.github.bzhangj13zzz.touchquell.tile

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import io.github.bzhangj13zzz.touchquell.R
import io.github.bzhangj13zzz.touchquell.accessibility.TouchBlockAccessibilityService
import io.github.bzhangj13zzz.touchquell.block.Backend
import io.github.bzhangj13zzz.touchquell.block.BlockSessionStore

/** Transparent trampoline that lets Quick Settings collapse before touchscreen capture begins. */
class AccessibilityToggleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val handled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            TouchBlockAccessibilityService.requestToggle()
        if (!handled) {
            val sessions = BlockSessionStore(this)
            if (sessions.activeBackend() == Backend.ACCESSIBILITY &&
                sessions.clearAccessibilityIfOwned()
            ) {
                TileRefresher.request(this)
            }
            Toast.makeText(this, R.string.accessibility_not_ready, Toast.LENGTH_SHORT).show()
        }

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
