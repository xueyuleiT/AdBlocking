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

import android.app.Service
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import com.ps.gkd.app
import com.ps.gkd.notif.notifyService
import com.ps.gkd.notif.screenshotNotif
import com.ps.gkd.util.OnCreate
import com.ps.gkd.util.OnDestroy
import com.ps.gkd.util.ScreenshotUtil
import com.ps.gkd.util.componentName
import com.ps.gkd.util.useAliveFlow
import com.ps.gkd.util.useLogLifecycle
import java.lang.ref.WeakReference

class ScreenshotService : Service(), OnCreate, OnDestroy {
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        onCreated()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            return super.onStartCommand(intent, flags, startId)
        } finally {
            intent?.let {
                screenshotUtil?.destroy()
                screenshotUtil = ScreenshotUtil(this, intent)
                LogUtils.d("screenshot restart")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
    }

    private var screenshotUtil: ScreenshotUtil? = null

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)
        onCreated { screenshotNotif.notifyService(this) }
        onCreated { instance = WeakReference(this) }
        onDestroyed { instance = WeakReference(null) }
        onDestroyed { screenshotUtil?.destroy() }
    }

    companion object {
        private var instance = WeakReference<ScreenshotService>(null)
        val isRunning = MutableStateFlow(false)
        suspend fun screenshot() = instance.get()?.screenshotUtil?.execute()
        fun start(intent: Intent) {
            intent.component = ScreenshotService::class.componentName
            app.startForegroundService(intent)
        }

        fun stop() {
            app.stopService(Intent(app, ScreenshotService::class.java))
        }
    }
}