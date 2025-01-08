/**
 * amagi and lisonge <https://github.com/gkd-kit/gkd>
 * Copyright (C) 2024 amagi and lisonge
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ps.gkd.debug

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.ps.gkd.util.OnChangeListen
import com.ps.gkd.util.OnDestroy
import com.ps.gkd.util.OnTileClick
import com.ps.gkd.util.useLogLifecycle

class HttpTileService : TileService(), OnDestroy, OnChangeListen, OnTileClick {
    override fun onStartListening() {
        super.onStartListening()
        onStartListened()
    }

    override fun onClick() {
        super.onClick()
        onTileClicked()
    }

    override fun onStopListening() {
        super.onStopListening()
        onStopListened()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
    }

    val scope = MainScope().also { scope ->
        onDestroyed { scope.cancel() }
    }
    private val listeningFlow = MutableStateFlow(false).also { listeningFlow ->
        onStartListened { listeningFlow.value = true }
        onStopListened { listeningFlow.value = false }
    }

    init {
        useLogLifecycle()
        scope.launch {
            combine(
                HttpService.isRunning,
                listeningFlow
            ) { v1, v2 -> v1 to v2 }.collect { (running, listening) ->
                if (listening) {
                    qsTile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    qsTile.updateTile()
                }
            }
        }
        onTileClicked {
            if (HttpService.isRunning.value) {
                HttpService.stop()
            } else {
                HttpService.start()
            }
        }
    }
}