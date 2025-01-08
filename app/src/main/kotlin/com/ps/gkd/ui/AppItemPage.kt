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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ImagePreviewPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.ExcludeData
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.data.TakePositionEvent
import com.ps.gkd.data.stringify
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.mainActivity
import com.ps.gkd.ui.component.EmptyText
import com.ps.gkd.ui.component.TowLineText
import com.ps.gkd.ui.component.waitResult
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemPadding
import com.ps.gkd.ui.style.scaffoldPadding
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.appInfoCacheFlow
import com.ps.gkd.util.getGroupRawEnable
import com.ps.gkd.util.json
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubscription
import kotlinx.coroutines.launch
import li.songe.json5.Json5
import li.songe.json5.encodeToJson5String

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AppItemPage(
    subsItemId: Long,
    appId: String,
    focusGroupKey: Int? = null, // 背景/边框高亮一下
) {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<AppItemVm>()
    val subsItem = vm.subsItemFlow.collectAsState().value
    val subsRaw = vm.subsRawFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()
    val categoryConfigs by vm.categoryConfigsFlow.collectAsState()
    val appRaw by vm.subsAppFlow.collectAsState()
    val appInfoCache by com.ps.gkd.util.appInfoCacheFlow.collectAsState()

    val groupToCategoryMap = subsRaw?.groupToCategoryMap ?: emptyMap()

    val (showGroupItem, setShowGroupItem) = remember {
        mutableStateOf<RawSubscription.RawAppGroup?>(
            null
        )
    }

    val editable = subsItem != null && subsItemId < 0

    var showAddDlg by remember { mutableStateOf(false) }

    val (editGroupRaw, setEditGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawAppGroup?>(null)
    }
    val (excludeGroupRaw, setExcludeGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawAppGroup?>(null)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
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
            TowLineText(
                title = subsRaw?.name ?: subsItemId.toString(),
                subTitle = appInfoCache[appId]?.name ?: appRaw.name ?: appId
            )
        }, actions = {})
    }, floatingActionButton = {
        if (editable) {
            FloatingActionButton(onClick = { showAddDlg = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "add",
                )
            }
        }
    }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding)
        ) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
            }
            itemsIndexed(appRaw.groups, { i, g -> i.toString() + g.key }) { _, group ->
                val subsConfig = subsConfigs.find { it.groupKey == group.key }
                val groupEnable = getGroupRawEnable(
                    group,
                    subsConfig,
                    groupToCategoryMap[group],
                    categoryConfigs.find { c -> c.categoryKey == groupToCategoryMap[group]?.key }
                )

                Row(
                    modifier = Modifier
                        .background(
                            if (group.key == focusGroupKey) MaterialTheme.colorScheme.inversePrimary else Color.Transparent
                        )
                        .clickable { setShowGroupItem(group) }
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = group.name,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (group.valid) {
                            if (!group.desc.isNullOrBlank()) {
                                Text(
                                    text = group.desc,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                text = getSafeString(R.string.invalid_selector),
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        IconButton(onClick = {
                            expanded = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "more",
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = getSafeString(R.string.copy))
                                },
                                onClick = {
                                    val groupAppText = json.encodeToJson5String(
                                        appRaw.copy(
                                            groups = listOf(group)
                                        )
                                    )
                                    ClipboardUtils.copyText(groupAppText)
                                    toast(getSafeString(R.string.copy_success))
                                    expanded = false
                                },
                            )
                            if (editable) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = getSafeString(R.string.edit))
                                    },
                                    onClick = {
                                        setEditGroupRaw(group)
                                        expanded = false
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(text = getSafeString(R.string.edit_disable))
                                },
                                onClick = {
                                    setExcludeGroupRaw(group)
                                    expanded = false
                                },
                            )
                            if (subsConfig?.enable != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = getSafeString(R.string.reset_switch_remove_rule_manual_configuration))
                                    },
                                    onClick = {
                                        expanded = false
                                        vm.viewModelScope.launchTry(Dispatchers.IO) {
                                            DbSet.subsConfigDao.insert(subsConfig.copy(enable = null))
                                        }
                                    },
                                )
                            }
                            if (editable && subsRaw != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = getSafeString(R.string.delete), color = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        expanded = false
                                        vm.viewModelScope.launchTry {
                                            context.mainVm.dialogFlow.waitResult(
                                                title = getSafeString(R.string.delete_rule_group),
                                                text = String.format(getSafeString(R.string.delete_rule_group_confirm),group.name),
                                                error = true,
                                            )
                                            val newSubsRaw = subsRaw.copy(
                                                apps = subsRaw.apps
                                                    .toMutableList()
                                                    .apply {
                                                        set(
                                                            indexOfFirst { a -> a.id == appRaw.id },
                                                            appRaw.copy(
                                                                groups = appRaw.groups
                                                                    .filter { g -> g.key != group.key }
                                                            )
                                                        )
                                                    }
                                            )
                                            updateSubscription(newSubsRaw)
                                            DbSet.subsItemDao.update(subsItem!!.copy(mtime = System.currentTimeMillis()))
                                            DbSet.subsConfigDao.delete(
                                                subsItem.id, appRaw.id, group.key
                                            )
                                            toast(getSafeString(R.string.delete_success))
                                        }
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    Switch(
                        checked = groupEnable, modifier = Modifier,
                        onCheckedChange = vm.viewModelScope.launchAsFn { enable ->
                            val newItem = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                                type = SubsConfig.AppGroupType,
                                subsItemId = subsItemId,
                                appId = appId,
                                groupKey = group.key,
                                enable = enable
                            ))
                            DbSet.subsConfigDao.insert(newItem)
                        })
                }
            }

            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (appRaw.groups.isEmpty()) {
                    EmptyText(text = getSafeString(R.string.no_rules))
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }

    showGroupItem?.let { showGroupItemVal ->
        AlertDialog(
            onDismissRequest = { setShowGroupItem(null) },
            title = {
                Text(text = getSafeString(R.string.rule_group_details))
            },
            text = {
                Column {
                    Text(text = showGroupItemVal.name)
                    if (showGroupItemVal.desc != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = showGroupItemVal.desc)
                    }
                }
            },
            confirmButton = {
                if (showGroupItemVal.allExampleUrls.isNotEmpty()) {
                    TextButton(onClick = throttle {
                        setShowGroupItem(null)
                        navController.toDestinationsNavigator().navigate(
                            ImagePreviewPageDestination(
                                title = showGroupItemVal.name,
                                uris = showGroupItemVal.allExampleUrls.toTypedArray()
                            )
                        )
                    }) {
                        Text(text = getSafeString(R.string.view_image))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = throttle {
                    setShowGroupItem(null)
                }) {
                    Text(text = getSafeString(R.string.close))
                }
            }
        )
    }

    if (editGroupRaw != null && subsItem != null) {
        var source by remember {
            mutableStateOf(json.encodeToJson5String(editGroupRaw))
        }
        val focusRequester = remember { FocusRequester() }
        val oldSource = remember { source }
        AlertDialog(
            title = { Text(text = getSafeString(R.string.edit_rule_group)) },
            text = {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text(text = getSafeString(R.string.please_enter_rule_group)) },
                    maxLines = 10,
                )
                LaunchedEffect(null) {
                    focusRequester.requestFocus()
                }
            },
            onDismissRequest = {
                if (source.isEmpty()) {
                    setEditGroupRaw(null)
                }
            },
            dismissButton = {
                TextButton(onClick = { setEditGroupRaw(null) }) {
                    Text(text = getSafeString(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = vm.viewModelScope.launchAsFn(Dispatchers.Default) {
                    if (oldSource == source) {
                        toast(getSafeString(R.string.rule_group_no_change))
                        setEditGroupRaw(null)
                        return@launchAsFn
                    }

                    val element = try {
                        Json5.parseToJson5Element(source).jsonObject
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        error(getSafeString(R.string.illegal_json) + ":${e.message}")
                    }
                    val newGroupRaw = try {
                        if (element["groups"] is JsonArray) {
                            RawSubscription.parseApp(element).groups.let {
                                it.find { g -> g.key == editGroupRaw.key } ?: it.firstOrNull()
                            }
                        } else {
                            null
                        } ?: RawSubscription.parseGroup(element)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        error(getSafeString(R.string.invalid_rule) + ":${e.message}")
                    }
                    if (newGroupRaw.key != editGroupRaw.key) {
                        toast(getSafeString(R.string.cannot_change_the_key_of_the_rule_group))
                        return@launchAsFn
                    }
                    if (newGroupRaw.errorDesc != null) {
                        toast(newGroupRaw.errorDesc!!)
                        return@launchAsFn
                    }
                    setEditGroupRaw(null)
                    subsRaw ?: return@launchAsFn
                    val newSubsRaw = subsRaw.copy(apps = subsRaw.apps.toMutableList().apply {
                        set(
                            indexOfFirst { a -> a.id == appRaw.id },
                            appRaw.copy(groups = appRaw.groups.toMutableList().apply {
                                set(
                                    indexOfFirst { g -> g.key == newGroupRaw.key }, newGroupRaw
                                )
                            })
                        )
                    })
                    updateSubscription(newSubsRaw)
                    DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                    toast(getSafeString(R.string.update_success))
                }, enabled = source.isNotEmpty()) {
                    Text(text = getSafeString(R.string.update))
                }
            },
        )
    }

    if (excludeGroupRaw != null && subsItem != null) {
        var source by remember {
            mutableStateOf(
                ExcludeData.parse(subsConfigs.find { s -> s.groupKey == excludeGroupRaw.key }?.exclude)
                    .stringify(appId)
            )
        }
        val oldSource = remember { source }
        val focusRequester = remember { FocusRequester() }
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
                            text = getSafeString(R.string.enter_activity_id) ,
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
                    setExcludeGroupRaw(null)
                }
            },
            dismissButton = {
                TextButton(onClick = { setExcludeGroupRaw(null) }) {
                    Text(text = getSafeString(R.string.cancel))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (oldSource == source) {
                        toast(getSafeString(R.string.no_change_in_disable))
                        setExcludeGroupRaw(null)
                        return@TextButton
                    }
                    setExcludeGroupRaw(null)
                    val newSubsConfig =
                        (subsConfigs.find { s -> s.groupKey == excludeGroupRaw.key } ?: SubsConfig(
                            type = SubsConfig.AppGroupType,
                            subsItemId = subsItemId,
                            appId = appId,
                            groupKey = excludeGroupRaw.key,
                        )).copy(exclude = ExcludeData.parse(appId, source).stringify())
                    vm.viewModelScope.launchTry(Dispatchers.IO) {
                        DbSet.subsConfigDao.insert(newSubsConfig)
                        toast(getSafeString(R.string.update_success))
                    }
                }) {
                    Text(text = getSafeString(R.string.update))
                }
            },
        )
    }

    if (showAddDlg && subsItem != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = getSafeString(R.string.add_rule_group)) }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = getSafeString(R.string.enter_rule_group_app)) },
                maxLines = 10,
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                showAddDlg = false
            }
        }, confirmButton = {
            TextButton(onClick = {
                val newAppRaw = try {
                    RawSubscription.parseRawApp(source)
                } catch (_: Exception) {
                    null
                }
                val tempGroups = if (newAppRaw == null) {
                    val newGroupRaw = try {
                        RawSubscription.parseRawGroup(source)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        toast(getSafeString(R.string.invalid_rule)+":${e.message}")
                        return@TextButton
                    }
                    listOf(newGroupRaw)
                } else {
                    if (newAppRaw.id != appRaw.id) {
                        toast(getSafeString(R.string.id_not_match))
                        return@TextButton
                    }
                    if (newAppRaw.groups.isEmpty()) {
                        toast(getSafeString(R.string.cannot_add_empty_group))
                        return@TextButton
                    }
                    newAppRaw.groups
                }
                tempGroups.find { g -> g.errorDesc != null }?.errorDesc?.let { errorDesc ->
                    toast(errorDesc)
                    return@TextButton
                }
                tempGroups.forEach { g ->
                    if (appRaw.groups.any { g2 -> g2.name == g.name }) {
                        toast(getSafeString(R.string.rule_name_already_exists) + "[${g.name}]")
                        return@TextButton
                    }
                }
                val newKey = (appRaw.groups.maxByOrNull { g -> g.key }?.key ?: -1) + 1
                subsRaw ?: return@TextButton
                val newSubsRaw = subsRaw.copy(apps = subsRaw.apps.toMutableList().apply {
                    val newApp =
                        appRaw.copy(groups = (appRaw.groups + tempGroups.mapIndexed { i, g ->
                            g.copy(
                                key = newKey + i
                            )
                        }))
                    val i = indexOfFirst { a -> a.id == appRaw.id }
                    if (i < 0) {
                        add(newApp)
                    } else {
                        set(i, newApp)
                    }
                })
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    DbSet.subsItemDao.update(subsItem.copy(mtime = System.currentTimeMillis()))
                    updateSubscription(newSubsRaw)
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
}

