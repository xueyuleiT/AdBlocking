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

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.LogUtils
import com.dylanc.activityresult.launcher.launchForResult
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ActivityLogPageDestination
import com.ramcosta.composedestinations.generated.destinations.SnapshotPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.appScope
import com.ps.gkd.debug.FloatingService
import com.ps.gkd.debug.HttpService
import com.ps.gkd.debug.ScreenshotService
import com.ps.gkd.getSafeString
import com.ps.gkd.permission.canDrawOverlaysState
import com.ps.gkd.permission.notificationState
import com.ps.gkd.permission.requiredPermission
import com.ps.gkd.permission.shizukuOkState
import com.ps.gkd.shizuku.shizukuCheckActivity
import com.ps.gkd.shizuku.shizukuCheckUserService
import com.ps.gkd.ui.component.AuthCard
import com.ps.gkd.ui.component.SettingItem
import com.ps.gkd.ui.component.TextSwitch
import com.ps.gkd.ui.component.updateDialogOptions
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemPadding
import com.ps.gkd.ui.style.titleItemPadding
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.openUri
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import rikka.shizuku.Shizuku

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AdvancedPage() {
    val context = LocalContext.current as MainActivity
    val vm = viewModel<AdvancedVm>()
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()
    val snapshotCount by vm.snapshotCountFlow.collectAsState()

    var showEditPortDlg by remember {
        mutableStateOf(false)
    }
    if (showEditPortDlg) {
        var value by remember {
            mutableStateOf(store.httpServerPort.toString())
        }
        AlertDialog(title = { Text(text = getSafeString(R.string.service_port)) }, text = {
            OutlinedTextField(
                value = value,
                placeholder = {
                    Text(text = getSafeString(R.string.please_enter_an_integer_between_5000_and_65535))
                },
                onValueChange = {
                    value = it.filter { c -> c.isDigit() }.take(5)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        text = "${value.length} / 5",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        }, onDismissRequest = {
            if (value.isEmpty()) {
                showEditPortDlg = false
            }
        }, confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = {
                    val newPort = value.toIntOrNull()
                    if (newPort == null || !(5000 <= newPort && newPort <= 65535)) {
                        toast(getSafeString(R.string.please_enter_an_integer_between_5000_and_65535))
                        return@TextButton
                    }
                    storeFlow.value = store.copy(
                        httpServerPort = newPort
                    )
                    showEditPortDlg = false
                    if (HttpService.httpServerFlow.value != null) {
                        toast(getSafeString(R.string.updated_restart_service))
                    } else {
                        toast(getSafeString(R.string.updated))
                    }
                }
            ) {
                Text(
                    text = getSafeString(R.string.confirm), modifier = Modifier
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showEditPortDlg = false }) {
                Text(
                    text = getSafeString(R.string.cancel)
                )
            }
        })
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
            }, title = { Text(text = getSafeString(R.string.advanced_settings)) }, actions = {})
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Text(
                text = "Shizuku",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            val shizukuOk by shizukuOkState.stateFlow.collectAsState()
            if (!shizukuOk) {
                AuthCard(title = getSafeString(R.string.shizuku_authorization),
                    desc = getSafeString(R.string.advanced_mode_accurately_distinguish_interface_id_force_simulate_click),
                    onAuthClick = {
                        try {
                            Shizuku.requestPermission(Activity.RESULT_OK)
                        } catch (e: Exception) {
                            context.mainVm.shizukuErrorFlow.value = true
                        }
                    })
                ShizukuFragment(false)
            } else {
                ShizukuFragment()
            }

            val server by HttpService.httpServerFlow.collectAsState()
            val httpServerRunning = server != null
            val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsState()

            Text(
                text = getSafeString(R.string.http_service),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.itemPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = getSafeString(R.string.http_service),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodyMedium
                    ) {
                        if (!httpServerRunning) {
                            Text(
                                text = getSafeString(R.string.connect_to_debugging_tool_in_browser),
                            )
                        } else {
                            Text(
                                text = getSafeString(R.string.click_any_link_below_to_connect_automatically),
                            )
                            Row {
                                Text(
                                    text = "http://127.0.0.1:${store.httpServerPort}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                    modifier = Modifier.clickable(onClick = throttle {
                                        openUri("http://127.0.0.1:${store.httpServerPort}")
                                    }),
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(text = getSafeString(R.string.only_accessible_by_this_device))
                            }
                            localNetworkIps.forEach { host ->
                                Text(
                                    text = "http://${host}:${store.httpServerPort}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                                    modifier = Modifier.clickable(onClick = throttle {
                                        openUri("http://${host}:${store.httpServerPort}")
                                    })
                                )
                            }
                        }
                    }
                }
                Switch(
                    checked = httpServerRunning,
                    onCheckedChange = throttle(fn = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
                            requiredPermission(context, notificationState)
                            HttpService.start()
                        } else {
                            HttpService.stop()
                        }
                    })
                )
            }

            SettingItem(
                title = getSafeString(R.string.service_port),
                subtitle = store.httpServerPort.toString(),
                imageVector = Icons.Default.Edit,
                onClick = {
                    showEditPortDlg = true
                }
            )

            TextSwitch(
                title = getSafeString(R.string.clear_subscriptions),
                subtitle = getSafeString(R.string.delete_memory_subscriptions_when_service_is_turned_off),
                checked = store.autoClearMemorySubs
            ) {
                storeFlow.value = store.copy(
                    autoClearMemorySubs = it
                )
            }

            Text(
                text = getSafeString(R.string.snapshot),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(
                title = getSafeString(R.string.snapshot_log),
                subtitle = if (snapshotCount > 0) String.format(getSafeString(R.string.there_are_records),snapshotCount) else getSafeString(R.string.no_records),
                onClick = {
                    navController.toDestinationsNavigator().navigate(SnapshotPageDestination)
                }
            )


            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val screenshotRunning by ScreenshotService.isRunning.collectAsState()
                TextSwitch(
                    title = getSafeString(R.string.screenshot_service),
                    subtitle = getSafeString(R.string.screenshot_service_is_required_to_generate_snapshots),
                    checked = screenshotRunning,
                    onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                        if (it) {
                            requiredPermission(context, notificationState)
                            val mediaProjectionManager =
                                context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            val activityResult =
                                context.launcher.launchForResult(mediaProjectionManager.createScreenCaptureIntent())
                            if (activityResult.resultCode == Activity.RESULT_OK && activityResult.data != null) {
                                ScreenshotService.start(intent = activityResult.data!!)
                            }
                        } else {
                            ScreenshotService.stop()
                        }
                    }
                )
            }

            val floatingRunning by FloatingService.isRunning.collectAsState()
            TextSwitch(
                title = getSafeString(R.string.floating_window_service),
                subtitle = getSafeString(R.string.display_floating_button_to_save_snapshot),
                checked = floatingRunning,
                onCheckedChange = vm.viewModelScope.launchAsFn<Boolean> {
                    if (it) {
                        requiredPermission(context, notificationState)
                        requiredPermission(context, canDrawOverlaysState)
                        FloatingService.start()
                    } else {
                        FloatingService.stop()
                    }
                }
            )

            TextSwitch(
                title = getSafeString(R.string.volume_snapshot),
                subtitle = getSafeString(R.string.volume_change_snapshot),
                checked = store.captureVolumeChange
            ) {
                storeFlow.value = store.copy(
                    captureVolumeChange = it
                )
            }

            TextSwitch(
                title = getSafeString(R.string.screenshot_snapshot),
                subtitle = getSafeString(R.string.trigger_snapshot),
                suffix = getSafeString(R.string.view_restrictions),
                onSuffixClick = {
                    context.mainVm.dialogFlow.updateDialogOptions(
                        title = getSafeString(R.string.restriction_description),
                        text = getSafeString(R.string.only_support_xiaomi),
                    )
                },
                checked = store.captureScreenshot
            ) {
                storeFlow.value = store.copy(
                    captureScreenshot = it
                )
            }

            TextSwitch(
                title = getSafeString(R.string.hide_status_bar),
                subtitle = getSafeString(R.string.hide_top_status_bar),
                checked = store.hideSnapshotStatusBar
            ) {
                storeFlow.value = store.copy(
                    hideSnapshotStatusBar = it
                )
            }

            TextSwitch(
                title = getSafeString(R.string.save_prompt),
                subtitle = getSafeString(R.string.saving_snapshot),
                checked = store.showSaveSnapshotToast
            ) {
                storeFlow.value = store.copy(
                    showSaveSnapshotToast = it
                )
            }

            SettingItem(
                title = "Github Cookie",
                subtitle = getSafeString(R.string.generate_snapshot_log_link),
                suffix = getSafeString(R.string.tutorial),
                onSuffixClick = {
                    openUri("https://gkd.li?r=1")
                },
                imageVector = Icons.Default.Edit,
                onClick = {
                    context.mainVm.showEditCookieDlgFlow.value = true
                }
            )

            Text(
                text = getSafeString(R.string.interface_record),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(
                title = getSafeString(R.string.record_interface),
                subtitle = getSafeString(R.string.record_opened_applications_and_interfaces),
                checked = store.enableActivityLog
            ) {
                storeFlow.value = store.copy(
                    enableActivityLog = it
                )
            }
            SettingItem(
                title = getSafeString(R.string.interface_log),
                onClick = {
                    navController.toDestinationsNavigator().navigate(ActivityLogPageDestination)
                }
            )

            Text(
                text = getSafeString(R.string.other),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(
                title = getSafeString(R.string.foreground_floating_window),
                subtitle = getSafeString(R.string.add_transparent_floating_window),
                suffix = getSafeString(R.string.view_function),
                onSuffixClick = {
                    context.mainVm.dialogFlow.updateDialogOptions(
                        title = getSafeString(R.string.floating_window_function),
                        text = getSafeString(R.string.floating_window_function1),
                    )
                },
                checked = store.enableAbFloatWindow,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        enableAbFloatWindow = it
                    )
                })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

}

@Composable
private fun ShizukuFragment(enabled: Boolean = true) {
    val store by storeFlow.collectAsState()
    TextSwitch(
        title = getSafeString(R.string.shizuku_interface_recognition),
        subtitle = getSafeString(R.string.more_accurate_interface_id),
        checked = store.enableShizukuActivity,
        enabled = enabled,
        onCheckedChange = appScope.launchAsFn<Boolean>(Dispatchers.IO) {
            if (it) {
                toast(getSafeString(R.string.detecting))
                if (!shizukuCheckActivity()) {
                    toast(getSafeString(R.string.detection_failed))
                    return@launchAsFn
                }
                toast(getSafeString(R.string.enabled))
            }
            storeFlow.value = store.copy(
                enableShizukuActivity = it
            )
        })

    TextSwitch(
        title = getSafeString(R.string.shizuku_simulate_click),
        subtitle = getSafeString(R.string.change_click_center),
        checked = store.enableShizukuClick,
        enabled = enabled,
        onCheckedChange = appScope.launchAsFn<Boolean>(Dispatchers.IO) {
            if (it) {
                toast(getSafeString(R.string.detecting))
                if (!shizukuCheckUserService()) {
                    toast(getSafeString(R.string.detection_failed))
                    return@launchAsFn
                }
                toast(getSafeString(R.string.enabled))
            }
            storeFlow.value = store.copy(
                enableShizukuClick = it
            )

        })

}
