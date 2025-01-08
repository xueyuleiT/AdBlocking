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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.generated.destinations.AppConfigPageDestination
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.ps.gkd.data.ResolvedAppGroup
import com.ps.gkd.data.ResolvedGlobalGroup
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.db.DbSet
import com.ps.gkd.util.RuleSortOption
import com.ps.gkd.util.collator
import com.ps.gkd.util.findOption
import com.ps.gkd.util.getGroupRawEnable
import com.ps.gkd.util.map
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.subsIdToRawFlow
import com.ps.gkd.util.subsItemsFlow

class AppConfigVm(stateHandle: SavedStateHandle) : ViewModel() {
    private val args = AppConfigPageDestination.argsFrom(stateHandle)

    private val latestGlobalLogsFlow = DbSet.actionLogDao.queryAppLatest(
        args.appId,
        SubsConfig.GlobalGroupType
    )

    private val latestAppLogsFlow = DbSet.actionLogDao.queryAppLatest(
        args.appId,
        SubsConfig.AppGroupType
    )

    val ruleSortTypeFlow =
        storeFlow.map(viewModelScope) { RuleSortOption.allSubObject.findOption(it.appRuleSortType) }

    private val subsFlow = combine(subsIdToRawFlow, subsItemsFlow) { subsIdToRaw, subsItems ->
        subsItems.mapNotNull { if (it.enable && subsIdToRaw[it.id] != null) it to subsIdToRaw[it.id]!! else null }
    }
    private val rawGlobalGroups = subsFlow.map {
        it.map { (subsItem, subscription) ->
            subscription.globalGroups.map { g ->
                ResolvedGlobalGroup(
                    group = g,
                    subsItem = subsItem,
                    subscription = subscription,
                    // secondary assignment
                    config = null,
                )
            }
        }.flatten()
    }
    private val sortedGlobalGroupsFlow = combine(
        rawGlobalGroups,
        ruleSortTypeFlow,
        latestGlobalLogsFlow
    ) { list, type, logs ->
        when (type) {
            RuleSortOption.Default -> list
            RuleSortOption.ByName -> list.sortedWith { a, b ->
                collator.compare(
                    a.group.name,
                    b.group.name
                )
            }

            RuleSortOption.ByTime -> list.sortedBy { a ->
                -(logs.find { c -> c.groupKey == a.group.key && c.subsId == a.subsItem.id }?.id
                    ?: 0)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val globalConfigs = subsFlow.map { subs ->
        DbSet.subsConfigDao.queryGlobalConfig(subs.map { it.first.id })
    }.flatMapLatest { it }
    val globalGroupsFlow = combine(sortedGlobalGroupsFlow, globalConfigs) { groups, configs ->
        groups.mapNotNull { g ->
            val config =
                configs.find { c -> c.subsItemId == g.subsItem.id && c.groupKey == g.group.key }
            if (config?.enable == false) {
                null
            } else {
                ResolvedGlobalGroup(
                    group = g.group,
                    subsItem = g.subsItem,
                    subscription = g.subscription,
                    config = config,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val unsortedAppGroupsFlow = subsFlow.map {
        it.mapNotNull { s ->
            s.second.apps.find { a -> a.id == args.appId }?.let { app ->
                app.groups.map { g ->
                    ResolvedAppGroup(
                        group = g,
                        subsItem = s.first,
                        subscription = s.second,
                        app = app,
                        // secondary assignment
                        config = null,
                        enable = false
                    )
                }
            }
        }.flatten()
    }
    private val sortedAppGroupsFlow = combine(
        unsortedAppGroupsFlow,
        ruleSortTypeFlow,
        latestAppLogsFlow
    ) { list, type, logs ->
        when (type) {
            RuleSortOption.Default -> list
            RuleSortOption.ByName -> list.sortedWith { a, b ->
                collator.compare(
                    a.group.name,
                    b.group.name
                )
            }

            RuleSortOption.ByTime -> list.sortedBy { a ->
                -(logs.find { c -> c.groupKey == a.group.key && c.subsId == a.subsItem.id }?.id
                    ?: 0)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val appConfigsFlow = subsFlow.map { subs ->
        DbSet.subsConfigDao.queryAppConfig(subs.map { it.first.id }, args.appId)
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val categoryConfigsFlow = subsFlow.map { subs ->
        DbSet.categoryConfigDao.queryBySubsIds(subs.map { it.first.id })
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val appGroupsFlow = combine(
        sortedAppGroupsFlow,
        appConfigsFlow,
        categoryConfigsFlow
    ) { groups, configs, categoryConfigs ->
        groups.map { g ->
            val config =
                configs.find { c -> c.subsItemId == g.subsItem.id && c.groupKey == g.group.key }
            val enable = g.group.valid && getGroupRawEnable(
                g.group,
                config,
                g.subscription.groupToCategoryMap[g.group],
                categoryConfigs.find { c -> c.subsItemId == g.subsItem.id && c.categoryKey == g.subscription.groupToCategoryMap[g.group]?.key }
            )
            ResolvedAppGroup(
                group = g.group,
                subsItem = g.subsItem,
                subscription = g.subscription,
                app = g.app,
                config = config,
                enable = enable
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

}

