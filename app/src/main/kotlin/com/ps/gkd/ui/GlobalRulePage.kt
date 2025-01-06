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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.GlobalRuleExcludePageDestination
import com.ramcosta.composedestinations.generated.destinations.ImagePreviewPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.EmptyText
import com.ps.gkd.ui.component.TowLineText
import com.ps.gkd.ui.component.waitResult
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemPadding
import com.ps.gkd.ui.style.scaffoldPadding
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.json
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubscription
import li.songe.json5.encodeToJson5String

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun GlobalRulePage(subsItemId: Long, focusGroupKey: Int? = null) {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<GlobalRuleVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val rawSubs = vm.subsRawFlow.collectAsState().value
    val subsConfigs by vm.subsConfigsFlow.collectAsState()

    val editable = subsItemId < 0 && rawSubs != null && subsItem != null
    val globalGroups = rawSubs?.globalGroups ?: emptyList()

    var showAddDlg by remember { mutableStateOf(false) }
    val (menuGroupRaw, setMenuGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawGlobalGroup?>(null)
    }
    val (editGroupRaw, setEditGroupRaw) = remember {
        mutableStateOf<RawSubscription.RawGlobalGroup?>(null)
    }
    val (showGroupItem, setShowGroupItem) = remember {
        mutableStateOf<RawSubscription.RawGlobalGroup?>(
            null
        )
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
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
                TowLineText(
                    title = rawSubs?.name ?: subsItemId.toString(),
                    subTitle = getSafeString(R.string.global_rule)
                )
            })
        },
        floatingActionButton = {
            if (editable) {
                FloatingActionButton(onClick = { showAddDlg = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "add",
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(paddingValues)
        ) {
            items(globalGroups, { g -> g.key }) { group ->
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
                                contentDescription = null,
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
                                    expanded = false
                                    val groupAppText = json.encodeToJson5String(group)
                                    ClipboardUtils.copyText(groupAppText)
                                    toast(getSafeString(R.string.copy_success))
                                }
                            )
                            if (editable) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = getSafeString(R.string.edit))
                                    },
                                    onClick = {
                                        expanded = false
                                        setEditGroupRaw(group)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(text = getSafeString(R.string.edit_disable))
                                },
                                onClick = throttle {
                                    expanded = false
                                    navController.toDestinationsNavigator().navigate(
                                        GlobalRuleExcludePageDestination(
                                            subsItemId,
                                            group.key
                                        )
                                    )
                                }
                            )
                            if (editable) {
                                DropdownMenuItem(
                                    text = {
                                        Text(text = getSafeString(R.string.delete), color = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        expanded = false
                                        vm.viewModelScope.launchTry {
                                            context.mainVm.dialogFlow.waitResult(
                                                title = getSafeString(R.string.delete_rule_group),
                                                text = String.format(getSafeString(R.string.confirm_deletion_of),group.name),
                                                error = true,
                                            )
                                            updateSubscription(
                                                rawSubs.copy(
                                                    globalGroups = rawSubs.globalGroups.filter { g -> g.key != group.key }
                                                )
                                            )
                                            val subsConfig =
                                                subsConfigs.find { it.groupKey == group.key }
                                            if (subsConfig != null) {
                                                DbSet.subsConfigDao.delete(subsConfig)
                                            }
                                            DbSet.subsItemDao.updateMtime(rawSubs.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))

                    val groupEnable = subsConfigs.find { c -> c.groupKey == group.key }?.enable
                        ?: group.enable ?: true
                    val subsConfig = subsConfigs.find { it.groupKey == group.key }
                    Switch(
                        checked = groupEnable, modifier = Modifier,
                        onCheckedChange = vm.viewModelScope.launchAsFn { enable ->
                            val newItem = (subsConfig?.copy(enable = enable) ?: SubsConfig(
                                type = SubsConfig.GlobalGroupType,
                                subsItemId = subsItemId,
                                groupKey = group.key,
                                enable = enable
                            ))
                            DbSet.subsConfigDao.insert(newItem)
                        }
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (globalGroups.isEmpty()) {
                    EmptyText(text = getSafeString(R.string.no_rules))
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }

    if (showAddDlg && rawSubs != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = getSafeString(R.string.add_global_rule_group)) }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = getSafeString(R.string.please_enter_rule_group)) },
                maxLines = 10,
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                showAddDlg = false
            }
        }, confirmButton = {
            TextButton(onClick = {
                val newGroup = try {
                    RawSubscription.parseRawGlobalGroup(source)
                } catch (e: Exception) {
                    toast(getSafeString(R.string.invalid_rule)+"\n${e.message ?: e}")
                    return@TextButton
                }
                if (newGroup.errorDesc != null) {
                    toast(newGroup.errorDesc!!)
                    return@TextButton
                }
                if (rawSubs.globalGroups.any { g -> g.name == newGroup.name }) {
                    toast(getSafeString(R.string.rule_name_already_exists)+"[${newGroup.name}]")
                    return@TextButton
                }
                val newKey = (rawSubs.globalGroups.maxByOrNull { g -> g.key }?.key ?: -1) + 1
                val newRawSubs = rawSubs.copy(
                    globalGroups = rawSubs.globalGroups.toMutableList()
                        .apply { add(newGroup.copy(key = newKey)) }
                )
                updateSubscription(newRawSubs)
                vm.viewModelScope.launchTry(Dispatchers.IO) {
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

    if (menuGroupRaw != null && rawSubs != null) {
        Dialog(onDismissRequest = { setMenuGroupRaw(null) }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {

                if (editable) {
                    Text(text = getSafeString(R.string.delete_rule_group), modifier = Modifier
                        .clickable {
                            setMenuGroupRaw(null)

                        }
                        .padding(16.dp)
                        .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
    if (editGroupRaw != null && rawSubs != null) {
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
                TextButton(onClick = {
                    if (oldSource == source) {
                        setEditGroupRaw(null)
                        toast(getSafeString(R.string.no_change_in_rule))
                        return@TextButton
                    }
                    val newGroupRaw = try {
                        RawSubscription.parseRawGlobalGroup(source)
                    } catch (e: Exception) {
                        LogUtils.d(e)
                        toast(getSafeString(R.string.invalid_rule)+":${e.message}")
                        return@TextButton
                    }
                    if (newGroupRaw.key != editGroupRaw.key) {
                        toast(getSafeString(R.string.cannot_change_the_key_of_the_rule_group))
                        return@TextButton
                    }
                    if (newGroupRaw.errorDesc != null) {
                        toast(newGroupRaw.errorDesc!!)
                        return@TextButton
                    }
                    setEditGroupRaw(null)
                    val newGlobalGroups = rawSubs.globalGroups.toMutableList().apply {
                        val i = rawSubs.globalGroups.indexOfFirst { g -> g.key == newGroupRaw.key }
                        if (i >= 0) {
                            set(i, newGroupRaw)
                        }
                    }
                    updateSubscription(rawSubs.copy(globalGroups = newGlobalGroups))
                    vm.viewModelScope.launchTry(Dispatchers.IO) {
                        DbSet.subsItemDao.updateMtime(rawSubs.id)
                        toast(getSafeString(R.string.update_success))
                    }
                }, enabled = source.isNotEmpty()) {
                    Text(text = getSafeString(R.string.update))
                }
            },
        )
    }

    if (showGroupItem != null) {
        AlertDialog(
            onDismissRequest = { setShowGroupItem(null) },
            title = {
                Text(text = getSafeString(R.string.rule_group_details))
            },
            text = {
                Column {
                    Text(text = showGroupItem.name)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = showGroupItem.desc ?: "")
                }
            },
            confirmButton = {
                if (showGroupItem.allExampleUrls.isNotEmpty()) {
                    TextButton(onClick = throttle {
                        setShowGroupItem(null)
                        navController.toDestinationsNavigator().navigate(
                            ImagePreviewPageDestination(
                                title = showGroupItem.name,
                                uris = showGroupItem.allExampleUrls.toTypedArray()
                            )
                        )
                    }) {
                        Text(text = getSafeString(R.string.view_image))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { setShowGroupItem(null) }) {
                    Text(text = getSafeString(R.string.close))
                }
            }
        )
    }
}