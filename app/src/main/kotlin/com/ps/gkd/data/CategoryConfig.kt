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
package com.ps.gkd.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "category_config",
)
data class CategoryConfig(
    @PrimaryKey @ColumnInfo(name = "id") val id: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "enable") val enable: Boolean? = null,
    @ColumnInfo(name = "subs_item_id") val subsItemId: Long,
    @ColumnInfo(name = "category_key") val categoryKey: Int,
) {
    @Dao
    interface CategoryConfigDao {

        @Update
        suspend fun update(vararg objects: CategoryConfig): Int

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insert(vararg objects: CategoryConfig): List<Long>

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertOrIgnore(vararg objects: CategoryConfig): List<Long>

        @Delete
        suspend fun delete(vararg objects: CategoryConfig): Int

        @Query("DELETE FROM category_config WHERE subs_item_id=:subsItemId")
        suspend fun deleteBySubsItemId(subsItemId: Long): Int

        @Query("DELETE FROM category_config WHERE subs_item_id IN (:subsIds)")
        suspend fun deleteBySubsId(vararg subsIds: Long): Int

        @Query("DELETE FROM category_config WHERE subs_item_id=:subsItemId AND category_key=:categoryKey")
        suspend fun deleteByCategoryKey(subsItemId: Long, categoryKey: Int): Int

        @Query("SELECT * FROM category_config WHERE subs_item_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)")
        fun queryUsedList(): Flow<List<CategoryConfig>>

        @Query("SELECT * FROM category_config WHERE subs_item_id=:subsItemId")
        fun queryConfig(subsItemId: Long): Flow<List<CategoryConfig>>

        @Query("SELECT * FROM category_config WHERE subs_item_id IN (:subsItemIds)")
        suspend fun querySubsItemConfig(subsItemIds: List<Long>): List<CategoryConfig>

        @Query("SELECT * FROM category_config WHERE subs_item_id IN (:subsItemIds)")
        fun queryBySubsIds(subsItemIds: List<Long>): Flow<List<CategoryConfig>>

    }
}
