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

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.ps.gkd.R
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.flow.update
import com.ps.gkd.data.AppInfo
import com.ps.gkd.data.ExcludeData
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.data.stringify
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.service.launcherAppId
import com.ps.gkd.ui.component.AppBarTextField
import com.ps.gkd.ui.component.AppNameText
import com.ps.gkd.ui.component.EmptyText
import com.ps.gkd.ui.component.QueryPkgAuthCard
import com.ps.gkd.ui.component.TowLineText
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.appItemPadding
import com.ps.gkd.ui.style.menuPadding
import com.ps.gkd.ui.style.scaffoldPadding
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.SortTypeOption
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.mapHashCode
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun GlobalRuleExcludePage(subsItemId: Long, groupKey: Int) {
    val navController = LocalNavController.current
    val vm = viewModel<GlobalRuleExcludeVm>()
    val rawSubs = vm.rawSubsFlow.collectAsState().value
    val group = vm.groupFlow.collectAsState().value
    val excludeData = vm.excludeDataFlow.collectAsState().value
    val showAppInfos = vm.showAppInfosFlow.collectAsState().value
    val searchStr by vm.searchStrFlow.collectAsState()
    val showSystemApp by vm.showSystemAppFlow.collectAsState()
    val showHiddenApp by vm.showHiddenAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()

    var showEditDlg by remember {
        mutableStateOf(false)
    }
    var showSearchBar by rememberSaveable {
        mutableStateOf(false)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (showSearchBar && searchStr.isEmpty()) {
            focusRequester.requestFocus()
        }
    })
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val listState = rememberLazyListState()
    var isFirstVisit by remember { mutableStateOf(true) }
    LaunchedEffect(
        key1 = showAppInfos.mapHashCode { it.id },
    ) {
        if (isFirstVisit) {
            isFirstVisit = false
        } else {
            listState.scrollToItem(0)
        }
    }
    var expanded by remember { mutableStateOf(false) }
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            IconButton(onClick = {
                navController.popBackStack()
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                )
            }
        }, title = {
            if (showSearchBar) {
                AppBarTextField(
                    value = searchStr,
                    onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                    hint = getSafeString(R.string.enter_app_name_id),
                    modifier = Modifier.focusRequester(focusRequester)
                )
            } else {
                TowLineText(
                    title = rawSubs?.name ?: subsItemId.toString(),
                    subTitle = (group?.name ?: groupKey.toString())
                )
            }
        }, actions = {
            if (showSearchBar) {
                IconButton(onClick = {
                    if (vm.searchStrFlow.value.isEmpty()) {
                        showSearchBar = false
                    } else {
                        vm.searchStrFlow.value = ""
                    }
                }) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                }
            } else {
                IconButton(onClick = {
                    showSearchBar = true
                }) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
                IconButton(onClick = {
                    showEditDlg = true
                }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                }

                IconButton(onClick = {
                    expanded = true
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null
                    )
                }
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Text(
                            text = getSafeString(R.string.sort),
                            modifier = Modifier.menuPadding(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        SortTypeOption.allSubObject.forEach { sortOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(sortOption.label)
                                },
                                trailingIcon = {
                                    RadioButton(selected = sortType == sortOption,
                                        onClick = {
                                            storeFlow.update { it.copy(subsExcludeSortType = sortOption.value) }
                                        }
                                    )
                                },
                                onClick = {
                                    storeFlow.update { it.copy(subsExcludeSortType = sortOption.value) }
                                },
                            )
                        }
                        Text(
                            text = getSafeString(R.string.option),
                            modifier = Modifier.menuPadding(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        DropdownMenuItem(
                            text = {
                                Text(getSafeString(R.string.show_system_apps))
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = showSystemApp,
                                    onCheckedChange = {
                                        storeFlow.update { it.copy(subsExcludeShowSystemApp = !it.subsExcludeShowSystemApp) }
                                    })
                            },
                            onClick = {
                                storeFlow.update { it.copy(subsExcludeShowSystemApp = !it.subsExcludeShowSystemApp) }
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(getSafeString(R.string.show_hidden_apps))
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = showHiddenApp,
                                    onCheckedChange = {
                                        storeFlow.update { it.copy(subsExcludeShowHiddenApp = !it.subsExcludeShowHiddenApp) }
                                    })
                            },
                            onClick = {
                                storeFlow.update { it.copy(subsExcludeShowHiddenApp = !it.subsExcludeShowHiddenApp) }
                            },
                        )
                    }
                }
            }
        })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState
        ) {
            items(showAppInfos, { it.id }) { appInfo ->
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .appItemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (appInfo.icon != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1f)
                        ) {
                            Image(
                                painter = rememberDrawablePainter(appInfo.icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(4.dp)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Android,
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxHeight()
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier
                            .padding(2.dp)
                            .fillMaxHeight()
                            .weight(1f),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        AppNameText(appInfo = appInfo)
                        Text(
                            text = appInfo.id,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    if (group != null) {
                        val checked = getChecked(excludeData, group, appInfo.id, appInfo)
                        if (checked != null) {
                            Switch(
                                checked = checked,
                                onCheckedChange = {
                                    vm.viewModelScope.launchTry {
                                        val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                                            type = SubsConfig.GlobalGroupType,
                                            subsItemId = subsItemId,
                                            groupKey = groupKey,
                                        )).copy(
                                            exclude = excludeData.copy(
                                                appIds = excludeData.appIds.toMutableMap().apply {
                                                    set(appInfo.id, !it)
                                                })
                                                .stringify()
                                        )
                                        DbSet.subsConfigDao.insert(subsConfig)
                                    }
                                },
                            )
                        } else {
                            InnerDisableSwitch()
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (showAppInfos.isEmpty() && searchStr.isNotEmpty()) {
                    val hasShowAll = showSystemApp && showHiddenApp
                    EmptyText(text = if (hasShowAll) getSafeString(R.string.no_search_results) else getSafeString(R.string.no_search_results_try))
                }
                QueryPkgAuthCard()
            }
        }
    })

    if (group != null && showEditDlg) {
        var source by remember {
            mutableStateOf(
                excludeData.stringify()
            )
        }
        val oldSource = remember { source }
        AlertDialog(
            title = { Text(text = getSafeString(R.string.edit_disable)) },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = tipText,
                            style = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
                        )
                    },
                    maxLines = 10,
                    textStyle = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize)
                )
                LaunchedEffect(null) {
                    focusRequester.requestFocus()
                }
            },
            onDismissRequest = {
                if (source.isEmpty()) {
                    showEditDlg = false
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDlg = false }) {
                    Text(text = getSafeString(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (oldSource == source) {
                        toast(getSafeString(R.string.no_change_in_disable))
                        showEditDlg = false
                        return@TextButton
                    }
                    showEditDlg = false
                    val subsConfig = (vm.subsConfigFlow.value ?: SubsConfig(
                        type = SubsConfig.GlobalGroupType,
                        subsItemId = subsItemId,
                        groupKey = groupKey,
                    )).copy(
                        exclude = ExcludeData.parse(source).stringify()
                    )
                    vm.viewModelScope.launchTry {
                        DbSet.subsConfigDao.insert(subsConfig)
                    }
                }) {
                    Text(text = getSafeString(R.string.update))
                }
            },
        )
    }

}

// null - 内置禁用
// true - 启用
// false - 禁用
fun getChecked(
    excludeData: ExcludeData,
    group: RawSubscription.RawGlobalGroup,
    appId: String,
    appInfo: AppInfo? = null
): Boolean? {
    val enable = group.appIdEnable[appId]
    if (enable == false) {
        return null
    }
    excludeData.appIds[appId]?.let { return !it }
    if (enable == true) return true
    if (appInfo?.id == launcherAppId) {
        return group.matchLauncher ?: false
    }
    if (appInfo?.isSystem == true) {
        return group.matchSystemApp ?: false
    }
    return group.matchAnyApp ?: true
}

private val tipText = getSafeString(R.string.tip_text).trimIndent()