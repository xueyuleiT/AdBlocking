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
package com.ps.gkd.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import com.ps.gkd.META
import com.ps.gkd.app
import com.ps.gkd.appScope
import com.ps.gkd.data.AppInfo
import com.ps.gkd.data.toAppInfo

val appInfoCacheFlow = MutableStateFlow(emptyMap<String, AppInfo>())

val systemAppInfoCacheFlow by lazy {
    com.ps.gkd.util.appInfoCacheFlow.map(appScope) { c ->
        c.filter { a -> a.value.isSystem }
    }
}

val systemAppsFlow by lazy { com.ps.gkd.util.systemAppInfoCacheFlow.map(appScope) { c -> c.keys } }

val orderedAppInfosFlow by lazy {
    com.ps.gkd.util.appInfoCacheFlow.map(appScope) { c ->
        c.values.sortedWith { a, b ->
            com.ps.gkd.util.collator.compare(a.name, b.name)
        }
    }
}

// https://github.com/orgs/gkd-kit/discussions/761
// 某些设备在应用更新后出现权限错乱/缓存错乱
private const val MINIMUM_NORMAL_APP_SIZE = 8
val mayQueryPkgNoAccessFlow by lazy {
    com.ps.gkd.util.appInfoCacheFlow.map(appScope) { c ->
        c.values.count { a -> !a.isSystem && !a.hidden && a.id != META.appId } < com.ps.gkd.util.MINIMUM_NORMAL_APP_SIZE
    }
}

private val willUpdateAppIds by lazy { MutableStateFlow(emptySet<String>()) }

private val packageReceiver by lazy {
    object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val appId = intent?.data?.schemeSpecificPart ?: return
            if (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_REPLACED || intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                /**
                 * 例: 小米应用商店更新应用产生连续 3个事件: PACKAGE_REMOVED->PACKAGE_ADDED->PACKAGE_REPLACED
                 * 使用 Flow + debounce 优化合并
                 */
                com.ps.gkd.util.willUpdateAppIds.update { it + appId }
            }
        }
    }.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(this, IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }, Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(
                this,
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addAction(Intent.ACTION_PACKAGE_REPLACED)
                    addAction(Intent.ACTION_PACKAGE_REMOVED)
                    addDataScheme("package")
                },
            )
        }
    }
}

private fun getAppInfo(appId: String): AppInfo? {
    return try {
        app.packageManager.getPackageInfo(appId, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }?.toAppInfo()
}

val updateAppMutex = com.ps.gkd.util.MutexState()

private suspend fun updateAppInfo(appIds: Set<String>) {
    if (appIds.isEmpty()) return
    com.ps.gkd.util.willUpdateAppIds.update { it - appIds }
    com.ps.gkd.util.updateAppMutex.withLock {
        LogUtils.d("updateAppInfo", appIds)
        val newMap = com.ps.gkd.util.appInfoCacheFlow.value.toMutableMap()
        appIds.forEach { appId ->
            val info = com.ps.gkd.util.getAppInfo(appId)
            if (info != null) {
                newMap[appId] = info
            } else {
                newMap.remove(appId)
            }
        }
        com.ps.gkd.util.appInfoCacheFlow.value = newMap
    }
}


suspend fun initOrResetAppInfoCache() {
    if (com.ps.gkd.util.updateAppMutex.mutex.isLocked) return
    LogUtils.d("initOrResetAppInfoCache start")
    com.ps.gkd.util.updateAppMutex.withLock {
        val oldAppIds = com.ps.gkd.util.appInfoCacheFlow.value.keys
        val appMap = com.ps.gkd.util.appInfoCacheFlow.value.toMutableMap()
        withContext(Dispatchers.IO) {
            app.packageManager.getInstalledPackages(0).forEach { packageInfo ->
                if (!oldAppIds.contains(packageInfo.packageName)) {
                    appMap[packageInfo.packageName] = packageInfo.toAppInfo()
                }
            }
        }
        com.ps.gkd.util.appInfoCacheFlow.value = appMap
    }
    LogUtils.d("initOrResetAppInfoCache end")
}

fun initAppState() {
    com.ps.gkd.util.packageReceiver
    appScope.launchTry(Dispatchers.IO) {
        com.ps.gkd.util.initOrResetAppInfoCache()
        com.ps.gkd.util.willUpdateAppIds.debounce(1000)
            .filter { it.isNotEmpty() }
            .collect { com.ps.gkd.util.updateAppInfo(it) }
    }
}