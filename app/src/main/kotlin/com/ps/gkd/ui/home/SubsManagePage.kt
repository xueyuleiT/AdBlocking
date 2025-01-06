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

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dylanc.activityresult.launcher.launchForResult
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.Value
import com.ps.gkd.data.deleteSubscription
import com.ps.gkd.data.exportData
import com.ps.gkd.data.importData
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.SubsItemCard
import com.ps.gkd.ui.component.TextMenu
import com.ps.gkd.ui.component.waitResult
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemVerticalPadding
import com.ps.gkd.util.LOCAL_SUBS_ID
import com.ps.gkd.util.SafeR
import com.ps.gkd.util.UpdateTimeOption
import com.ps.gkd.util.checkSubsUpdate
import com.ps.gkd.util.findOption
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.map
import com.ps.gkd.util.openUri
import com.ps.gkd.util.saveFileToDownloads
import com.ps.gkd.util.shareFile
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.subsIdToRawFlow
import com.ps.gkd.util.subsItemsFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubsMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

val subsNav = BottomNavItem(
    label = getSafeString(R.string.subscription), icon = Icons.AutoMirrored.Filled.FormatListBulleted
)

@Composable
fun useSubsManagePage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity

    val vm = viewModel<HomeVm>()
    val subItems by subsItemsFlow.collectAsState()
    val subsIdToRaw by subsIdToRawFlow.collectAsState()

    var orderSubItems by remember {
        mutableStateOf(subItems)
    }

    LaunchedEffect(subItems) {
        orderSubItems = subItems
    }

    val refreshing by updateSubsMutex.state.collectAsState()
    val pullToRefreshState = rememberPullToRefreshState()
    var isSelectedMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val draggedFlag = remember { Value(false) }
    LaunchedEffect(key1 = isSelectedMode) {
        if (!isSelectedMode && selectedIds.isNotEmpty()) {
            selectedIds = emptySet()
        }
    }
    if (isSelectedMode) {
        BackHandler {
            isSelectedMode = false
        }
    }
    LaunchedEffect(key1 = subItems.size) {
        if (subItems.size <= 1) {
            isSelectedMode = false
        }
    }

    var showSettingsDlg by remember { mutableStateOf(false) }
    if (showSettingsDlg) {
        AlertDialog(
            onDismissRequest = { showSettingsDlg = false },
            title = { Text(getSafeString(R.string.subscription_settings)) },
            text = {
                val store by storeFlow.collectAsState()
                Column {
                    TextMenu(
                        modifier = Modifier.padding(0.dp, itemVerticalPadding),
                        title = getSafeString(R.string.update_subscription),
                        option = UpdateTimeOption.allSubObject.findOption(store.updateSubsInterval)
                    ) {
                        storeFlow.update { s -> s.copy(updateSubsInterval = it.value) }
                    }

                    val updateValue = remember {
                        throttle(fn = {
                            storeFlow.update { it.copy(subsPowerWarn = !it.subsPowerWarn) }
                        })
                    }
                    Row(
                        modifier = Modifier
                            .padding(0.dp, itemVerticalPadding)
                            .clickable(onClick = updateValue),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = getSafeString(R.string.power_consumption_warning),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = getSafeString(R.string.confirm_before_multiple_subscriptions),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Checkbox(
                            checked = store.subsPowerWarn,
                            onCheckedChange = { updateValue() }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDlg = false }) {
                    Text(getSafeString(R.string.close))
                }
            }
        )
    }

    ShareDataDialog(vm)
    vm.inputSubsLinkOption.ContentDialog()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    return ScaffoldExt(
        navItem = subsNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                if (isSelectedMode) {
                    IconButton(onClick = { isSelectedMode = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                        )
                    }
                }
            }, title = {
                if (isSelectedMode) {
                    Text(
                        text = if (selectedIds.isNotEmpty()) selectedIds.size.toString() else "",
                    )
                } else {
                    Text(
                        text = subsNav.label,
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                if (isSelectedMode) {
                    val canDeleteIds = if (selectedIds.contains(LOCAL_SUBS_ID)) {
                        selectedIds - LOCAL_SUBS_ID
                    } else {
                        selectedIds
                    }
                    if (canDeleteIds.isNotEmpty()) {
                        val text = if (selectedIds.contains(LOCAL_SUBS_ID)) String.format(getSafeString(R.string.confirm_deletion_of_subscriptions2),canDeleteIds.size) else  String.format(getSafeString(R.string.confirm_deletion_of_subscriptions),canDeleteIds.size)
                        IconButton(onClick = vm.viewModelScope.launchAsFn {
                            context.mainVm.dialogFlow.waitResult(
                                title = getSafeString(R.string.delete_subscription),
                                text = text,
                                error = true,
                            )
                            deleteSubscription(*canDeleteIds.toLongArray())
                            selectedIds = selectedIds - canDeleteIds
                            if (selectedIds.size == canDeleteIds.size) {
                                isSelectedMode = false
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = null,
                            )
                        }
                    }
                    IconButton(onClick = {
                        vm.showShareDataIdsFlow.value = selectedIds
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        expanded = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                } else {
                    IconButton(onClick = throttle {
                        if (storeFlow.value.enableMatch) {
                            toast(getSafeString(R.string.pause_rule_matching))
                        } else {
                            toast(getSafeString(R.string.enable_rule_matching))
                        }
                        storeFlow.update { s -> s.copy(enableMatch = !s.enableMatch) }
                    }) {
                        val scope = rememberCoroutineScope()
                        val enableMatch by remember {
                            storeFlow.map(scope) { it.enableMatch }
                        }.collectAsState()
                        val id = if (enableMatch) SafeR.ic_flash_on else SafeR.ic_flash_off
                        Icon(
                            painter = painterResource(id = id),
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        showSettingsDlg = true
                    }) {
                        Icon(
                            painter = painterResource(id = SafeR.ic_page_info),
                            contentDescription = null,
                        )
                    }
                    IconButton(onClick = {
                        if (updateSubsMutex.mutex.isLocked) {
                            toast(getSafeString(R.string.refreshing_subscription_please_wait))
                        } else {
                            expanded = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = null,
                        )
                    }
                }
                Box(
                    modifier = Modifier.wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (isSelectedMode) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = getSafeString(R.string.select_all))
                                },
                                onClick = {
                                    expanded = false
                                    selectedIds = subItems.map { it.id }.toSet()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = getSafeString(R.string.deselect_all))
                                },
                                onClick = {
                                    expanded = false
                                    val newSelectedIds =
                                        subItems.map { it.id }.toSet() - selectedIds
                                    if (newSelectedIds.isEmpty()) {
                                        isSelectedMode = false
                                    }
                                    selectedIds = newSelectedIds
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                    )
                                },
                                text = {
                                    Text(text = getSafeString(R.string.import_data))
                                },
                                onClick = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                                    expanded = false
                                    val result =
                                        context.launcher.launchForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "application/zip"
                                        })
                                    val uri = result.data?.data
                                    if (uri == null) {
                                        toast(getSafeString(R.string.no_file_selected))
                                        return@launchAsFn
                                    }
                                    importData(uri)
                                },
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            if (!isSelectedMode) {
                FloatingActionButton(onClick = {
                    if (updateSubsMutex.mutex.isLocked) {
                        toast(getSafeString(R.string.refreshing_subscription_please_wait))
                        return@FloatingActionButton
                    }
                    vm.viewModelScope.launchTry {
                        val url = vm.inputSubsLinkOption.getResult() ?: return@launchTry
                        vm.addOrModifySubs(url)
                    }
                }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "info",
                    )
                }
            }
        },
    ) { contentPadding ->
        val lazyListState = rememberLazyListState()
        val reorderableLazyColumnState =
            rememberReorderableLazyListState(lazyListState) { from, to ->
                orderSubItems = orderSubItems.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                    forEachIndexed { index, subsItem ->
                        if (subsItem.order != index) {
                            this[index] = subsItem.copy(order = index)
                        }
                    }
                }
                draggedFlag.value = true
            }
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = { checkSubsUpdate(true) }
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(orderSubItems, { _, subItem -> subItem.id }) { index, subItem ->
                    val canDrag = !refreshing && orderSubItems.size > 1
                    ReorderableItem(
                        reorderableLazyColumnState,
                        key = subItem.id,
                        enabled = canDrag,
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        SubsItemCard(
                            modifier = Modifier.longPressDraggableHandle(
                                enabled = canDrag,
                                interactionSource = interactionSource,
                                onDragStarted = {
                                    if (orderSubItems.size > 1 && !isSelectedMode) {
                                        isSelectedMode = true
                                        selectedIds = setOf(subItem.id)
                                    }
                                },
                                onDragStopped = {
                                    if (draggedFlag.value) {
                                        draggedFlag.value = false
                                        isSelectedMode = false
                                        selectedIds = emptySet()
                                    }
                                    val changeItems = orderSubItems.filter { newItem ->
                                        subItems.find { oldItem -> oldItem.id == newItem.id }?.order != newItem.order
                                    }
                                    if (changeItems.isNotEmpty()) {
                                        vm.viewModelScope.launchTry {
                                            DbSet.subsItemDao.batchUpdateOrder(changeItems)
                                        }
                                    }
                                },
                            ),
                            interactionSource = interactionSource,
                            subsItem = subItem,
                            subscription = subsIdToRaw[subItem.id],
                            index = index + 1,
                            vm = vm,
                            isSelectedMode = isSelectedMode,
                            isSelected = selectedIds.contains(subItem.id),
                            onCheckedChange = { checked ->
                                context.mainVm.viewModelScope.launch {
                                    if (checked && storeFlow.value.subsPowerWarn && !subItem.isLocal && subsItemsFlow.value.count { !it.isLocal } > 1) {
                                        context.mainVm.dialogFlow.waitResult(
                                            title = getSafeString(R.string.power_consumption_warning),
                                            textContent = {
                                                Column {
                                                    Text(text = getSafeString(R.string.power_consumption_warning_enabling_multiple_remote_subscriptions_may_cause_a_large_number_of_duplicate_rules_to_be_executed_which_may_cause_rule_execution_lag_and_excessive_power_consumption))
                                                    Text(
                                                        text = getSafeString(R.string.view_power_consumption_instructions),
                                                        modifier = Modifier.clickable(
                                                            onClick = throttle(
                                                                fn = { openUri("https://gkd.li?r=6") }
                                                            )
                                                        ),
                                                        textDecoration = TextDecoration.Underline,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            },
                                            confirmText = getSafeString(R.string.still_enable),
                                            error = true
                                        )
                                    }
                                    DbSet.subsItemDao.updateEnable(subItem.id, checked)
                                }
                            },
                            onSelectedChange = {
                                val newSelectedIds = if (selectedIds.contains(subItem.id)) {
                                    selectedIds.toMutableSet().apply {
                                        remove(subItem.id)
                                    }
                                } else {
                                    selectedIds + subItem.id
                                }
                                selectedIds = newSelectedIds
                                if (newSelectedIds.isEmpty()) {
                                    isSelectedMode = false
                                }
                            },
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}

@Composable
private fun ShareDataDialog(vm: HomeVm) {
    val context = LocalContext.current as MainActivity
    val showShareDataIds = vm.showShareDataIdsFlow.collectAsState().value
    if (showShareDataIds != null) {
        Dialog(onDismissRequest = { vm.showShareDataIdsFlow.value = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                val modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                Text(
                    text = getSafeString(R.string.share_to_other_apps), modifier = Modifier
                        .clickable(onClick = throttle {
                            vm.showShareDataIdsFlow.value = null
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val file = exportData(showShareDataIds)
                                context.shareFile(file, getSafeString(R.string.share_data_file))
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = getSafeString(R.string.save_to_downloads),
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            vm.showShareDataIdsFlow.value = null
                            vm.viewModelScope.launchTry(Dispatchers.IO) {
                                val file = exportData(showShareDataIds)
                                context.saveFileToDownloads(file)
                            }
                        })
                        .then(modifier)
                )
            }
        }
    }
}