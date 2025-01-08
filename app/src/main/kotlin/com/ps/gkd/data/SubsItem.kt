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

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import com.ps.gkd.appScope
import com.ps.gkd.db.DbSet
import com.ps.gkd.util.LOCAL_SUBS_IDS
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.subsFolder
import com.ps.gkd.util.subsIdToRawFlow

@Serializable
@Entity(
    tableName = "subs_item",
)
data class SubsItem(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long,

    @ColumnInfo(name = "ctime") val ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mtime") val mtime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "enable") val enable: Boolean = false,
    @ColumnInfo(name = "enable_update") val enableUpdate: Boolean = true,
    @ColumnInfo(name = "order") val order: Int,
    @ColumnInfo(name = "update_url") val updateUrl: String? = null,

    ) {

    val isLocal: Boolean
        get() = LOCAL_SUBS_IDS.contains(id)

    @Dao
    interface SubsItemDao {
        @Update
        suspend fun update(vararg objects: SubsItem): Int

        @Query("UPDATE subs_item SET enable=:enable WHERE id=:id")
        suspend fun updateEnable(id: Long, enable: Boolean): Int

        @Query("UPDATE subs_item SET `order`=:order WHERE id=:id")
        suspend fun updateOrder(id: Long, order: Int): Int

        @Transaction
        suspend fun batchUpdateOrder(subsItems: List<SubsItem>) {
            subsItems.forEach { subsItem ->
                updateOrder(subsItem.id, subsItem.order)
            }
        }

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg users: SubsItem): List<Long>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg users: SubsItem): List<Long>

        @Delete
        suspend fun delete(vararg users: SubsItem): Int

        @Query("UPDATE subs_item SET mtime=:mtime WHERE id=:id")
        suspend fun updateMtime(id: Long, mtime: Long = System.currentTimeMillis()): Int

        @Query("SELECT * FROM subs_item ORDER BY `order`")
        fun query(): Flow<List<SubsItem>>

        @Query("SELECT * FROM subs_item ORDER BY `order`")
        fun queryAll(): List<SubsItem>

        @Query("DELETE FROM subs_item WHERE id IN (:ids)")
        suspend fun deleteById(vararg ids: Long)
    }

}


fun deleteSubscription(vararg subsIds: Long) {
    appScope.launchTry(Dispatchers.IO) {
        DbSet.subsItemDao.deleteById(*subsIds)
        DbSet.subsConfigDao.deleteBySubsId(*subsIds)
        DbSet.actionLogDao.deleteBySubsId(*subsIds)
        DbSet.categoryConfigDao.deleteBySubsId(*subsIds)
        val newMap = subsIdToRawFlow.value.toMutableMap()
        subsIds.forEach { id ->
            newMap.remove(id)
            subsFolder.resolve("$id.json").apply {
                if (exists()) {
                    delete()
                }
            }
        }
        subsIdToRawFlow.value = newMap
    }
}
