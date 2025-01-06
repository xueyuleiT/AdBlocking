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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.generated.destinations.AboutPageDestination
import com.ramcosta.composedestinations.generated.destinations.AdvancedPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.flow.update
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.SettingItem
import com.ps.gkd.ui.component.TextMenu
import com.ps.gkd.ui.component.TextSwitch
import com.ps.gkd.ui.component.updateDialogOptions
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.titleItemPadding
import com.ps.gkd.ui.theme.supportDynamicColor
import com.ps.gkd.util.DarkThemeOption
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.findOption
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.throttle

val settingsNav = BottomNavItem(
    label = getSafeString(R.string.settings), icon = Icons.Outlined.Settings
)

@Composable
fun useSettingsPage(): ScaffoldExt {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val store by storeFlow.collectAsState()
    val vm = viewModel<HomeVm>()

    var showToastInputDlg by remember {
        mutableStateOf(false)
    }
    var showNotifTextInputDlg by remember {
        mutableStateOf(false)
    }


    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.clickToast)
        }
        val maxCharLen = 32
        AlertDialog(title = { Text(text = getSafeString(R.string.trigger_prompt)) }, text = {
            OutlinedTextField(
                value = value,
                placeholder = {
                    Text(text = getSafeString(R.string.please_enter_prompt_content))
                },
                onValueChange = {
                    value = it.take(maxCharLen)
                },
                singleLine = true,
                supportingText = {
                    Text(
                        text = "${value.length} / $maxCharLen",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        }, onDismissRequest = {
            if (value.isEmpty()) {
                showToastInputDlg = false
            }
        }, confirmButton = {
            TextButton(enabled = value.isNotEmpty(), onClick = {
                storeFlow.update { it.copy(clickToast = value) }
                showToastInputDlg = false
            }) {
                Text(
                    text = getSafeString(R.string.confirm),
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showToastInputDlg = false }) {
                Text(
                    text = getSafeString(R.string.cancel),
                )
            }
        })
    }
    if (showNotifTextInputDlg) {
        var value by remember {
            mutableStateOf(store.customNotifText)
        }
        AlertDialog(title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = getSafeString(R.string.notification_copy))
                IconButton(onClick = throttle {
                    context.mainVm.dialogFlow.updateDialogOptions(
                        title = getSafeString(R.string.copy_rule),
                        text = getSafeString(R.string.notification_copy_supports_variable_replacement_rules_as_follows_global_rule_count_application_count_application_rule_group_count_trigger_count) ,
                    )
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = null,
                    )
                }
            }
        }, text = {
            val maxCharLen = 64
            OutlinedTextField(
                value = value,
                placeholder = {
                    Text(text = getSafeString(R.string.please_enter_copy_content_supports_variable_replacement))
                },
                onValueChange = {
                    value = if (it.length > maxCharLen) it.take(maxCharLen) else it
                },
                maxLines = 4,
                supportingText = {
                    Text(
                        text = "${value.length} / $maxCharLen",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        }, onDismissRequest = {
            if (value.isEmpty()) {
                showNotifTextInputDlg = false
            }
        }, confirmButton = {
            TextButton(enabled = value.isNotEmpty(), onClick = {
                storeFlow.update { it.copy(customNotifText = value) }
                showNotifTextInputDlg = false
            }) {
                Text(
                    text = getSafeString(R.string.confirm),
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showNotifTextInputDlg = false }) {
                Text(
                    text = getSafeString(R.string.cancel),
                )
            }
        })
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()
    return ScaffoldExt(
        navItem = settingsNav,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, title = {
                Text(
                    text = settingsNav.label,
                )
            })
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
        ) {

            Text(
                text = getSafeString(R.string.regular),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextSwitch(
                title = getSafeString(R.string.trigger_prompt),
                subtitle = store.clickToast,
                checked = store.toastWhenClick,
                modifier = Modifier.clickable {
                    showToastInputDlg = true
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        toastWhenClick = it
                    )
                })

            AnimatedVisibility(visible = store.toastWhenClick) {
                TextSwitch(
                    title = getSafeString(R.string.system_prompt),
                    subtitle = getSafeString(R.string.system_style_trigger_prompt),
                    suffix = getSafeString(R.string.view_restrictions),
                    onSuffixClick = {
                        context.mainVm.dialogFlow.updateDialogOptions(
                            title = getSafeString(R.string.restriction_description),
                            text = getSafeString(R.string.system_toast_has_a_frequency_limit_triggering_too_frequently_will_be_forced_to_hide_by_the_system) ,
                        )
                    },
                    checked = store.useSystemToast,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            useSystemToast = it
                        )
                    })
            }

            val subsStatus by vm.subsStatusFlow.collectAsState()
            TextSwitch(
                title = getSafeString(R.string.notification_copy),
                subtitle = if (store.useCustomNotifText) store.customNotifText else subsStatus,
                checked = store.useCustomNotifText,
                modifier = Modifier.clickable {
                    showNotifTextInputDlg = true
                },
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        useCustomNotifText = it
                    )
                })

            TextSwitch(
                title = getSafeString(R.string.hide_in_background),
                subtitle = getSafeString(R.string.hide_app_in_recent_tasks),
                checked = store.excludeFromRecents,
                onCheckedChange = {
                    storeFlow.value = store.copy(
                        excludeFromRecents = it
                    )
                })

            Text(
                text = getSafeString(R.string.theme),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextMenu(
                title = getSafeString(R.string.dark_mode),
                option = DarkThemeOption.allSubObject.findOption(store.enableDarkTheme)
            ) {
                storeFlow.update { s -> s.copy(enableDarkTheme = it.value) }
            }

            if (supportDynamicColor) {
                TextSwitch(title = getSafeString(R.string.dynamic_color),
                    subtitle = getSafeString(R.string.color_follows_system_theme),
                    checked = store.enableDynamicColor,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            enableDynamicColor = it
                        )
                    })
            }

            Text(
                text = getSafeString(R.string.other),
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(title = getSafeString(R.string.advanced_settings), onClick = {
                navController.toDestinationsNavigator().navigate(AdvancedPageDestination)
            })

            SettingItem(title = getSafeString(R.string.about), onClick = {
                navController.toDestinationsNavigator().navigate(AboutPageDestination)
            })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}
