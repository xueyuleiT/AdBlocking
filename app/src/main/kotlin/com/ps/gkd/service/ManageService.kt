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
package com.ps.gkd.service

import android.app.Service
import android.content.Intent
import com.ps.gkd.R
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ps.gkd.app
import com.ps.gkd.getSafeString
import com.ps.gkd.notif.abNotif
import com.ps.gkd.notif.notifyService
import com.ps.gkd.permission.notificationState
import com.ps.gkd.util.OnCreate
import com.ps.gkd.util.OnDestroy
import com.ps.gkd.util.actionCountFlow
import com.ps.gkd.util.getSubsStatus
import com.ps.gkd.util.ruleSummaryFlow
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.useAliveFlow

class ManageService : Service(), OnCreate, OnDestroy {
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        onCreated()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
    }

    val scope = MainScope().apply { onDestroyed { cancel() } }

    init {
        useAliveFlow(isRunning)
        useNotif()
    }

    companion object {
        val isRunning = MutableStateFlow(false)

        fun start() {
            if (!notificationState.checkOrToast()) return
            app.startForegroundService(Intent(app, ManageService::class.java))
        }

        fun stop() {
            app.stopService(Intent(app, ManageService::class.java))
        }

        fun autoStart() {
            // 在[系统重启]/[被其它高权限应用重启]时自动打开通知栏状态服务
            if (storeFlow.value.enableStatusService
                && !isRunning.value
                && notificationState.updateAndGet()
            ) {
                start()
            }
        }
    }
}

private fun ManageService.useNotif() {
    onCreated {
        abNotif.notifyService(this)
        scope.launch {
            combine(
                A11yService.isRunning,
                storeFlow,
                ruleSummaryFlow,
                actionCountFlow,
            ) { abRunning, store, ruleSummary, count ->
                if (!abRunning) return@combine getSafeString(R.string.accessibility_authorization)
                if (!store.enableMatch) return@combine getSafeString(R.string.pause_rule_matching)
                if (store.useCustomNotifText) {
                    return@combine store.customNotifText
                        .replace("\${i}", ruleSummary.globalGroups.size.toString())
                        .replace("\${k}", ruleSummary.appSize.toString())
                        .replace("\${u}", ruleSummary.appGroupSize.toString())
                        .replace("\${n}", count.toString())
                }
                return@combine getSubsStatus(ruleSummary, count)
            }.debounce(500L).stateIn(scope, SharingStarted.Eagerly, "").collect { text ->
                abNotif.copy(text = text).notifyService(this@useNotif)
            }
        }
    }
}
