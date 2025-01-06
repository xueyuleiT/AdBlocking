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

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.ClipboardUtils
import com.blankj.utilcode.util.ImageUtils
import com.blankj.utilcode.util.UriUtils
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ImagePreviewPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.GithubPoliciesAsset
import com.ps.gkd.data.Snapshot
import com.ps.gkd.db.DbSet
import com.ps.gkd.debug.SnapshotExt
import com.ps.gkd.getSafeString
import com.ps.gkd.permission.canWriteExternalStorage
import com.ps.gkd.permission.requiredPermission
import com.ps.gkd.ui.component.EmptyText
import com.ps.gkd.ui.component.StartEllipsisText
import com.ps.gkd.ui.component.waitResult
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.ui.style.scaffoldPadding
import com.ps.gkd.util.IMPORT_SHORT_URL
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.saveFileToDownloads
import com.ps.gkd.util.shareFile
import com.ps.gkd.util.snapshotZipDir
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun SnapshotPage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val colorScheme = MaterialTheme.colorScheme

    val vm = viewModel<SnapshotVm>()
    val snapshots by vm.snapshotsState.collectAsState()

    var selectedSnapshot by remember {
        mutableStateOf<Snapshot?>(null)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        TopAppBar(scrollBehavior = scrollBehavior,
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
            title = { Text(text = getSafeString(R.string.snapshot_log)) },
            actions = {
                if (snapshots.isNotEmpty()) {
                    IconButton(onClick = throttle(fn = vm.viewModelScope.launchAsFn(Dispatchers.IO) {
                        context.mainVm.dialogFlow.waitResult(
                            title = getSafeString(R.string.delete_log),
                            text = String.format(getSafeString(R.string.confirm_deletion_of_all_snapshot_records),snapshots.size) ,
                            error = true,
                        )
                        snapshots.forEach { s ->
                            SnapshotExt.removeAssets(s.id)
                        }
                        DbSet.snapshotDao.deleteAll()
                    })) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                        )
                    }
                }
            })
    }, content = { contentPadding ->
        LazyColumn(
            modifier = Modifier.scaffoldPadding(contentPadding),
        ) {
            items(snapshots, { it.id }) { snapshot ->
                if (snapshot.id != snapshots.firstOrNull()?.id) {
                    HorizontalDivider()
                }
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        selectedSnapshot = snapshot
                    }
                    .padding(10.dp)) {
                    Row {
                        Text(
                            text = snapshot.date,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = snapshot.appName ?: snapshot.appId ?: snapshot.id.toString(),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    val showActivityId = if (snapshot.activityId != null) {
                        if (snapshot.appId != null && snapshot.activityId.startsWith(
                                snapshot.appId
                            )
                        ) {
                            snapshot.activityId.substring(snapshot.appId.length)
                        } else {
                            snapshot.activityId
                        }
                    } else {
                        null
                    }
                    if (showActivityId != null) {
                        StartEllipsisText(text = showActivityId)
                    } else {
                        Text(text = "null", color = LocalContentColor.current.copy(alpha = 0.5f))
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (snapshots.isEmpty()) {
                    EmptyText(text = getSafeString(R.string.no_records))
                }
            }
        }

    })

    selectedSnapshot?.let { snapshotVal ->
        Dialog(onDismissRequest = { selectedSnapshot = null }) {
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
                    text = getSafeString(R.string.view), modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            navController
                                .toDestinationsNavigator()
                                .navigate(
                                    ImagePreviewPageDestination(
                                        title = snapshotVal.appName,
                                        uri = snapshotVal.screenshotFile.absolutePath,
                                    )
                                )
                            selectedSnapshot = null
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = getSafeString(R.string.share_to_other_apps),
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            val zipFile = SnapshotExt.getSnapshotZipFile(
                                snapshotVal.id,
                                snapshotVal.appId,
                                snapshotVal.activityId
                            )
                            context.shareFile(zipFile, getSafeString(R.string.share_snapshot_file))
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = getSafeString(R.string.save_to_downloads),
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            selectedSnapshot = null
                            val zipFile = SnapshotExt.getSnapshotZipFile(
                                snapshotVal.id,
                                snapshotVal.appId,
                                snapshotVal.activityId
                            )
                            context.saveFileToDownloads(zipFile)
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                if (snapshotVal.githubAssetId != null) {
                    Text(
                        text = getSafeString(R.string.copy_link), modifier = Modifier
                            .clickable(onClick = throttle {
                                selectedSnapshot = null
                                ClipboardUtils.copyText(IMPORT_SHORT_URL + snapshotVal.githubAssetId)
                                toast(getSafeString(R.string.copy_success))
                            })
                            .then(modifier)
                    )
                } else {
                    Text(
                        text = getSafeString(R.string.generate_link_requires_scientific_internet), modifier = Modifier
                            .clickable(onClick = throttle {
                                selectedSnapshot = null
                                context.mainVm.uploadOptions.startTask(
                                    getFile = { SnapshotExt.getSnapshotZipFile(snapshotVal.id) },
                                    showHref = { IMPORT_SHORT_URL + it.id },
                                    onSuccessResult = vm.viewModelScope.launchAsFn<GithubPoliciesAsset>(
                                        Dispatchers.IO
                                    ) {
                                        DbSet.snapshotDao.update(snapshotVal.copy(githubAssetId = it.id))
                                    }
                                )
                            })
                            .then(modifier)
                    )
                }
                HorizontalDivider()

                Text(
                    text = getSafeString(R.string.save_screenshot_to_gallery),
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            requiredPermission(context, canWriteExternalStorage)
                            ImageUtils.save2Album(
                                ImageUtils.getBitmap(snapshotVal.screenshotFile),
                                Bitmap.CompressFormat.PNG,
                                true
                            )
                            toast(getSafeString(R.string.save_success))
                            selectedSnapshot = null
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = getSafeString(R.string.replace_screenshot_remove_privacy),
                    modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            val uri = context.pickContentLauncher.launchForImageResult()
                            withContext(Dispatchers.IO) {
                                val oldBitmap = ImageUtils.getBitmap(snapshotVal.screenshotFile)
                                val newBytes = UriUtils.uri2Bytes(uri)
                                val newBitmap = ImageUtils.getBitmap(newBytes, 0)
                                if (oldBitmap.width == newBitmap.width && oldBitmap.height == newBitmap.height) {
                                    snapshotVal.screenshotFile.writeBytes(newBytes)
                                    snapshotZipDir
                                        .listFiles { f -> f.isFile && f.name.endsWith("${snapshotVal.id}.zip") }
                                        ?.forEach { f ->
                                            f.delete()
                                        }
                                    if (snapshotVal.githubAssetId != null) {
                                        // 当本地快照变更时, 移除快照链接
                                        DbSet.snapshotDao.deleteGithubAssetId(snapshotVal.id)
                                    }
                                } else {
                                    toast(getSafeString(R.string.screenshot_size_mismatch_cannot_replace))
                                    return@withContext
                                }
                            }
                            toast(getSafeString(R.string.replace_success))
                            selectedSnapshot = null
                        }))
                        .then(modifier)
                )
                HorizontalDivider()
                Text(
                    text = getSafeString(R.string.delete), modifier = Modifier
                        .clickable(onClick = throttle(fn = vm.viewModelScope.launchAsFn {
                            DbSet.snapshotDao.delete(snapshotVal)
                            withContext(Dispatchers.IO) {
                                SnapshotExt.removeAssets(snapshotVal.id)
                            }
                            selectedSnapshot = null
                        }))
                        .then(modifier), color = colorScheme.error
                )
            }
        }
    }
}


