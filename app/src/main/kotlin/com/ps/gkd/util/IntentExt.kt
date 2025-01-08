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
package com.ps.gkd.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.app
import com.ps.gkd.getSafeString
import com.ps.gkd.permission.canWriteExternalStorage
import com.ps.gkd.permission.requiredPermission
import java.io.File

fun Context.shareFile(file: File, title: String) {
    val uri = FileProvider.getUriForFile(
        this, "${packageName}.provider", file
    )
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, uri)
        type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    tryStartActivity(
        Intent.createChooser(
            intent, title
        )
    )
}

suspend fun MainActivity.saveFileToDownloads(file: File) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        requiredPermission(this, canWriteExternalStorage)
        val targetFile = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            file.name
        )
        targetFile.writeBytes(file.readBytes())
    } else {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        withContext(Dispatchers.IO) {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error(getSafeString(R.string.create_uri_failed))
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(file.readBytes())
                outputStream.flush()
            }
        }
    }
    toast(String.format(getSafeString(R.string.file_saved),file.name))
}

fun Context.tryStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        LogUtils.d("tryStartActivity", e)
        // 在某些模拟器上/特定设备 ActivityNotFoundException
        toast(e.message ?: e.stackTraceToString())
    }
}

fun openA11ySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    app.tryStartActivity(intent)
}

fun openUri(uri: String) {
    val u = try {
        Uri.parse(uri)
    } catch (e: Exception) {
        e.printStackTrace()
        toast(
            getSafeString(R.string.illegal_link))
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, u)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    app.tryStartActivity(intent)
}

fun openApp(appId: String) {
    val intent = app.packageManager.getLaunchIntentForPackage(appId)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.tryStartActivity(intent)
    } else {
        toast(getSafeString(R.string.check_app_installed))
    }
}