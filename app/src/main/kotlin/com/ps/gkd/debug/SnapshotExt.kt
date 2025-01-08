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
package com.ps.gkd.debug

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.set
import com.blankj.utilcode.util.BarUtils
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.blankj.utilcode.util.ZipUtils
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.data.ComplexSnapshot
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.RpcError
import com.ps.gkd.data.TakePositionEvent
import com.ps.gkd.data.createComplexSnapshot
import com.ps.gkd.data.toSnapshot
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.mainActivity
import com.ps.gkd.notif.notify
import com.ps.gkd.notif.snapshotNotif
import com.ps.gkd.service.A11yService
import com.ps.gkd.util.keepNullJson
import com.ps.gkd.util.snapshotFolder
import com.ps.gkd.util.snapshotZipDir
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.toast
import com.ramcosta.composedestinations.generated.destinations.TakePositionPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import java.io.File
import kotlin.math.min

object SnapshotExt {

    private fun getSnapshotParentPath(snapshotId: Long) =
        "${snapshotFolder.absolutePath}/${snapshotId}"

    fun getSnapshotPath(snapshotId: Long) =
        "${getSnapshotParentPath(snapshotId)}/${snapshotId}.json"

    fun getScreenshotPath(snapshotId: Long) =
        "${getSnapshotParentPath(snapshotId)}/${snapshotId}.png"

    suspend fun getSnapshotZipFile(
        snapshotId: Long,
        appId: String? = null,
        activityId: String? = null
    ): File {
        val filename = if (appId != null) {
            val name =
                com.ps.gkd.util.appInfoCacheFlow.value[appId]?.name?.filterNot { c -> c in "\\/:*?\"<>|" || c <= ' ' }
            if (activityId != null) {
                "${(name ?: appId).take(20)}_${
                    activityId.split('.').last().take(40)
                }-${snapshotId}.zip"
            } else {
                "${(name ?: appId).take(20)}-${snapshotId}.zip"
            }
        } else {
            "${snapshotId}.zip"
        }
        val file = snapshotZipDir.resolve(filename)
        if (file.exists()) {
            return file
        }
        withContext(Dispatchers.IO) {
            ZipUtils.zipFiles(
                listOf(
                    getSnapshotPath(snapshotId), getScreenshotPath(snapshotId)
                ), file.absolutePath
            )
        }
        return file
    }

    fun removeAssets(id: Long) {
        File(getSnapshotParentPath(id)).apply {
            if (exists()) {
                deleteRecursively()
            }
        }
    }

    private val captureLoading = MutableStateFlow(false)

    suspend fun captureSnapshot(skipScreenshot: Boolean = false): ComplexSnapshot {
        if (!A11yService.isRunning.value) {
            throw RpcError(getSafeString(R.string.accessibility_not_enabled))
        }
        if (captureLoading.value) {
            throw RpcError(getSafeString(R.string.saving_snapshot))
        }
        captureLoading.value = true
        if (storeFlow.value.showSaveSnapshotToast) {
            toast(getSafeString(R.string.saving_snapshot_in_progress))
        }

        try {
            val snapshotDef = coroutineScope { async(Dispatchers.IO) { createComplexSnapshot() } }
            val bitmapDef = coroutineScope {// TODO 也许在分屏模式下可能需要处理
                async(Dispatchers.IO) {
                    if (skipScreenshot) {
                        LogUtils.d(getSafeString(R.string.skip_screenshot))
                        Bitmap.createBitmap(
                            ScreenUtils.getScreenWidth(),
                            ScreenUtils.getScreenHeight(),
                            Bitmap.Config.ARGB_8888
                        )
                    } else {
                        A11yService.currentScreenshot() ?: withTimeoutOrNull(3_000) {
                            if (!ScreenshotService.isRunning.value) {
                                return@withTimeoutOrNull null
                            }
                            ScreenshotService.screenshot()
                        } ?: Bitmap.createBitmap(
                            ScreenUtils.getScreenWidth(),
                            ScreenUtils.getScreenHeight(),
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            LogUtils.d(getSafeString(R.string.screenshot_unavailable))
                        }
                    }
                }
            }

            var bitmap = bitmapDef.await()
            if (storeFlow.value.hideSnapshotStatusBar) {
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                for (x in 0 until bitmap.width) {
                    for (y in 0 until min(BarUtils.getStatusBarHeight(), bitmap.height)) {
                        bitmap[x, y] = 0
                    }
                }
            }
            val snapshot = snapshotDef.await()

            withContext(Dispatchers.IO) {
                File(getSnapshotParentPath(snapshot.id)).apply { if (!exists()) mkdirs() }
                File(getScreenshotPath(snapshot.id)).outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                val text = keepNullJson.encodeToString(snapshot)
                File(getSnapshotPath(snapshot.id)).writeText(text)
                DbSet.snapshotDao.insert(snapshot.toSnapshot())
                mainActivity!!.snapshot.emit(TakePositionEvent(snapshot.id,RawSubscription.Position(null,null,null,null)))
            }
            toast(getSafeString(R.string.snapshot_success))


            val desc = snapshot.appInfo?.name ?: snapshot.appId
            snapshotNotif.copy(
                text = if (desc != null) {
                    "${getSafeString(R.string.snapshot)}[$desc]${getSafeString(R.string.saved_to_records)}"
                } else {
                    snapshotNotif.text
                }
            ).notify()
            return snapshot
        } finally {
            captureLoading.value = false
        }
    }
}


