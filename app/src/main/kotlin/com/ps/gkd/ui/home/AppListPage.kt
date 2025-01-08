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
package com.ps.gkd.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.KeyboardUtils
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.ramcosta.composedestinations.generated.destinations.AppConfigPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.update
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.AppBarTextField
import com.ps.gkd.ui.component.AppNameText
import com.ps.gkd.ui.component.EmptyText
import com.ps.gkd.ui.component.QueryPkgAuthCard
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.appItemPadding
import com.ps.gkd.ui.style.menuPadding
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.SortTypeOption
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.mapHashCode
import com.ps.gkd.util.ruleSummaryFlow
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.throttle

val appListNav = BottomNavItem(
    label = getSafeString(R.string.app), icon = Icons.Default.Apps
)

@Composable
fun useAppListPage(): ScaffoldExt {
    val navController = LocalNavController.current
    val context = LocalContext.current as MainActivity
    val softwareKeyboardController = LocalSoftwareKeyboardController.current

    val vm = viewModel<HomeVm>()
    val showSystemApp by vm.showSystemAppFlow.collectAsState()
    val showHiddenApp by vm.showHiddenAppFlow.collectAsState()
    val sortType by vm.sortTypeFlow.collectAsState()
    val orderedAppInfos by vm.appInfosFlow.collectAsState()
    val searchStr by vm.searchStrFlow.collectAsState()
    val ruleSummary by ruleSummaryFlow.collectAsState()

    val globalDesc = if (ruleSummary.globalGroups.isNotEmpty()) {
        "${ruleSummary.globalGroups.size}${getSafeString(R.string.global)}"
    } else {
        null
    }

    var expanded by remember { mutableStateOf(false) }
    var showSearchBar by rememberSaveable {
        mutableStateOf(false)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key1 = showSearchBar, block = {
        if (showSearchBar && searchStr.isEmpty()) {
            focusRequester.requestFocus()
        }
        if (!showSearchBar) {
            vm.searchStrFlow.value = ""
        }
    })
    val listState = rememberLazyListState()

    var isFirstVisit by remember { mutableStateOf(true) }
    LaunchedEffect(
        key1 = orderedAppInfos.mapHashCode { it.id }
    ) {
        if (isFirstVisit) {
            isFirstVisit = false
        } else {
            listState.scrollToItem(0)
        }
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    return ScaffoldExt(
        navItem = appListNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DisposableEffect(null) {
                onDispose {
                    if (vm.searchStrFlow.value.isEmpty()) {
                        showSearchBar = false
                    }
                }
            }
            TopAppBar(scrollBehavior = scrollBehavior, title = {
                if (showSearchBar) {
                    BackHandler(searchStr.isEmpty()) {
                        if (KeyboardUtils.isSoftInputVisible(context)) {
                            softwareKeyboardController?.hide()
                        } else {
                            showSearchBar = false
                        }
                    }
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = { newValue -> vm.searchStrFlow.value = newValue.trim() },
                        hint = getSafeString(R.string.enter_app_name_id),
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                } else {
                    Text(
                        modifier = Modifier.clickable(
                            enabled = orderedAppInfos.isNotEmpty(),
                            onClick = throttle {
                                if (orderedAppInfos.isNotEmpty()) {
                                    vm.viewModelScope.launchTry { listState.scrollToItem(0) }
                                }
                            }
                        ),
                        text = appListNav.label,
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
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null
                        )
                    }
                } else {
                    IconButton(onClick = {
                        showSearchBar = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
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
                                                storeFlow.update { s -> s.copy(sortType = sortOption.value) }
                                            }
                                        )
                                    },
                                    onClick = {
                                        storeFlow.update { s -> s.copy(sortType = sortOption.value) }
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
                                            storeFlow.update { s -> s.copy(showSystemApp = !showSystemApp) }
                                        }
                                    )
                                },
                                onClick = {
                                    storeFlow.update { s -> s.copy(showSystemApp = !showSystemApp) }
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
                                            storeFlow.update { s -> s.copy(showHiddenApp = !s.showHiddenApp) }
                                        })
                                },
                                onClick = {
                                    storeFlow.update { s -> s.copy(showHiddenApp = !showHiddenApp) }
                                },
                            )
                        }
                    }
                }
            })
        }
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
            state = listState
        ) {
            items(orderedAppInfos, { it.id }) { appInfo ->
                Row(
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            navController
                                .toDestinationsNavigator()
                                .navigate(AppConfigPageDestination(appInfo.id))
                        })
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
                    ) {
                        AppNameText(appInfo = appInfo)
                        val appGroups = ruleSummary.appIdToAllGroups[appInfo.id] ?: emptyList()

                        val appDesc = if (appGroups.isNotEmpty()) {
                            when (val disabledCount = appGroups.count { g -> !g.enable }) {
                                0 -> {
                                    "${appGroups.size}${getSafeString(R.string.group_rule)}"
                                }

                                appGroups.size -> {
                                    "${appGroups.size}${getSafeString(R.string.group_rule)}/${disabledCount}${getSafeString(R.string.close)}"
                                }

                                else -> {
                                    "${appGroups.size}${getSafeString(R.string.group_rule)}/${appGroups.size - disabledCount}${getSafeString(R.string.enable)}/${disabledCount}${getSafeString(R.string.close)}"
                                }
                            }
                        } else {
                            null
                        }

                        val desc = if (globalDesc != null) {
                            if (appDesc != null) {
                                "$globalDesc/$appDesc"
                            } else {
                                globalDesc
                            }
                        } else {
                            appDesc
                        }

                        if (desc != null) {
                            Text(
                                text = desc,
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
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (orderedAppInfos.isEmpty() && searchStr.isNotEmpty()) {
                    val hasShowAll = showSystemApp && showHiddenApp
                    EmptyText(text = if (hasShowAll) getSafeString(R.string.no_search_results) else getSafeString(R.string.no_search_results_try))
                }
                QueryPkgAuthCard()
            }
        }
    }
}