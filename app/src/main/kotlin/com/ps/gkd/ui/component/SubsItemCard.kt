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
package com.ps.gkd.ui.component

import android.view.MotionEvent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import com.ramcosta.composedestinations.generated.destinations.CategoryPageDestination
import com.ramcosta.composedestinations.generated.destinations.GlobalRulePageDestination
import com.ramcosta.composedestinations.generated.destinations.SubsPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsItem
import com.ps.gkd.data.deleteSubscription
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.home.HomeVm
import com.ps.gkd.util.LOCAL_SUBS_ID
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.SafeR
import com.ps.gkd.util.formatTimeAgo
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.map
import com.ps.gkd.util.openUri
import com.ps.gkd.util.subsLoadErrorsFlow
import com.ps.gkd.util.subsRefreshErrorsFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubsMutex


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SubsItemCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    subsItem: SubsItem,
    subscription: RawSubscription?,
    index: Int,
    vm: HomeVm,
    isSelectedMode: Boolean,
    isSelected: Boolean,
    onCheckedChange: ((Boolean) -> Unit),
    onSelectedChange: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val subsLoadError by remember(subsItem.id) {
        subsLoadErrorsFlow.map(vm.viewModelScope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshError by remember(subsItem.id) {
        subsRefreshErrorsFlow.map(vm.viewModelScope) { it[subsItem.id] }
    }.collectAsState()
    val subsRefreshing by updateSubsMutex.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val dragged by interactionSource.collectIsDraggedAsState()
    var clickPositionX by remember {
        mutableStateOf(0.dp)
    }
    val onClick = {
        if (!dragged) {
            if (isSelectedMode) {
                onSelectedChange?.invoke()
            } else if (!updateSubsMutex.mutex.isLocked) {
                expanded = true
            }
        }
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .padding(16.dp, 2.dp)
            .pointerInteropFilter { event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    clickPositionX = with(density) { event.x.toDp() }
                }
                false
            },
        shape = MaterialTheme.shapes.small,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Unspecified
            }
        ),
    ) {
        SubsMenuItem(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            subItem = subsItem,
            subscription = subscription,
            offsetX = clickPositionX,
            vm = vm
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (subscription != null) {
                    Text(
                        text = if (subscription.id == LOCAL_SUBS_ID) "$index." +getSafeString(R.string.local_subscription) else "$index.${subscription.name}",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = subscription.numText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (subscription.groupsSize == 0) {
                            LocalContentColor.current.copy(alpha = 0.5f)
                        } else {
                            LocalContentColor.current
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (subsItem.id >= 0) {
                            if (subscription.author != null) {
                                Text(
                                    text = subscription.author,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                text = "v" + (subscription.version.toString()),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            Text(
                                text = stringResource(SafeR.app_name),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Text(
                            text = formatTimeAgo(subsItem.mtime),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    Text(
                        text = "id=${subsItem.id}",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val color = if (subsLoadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    }
                    Text(
                        text = subsLoadError?.message
                            ?: if (subsRefreshing) getSafeString(R.string.loading) else getSafeString(R.string.file_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                if (subsRefreshError != null) {
                    Text(
                        text = getSafeString(R.string.update_error)+ "${subsRefreshError?.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = subsItem.enable,
                enabled = !isSelectedMode,
                onCheckedChange = if (isSelectedMode) null else throttle(fn = onCheckedChange),
            )
        }
    }
}

@Composable
private fun SubsMenuItem(
    expanded: Boolean,
    onExpandedChange: ((Boolean) -> Unit),
    subItem: SubsItem,
    subscription: RawSubscription?,
    offsetX: Dp,
    vm: HomeVm
) {
    val navController = LocalNavController.current
    val context = LocalContext.current as MainActivity
    val density = LocalDensity.current
    var halfMenuWidth by remember {
        mutableStateOf(0.dp)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { onExpandedChange(false) },
        modifier = Modifier.onGloballyPositioned {
            halfMenuWidth = with(density) { it.size.width.toDp() } / 2
        },
        offset = DpOffset(if (offsetX < halfMenuWidth) 0.dp else offsetX - halfMenuWidth, 0.dp)
    ) {
        if (subscription != null) {
            if (subItem.id < 0 || subscription.apps.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(text = getSafeString(R.string.application_rule))
                    },
                    onClick = throttle {
                        onExpandedChange(false)
                        navController.toDestinationsNavigator()
                            .navigate(SubsPageDestination(subItem.id))
                    }
                )
            }
            if (subItem.id < 0 || subscription.categories.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(text = getSafeString(R.string.rule_category))
                    },
                    onClick = throttle {
                        onExpandedChange(false)
                        navController.toDestinationsNavigator()
                            .navigate(CategoryPageDestination(subItem.id))
                    }
                )
            }
            if (subItem.id < 0 || subscription.globalGroups.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(text = getSafeString(R.string.global_rule))
                    },
                    onClick = throttle {
                        onExpandedChange(false)
                        navController.toDestinationsNavigator()
                            .navigate(GlobalRulePageDestination(subItem.id))
                    }
                )
            }
        }
        subscription?.supportUri?.let { supportUri ->
            DropdownMenuItem(
                text = {
                    Text(text = getSafeString(R.string.feedback))
                },
                onClick = {
                    onExpandedChange(false)
                    openUri(supportUri)
                }
            )
        }
        DropdownMenuItem(
            text = {
                Text(text = getSafeString(R.string.export_data))
            },
            onClick = {
                onExpandedChange(false)
                vm.viewModelScope.launchTry(Dispatchers.IO) {
                    vm.showShareDataIdsFlow.value = setOf(subItem.id)
                }
            }
        )
        subItem.updateUrl?.let {
            DropdownMenuItem(
                text = {
                    Text(text = getSafeString(R.string.copy_link))
                },
                onClick = {
                    onExpandedChange(false)
                    ClipboardUtils.copyText(subItem.updateUrl)
                    toast(getSafeString(R.string.copy_success))
                }
            )
            DropdownMenuItem(
                text = {
                    Text(text = getSafeString(R.string.edit_link))
                },
                onClick = {
                    onExpandedChange(false)
                    vm.viewModelScope.launchTry {
                        val newUrl = vm.inputSubsLinkOption.getResult(initValue = it)
                        newUrl ?: return@launchTry
                        vm.addOrModifySubs(newUrl, subItem)
                    }
                }
            )
        }
        if (subItem.id != LOCAL_SUBS_ID) {
            DropdownMenuItem(
                text = {
                    Text(text = getSafeString(R.string.delete_subscription), color = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    onExpandedChange(false)
                    vm.viewModelScope.launchTry {
                        context.mainVm.dialogFlow.waitResult(
                            title = getSafeString(R.string.delete_subscription),
                            text = getSafeString(R.string.confirm_delete)+ " ${subscription?.name ?: subItem.id} ?",
                            error = true,
                        )
                        deleteSubscription(subItem.id)
                    }
                }
            )
        }
    }
}
