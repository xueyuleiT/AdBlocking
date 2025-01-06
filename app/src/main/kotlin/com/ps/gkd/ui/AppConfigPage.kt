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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AppItemPageDestination
import com.ramcosta.composedestinations.generated.destinations.GlobalRulePageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.update
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.ExcludeData
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.ResolvedGroup
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.data.stringify
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.EmptyText
import com.ps.gkd.ui.component.updateDialogOptions
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemPadding
import com.ps.gkd.ui.style.itemVerticalPadding
import com.ps.gkd.ui.style.menuPadding
import com.ps.gkd.ui.style.scaffoldPadding
import com.ps.gkd.ui.style.titleItemPadding
import com.ps.gkd.util.LOCAL_SUBS_ID
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.RuleSortOption
import com.ps.gkd.util.appInfoCacheFlow
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.throttle

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AppConfigPage(appId: String) {
    val navController = LocalNavController.current
    val vm = viewModel<AppConfigVm>()
    val ruleSortType by vm.ruleSortTypeFlow.collectAsState()
    val appInfoCache by com.ps.gkd.util.appInfoCacheFlow.collectAsState()
    val appInfo = appInfoCache[appId]
    val globalGroups by vm.globalGroupsFlow.collectAsState()
    val appGroups by vm.appGroupsFlow.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var expanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var isFirstVisit by remember { mutableStateOf(true) }
    LaunchedEffect(globalGroups.size, appGroups.size, ruleSortType.value) {
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
                Text(
                    text = appInfo?.name ?: appId,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }, actions = {
                IconButton(onClick = {
                    expanded = true
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
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
                        RuleSortOption.allSubObject.forEach { s ->
                            DropdownMenuItem(
                                text = {
                                    Text(s.label)
                                },
                                trailingIcon = {
                                    RadioButton(
                                        selected = ruleSortType == s,
                                        onClick = {
                                            storeFlow.update { it.copy(appRuleSortType = s.value) }
                                        }
                                    )
                                },
                                onClick = {
                                    storeFlow.update { it.copy(appRuleSortType = s.value) }
                                },
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = throttle {
                    navController.toDestinationsNavigator()
                        .navigate(AppItemPageDestination(LOCAL_SUBS_ID, appId))
                },
                content = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                    )
                }
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
            state = listState,
        ) {
            itemsIndexed(globalGroups) { i, g ->
                val excludeData = remember(g.config?.exclude) {
                    ExcludeData.parse(g.config?.exclude)
                }
                val checked = getChecked(excludeData, g.group, appId, appInfo)
                TitleGroupCard(globalGroups, i) {
                    AppGroupCard(
                        group = g.group,
                        checked = checked,
                        onClick = throttle {
                            navController.toDestinationsNavigator().navigate(
                                GlobalRulePageDestination(
                                    g.subsItem.id,
                                    g.group.key
                                )
                            )
                        }
                    ) { newChecked ->
                        vm.viewModelScope.launchTry {
                            DbSet.subsConfigDao.insert(
                                (g.config ?: SubsConfig(
                                    type = SubsConfig.GlobalGroupType,
                                    subsItemId = g.subsItem.id,
                                    groupKey = g.group.key,
                                )).copy(
                                    exclude = excludeData.copy(
                                        appIds = excludeData.appIds.toMutableMap().apply {
                                            set(appId, !newChecked)
                                        }
                                    ).stringify()
                                )
                            )
                        }
                    }
                }
            }
            item {
                if (globalGroups.isNotEmpty() && appGroups.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(0.dp, itemVerticalPadding),
                    )
                }
            }
            itemsIndexed(appGroups) { i, g ->
                TitleGroupCard(appGroups, i) {
                    AppGroupCard(
                        group = g.group,
                        checked = g.enable,
                        onClick = {
                            navController.toDestinationsNavigator().navigate(
                                AppItemPageDestination(
                                    g.subsItem.id,
                                    appId,
                                    g.group.key,
                                )
                            )
                        }
                    ) {
                        vm.viewModelScope.launchTry {
                            DbSet.subsConfigDao.insert(
                                g.config?.copy(enable = it) ?: SubsConfig(
                                    type = SubsConfig.AppGroupType,
                                    subsItemId = g.subsItem.id,
                                    appId = appId,
                                    groupKey = g.group.key,
                                    enable = it
                                )
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (globalGroups.size + appGroups.size == 0) {
                    EmptyText(text = getSafeString(R.string.no_rules))
                } else {
                    // 避免被 floatingActionButton 遮挡
                    Spacer(modifier = Modifier.height(EmptyHeight))
                }
            }
        }
    }
}

@Composable
private fun TitleGroupCard(groups: List<ResolvedGroup>, i: Int, content: @Composable () -> Unit) {
    val lastGroup = groups.getOrNull(i - 1)
    val g = groups[i]
    if (g.subsItem !== lastGroup?.subsItem) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = g.subscription.name,
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            content()
        }
    } else {
        content()
    }
}

@Composable
private fun AppGroupCard(
    group: RawSubscription.RawGroupProps,
    checked: Boolean?,
    onClick: () -> Unit,
    onCheckedChange: ((Boolean) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
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
                style = MaterialTheme.typography.bodyLarge
            )
            if (group.valid) {
                if (!group.desc.isNullOrBlank()) {
                    Text(
                        text = group.desc!!,
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
        if (checked != null) {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        } else {
            InnerDisableSwitch()
        }
    }
}

@Composable
fun InnerDisableSwitch() {
    val context = LocalContext.current as MainActivity
    Switch(
        checked = false,
        enabled = false,
        onCheckedChange = null,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
        ) {
            context.mainVm.dialogFlow.updateDialogOptions(
                title = getSafeString(R.string.builtin_disable),
                text = getSafeString(R.string.this_rule_group_has_already_configured_the_disablement_of_the_current_application_in_its_apps_field_therefore_it_cannot_be_manually_enabled),
            )
        }
    )
}