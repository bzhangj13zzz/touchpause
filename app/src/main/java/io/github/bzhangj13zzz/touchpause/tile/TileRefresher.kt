package io.github.bzhangj13zzz.touchpause.tile

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

/** Requests a one-shot update for the active Quick Settings tile. */
object TileRefresher {
    fun request(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            TileService.requestListeningState(
                appContext,
                ComponentName(appContext, TouchBlockTileService::class.java)
            )
        }
    }
}
