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

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.LogUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import com.ps.gkd.META
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import com.ps.gkd.permission.shizukuOkState
import com.ps.gkd.permission.writeSecureSettingsState
import com.ps.gkd.service.A11yService
import com.ps.gkd.service.fixRestartService
import com.ps.gkd.shizuku.execCommandForResult
import com.ps.gkd.ui.component.updateDialogOptions
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemHorizontalPadding
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.openA11ySettings
import com.ps.gkd.util.openUri
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import rikka.shizuku.Shizuku
import java.io.DataOutputStream

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AuthA11yPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current

    val vm = viewModel<AuthA11yVm>()
    val showCopyDlg by vm.showCopyDlgFlow.collectAsState()
    val writeSecureSettings by writeSecureSettingsState.stateFlow.collectAsState()
    val a11yRunning by A11yService.isRunning.collectAsState()

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
            Text(text = getSafeString(R.string.authorization_status))
        }, actions = {})
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            Text(
                text = getSafeString(R.string.select_an_authorization_mode_to_operate),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(itemHorizontalPadding)
            )
            Card(
                modifier = Modifier
                    .padding(itemHorizontalPadding, 0.dp)
                    .fillMaxWidth(),
                onClick = { }
            ) {
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 8.dp),
                    text = getSafeString(R.string.normal_authorization_simple),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    text = getSafeString(R.string.grant_accessibility_permission_2_accessibility_service_needs_to_be_reauthorized_after_closing)
                )
                if (writeSecureSettings || a11yRunning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = getSafeString(R.string.already_holding_accessibility_permission_can_continue_to_use),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    Row(
                        modifier = Modifier
                            .padding(4.dp, 0.dp)
                            .fillMaxWidth(),
                    ) {
                        TextButton(onClick = throttle { openA11ySettings() }) {
                            Text(
                                text = getSafeString(R.string.manual_authorization),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    Text(
                        modifier = Modifier
                            .padding(cardHorizontalPadding, 0.dp)
                            .clickable {
                                openUri("https://gkd.li?r=2")
                            },
                        text = getSafeString(R.string.cannot_enable_accessibility),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .padding(itemHorizontalPadding, 0.dp)
                    .fillMaxWidth(),
                onClick = { }
            ) {
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 8.dp),
                    text = getSafeString(R.string.advanced_authorization_recommended),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    text = getSafeString(R.string.grant_write_secure_settings_permission_2_authorization_is_permanently_valid_including_accessibility_permission_3_application_can_automatically_open_accessibility_service_after_restarting_4_quick_restart_in_notification_bar_quick_switch_no_sense_keepalive)
                )
                if (!writeSecureSettings) {
                    AuthButtonGroup()
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = getSafeString(R.string.already_holding_write_secure_settings_permission_use_this_permission_first),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(4.dp, 0.dp)
                        .fillMaxWidth(),
                ) {
                    TextButton(onClick = throttle {
                        if (!writeSecureSettings) {
                            toast(getSafeString(R.string.please_authorize_first))
                        }
                        context.mainVm.dialogFlow.updateDialogOptions(
                            title = getSafeString(R.string.no_sense_keepalive),
                            text = getSafeString(R.string.add_notification_bar_quick_switch_1_pull_down_the_notification_bar_to_the_quick_switch_icon_interface_2_find_the_quick_switch_named_3_add_this_switch_to_the_notification_panel)
                        )
                    }) {
                        Text(
                            text = getSafeString(R.string.no_sense_keepalive),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (writeSecureSettings) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier
                        .padding(itemHorizontalPadding, 0.dp)
                        .fillMaxWidth(),
                    onClick = { }
                ) {
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 8.dp),
                        text = getSafeString(R.string.remove_possible_accessibility_restrictions),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        text = getSafeString(R.string.remove_possible_accessibility_restrictions_1_some_systems_have_stricter_accessibility_restrictions_2_after_gkd_is_updated_the_system_will_restrict_the_switching_of_accessibility_3_re_authorization_can_solve_this_problem)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        modifier = Modifier.padding(cardHorizontalPadding, 0.dp),
                        text = getSafeString(R.string.if_accessibility_can_be_switched_normally_please_ignore_this_item),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    AuthButtonGroup()
                    Text(
                        modifier = Modifier
                            .padding(cardHorizontalPadding, 0.dp)
                            .clickable {
                                openUri("https://gkd.li?r=2")
                            },
                        text = getSafeString(R.string.other_ways_to_remove_restrictions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

    if (showCopyDlg) {
        AlertDialog(
            onDismissRequest = { vm.showCopyDlgFlow.value = false },
            title = { Text(text = getSafeString(R.string.manual_authorization)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(text = getSafeString(R.string.manual_authorization_1_have_a_computer_with_adb_installed_2_after_enabling_debugging_mode_on_the_phone_connect_to_the_computer_for_debugging_authorization_3_run_the_following_command_in_the_computer_cmdpwsh))
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = commandText,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        modifier = Modifier
                            .clickable {
                                openUri("https://gkd.li?r=3")
                            },
                        text = getSafeString(R.string.authorization_failed_after_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.showCopyDlgFlow.value = false
                    ClipboardUtils.copyText(commandText)
                    toast(getSafeString(R.string.copy_success))
                }) {
                    Text(text = getSafeString(R.string.copy_and_close))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.showCopyDlgFlow.value = false }) {
                    Text(text = getSafeString(R.string.close))
                }
            }
        )
    }
}

private val innerCommandText by lazy {
    arrayOf(
        "appops set ${META.appId} ACCESS_RESTRICTED_SETTINGS allow",
        "pm grant ${META.appId} android.permission.WRITE_SECURE_SETTINGS"
    ).joinToString("; ")
}
private val commandText by lazy { "adb shell \"${innerCommandText}\"" }

private fun successAuthExec() {
    if (writeSecureSettingsState.updateAndGet()) {
        toast(getSafeString(R.string.authorization_successful))
        storeFlow.update { it.copy(enableService = true) }
        fixRestartService()
    }
}

private suspend fun MainActivity.grantPermissionByShizuku() {
    if (shizukuOkState.stateFlow.value) {
        try {
            execCommandForResult(innerCommandText)
            successAuthExec()
        } catch (e: Exception) {
            toast(String.format(getSafeString(R.string.authorization_failed),e.message))
            LogUtils.d(e)
        }
    } else {
        try {
            Shizuku.requestPermission(Activity.RESULT_OK)
        } catch (e: Exception) {
            mainVm.shizukuErrorFlow.value = true
        }
    }
}

private val cardHorizontalPadding = 12.dp

private fun grantPermissionByRoot() {
    var p: Process? = null
    try {
        p = Runtime.getRuntime().exec("su")
        val o = DataOutputStream(p.outputStream)
        o.writeBytes("${innerCommandText}\nexit\n")
        o.flush()
        o.close()
        p.waitFor()
        if (p.exitValue() == 0) {
            successAuthExec()
        }
    } catch (e: Exception) {
        toast(String.format(getSafeString(R.string.authorization_failed),e.message))
        LogUtils.d(e)
    } finally {
        p?.destroy()
    }
}


@Composable
private fun AuthButtonGroup() {
    val context = LocalContext.current as MainActivity
    val vm = viewModel<AuthA11yVm>()
    Row(
        modifier = Modifier
            .padding(4.dp, 0.dp)
            .fillMaxWidth(),
    ) {
        TextButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
            context.grantPermissionByShizuku()
        })) {
            Text(
                text = getSafeString(R.string.shizuku_authorization),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        TextButton(onClick = {
            vm.showCopyDlgFlow.value = true
        }) {
            Text(
                text = getSafeString(R.string.manual_authorization),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        TextButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
            grantPermissionByRoot()
        })) {
            Text(
                text = getSafeString(R.string.root_authorization),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}