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
package com.ps.gkd.data

import android.net.Uri
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.UriUtils
import com.blankj.utilcode.util.ZipUtils
import com.ps.gkd.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import com.ps.gkd.db.DbSet
import com.ps.gkd.getSafeString
import com.ps.gkd.util.LOCAL_SUBS_IDS
import com.ps.gkd.util.checkSubsUpdate
import com.ps.gkd.util.exportZipDir
import com.ps.gkd.util.importZipDir
import com.ps.gkd.util.json
import com.ps.gkd.util.resetDirectory
import com.ps.gkd.util.subsIdToRawFlow
import com.ps.gkd.util.subsItemsFlow
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubscription
import java.io.File

@Serializable
private data class TransferData(
    val type: String = TYPE,
    val ctime: Long = System.currentTimeMillis(),
    val subsItems: List<SubsItem> = emptyList(),
    val subsConfigs: List<SubsConfig> = emptyList(),
    val categoryConfigs: List<CategoryConfig> = emptyList(),
) {
    companion object {
        const val TYPE = "transfer_data"
    }
}

private suspend fun importTransferData(transferData: TransferData): Boolean {
    // TODO transaction
    val maxOrder = (subsItemsFlow.value.maxOfOrNull { it.order } ?: -1) + 1
    val subsItems =
        transferData.subsItems.filter { s -> s.id >= 0 || LOCAL_SUBS_IDS.contains(s.id) }
            .mapIndexed { i, s ->
                s.copy(order = maxOrder + i)
            }
    val hasNewSubsItem =
        subsItems.any { newSubs -> newSubs.id >= 0 && subsItemsFlow.value.all { oldSubs -> oldSubs.id != newSubs.id } }
    DbSet.subsItemDao.insertOrIgnore(*subsItems.toTypedArray())
    DbSet.subsConfigDao.insertOrIgnore(*transferData.subsConfigs.toTypedArray())
    DbSet.categoryConfigDao.insertOrIgnore(*transferData.categoryConfigs.toTypedArray())
    return hasNewSubsItem
}

suspend fun exportData(subsIds: Collection<Long>):File {
    exportZipDir.resetDirectory()
    val dataFile = exportZipDir.resolve("${TransferData.TYPE}.json")
    dataFile.writeText(
        json.encodeToString(
            TransferData(
                subsItems = subsItemsFlow.value.filter { subsIds.contains(it.id) },
                subsConfigs = DbSet.subsConfigDao.querySubsItemConfig(subsIds.toList()),
                categoryConfigs = DbSet.categoryConfigDao.querySubsItemConfig(subsIds.toList()),
            )
        )
    )
    val files = exportZipDir.resolve("files").apply { mkdir() }
    subsIdToRawFlow.value.values.filter { it.id < 0 && subsIds.contains(it.id) }.forEach {
        val file = files.resolve("${it.id}.json")
        file.writeText(json.encodeToString(it))
    }
    val file = exportZipDir.resolve("backup-${System.currentTimeMillis()}.zip")
    ZipUtils.zipFiles(listOf(dataFile, files), file)
    dataFile.delete()
    files.deleteRecursively()
    return file
}

suspend fun importData(uri: Uri) {
    importZipDir.resetDirectory()
    val zipFile = importZipDir.resolve("import.zip")
    zipFile.writeBytes(UriUtils.uri2Bytes(uri))
    val unZipImportFile = importZipDir.resolve("unzipImport")
    ZipUtils.unzipFile(zipFile, unZipImportFile)
    val transferFile = unZipImportFile.resolve("${TransferData.TYPE}.json")
    if (!transferFile.exists() || !transferFile.isFile) {
        toast(getSafeString(R.string.import_no_data))
        return
    }
    val data = withContext(Dispatchers.Default) {
        json.decodeFromString<TransferData>(transferFile.readText())
    }
    val hasNewSubsItem = importTransferData(data)
    val files = unZipImportFile.resolve("files")
    val subscriptions = (files.listFiles { f -> f.isFile && f.name.endsWith(".json") }
        ?: emptyArray()).mapNotNull { f ->
        try {
            RawSubscription.parse(f.readText())
        } catch (e: Exception) {
            LogUtils.d(e)
            null
        }
    }
    subscriptions.forEach { subscription ->
        if (LOCAL_SUBS_IDS.contains(subscription.id)) {
            updateSubscription(subscription)
        }
    }
    toast(getSafeString(R.string.import_success))
    importZipDir.resetDirectory()
    if (hasNewSubsItem) {
        delay(1000)
        checkSubsUpdate(true)
    }
}
