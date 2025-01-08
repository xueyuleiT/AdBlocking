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

import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ZipUtils
import kotlinx.serialization.encodeToString
import com.ps.gkd.app
import java.io.File

private val filesDir by lazy {
    app.getExternalFilesDir(null) ?: app.filesDir
}
val dbFolder by lazy { filesDir.resolve("db") }
val subsFolder by lazy { filesDir.resolve("subscription") }
val snapshotFolder by lazy { filesDir.resolve("snapshot") }

private val cacheDir by lazy {
    app.externalCacheDir ?: app.cacheDir
}
val snapshotZipDir by lazy { cacheDir.resolve("snapshotZip") }
val newVersionApkDir by lazy { cacheDir.resolve("newVersionApk") }
val logZipDir by lazy { cacheDir.resolve("logZip") }
val imageCacheDir by lazy { cacheDir.resolve("imageCache") }
val exportZipDir by lazy { cacheDir.resolve("exportZip") }
val importZipDir by lazy { cacheDir.resolve("exportZip") }

fun initFolder() {
    listOf(
        dbFolder,
        subsFolder,
        snapshotFolder,
        snapshotZipDir,
        newVersionApkDir,
        logZipDir,
        imageCacheDir,
        exportZipDir,
        importZipDir
    ).forEach { f ->
        if (!f.exists()) {
            // TODO 在某些机型上无法创建目录 用户反馈重启手机后解决 是否存在其它解决方式?
            f.mkdirs()
        }
    }
}

fun clearCache() {
    listOf(
        snapshotZipDir,
        newVersionApkDir,
        logZipDir,
        imageCacheDir,
        exportZipDir,
        importZipDir
    ).forEach { dir ->
        if (dir.isDirectory && dir.exists()) {
            dir.deleteRecursively()
            dir.mkdir()
        }
    }
}

fun File.resetDirectory() {
    if (isFile) {
        delete()
    } else if (isDirectory) {
        deleteRecursively()
    }
    if (!exists()) {
        mkdir()
    }
}


fun buildLogFile(): File {
    val files = mutableListOf(dbFolder, subsFolder)
    LogUtils.getLogFiles().firstOrNull()?.parentFile?.let { files.add(it) }
    val appListFile = logZipDir
        .resolve("appList.json")
    appListFile.writeText(json.encodeToString(com.ps.gkd.util.appInfoCacheFlow.value.values.toList()))
    files.add(appListFile)
    val storeFile = logZipDir
        .resolve("store.json")
    storeFile.writeText(json.encodeToString(storeFlow.value))
    files.add(storeFile)
    val logZipFile = logZipDir.resolve("log-${System.currentTimeMillis()}.zip")
    ZipUtils.zipFiles(files, logZipFile)
    appListFile.delete()
    storeFile.delete()
    return logZipFile
}