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
package com.ps.gkd.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.ps.gkd.R
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.ps.gkd.appScope
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsItem
import com.ps.gkd.data.TakePositionEvent
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.InputSubsLinkOption
import com.ps.gkd.util.SortTypeOption
import com.ps.gkd.util.actionCountFlow
import com.ps.gkd.util.appInfoCacheFlow
import com.ps.gkd.util.client
import com.ps.gkd.util.getSubsStatus
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.map
import com.ps.gkd.util.orderedAppInfosFlow
import com.ps.gkd.util.ruleSummaryFlow
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.subsIdToRawFlow
import com.ps.gkd.util.subsItemsFlow
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubsMutex
import com.ps.gkd.util.updateSubscription
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class HomeVm : ViewModel() {


    val tabFlow = MutableStateFlow(controlNav)

    private val latestRecordFlow =
        DbSet.actionLogDao.queryLatest().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val latestRecordDescFlow = combine(
        latestRecordFlow, subsIdToRawFlow, com.ps.gkd.util.appInfoCacheFlow
    ) { latestRecord, subsIdToRaw, appInfoCache ->
        if (latestRecord == null) return@combine null
        val groupName =
            subsIdToRaw[latestRecord.subsId]?.apps?.find { a -> a.id == latestRecord.appId }?.groups?.find { g -> g.key == latestRecord.groupKey }?.name
        val appName = appInfoCache[latestRecord.appId]?.name
        val appShowName = appName ?: latestRecord.appId
        if (groupName != null) {
            if (groupName.contains(appShowName)) {
                groupName
            } else {
                "$appShowName-$groupName"
            }
        } else {
            appShowName
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val subsStatusFlow by lazy {
        combine(ruleSummaryFlow, actionCountFlow) { ruleSummary, count ->
            getSubsStatus(ruleSummary, count)
        }.stateIn(appScope, SharingStarted.Eagerly, "")
    }

    fun addOrModifySubs(
        url: String,
        oldItem: SubsItem? = null,
    ) = viewModelScope.launchTry(Dispatchers.IO) {
        if (updateSubsMutex.mutex.isLocked) return@launchTry
        updateSubsMutex.withLock {
            val subItems = subsItemsFlow.value
            val text = try {
                client.get(url).bodyAsText()
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                toast(getSafeString(R.string.download_subscription_failed))
                return@launchTry
            }
            val newSubsRaw = try {
                RawSubscription.parse(text)
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                toast(getSafeString(R.string.parse_subscription_failed))
                return@launchTry
            }
            if (oldItem == null) {
                if (subItems.any { it.id == newSubsRaw.id }) {
                    toast(getSafeString(R.string.subscription_exists))
                    return@launchTry
                }
            } else {
                if (oldItem.id != newSubsRaw.id) {
                    toast(getSafeString(R.string.subscription_id_mismatch))
                    return@launchTry
                }
            }
            if (newSubsRaw.id < 0) {
                toast(String.format(getSafeString(R.string.subscription_id_cannot_be_negative_id_is_for_internal_use),newSubsRaw.id))
                return@launchTry
            }
            val newItem = oldItem?.copy(updateUrl = url) ?: SubsItem(
                id = newSubsRaw.id,
                updateUrl = url,
                order = if (subItems.isEmpty()) 1 else (subItems.maxBy { it.order }.order + 1)
            )
            updateSubscription(newSubsRaw)
            if (oldItem == null) {
                DbSet.subsItemDao.insert(newItem)
                toast(getSafeString(R.string.subscription_added))
            } else {
                DbSet.subsItemDao.update(newItem)
                toast(getSafeString(R.string.subscription_modified))
            }
        }
    }


    fun addSubs(
        json: String,
        url: String,
    ) = viewModelScope.launchTry(Dispatchers.IO) {
        if (updateSubsMutex.mutex.isLocked) return@launchTry
        updateSubsMutex.withLock {
            val subItems = subsItemsFlow.value

            val newSubsRaw = try {
                RawSubscription.parse(json)
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtils.d(e)
                toast(getSafeString(R.string.parse_subscription_failed))
                return@launchTry
            }


            if (newSubsRaw.id < 0) {
                toast(String.format(getSafeString(R.string.subscription_id_cannot_be_negative_id_is_for_internal_use),newSubsRaw.id))
                return@launchTry
            }
            val newItem = SubsItem(
                id = newSubsRaw.id,
                updateUrl = url,
                enable = true,
                order = if (subItems.isEmpty()) 1 else (subItems.maxBy { it.order }.order + 1)
            )
            DbSet.subsItemDao.insert(newItem)
            updateSubscription(newSubsRaw)
            toast(getSafeString(R.string.subscription_added))

        }
    }

    private val appIdToOrderFlow = DbSet.actionLogDao.queryLatestUniqueAppIds().map { appIds ->
        appIds.mapIndexed { index, appId -> appId to index }.toMap()
    }

    val sortTypeFlow = storeFlow.map(viewModelScope) { s ->
        SortTypeOption.allSubObject.find { o -> o.value == s.sortType }
            ?: SortTypeOption.SortByName
    }
    val showSystemAppFlow = storeFlow.map(viewModelScope) { s -> s.showSystemApp }
    val showHiddenAppFlow = storeFlow.map(viewModelScope) { s -> s.showHiddenApp }
    val searchStrFlow = MutableStateFlow("")
    private val debounceSearchStrFlow = searchStrFlow.debounce(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchStrFlow.value)
    val appInfosFlow =
        combine(com.ps.gkd.util.orderedAppInfosFlow.combine(showHiddenAppFlow) { appInfos, showHiddenApp ->
            if (showHiddenApp) {
                appInfos
            } else {
                appInfos.filter { a -> !a.hidden }
            }
        }.combine(showSystemAppFlow) { appInfos, showSystemApp ->
            if (showSystemApp) {
                appInfos
            } else {
                appInfos.filter { a -> !a.isSystem }
            }
        }, sortTypeFlow, appIdToOrderFlow) { appInfos, sortType, appIdToOrder ->
            when (sortType) {
                SortTypeOption.SortByAppMtime -> {
                    appInfos.sortedBy { a -> -a.mtime }
                }

                SortTypeOption.SortByTriggerTime -> {
                    appInfos.sortedBy { a -> appIdToOrder[a.id] ?: Int.MAX_VALUE }
                }

                SortTypeOption.SortByName -> {
                    appInfos
                }
            }
        }.combine(debounceSearchStrFlow) { appInfos, str ->
            if (str.isBlank()) {
                appInfos
            } else {
                (appInfos.filter { a -> a.name.contains(str, true) } + appInfos.filter { a ->
                    a.id.contains(
                        str,
                        true
                    )
                }).distinct()
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val showShareDataIdsFlow = MutableStateFlow<Set<Long>?>(null)

    val inputSubsLinkOption = InputSubsLinkOption()
}