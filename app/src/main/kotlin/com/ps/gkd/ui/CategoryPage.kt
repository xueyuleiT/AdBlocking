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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.CategoryConfig
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.EmptyText
import com.ps.gkd.ui.component.TowLineText
import com.ps.gkd.ui.component.updateDialogOptions
import com.ps.gkd.ui.component.waitResult
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemPadding
import com.ps.gkd.ui.style.scaffoldPadding
import com.ps.gkd.util.EnableGroupOption
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.findOption
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubscription

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun CategoryPage(subsItemId: Long) {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current

    val vm = viewModel<CategoryVm>()
    val subsItem by vm.subsItemFlow.collectAsState()
    val subsRaw by vm.subsRawFlow.collectAsState()
    val categoryConfigs by vm.categoryConfigsFlow.collectAsState()
    val editable = subsItem != null && subsItemId < 0

    var showAddDlg by remember {
        mutableStateOf(false)
    }
    val (editNameCategory, setEditNameCategory) = remember {
        mutableStateOf<RawSubscription.RawCategory?>(null)
    }

    val categories = subsRaw?.categories ?: emptyList()
    val categoriesGroups = subsRaw?.categoryToGroupsMap ?: emptyMap()
    val categoriesApps = subsRaw?.categoryToAppMap ?: emptyMap()

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
                subTitle = getSafeString(R.string.rule_category)
            )
        }, actions = {
            IconButton(onClick = throttle {
                context.mainVm.dialogFlow.updateDialogOptions(
                    title = getSafeString(R.string.switch_priority),
                    text = getSafeString(R.string.rule_manual_configuration_classification_manual_configuration_classification_default_rule_default),
                )
            }) {
                Icon(Icons.Outlined.Info, contentDescription = null)
            }
        })
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
            items(categories, { it.key }) { category ->
                var selectedExpanded by remember { mutableStateOf(false) }
                Row(modifier = Modifier
                    .clickable { selectedExpanded = true }
                    .itemPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    val groups = categoriesGroups[category] ?: emptyList()
                    val size = groups.size
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (size > 0) {
                            val appSize = categoriesApps[category]?.size ?: 0
                            Text(
                                text = String.format(getSafeString(R.string.applications_rule_groups),appSize,size) ,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = getSafeString(R.string.no_rules),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                    var expanded by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier.wrapContentSize(Alignment.TopStart)
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
                            DropdownMenuItem(text = {
                                Text(text = getSafeString(R.string.reset_switch_remove_rule_manual_configuration))
                            }, onClick = {
                                expanded = false
                                vm.viewModelScope.launchTry(Dispatchers.IO) {
                                    val updatedList = DbSet.subsConfigDao.batchResetAppGroupEnable(
                                        subsItemId,
                                        groups
                                    )
                                    if (updatedList.isNotEmpty()) {
                                        toast(String.format(getSafeString(R.string.successfully_reset_rule_group_switches),updatedList.size))
                                    } else {
                                        toast(getSafeString(R.string.no_rule_groups_to_reset))
                                    }
                                }
                            })
                            if (editable) {
                                DropdownMenuItem(text = {
                                    Text(text = getSafeString(R.string.edit))
                                }, onClick = {
                                    expanded = false
                                    setEditNameCategory(category)
                                })
                                DropdownMenuItem(text = {
                                    Text(text = getSafeString(R.string.delete), color = MaterialTheme.colorScheme.error)
                                }, onClick = {
                                    expanded = false
                                    vm.viewModelScope.launchTry {
                                        context.mainVm.dialogFlow.waitResult(
                                            title = getSafeString(R.string.delete_category),
                                            text = String.format(getSafeString(R.string.confirm_deletion_of),category.name),
                                            error = true,
                                        )
                                        subsItem?.apply {
                                            updateSubscription(subsRaw!!.copy(categories = subsRaw!!.categories.filter { c -> c.key != category.key }))
                                            DbSet.subsItemDao.update(copy(mtime = System.currentTimeMillis()))
                                        }
                                        DbSet.categoryConfigDao.deleteByCategoryKey(
                                            subsItemId, category.key
                                        )
                                        toast(getSafeString(R.string.delete_success))
                                    }
                                })
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categoryConfig =
                            categoryConfigs.find { c -> c.categoryKey == category.key }
                        val enable =
                            if (categoryConfig != null) categoryConfig.enable else category.enable
                        Text(
                            text = EnableGroupOption.allSubObject.findOption(enable).label,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Icon(
                            imageVector = Icons.Default.UnfoldMore, contentDescription = null
                        )
                        DropdownMenu(expanded = selectedExpanded,
                            onDismissRequest = { selectedExpanded = false }) {
                            EnableGroupOption.allSubObject.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(text = option.label)
                                    },
                                    onClick = {
                                        selectedExpanded = false
                                        if (option.value != enable) {
                                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                                DbSet.categoryConfigDao.insert(
                                                    (categoryConfig ?: CategoryConfig(
                                                        enable = option.value,
                                                        subsItemId = subsItemId,
                                                        categoryKey = category.key
                                                    )).copy(enable = option.value)
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (categories.isEmpty()) {
                    EmptyText(text = getSafeString(R.string.no_categories))
                } else if (editable) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }

    val subsRawVal = subsRaw
    if (editNameCategory != null && subsRawVal != null) {
        var source by remember {
            mutableStateOf(editNameCategory.name)
        }
        AlertDialog(title = { Text(text = getSafeString(R.string.edit_category)) }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = getSafeString(R.string.please_enter_category_name)) },
                singleLine = true
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                setEditNameCategory(null)
            }
        }, dismissButton = {
            TextButton(onClick = { setEditNameCategory(null) }) {
                Text(text = getSafeString(R.string.cancel))
            }
        }, confirmButton = {
            TextButton(enabled = source.isNotBlank() && source != editNameCategory.name, onClick = {
                if (categories.any { c -> c.key != editNameCategory.key && c.name == source }) {
                    toast(getSafeString(R.string.cannot_add_same_name_category))
                    return@TextButton
                }
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    subsItem?.apply {
                        updateSubscription(
                            subsRawVal.copy(categories = categories.toMutableList().apply {
                                val i =
                                    categories.indexOfFirst { c -> c.key == editNameCategory.key }
                                if (i >= 0) {
                                    set(i, editNameCategory.copy(name = source))
                                }
                            })
                        )
                        DbSet.subsItemDao.update(copy(mtime = System.currentTimeMillis()))
                    }
                    toast(getSafeString(R.string.modification_successful))
                    setEditNameCategory(null)
                }
            }) {
                Text(text = getSafeString(R.string.confirm))
            }
        })
    }
    if (showAddDlg && subsRawVal != null) {
        var source by remember {
            mutableStateOf("")
        }
        AlertDialog(title = { Text(text = getSafeString(R.string.add_category)) }, text = {
            OutlinedTextField(
                value = source,
                onValueChange = { source = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(text = getSafeString(R.string.please_enter_category_name)) },
                singleLine = true
            )
        }, onDismissRequest = {
            if (source.isEmpty()) {
                showAddDlg = false
            }
        }, dismissButton = {
            TextButton(onClick = { showAddDlg = false }) {
                Text(text = getSafeString(R.string.cancel))
            }
        }, confirmButton = {
            TextButton(enabled = source.isNotEmpty(), onClick = {
                if (categories.any { c -> c.name == source }) {
                    toast(getSafeString(R.string.cannot_add_same_name_category))
                    return@TextButton
                }
                showAddDlg = false
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    subsItem?.apply {
                        updateSubscription(
                            subsRawVal.copy(categories = categories.toMutableList().apply {
                                add(RawSubscription.RawCategory(key = (categories.maxOfOrNull { c -> c.key }
                                    ?: -1) + 1, name = source, enable = null))
                            })
                        )
                        DbSet.subsItemDao.update(copy(mtime = System.currentTimeMillis()))
                        toast(getSafeString(R.string.add_success))
                    }
                }
            }) {
                Text(text = getSafeString(R.string.confirm))
            }
        })
    }
}