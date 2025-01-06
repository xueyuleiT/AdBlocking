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

import androidx.compose.ui.res.stringResource
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import com.ps.gkd.META
import com.ps.gkd.R
import com.ps.gkd.appScope
import com.ps.gkd.getSafeString

private inline fun <reified T> createJsonFlow(
    key: String,
    crossinline default: () -> T,
    crossinline transform: (T) -> T = { it }
): MutableStateFlow<T> {
    val str = kv.getString(key, null)
    val initValue = if (str != null) {
        try {
            json.decodeFromString<T>(str)
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtils.d(e)
            null
        }
    } else {
        null
    }
    val stateFlow = MutableStateFlow(transform(initValue ?: default()))
    appScope.launch {
        stateFlow.drop(1).collect {
            withContext(Dispatchers.IO) {
                kv.encode(key, json.encodeToString(it))
            }
        }
    }
    return stateFlow
}

private fun createLongFlow(
    key: String,
    default: Long = 0,
    transform: (Long) -> Long = { it }
): MutableStateFlow<Long> {
    val stateFlow = MutableStateFlow(transform(kv.getLong(key, default)))
    appScope.launch {
        stateFlow.drop(1).collect {
            withContext(Dispatchers.IO) { kv.encode(key, it) }
        }
    }
    return stateFlow
}

@Serializable
data class Store(
    val enableService: Boolean = true,
    val enableMatch: Boolean = true,
    val enableStatusService: Boolean = true,
    val excludeFromRecents: Boolean = false,
    val captureScreenshot: Boolean = false,
    val httpServerPort: Int = 8888,
    val updateSubsInterval: Long = UpdateTimeOption.Everyday.value,
    val captureVolumeChange: Boolean = false,
    val autoCheckAppUpdate: Boolean = META.updateEnabled,
    val toastWhenClick: Boolean = true,
    val clickToast: String = getSafeString(R.string.app_name),
    val autoClearMemorySubs: Boolean = true,
    val hideSnapshotStatusBar: Boolean = false,
    val enableShizukuActivity: Boolean = false,
    val enableShizukuClick: Boolean = false,
    val log2FileSwitch: Boolean = true,
    val enableDarkTheme: Boolean? = null,
    val enableDynamicColor: Boolean = true,
    val enableAbFloatWindow: Boolean = true,
    val showSaveSnapshotToast: Boolean = true,
    val useSystemToast: Boolean = false,
    val useCustomNotifText: Boolean = false,
    val customNotifText: String = String.format(getSafeString(R.string.trigger_info)),
    val enableActivityLog: Boolean = false,
    val updateChannel: Int = if (META.versionName.contains("beta")) UpdateChannelOption.Beta.value else UpdateChannelOption.Stable.value,
    val sortType: Int = SortTypeOption.SortByName.value,
    val showSystemApp: Boolean = true,
    val showHiddenApp: Boolean = false,
    val appRuleSortType: Int = RuleSortOption.Default.value,
    val subsAppSortType: Int = SortTypeOption.SortByName.value,
    val subsAppShowUninstallApp: Boolean = false,
    val subsExcludeSortType: Int = SortTypeOption.SortByName.value,
    val subsExcludeShowSystemApp: Boolean = true,
    val subsExcludeShowHiddenApp: Boolean = false,
    val subsPowerWarn: Boolean = true,
)

val storeFlow by lazy {
    createJsonFlow(
        key = "store-v2",
        default = { Store() },
        transform = {
            if (UpdateTimeOption.allSubObject.all { e -> e.value != it.updateSubsInterval }) {
                it.copy(
                    updateSubsInterval = UpdateTimeOption.Everyday.value
                )
            } else {
                it
            }
        }
    )
}

//@Deprecated("use actionCountFlow instead")
@Serializable
private data class RecordStore(
    val clickCount: Int = 0,
)

//@Deprecated("use actionCountFlow instead")
private val recordStoreFlow by lazy {
    createJsonFlow(
        key = "record_store-v2",
        default = { RecordStore() }
    )
}

val actionCountFlow by lazy {
    createLongFlow(
        key = "action_count",
        transform = {
            if (it == 0L) {
                recordStoreFlow.value.clickCount.toLong()
            } else {
                it
            }
        }
    )
}

@Serializable
data class PrivacyStore(
    val githubCookie: String? = null,
)

val privacyStoreFlow by lazy {
    createJsonFlow(
        key = "privacy_store",
        default = { PrivacyStore() }
    )
}

fun initStore() {
    storeFlow.value
    actionCountFlow.value
}
