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

import android.accessibilityservice.AccessibilityService
import android.service.quicksettings.TileService
import com.blankj.utilcode.util.LogUtils
import com.ps.gkd.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ps.gkd.appScope
import com.ps.gkd.debug.SnapshotExt.captureSnapshot
import com.ps.gkd.getSafeString
import com.ps.gkd.service.A11yService
import com.ps.gkd.service.TopActivity
import com.ps.gkd.service.getAndUpdateCurrentRules
import com.ps.gkd.service.safeActiveWindowAppId
import com.ps.gkd.service.updateTopActivity
import com.ps.gkd.shizuku.safeGetTopActivity
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.toast

class SnapshotTileService : TileService() {
    override fun onClick() {
        super.onClick()
        LogUtils.d("SnapshotTileService::onClick")
        val service = A11yService.instance
        if (service == null) {
            toast(getSafeString(R.string.accessibility_not_enabled))
            return
        }
        appScope.launchTry(Dispatchers.IO) {
            val oldAppId = service.safeActiveWindowAppId
                ?: return@launchTry toast(getSafeString(R.string.get_root_node_failed))

            val startTime = System.currentTimeMillis()
            fun timeout(): Boolean {
                return System.currentTimeMillis() - startTime > 3000L
            }

            val timeoutText = getSafeString(R.string.no_ui_change_detected)
            while (true) {
                val latestAppId = service.safeActiveWindowAppId
                if (latestAppId == null) {
                    // https://github.com/gkd-kit/gkd/issues/713
                    delay(250)
                    if (timeout()) {
                        toast(timeoutText)
                        break
                    }
                } else if (latestAppId != oldAppId) {
                    LogUtils.d("SnapshotTileService::eventExecutor.execute")
                    appScope.launch(A11yService.eventThread) {
                        val topActivity = safeGetTopActivity() ?: TopActivity(appId = latestAppId)
                        updateTopActivity(topActivity)
                        getAndUpdateCurrentRules()
                        appScope.launchTry(Dispatchers.IO) {
                            captureSnapshot()
                        }
                    }
                    break
                } else {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    delay(500)
                    if (timeout()) {
                        toast(timeoutText)
                        break
                    }
                }
            }
        }
    }

}