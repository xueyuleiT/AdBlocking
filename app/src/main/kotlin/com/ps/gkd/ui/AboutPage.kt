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

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import com.ps.gkd.META
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.WebParams
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.component.RotatingLoadingIcon
import com.ps.gkd.ui.component.SettingItem
import com.ps.gkd.ui.component.TextMenu
import com.ps.gkd.ui.component.TextSwitch
import com.ps.gkd.ui.component.waitResult
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.itemPadding
import com.ps.gkd.ui.style.titleItemPadding
import com.ps.gkd.util.ISSUES_URL
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ORIGINAL_REPOSITORY_URL
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.REPOSITORY_URL
import com.ps.gkd.util.SafeR
import com.ps.gkd.util.UpdateChannelOption
import com.ps.gkd.util.buildLogFile
import com.ps.gkd.util.checkUpdate
import com.ps.gkd.util.findOption
import com.ps.gkd.util.format
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.openUri
import com.ps.gkd.util.saveFileToDownloads
import com.ps.gkd.util.shareFile
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import com.ramcosta.composedestinations.generated.destinations.TakePositionPageDestination
import com.ramcosta.composedestinations.generated.destinations.WebPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun AboutPage() {
    val navController = LocalNavController.current
    val context = LocalContext.current as MainActivity
    val store by storeFlow.collectAsState()

    var showInfoDlg by remember { mutableStateOf(false) }
    if (showInfoDlg) {
        AlertDialog(
            onDismissRequest = { showInfoDlg = false },
            title = { Text(text = getSafeString(R.string.version_information)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(text = getSafeString(R.string.build_channel))
                        Text(text = META.channel)
                    }
                    Column {
                        Text(text = getSafeString(R.string.version_code))
                        Text(text = META.versionCode.toString())
                    }
                    Column {
                        Text(text = getSafeString(R.string.version_name))
                        Text(text = META.versionName)
                    }
                    Column {
                        Text(text = getSafeString(R.string.code_record))
                        Text(
                            modifier = Modifier.clickable { openUri(META.commitUrl) },
                            text = META.commitId.substring(0, 16),
                            color = MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                    Column {
                        Text(text = getSafeString(R.string.commit_time))
                        Text(text = META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showInfoDlg = false
                }) {
                    Text(text = getSafeString(R.string.close))
                }
            },
        )
    }
    var showShareLogDlg by remember { mutableStateOf(false) }
    if (showShareLogDlg) {
        Dialog(onDismissRequest = { showShareLogDlg = false }) {
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
                            showShareLogDlg = false
                            context.mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                context.shareFile(logZipFile, getSafeString(R.string.share_log_file))
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = getSafeString(R.string.save_to_downloads), modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareLogDlg = false
                            context.mainVm.viewModelScope.launchTry(Dispatchers.IO) {
                                val logZipFile = buildLogFile()
                                context.saveFileToDownloads(logZipFile)
                            }
                        })
                        .then(modifier)
                )
                Text(
                    text = getSafeString(R.string.generate_link_requires_scientific_internet),
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            showShareLogDlg = false
                            context.mainVm.uploadOptions.startTask(
                                getFile = { buildLogFile() }
                            )
                        })
                        .then(modifier)
                )
            }
        }
    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                title = { Text(text = getSafeString(R.string.about)) },
//                actions = {
//                    IconButton(onClick = throttle(fn = {
//                        showInfoDlg = true
//                    })) {
//                        Icon(
//                            imageVector = Icons.Outlined.Info,
//                            contentDescription = null,
//                        )
//                    }
//                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedLogoIcon(
                    modifier = Modifier
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = throttle { toast(getSafeString(R.string.what_are_you_doing_ouch)) }
                        )
                        .fillMaxWidth(0.33f)
                        .aspectRatio(1f)
                )
                Text(text = META.appName, style = MaterialTheme.typography.titleMedium)
                Text(text = META.versionName, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(32.dp))
            }

            Column(
                modifier = Modifier
                    .clickable {
                        openUri(REPOSITORY_URL)
                    }
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = getSafeString(R.string.open_source_address),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = REPOSITORY_URL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )

            }

            Column(
                modifier = Modifier
                    .clickable {
                        openUri(ORIGINAL_REPOSITORY_URL)
                    }
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = getSafeString(R.string.original_open_source_address),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = ORIGINAL_REPOSITORY_URL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )

            }

            Column(
                modifier = Modifier
                    .clickable {
                        val webParams = WebParams("用户协议","https://pinshengtb.cn/legel/user_agreement/ad_blocking_user_agreement.html")
                        navController.toDestinationsNavigator().navigate(WebPageDestination(webParams))
                    }
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = getSafeString(R.string.user_agreement),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = TextDecoration.Underline,
                    color = Color(context.getColor(R.color.blue_3a75f3))
                )

            }

            Column(
                modifier = Modifier
                    .clickable {
                        val webParams = WebParams("隐私政策","https://pinshengtb.cn/legel/user_private_policy/ad_blocking_privacy_policy.html")
                        navController.toDestinationsNavigator().navigate(WebPageDestination(webParams))
                    }
                    .fillMaxWidth()
                    .itemPadding()
            ) {
                Text(
                    text = getSafeString(R.string.user_privacy_policy),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = TextDecoration.Underline,
                    color = Color(context.getColor(R.color.blue_3a75f3))
                )

            }

//            Column(
//                modifier = Modifier
//                    .clickable {
//                        openUri(ISSUES_URL)
//                    }
//                    .fillMaxWidth()
//                    .itemPadding()
//            ) {
//                Text(
//                    text = getSafeString(R.string.feedback),
//                    style = MaterialTheme.typography.bodyLarge,
//                )
//                Text(
//                    text = ISSUES_URL,
//                    style = MaterialTheme.typography.bodyMedium,
//                    color = MaterialTheme.colorScheme.secondary,
//                )
//            }
            if (META.updateEnabled) {
                val checkUpdating by context.mainVm.updateStatus.checkUpdatingFlow.collectAsState()
                Text(
                    text = getSafeString(R.string.update),
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextSwitch(
                    title = getSafeString(R.string.auto_update),
                    subtitle = getSafeString(R.string.auto_check_for_updates),
                    checked = store.autoCheckAppUpdate,
                    onCheckedChange = {
                        storeFlow.value = store.copy(
                            autoCheckAppUpdate = it
                        )
                    }
                )
                TextMenu(
                    title = getSafeString(R.string.update_channel),
                    option = UpdateChannelOption.allSubObject.findOption(store.updateChannel)
                ) {
                    if (context.mainVm.updateStatus.checkUpdatingFlow.value) return@TextMenu
                    if (it.value == UpdateChannelOption.Beta.value) {
                        context.mainVm.viewModelScope.launchTry {
                            context.mainVm.dialogFlow.waitResult(
                                title = getSafeString(R.string.version_channel),
                                text = getSafeString(R.string.beta_channel_updates_faster_but_unstable_may_contain_more_bugs_please_use_with_caution),
                            )
                            storeFlow.update { s -> s.copy(updateChannel = it.value) }
                        }
                    } else {
                        storeFlow.update { s -> s.copy(updateChannel = it.value) }
                    }
                }

                Row(
                    modifier = Modifier
                        .clickable(
                            onClick = throttle(fn = context.mainVm.viewModelScope.launchAsFn {
                                if (context.mainVm.updateStatus.checkUpdatingFlow.value) return@launchAsFn
                                val newVersion = context.mainVm.updateStatus.checkUpdate()
                                if (newVersion == null) {
                                    toast(getSafeString(R.string.no_updates))
                                }
                            })
                        )
                        .fillMaxWidth()
                        .itemPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = getSafeString(R.string.check_for_updates),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    RotatingLoadingIcon(loading = checkUpdating)
                }
            }

//            Text(
//                text = getSafeString(R.string.log),
//                modifier = Modifier.titleItemPadding(),
//                style = MaterialTheme.typography.titleSmall,
//                color = MaterialTheme.colorScheme.primary,
//            )
//
//            TextSwitch(
//                title = getSafeString(R.string.save_log),
//                subtitle = getSafeString(R.string.save_7_days_of_logs_for_feedback),
//                checked = store.log2FileSwitch,
//                onCheckedChange = {
//                    storeFlow.value = store.copy(
//                        log2FileSwitch = it
//                    )
//                })
//
//            if (store.log2FileSwitch) {
//                SettingItem(
//                    title = getSafeString(R.string.export_log),
//                    imageVector = Icons.Default.Share,
//                    onClick = {
//                        showShareLogDlg = true
//                    }
//                )
//            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun AnimatedLogoIcon(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current as MainActivity
    val enableDarkTheme by context.mainVm.enableDarkThemeFlow.collectAsState()
    val darkTheme = enableDarkTheme ?: isSystemInDarkTheme()
    var atEnd by remember { mutableStateOf(false) }
    val animation = AnimatedImageVector.animatedVectorResource(id = SafeR.ic_logo_animation)
    val painter = rememberAnimatedVectorPainter(
        animation,
        atEnd
    )
    LaunchedEffect(Unit) {
        while (true) {
            atEnd = !atEnd
            delay(animation.totalDuration.toLong())
        }
    }
    val colorRid = if (darkTheme) SafeR.better_white else SafeR.better_black
    Icon(
        modifier = modifier,
        painter = painter,
        contentDescription = null,
        tint = colorResource(colorRid),
    )
}