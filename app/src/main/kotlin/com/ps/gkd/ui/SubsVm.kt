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
package com.ps.gkd.ui

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.GsonUtils
import com.blankj.utilcode.util.LogUtils
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.ComplexSnapshot
import com.ramcosta.composedestinations.generated.destinations.SubsPageDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.data.TakePositionEvent
import com.ps.gkd.data.Tuple3
import com.ps.gkd.db.DbSet
import com.ps.gkd.debug.SnapshotExt.getSnapshotPath
import com.ps.gkd.getSafeString
import com.ps.gkd.mainActivity
import com.ps.gkd.util.SortTypeOption
import com.ps.gkd.util.collator
import com.ps.gkd.util.findOption
import com.ps.gkd.util.getGroupRawEnable
import com.ps.gkd.util.map
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.subsIdToRawFlow
import com.ps.gkd.util.subsItemsFlow
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubscription
import com.ramcosta.composedestinations.generated.destinations.TakePositionPageDestination.invoke
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SubsVm(stateHandle: SavedStateHandle) : ViewModel(), DefaultLifecycleObserver {
    private val args = SubsPageDestination.argsFrom(stateHandle)

    private fun parseSnapshot(takePositionEvent: TakePositionEvent): ComplexSnapshot{
        val snapshot = File(getSnapshotPath(takePositionEvent.snapshotId)).readText()
        val shot = GsonUtils.fromJson(snapshot, ComplexSnapshot::class.java)
        return shot
    }

    private fun snapshotToJson(shot: ComplexSnapshot, subscriptionPosition: RawSubscription.Position):JSONObject {
        val groups = JSONArray()
        val group = JSONObject()
        group.put("actionCd", 3000)
        group.put("name", shot.appName)
        group.put("desc", getSafeString(R.string.close_ad))
        group.put("enable", true)
        val rules = JSONArray()
        val rule = JSONObject()
        val position = JSONObject()
        position.put("left", subscriptionPosition.left)
        position.put("top", subscriptionPosition.top)
        rule.put("position", position)
        rule.put("activityIds", shot.activityId)
        rules.put(rule)
        group.put("rules", rules)
        groups.put(group)
        val json = JSONObject()
        json.put("id", shot.appInfo!!.id)
        json.put("name", shot.appName)
        json.put("groups", groups)
        return json
    }

    private suspend fun addNewRule(
        oldAppRaw: RawSubscription.RawApp?,
        newAppRaw: RawSubscription.RawApp,
        oldAppRawIndex: Int
    ) {

        // 重写添加的规则的 key
        val initKey =
            ((oldAppRaw?.groups ?: emptyList()).maxByOrNull { g -> g.key }?.key
                ?: -1) + 1
        val finalAppRaw = if (oldAppRaw != null) {
            newAppRaw.copy(groups = oldAppRaw.groups + newAppRaw.groups.mapIndexed { i, g ->
                g.copy(
                    key = initKey + i
                )
            })
        } else {
            newAppRaw.copy(groups = newAppRaw.groups.mapIndexed { i, g ->
                g.copy(
                    key = initKey + i
                )
            })
        }
        val newApps = if (oldAppRaw != null) {
            subsRawFlow.value!!.apps.toMutableList().apply {
                set(oldAppRawIndex, finalAppRaw)
            }
        } else {
            subsRawFlow.value!!.apps.toMutableList().apply {
                add(finalAppRaw)
            }
        }
        updateSubscription(
            subsRawFlow.value!!.copy(
                apps = newApps, version = subsRawFlow.value!!.version + 1
            )
        )
        DbSet.subsItemDao.update(subsItemFlow.value!!.copy(mtime = System.currentTimeMillis()))
        toast(getSafeString(R.string.add_success))
    }

    val subsRawFlow = subsIdToRawFlow.map(viewModelScope) { s -> s[args.subsItemId] }

    private fun moveToFront(activity: MainActivity?) {
        if (activity == null) {
            return
        }
        val intent = activity.packageManager?.getLaunchIntentForPackage(activity.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            val pendingIntent: PendingIntent? = PendingIntent.getActivity(activity.applicationContext, 0, intent,
                PendingIntent.FLAG_IMMUTABLE)
            pendingIntent?.send()
        } catch (e1: Exception) {
            e1.printStackTrace()
        }
    }

    init {

        if (subsRawFlow.value!!.id < 0) {
            viewModelScope.launch {
                mainActivity!!.snapshot.collect {
                    withContext(Dispatchers.IO) {
                        try {
                            val shot = parseSnapshot(it)

                            if (it.position.left == null) {
                                mainActivity?.runOnUiThread {
                                    moveToFront(mainActivity)
                                    mainActivity?.navController()?.toDestinationsNavigator()?.navigate(
                                        com.ramcosta.composedestinations.generated.destinations.TakePositionPageDestination(
                                            shot
                                        )
                                    )
                                }
                                return@withContext
                            }

                            val json = snapshotToJson(shot,it.position)

                            val newAppRaw = try {
                                RawSubscription.parseRawApp(json.toString())
                            } catch (e: Exception) {
                                LogUtils.d(e)
                                toast(getSafeString(R.string.invalid_rule) + "${e.message}")
                                return@withContext
                            }
                            if (newAppRaw.groups.isEmpty()) {
                                toast(getSafeString(R.string.no_empty_rule_group))
                                return@withContext
                            }
                            if (newAppRaw.groups.any { s -> s.name.isBlank() }) {
                                toast(getSafeString(R.string.no_blank_name_rule_group))
                                return@withContext
                            }
                            val oldAppRawIndex =
                                subsRawFlow.value!!.apps.indexOfFirst { a -> a.id == newAppRaw.id }
                            val oldAppRaw = subsRawFlow.value!!.apps.getOrNull(oldAppRawIndex)
                            if (oldAppRaw != null) {
                                // check same group name
                                newAppRaw.groups.forEach { g ->
                                    if (oldAppRaw.groups.any { g0 -> g0.name == g.name }) {
                                        toast(
                                            String.format(
                                                getSafeString(R.string.rule_name_exists),
                                                g.name
                                            )
                                        )
                                        return@withContext
                                    }
                                }
                            }

                           addNewRule(oldAppRaw,newAppRaw,oldAppRawIndex)

                        } catch (e: Exception) {
                            e.message
                            toast(e.message ?: "未知错误")
                        }

                    }
                    it.snapshotId
                }
            }
        }

    }

    val subsItemFlow =
        subsItemsFlow.map(viewModelScope) { s -> s.find { v -> v.id == args.subsItemId } }


    private val appSubsConfigsFlow = DbSet.subsConfigDao.queryAppTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val groupSubsConfigsFlow = DbSet.subsConfigDao.querySubsGroupTypeConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val categoryConfigsFlow = DbSet.categoryConfigDao.queryConfig(args.subsItemId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val appIdToOrderFlow =
        DbSet.actionLogDao.queryLatestUniqueAppIds(args.subsItemId).map { appIds ->
            appIds.mapIndexed { index, appId -> appId to index }.toMap()
        }
    val sortTypeFlow = storeFlow.map(viewModelScope) { SortTypeOption.allSubObject.findOption(it.subsAppSortType) }

    val showUninstallAppFlow = storeFlow.map(viewModelScope) { it.subsAppShowUninstallApp }
    private val sortAppsFlow =
        combine(combine((subsRawFlow.combine(com.ps.gkd.util.appInfoCacheFlow) { subs, appInfoCache ->
            (subs?.apps ?: emptyList()).sortedWith { a, b ->
                // 顺序: 已安装(有名字->无名字)->未安装(有名字(来自订阅)->无名字)
                collator.compare(appInfoCache[a.id]?.name ?: a.name?.let { "\uFFFF" + it }
                ?: ("\uFFFF\uFFFF" + a.id),
                    appInfoCache[b.id]?.name ?: b.name?.let { "\uFFFF" + it }
                    ?: ("\uFFFF\uFFFF" + b.id))
            }
        }),
            com.ps.gkd.util.appInfoCacheFlow, showUninstallAppFlow) { apps, appInfoCache, showUninstallApp ->
            if (showUninstallApp) {
                apps
            } else {
                apps.filter { a -> appInfoCache.containsKey(a.id) }
            }
        },
            com.ps.gkd.util.appInfoCacheFlow,
            appIdToOrderFlow,
            sortTypeFlow
        ) { apps, appInfoCache, appIdToOrder, sortType ->
            when (sortType) {
                SortTypeOption.SortByAppMtime -> {
                    apps.sortedBy { a -> -(appInfoCache[a.id]?.mtime ?: 0) }
                }

                SortTypeOption.SortByTriggerTime -> {
                    apps.sortedBy { a -> appIdToOrder[a.id] ?: Int.MAX_VALUE }
                }

                SortTypeOption.SortByName -> {
                    apps
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchStrFlow = MutableStateFlow("")

    private val debounceSearchStr = searchStrFlow.debounce(200)
        .stateIn(viewModelScope, SharingStarted.Eagerly, searchStrFlow.value)


    private val appAndConfigsFlow = combine(
        subsRawFlow,
        sortAppsFlow,
        categoryConfigsFlow,
        appSubsConfigsFlow,
        groupSubsConfigsFlow,
    ) { subsRaw, apps, categoryConfigs, appSubsConfigs, groupSubsConfigs ->
        val groupToCategoryMap = subsRaw?.groupToCategoryMap ?: emptyMap()
        apps.map { app ->
            val appGroupSubsConfigs = groupSubsConfigs.filter { s -> s.appId == app.id }
            val enableSize = app.groups.count { g ->
                getGroupRawEnable(
                    g,
                    appGroupSubsConfigs.find { c -> c.groupKey == g.key },
                    groupToCategoryMap[g],
                    categoryConfigs.find { c -> c.categoryKey == groupToCategoryMap[g]?.key }
                )
            }
            Tuple3(app, appSubsConfigs.find { s -> s.appId == app.id }, enableSize)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filterAppAndConfigsFlow = combine(
        appAndConfigsFlow, debounceSearchStr, com.ps.gkd.util.appInfoCacheFlow
    ) { appAndConfigs, searchStr, appInfoCache ->
        if (searchStr.isBlank()) {
            appAndConfigs
        } else {
            val results = mutableListOf<Tuple3<RawSubscription.RawApp, SubsConfig?, Int>>()
            val remnantList = appAndConfigs.toMutableList()
            //1. 搜索已安装应用名称
            remnantList.toList().apply { remnantList.clear() }.forEach { a ->
                val name = appInfoCache[a.t0.id]?.name
                if (name?.contains(searchStr, true) == true) {
                    results.add(a)
                } else {
                    remnantList.add(a)
                }
            }
            //2. 搜索未安装应用名称
            remnantList.toList().apply { remnantList.clear() }.forEach { a ->
                val name = a.t0.name
                if (appInfoCache[a.t0.id] == null && name?.contains(searchStr, true) == true) {
                    results.add(a)
                } else {
                    remnantList.add(a)
                }
            }
            //3. 搜索应用 id
            remnantList.toList().apply { remnantList.clear() }.forEach { a ->
                if (a.t0.id.contains(searchStr, true)) {
                    results.add(a)
                } else {
                    remnantList.add(a)
                }
            }
            results
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


}