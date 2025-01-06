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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.generated.destinations.ActionLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.AuthA11YPageDestination
import com.ramcosta.composedestinations.generated.destinations.SlowGroupPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.a11yServiceEnabledFlow
import com.ps.gkd.getSafeString
import com.ps.gkd.permission.notificationState
import com.ps.gkd.permission.requiredPermission
import com.ps.gkd.permission.writeSecureSettingsState
import com.ps.gkd.service.A11yService
import com.ps.gkd.service.ManageService
import com.ps.gkd.service.switchA11yService
import com.ps.gkd.ui.component.AuthCard
import com.ps.gkd.ui.component.SettingItem
import com.ps.gkd.ui.component.TextSwitch
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemHorizontalPadding
import com.ps.gkd.ui.style.itemPadding
import com.ps.gkd.util.HOME_PAGE_URL
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.SafeR
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.openUri
import com.ps.gkd.util.ruleSummaryFlow
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.throttle

val controlNav = BottomNavItem(label = getSafeString(R.string.home), icon = Icons.Outlined.Home)

@Composable
fun useControlPage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<HomeVm>()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()
    return ScaffoldExt(navItem = controlNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = stringResource(SafeR.app_name),
                )
            }, actions = {
                IconButton(onClick = throttle {
                    navController.toDestinationsNavigator().navigate(AuthA11YPageDestination)
                }) {
                    Icon(
                        imageVector = Icons.Outlined.RocketLaunch,
                        contentDescription = null,
                    )
                }
                IconButton(onClick = throttle { openUri(HOME_PAGE_URL) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                    )
                }
            })
        }
    ) { contentPadding ->
        val latestRecordDesc by vm.latestRecordDescFlow.collectAsState()
        val subsStatus by vm.subsStatusFlow.collectAsState()
        val store by storeFlow.collectAsState()
        val ruleSummary by ruleSummaryFlow.collectAsState()

        val a11yRunning by A11yService.isRunning.collectAsState()
        val manageRunning by ManageService.isRunning.collectAsState()
        val a11yServiceEnabled by a11yServiceEnabledFlow.collectAsState()

        // 无障碍故障: 设置中无障碍开启, 但是实际 service 没有运行
        val a11yBroken = !writeSecureSettings && !a11yRunning && a11yServiceEnabled

        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
        ) {
            if (writeSecureSettings) {
                TextSwitch(
                    title = getSafeString(R.string.service_status),
                    subtitle = if (a11yRunning) getSafeString(R.string.accessibility_running) else  getSafeString(R.string.accessibility_not_running),
                    checked = a11yRunning,
                    onCheckedChange = {
                        switchA11yService()
                    })
            }
            if (!writeSecureSettings && !a11yRunning) {
                AuthCard(
                    title =  getSafeString(R.string.accessibility_authorization),
                    desc = if (a11yBroken)  getSafeString(R.string.service_fault) else  getSafeString(R.string.authorize_to_run_accessibility_service),
                    onAuthClick = {
                        navController.toDestinationsNavigator().navigate(AuthA11YPageDestination)
                    })
            }

            TextSwitch(
                title = getSafeString(R.string.persistent_notification) ,
                subtitle = getSafeString(R.string.show_running_status_and_statistics) ,
                checked = manageRunning && store.enableStatusService,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, notificationState)
                        storeFlow.value = store.copy(
                            enableStatusService = true
                        )
                        ManageService.start()
                    } else {
                        storeFlow.value = store.copy(
                            enableStatusService = false
                        )
                        ManageService.stop()
                    }
                })

            SettingItem(
                title = getSafeString(R.string.trigger_log) ,
                subtitle = getSafeString(R.string.rule_misfire_can_be_located_and_turned_off) ,
                onClick = {
                    navController.toDestinationsNavigator().navigate(ActionLogPageDestination)
                }
            )

            if (store.enableActivityLog) {
                SettingItem(
                    title = getSafeString(R.string.interface_record),
                    subtitle = getSafeString(R.string.record_open_app_interface),
                    onClick = {
                        navController.toDestinationsNavigator().navigate(ActivityLogPageDestination)
                    }
                )
            }

            if (ruleSummary.slowGroupCount > 0) {
                SettingItem(
                    title = getSafeString(R.string.slow_query),
                    subtitle = String.format(getSafeString(R.string.there_are_records),ruleSummary.slowGroupCount),
                    onClick = {
                        navController.toDestinationsNavigator().navigate(SlowGroupPageDestination)
                    }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = itemHorizontalPadding))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = subsStatus,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (latestRecordDesc != null) {
                    Text(
                        text = String.format(getSafeString(R.string.latest_click),latestRecordDesc) ,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
