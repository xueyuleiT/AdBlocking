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

import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ps.gkd.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.ps.gkd.accessRestrictedSettingsShowFlow
import com.ps.gkd.app
import com.ps.gkd.appScope
import com.ps.gkd.getSafeString
import com.ps.gkd.permission.writeSecureSettingsState
import com.ps.gkd.util.OnChangeListen
import com.ps.gkd.util.OnDestroy
import com.ps.gkd.util.OnTileClick
import com.ps.gkd.util.componentName
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.toast
import com.ps.gkd.util.useLogLifecycle

class GkdTileService : TileService(), OnDestroy, OnChangeListen, OnTileClick {
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
                A11yService.isRunning,
                listeningFlow
            ) { v1, v2 -> v1 to v2 }.collect { (running, listening) ->
                if (listening) {
                    qsTile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    qsTile.updateTile()
                }
            }
        }
        onStartListened {
            fixRestartService()
        }
        onTileClicked {
            switchA11yService()
        }
    }
}

private fun getServiceNames(): MutableList<String> {
    val value = try {
        Settings.Secure.getString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    } catch (_: Exception) {
        null
    } ?: ""
    if (value.isEmpty()) return mutableListOf()
    return value.split(':').toMutableList()
}

private fun updateServiceNames(names: List<String>) {
    Settings.Secure.putString(
        app.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        names.joinToString(":")
    )
}

private fun enableA11yService() {
    Settings.Secure.putInt(
        app.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        1
    )
}

private val modifyA11yMutex by lazy { Mutex() }

fun switchA11yService() = appScope.launchTry(Dispatchers.IO) {
    modifyA11yMutex.withLock {
        if (!writeSecureSettingsState.updateAndGet()) {
            toast(getSafeString(R.string.grant_write_secure_settings_permission))
            return@launchTry
        }
        val names = getServiceNames()
        storeFlow.update { it.copy(enableService = !A11yService.isRunning.value) }
        if (A11yService.isRunning.value) {
            names.remove(a11yClsName)
            updateServiceNames(names)
            delay(500)
            // https://github.com/orgs/gkd-kit/discussions/799
            if (A11yService.isRunning.value) {
                toast(getSafeString(R.string.close_accessibility_failed))
                accessRestrictedSettingsShowFlow.value = true
                return@launchTry
            }
            toast(getSafeString(R.string.close_accessibility))
        } else {
            enableA11yService()
            if (names.contains(a11yClsName)) { // 当前无障碍异常, 重启服务
                names.remove(a11yClsName)
                updateServiceNames(names)
                delay(500)
            }
            names.add(a11yClsName)
            updateServiceNames(names)
            delay(500)
            if (!A11yService.isRunning.value) {
                toast(getSafeString(R.string.open_accessibility_failed))
                accessRestrictedSettingsShowFlow.value = true
                return@launchTry
            }
            toast(getSafeString(R.string.open_accessibility))
        }
    }
}

fun fixRestartService() = appScope.launchTry(Dispatchers.IO) {
    if (modifyA11yMutex.isLocked) return@launchTry
    modifyA11yMutex.withLock {
        // 1. 服务没有运行
        // 2. 用户配置开启了服务
        // 3. 有写入系统设置权限
        if (!A11yService.isRunning.value && storeFlow.value.enableService && writeSecureSettingsState.updateAndGet()) {
            val names = getServiceNames()
            val a11yBroken = names.contains(a11yClsName)
            if (a11yBroken) {
                // 无障碍出现故障, 重启服务
                names.remove(a11yClsName)
                updateServiceNames(names)
                // 必须等待一段时间, 否则概率不会触发系统重启无障碍服务
                delay(500)
            }
            names.add(a11yClsName)
            updateServiceNames(names)
            delay(500)
            if (!A11yService.isRunning.value) {
                toast(getSafeString(R.string.restart_accessibility_failed))
                accessRestrictedSettingsShowFlow.value = true
                return@launchTry
            }
            toast(getSafeString(R.string.restart_accessibility))
        }
    }
}

val a11yClsName by lazy { A11yService::class.componentName.flattenToShortString() }
