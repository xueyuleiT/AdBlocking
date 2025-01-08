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
package com.ps.gkd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsItem
import com.ps.gkd.db.DbSet
import com.ps.gkd.permission.AuthReason
import com.ps.gkd.ui.component.AlertDialogOptions
import com.ps.gkd.ui.component.UploadOptions
import com.ps.gkd.util.LOCAL_SUBS_ID
import com.ps.gkd.util.UpdateStatus
import com.ps.gkd.util.checkUpdate
import com.ps.gkd.util.clearCache
import com.ps.gkd.util.kv
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.map
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.updateSubscription

class MainViewModel : ViewModel() {
    val enableDarkThemeFlow = storeFlow.debounce(300).map { s -> s.enableDarkTheme }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        storeFlow.value.enableDarkTheme
    )
    val enableDynamicColorFlow = storeFlow.debounce(300).map { s -> s.enableDynamicColor }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        storeFlow.value.enableDynamicColor
    )

    val dialogFlow = MutableStateFlow<AlertDialogOptions?>(null)
    val authReasonFlow = MutableStateFlow<AuthReason?>(null)

    val updateStatus = UpdateStatus()

    val shizukuErrorFlow = MutableStateFlow(false)

    val uploadOptions = UploadOptions(this)

    val showEditCookieDlgFlow = MutableStateFlow(false)

    init {
        viewModelScope.launchTry(Dispatchers.IO) {
            val subsItems = DbSet.subsItemDao.queryAll()
            if (!subsItems.any { s -> s.id == LOCAL_SUBS_ID }) {
                DbSet.subsItemDao.insert(
                    SubsItem(
                        id = LOCAL_SUBS_ID,
                        enable = true,
                        order = subsItems.minByOrNull { it.order }?.order ?: 0,
                    )
                )

                updateSubscription(
                    RawSubscription(
                        id = LOCAL_SUBS_ID,
                        name = getSafeString(R.string.local_subscription),
                        version = 0,

                        )
                )

            }
        }

        viewModelScope.launchTry(Dispatchers.IO) {
            // 每次进入删除缓存
            clearCache()
        }

        if (META.updateEnabled && storeFlow.value.autoCheckAppUpdate) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    updateStatus.checkUpdate()
                } catch (e: Exception) {
                    e.printStackTrace()
                    LogUtils.d(e)
                }
            }
        }

        viewModelScope.launch {
            storeFlow.map(viewModelScope) { s -> s.log2FileSwitch }.collect {
                LogUtils.getConfig().isLog2FileSwitch = it
            }
        }
    }
}