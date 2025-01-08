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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppItemPageDestination
import com.ramcosta.composedestinations.generated.destinations.GlobalRulePageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.ActionLog
import com.ps.gkd.data.ExcludeData
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.data.Tuple3
import com.ps.gkd.data.stringify
import com.ps.gkd.data.switch
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.AppNameText
import com.ps.gkd.ui.component.EmptyText
import com.ps.gkd.ui.component.StartEllipsisText
import com.ps.gkd.ui.component.waitResult
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemHorizontalPadding
import com.ps.gkd.ui.style.scaffoldPadding
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.appInfoCacheFlow
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.subsIdToRawFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun ActionLogPage() {
    val context = LocalContext.current as MainActivity
    val mainVm = context.mainVm
    val navController = LocalNavController.current
    val vm = viewModel<ActionLogVm>()
    val actionLogCount by vm.actionLogCountFlow.collectAsState()
    val actionDataItems = vm.pagingDataFlow.collectAsLazyPagingItems()

    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    var previewActionLog by remember {
        mutableStateOf<ActionLog?>(null)
    }
    val (previewConfigFlow, setPreviewConfigFlow) = remember {
        mutableStateOf<StateFlow<SubsConfig?>>(MutableStateFlow(null))
    }
    LaunchedEffect(key1 = previewActionLog, block = {
        val log = previewActionLog
        if (log != null) {
            val stateFlow = (if (log.groupType == SubsConfig.AppGroupType) {
                DbSet.subsConfigDao.queryAppGroupTypeConfig(
                    log.subsId, log.appId, log.groupKey
                )
            } else {
                DbSet.subsConfigDao.queryGlobalGroupTypeConfig(log.subsId, log.groupKey)
            }).map { s -> s.firstOrNull() }.stateIn(vm.viewModelScope, SharingStarted.Eagerly, null)
            setPreviewConfigFlow(stateFlow)
        } else {
            setPreviewConfigFlow(MutableStateFlow(null))
        }
    })

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = throttle {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            title = { Text(text = getSafeString(R.string.trigger_log) ) },
            actions = {
                if (actionLogCount > 0) {
                    IconButton(onClick = throttle(fn = mainVm.viewModelScope.launchAsFn {
                        mainVm.dialogFlow.waitResult(
                            title = getSafeString(R.string.delete_log),
                            text = getSafeString(R.string.confirm_delete_all_logs),
                            error = true,
                        )
                        DbSet.actionLogDao.deleteAll()
                    })) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                    }
                }
            })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
        ) {
            items(
                count = actionDataItems.itemCount,
                key = actionDataItems.itemKey { c -> c.t0.id }
            ) { i ->
                val item = actionDataItems[i] ?: return@items
                val lastItem = if (i > 0) actionDataItems[i - 1] else null
                ActionLogCard(
                    i = i,
                    item = item,
                    lastItem = lastItem,
                    onClick = {
                        previewActionLog = item.t0
                    }
                )
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (actionLogCount == 0 && actionDataItems.loadState.refresh !is LoadState.Loading) {
                    EmptyText(text = getSafeString(R.string.no_records))
                }
            }
        }
    })

    previewActionLog?.let { clickLog ->
        Dialog(onDismissRequest = { previewActionLog = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val appInfoCache by com.ps.gkd.util.appInfoCacheFlow.collectAsState()
                val previewConfig = previewConfigFlow.collectAsState().value
                val oldExclude = remember(key1 = previewConfig?.exclude) {
                    ExcludeData.parse(previewConfig?.exclude)
                }
                val appInfo = appInfoCache[clickLog.appId]

                Text(
                    text = getSafeString(R.string.view_rule_group), modifier = Modifier
                        .clickable(onClick = throttle {
                            if (clickLog.groupType == SubsConfig.AppGroupType) {
                                navController
                                    .toDestinationsNavigator()
                                    .navigate(
                                        AppItemPageDestination(
                                            clickLog.subsId, clickLog.appId, clickLog.groupKey
                                        )
                                    )
                            } else if (clickLog.groupType == SubsConfig.GlobalGroupType) {
                                navController
                                    .toDestinationsNavigator()
                                    .navigate(
                                        GlobalRulePageDestination(
                                            clickLog.subsId, clickLog.groupKey
                                        )
                                    )
                            }
                            previewActionLog = null
                        })
                        .fillMaxWidth()
                        .padding(16.dp)
                )
                if (clickLog.groupType == SubsConfig.GlobalGroupType) {
                    val group =
                        subsIdToRaw[clickLog.subsId]?.globalGroups?.find { g -> g.key == clickLog.groupKey }
                    val appChecked = if (group != null) {
                        getChecked(
                            oldExclude,
                            group,
                            clickLog.appId,
                            appInfo
                        )
                    } else {
                        null
                    }
                    if (appChecked != null) {
                        Text(
                            text = if (appChecked) getSafeString(R.string.disable_in_this_app) else getSafeString(R.string.remove_disable_in_app),
                            modifier = Modifier
                                .clickable(
                                    onClick = vm.viewModelScope.launchAsFn(
                                        Dispatchers.IO
                                    ) {
                                        val subsConfig = previewConfig ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsItemId = clickLog.subsId,
                                            groupKey = clickLog.groupKey,
                                        )
                                        val newSubsConfig = subsConfig.copy(
                                            exclude = oldExclude
                                                .copy(
                                                    appIds = oldExclude.appIds
                                                        .toMutableMap()
                                                        .apply {
                                                            set(clickLog.appId, appChecked)
                                                        })
                                                .stringify()
                                        )
                                        DbSet.subsConfigDao.insert(newSubsConfig)
                                        toast(getSafeString(R.string.update_disable))
                                    })
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                    }
                }
                if (clickLog.activityId != null) {
                    val disabled =
                        oldExclude.activityIds.contains(clickLog.appId to clickLog.activityId)
                    Text(
                        text = if (disabled) getSafeString(R.string.remove_disable_in_page) else getSafeString(R.string.disable_in_page),
                        modifier = Modifier
                            .clickable(onClick = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                val subsConfig =
                                    if (clickLog.groupType == SubsConfig.AppGroupType) {
                                        previewConfig ?: SubsConfig(
                                            type = SubsConfig.AppGroupType,
                                            subsItemId = clickLog.subsId,
                                            appId = clickLog.appId,
                                            groupKey = clickLog.groupKey,
                                        )
                                    } else {
                                        previewConfig ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsItemId = clickLog.subsId,
                                            groupKey = clickLog.groupKey,
                                        )
                                    }
                                val newSubsConfig = subsConfig.copy(
                                    exclude = oldExclude
                                        .switch(
                                            clickLog.appId,
                                            clickLog.activityId
                                        )
                                        .stringify()
                                )
                                DbSet.subsConfigDao.insert(newSubsConfig)
                                toast(getSafeString(R.string.update_disable))
                            })
                            .fillMaxWidth()
                            .padding(16.dp),
                    )
                }

                Text(
                    text = getSafeString(R.string.delete_log),
                    modifier = Modifier
                        .clickable(onClick = vm.viewModelScope.launchAsFn {
                            previewActionLog = null
                            DbSet.actionLogDao.delete(clickLog)
                            toast(getSafeString(R.string.delete_success))
                        })
                        .fillMaxWidth()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}


@Composable
private fun ActionLogCard(
    i: Int,
    item: Tuple3<ActionLog, RawSubscription.RawGroupProps?, RawSubscription.RawRuleProps?>,
    lastItem: Tuple3<ActionLog, RawSubscription.RawGroupProps?, RawSubscription.RawRuleProps?>?,
    onClick: () -> Unit
) {
    val (actionLog, group, rule) = item
    val lastActionLog = lastItem?.t0
    val isDiffApp = actionLog.appId != lastActionLog?.appId
    val verticalPadding = if (i == 0) 0.dp else if (isDiffApp) 12.dp else 8.dp
    val subsIdToRaw by subsIdToRawFlow.collectAsState()
    val subscription = subsIdToRaw[actionLog.subsId]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = itemHorizontalPadding,
                end = itemHorizontalPadding,
                top = verticalPadding
            )
    ) {
        if (isDiffApp) {
            AppNameText(appId = actionLog.appId)
        }
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Spacer(modifier = Modifier.width(2.dp))
            Spacer(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = actionLog.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                    val showActivityId = actionLog.showActivityId
                    if (showActivityId != null) {
                        StartEllipsisText(
                            text = showActivityId,
                            modifier = Modifier.height(LocalTextStyle.current.lineHeight.value.dp),
                        )
                    } else {
                        Text(
                            text = "null",
                            color = LocalContentColor.current.copy(alpha = 0.5f),
                        )
                    }
                    Text(text = subscription?.name ?: actionLog.subsId.toString())
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val groupDesc = group?.name.toString()
                        if (actionLog.groupType == SubsConfig.GlobalGroupType) {
                            Icon(
                                imageVector = Icons.Default.SportsBasketball,
                                contentDescription = null,
                                modifier = Modifier
                                    .clickable(onClick = throttle {
                                        toast("${group?.name ?: getSafeString(R.string.current_rule_group)} ${getSafeString(R.string.global_rule_group)}")
                                    })
                                    .size(LocalTextStyle.current.lineHeight.value.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = groupDesc,
                            color = LocalContentColor.current.let {
                                if (group?.name == null) it.copy(alpha = 0.5f) else it
                            },
                        )

                        val ruleDesc = rule?.name ?: (if ((group?.rules?.size ?: 0) > 1) {
                            val keyDesc = actionLog.ruleKey?.let { "key=$it, " } ?: ""
                            "${keyDesc}index=${actionLog.ruleIndex}"
                        } else {
                            null
                        })
                        if (ruleDesc != null) {
                            Text(
                                text = ruleDesc,
                                modifier = Modifier.padding(start = 8.dp),
                                color = LocalContentColor.current.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }
    }
}

