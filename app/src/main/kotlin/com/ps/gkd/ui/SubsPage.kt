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
package com.ps.gkd.ui

import android.text.TextUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppItemPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.update
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.AppBarTextField
import com.ps.gkd.ui.component.EmptyText
import com.ps.gkd.ui.component.QueryPkgAuthCard
import com.ps.gkd.ui.component.SubsAppCard
import com.ps.gkd.ui.component.TowLineText
import com.ps.gkd.ui.component.waitResult
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.menuPadding
import com.ps.gkd.ui.style.scaffoldPadding
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.SortTypeOption
import com.ps.gkd.util.appInfoCacheFlow
import com.ps.gkd.util.json
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.mapHashCode
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubscription
import li.songe.json5.encodeToJson5String


@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SubsPage(
    subsItemId: Long,
) {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current

    val vm = viewModel<SubsVm>()
    val subsItem = vm.subsItemFlow.collectAsState().value
    val appAndConfigs by vm.filterAppAndConfigsFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    val appInfoCache by com.ps.gkd.util.appInfoCacheFlow.collectAsState()

    val subsRaw = vm.subsRawFlow.collectAsState().value

    // 本地订阅
    val editable = subsItem?.id.let { it != null && it < 0 }

    var showAddDlg by remember {
        mutableStateOf(false)
    }

    var editRawApp by remember {
        mutableStateOf<RawSubscription.RawApp?>(null)
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
    var expanded by remember { mutableStateOf(false) }
    val showUninstallApp by vm.showUninstallAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()
    val listState = rememberLazyListState()
    var isFirstVisit by remember { mutableStateOf(true) }
    LaunchedEffect(
        key1 = appAndConfigs.mapHashCode { it.t0.id }
    ) {
        if (isFirstVisit) {
            isFirstVisit = false
        } else {
            listState.scrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
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
                        title = if (subsRaw != null && !TextUtils.isEmpty(subsRaw.name)) if (subsRaw.id == -2L) getSafeString(R.string.local_subscription) else subsRaw.name  else subsItemId.toString(),
                        subTitle = getSafeString(R.string.application_rule),
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
                        Icon(Icons.Outlined.Search, contentDescription = null)
                    }
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null
                        )
                    }
                    Box(
                        modifier = Modifier.wrapContentSize(Alignment.TopStart)
                    ) {
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                                        RadioButton(
                                            selected = sortType == sortOption,
                                            onClick = {
                                                storeFlow.update { s -> s.copy(subsAppSortType = sortOption.value) }
                                            })
                                    },
                                    onClick = {
                                        storeFlow.update { s -> s.copy(subsAppSortType = sortOption.value) }
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
                                    Text(getSafeString(R.string.show_uninstalled_apps))
                                },
                                trailingIcon = {
                                    Checkbox(checked = showUninstallApp, onCheckedChange = {
                                        storeFlow.update { s -> s.copy(subsAppShowUninstallApp = it) }
                                    })
                                },
                                onClick = {
                                    storeFlow.update { s -> s.copy(subsAppShowUninstallApp = !showUninstallApp) }
                                },
                            )
                        }
                    }

                }
            })
        },
        floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = { showAddDlg = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                    )
                }
            }
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState
        ) {
            itemsIndexed(appAndConfigs, { i, a -> i.toString() + a.t0.id }) { _, a ->
                val (appRaw, subsConfig, enableSize) = a
                SubsAppCard(
                    rawApp = appRaw,
                    appInfo = appInfoCache[appRaw.id],
                    subsConfig = subsConfig,
                    enableSize = enableSize,
                    onClick = throttle {
                        navController.toDestinationsNavigator()
                            .navigate(AppItemPageDestination(subsItemId, appRaw.id))
                    },
                    onValueChange = throttle(fn = vm.viewModelScope.launchAsFn { enable ->
                        val newItem = subsConfig?.copy(
                            enable = enable
                        ) ?: SubsConfig(
                            enable = enable,
                            type = SubsConfig.AppType,
                            subsItemId = subsItemId,
                            appId = appRaw.id,
                        )
                        DbSet.subsConfigDao.insert(newItem)
                    }),
                    showMenu = editable,
                    onDelClick = throttle(fn = vm.viewModelScope.launchAsFn {
                        context.mainVm.dialogFlow.waitResult(
                            title = getSafeString(R.string.delete_rule_group),
                            text = String.format(getSafeString(R.string.confirm_delete_rule_group),appInfoCache[appRaw.id]?.name ?: appRaw.name ?: appRaw.id),
                            error = true,
                        )
                        if (subsRaw != null && subsItem != null) {
                            updateSubscription(subsRaw.copy(apps = subsRaw.apps.filter { a -> a.id != appRaw.id }))
                            DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                            DbSet.subsConfigDao.delete(subsItem.id, appRaw.id)
                            toast(getSafeString(R.string.delete_success))
                        }
                    })
                )
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (appAndConfigs.isEmpty()) {
                    EmptyText(
                        text = if (searchStr.isNotEmpty()) {
                            if (showUninstallApp) getSafeString(R.string.no_search_results) else getSafeString(R.string.no_search_results_try)
                        } else {
                            getSafeString(R.string.no_rules)
                        }
                    )
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
                QueryPkgAuthCard()
            }
        }
    }


    if (showAddDlg && subsRaw != null && subsItem != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = getSafeString(R.string.add_app_rule)) }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = getSafeString(R.string.enter_rule)) },
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                showAddDlg = false
            }
        }, confirmButton = {
            TextButton(onClick = {
                val newAppRaw = try {
                    RawSubscription.parseRawApp(source)
                } catch (e: Exception) {
                    LogUtils.d(e)
                    toast(getSafeString(R.string.invalid_rule)+"${e.message}")
                    return@TextButton
                }
                if (newAppRaw.groups.isEmpty()) {
                    toast(getSafeString(R.string.no_empty_rule_group))
                    return@TextButton
                }
                if (newAppRaw.groups.any { s -> s.name.isBlank() }) {
                    toast(getSafeString(R.string.no_blank_name_rule_group))
                    return@TextButton
                }
                val oldAppRawIndex = subsRaw.apps.indexOfFirst { a -> a.id == newAppRaw.id }
                val oldAppRaw = subsRaw.apps.getOrNull(oldAppRawIndex)
                if (oldAppRaw != null) {
                    // check same group name
                    newAppRaw.groups.forEach { g ->
                        if (oldAppRaw.groups.any { g0 -> g0.name == g.name }) {
                            toast(String.format(getSafeString(R.string.rule_name_exists),g.name))
                            return@TextButton
                        }
                    }
                }
                // 重写添加的规则的 key
                val initKey =
                    ((oldAppRaw?.groups ?: emptyList()).maxByOrNull { g -> g.key }?.key ?: -1) + 1
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
                    subsRaw.apps.toMutableList().apply {
                        set(oldAppRawIndex, finalAppRaw)
                    }
                } else {
                    subsRaw.apps.toMutableList().apply {
                        add(finalAppRaw)
                    }
                }
                vm.viewModelScope.launchTry {
                    updateSubscription(
                        subsRaw.copy(
                            apps = newApps, version = subsRaw.version + 1
                        )
                    )
                    DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                    showAddDlg = false
                    toast(getSafeString(R.string.add_success))
                }
            }, enabled = source.isNotEmpty()) {
                Text(text = getSafeString(R.string.add))
            }
        }, dismissButton = {
            TextButton(onClick = { showAddDlg = false }) {
                Text(text = getSafeString(R.string.cancel))
            }
        })
    }

    val editAppRawVal = editRawApp
    if (editAppRawVal != null && subsItem != null && subsRaw != null) {
        var source by remember {
            mutableStateOf(json.encodeToJson5String(editAppRawVal))
        }
        AlertDialog(
            title = { Text(text = getSafeString(R.string.edit_app_rule)) },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(text = getSafeString(R.string.enter_rule_edit)) },
                )
                LaunchedEffect(null) {
                    focusRequester.requestFocus()
                }
            },
            onDismissRequest = {
                if (source.isEmpty()) {
                    editRawApp = null
                }
            }, confirmButton = {
                TextButton(onClick = {
                    try {
                        val newAppRaw = RawSubscription.parseRawApp(source)
                        if (newAppRaw.id != editAppRawVal.id) {
                            toast(getSafeString(R.string.cannot_modify_rule_id))
                            return@TextButton
                        }
                        val oldAppRawIndex =
                            subsRaw.apps.indexOfFirst { a -> a.id == editAppRawVal.id }
                        vm.viewModelScope.launchTry {
                            updateSubscription(
                                subsRaw.copy(
                                    apps = subsRaw.apps.toMutableList().apply {
                                        set(oldAppRawIndex, newAppRaw)
                                    }, version = subsRaw.version + 1
                                )
                            )
                            DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                            editRawApp = null
                            toast(getSafeString(R.string.update_success))
                        }
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        toast(getSafeString(R.string.invalid_rule)+ "${e.message}")
                    }
                }, enabled = source.isNotEmpty()) {
                    Text(text = getSafeString(R.string.add))
                }
            }, dismissButton = {
                TextButton(onClick = { editRawApp = null }) {
                    Text(text = getSafeString(R.string.cancel))
                }
            })
    }
}