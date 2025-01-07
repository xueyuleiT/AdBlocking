/**
 * amagi <https://github.com/gkd-kit/gkd>
 * Copyright (C) 2024 amagi
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

import android.content.Intent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ps.gkd.R
import com.torrydo.floatingbubbleview.FloatingBubbleListener
import com.torrydo.floatingbubbleview.service.expandable.BubbleBuilder
import com.torrydo.floatingbubbleview.service.expandable.ExpandableBubbleService
import kotlinx.coroutines.flow.MutableStateFlow
import com.ps.gkd.app
import com.ps.gkd.appScope
import com.ps.gkd.data.Tuple3
import com.ps.gkd.mainActivity
import com.ps.gkd.notif.floatingNotif
import com.ps.gkd.notif.notifyService
import com.ps.gkd.permission.canDrawOverlaysState
import com.ps.gkd.permission.notificationState
import com.ps.gkd.util.launchTry
import com.ramcosta.composedestinations.generated.destinations.TakePositionPageDestination
import com.ramcosta.composedestinations.generated.destinations.TakePositionPageDestination.invoke
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlin.math.sqrt

class FloatingService : ExpandableBubbleService() {
    override fun configExpandedBubble() = null

    override fun onCreate() {
        super.onCreate()
        isRunning.value = true
        minimize()
    }

    override fun configBubble(): BubbleBuilder {
        val builder = BubbleBuilder(this).bubbleCompose {
            Icon(
                imageVector = Icons.Default.CenterFocusWeak,
                contentDescription = "capture",
                modifier = Modifier.size(40.dp),
                tint = Color(getColor(R.color.blue_3a75f3))
            )
        }.enableAnimateToEdge(false)

        // https://github.com/gkd-kit/gkd/issues/62
        // https://github.com/gkd-kit/gkd/issues/61
        val defaultFingerData = Tuple3(0L, 200f, 200f)
        var fingerDownData = defaultFingerData
        val maxDistanceOffset = 50
        builder.addFloatingBubbleListener(object : FloatingBubbleListener {
            override fun onFingerDown(x: Float, y: Float) {
                fingerDownData = Tuple3(System.currentTimeMillis(), x, y)
            }

            override fun onFingerMove(x: Float, y: Float) {
                if (fingerDownData === defaultFingerData) {
                    return
                }
                val dx = fingerDownData.t1 - x
                val dy = fingerDownData.t2 - y
                val distance = sqrt(dx * dx + dy * dy)
                if (distance > maxDistanceOffset) {
                    // reset
                    fingerDownData = defaultFingerData
                }
            }

            override fun onFingerUp(x: Float, y: Float) {
                if (System.currentTimeMillis() - fingerDownData.t0 < ViewConfiguration.getTapTimeout()) {
                    // is onClick
                    appScope.launchTry {
                        SnapshotExt.captureSnapshot()
                    }
                }
            }
        })
        return builder
    }


    override fun startNotificationForeground() {
        floatingNotif.notifyService(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.value = false
    }

    companion object {
        val isRunning = MutableStateFlow(false)

        fun start() {
            if (!notificationState.checkOrToast()) return
            if (!canDrawOverlaysState.checkOrToast()) return
            app.startForegroundService(Intent(app, FloatingService::class.java))
        }
        fun stop() {
            app.stopService(Intent(app, FloatingService::class.java))
        }
    }
}