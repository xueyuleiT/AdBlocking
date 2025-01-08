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

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.DeleteTable
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.migration.AutoMigrationSpec
import kotlinx.coroutines.flow.Flow
import com.ps.gkd.util.format
import com.ps.gkd.util.getShowActivityId

@Entity(
    tableName = "action_log",
)
data class ActionLog(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "ctime") val ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
    @ColumnInfo(name = "subs_id") val subsId: Long,
    @ColumnInfo(name = "subs_version", defaultValue = "0") val subsVersion: Int,
    @ColumnInfo(name = "group_key") val groupKey: Int,
    @ColumnInfo(name = "group_type", defaultValue = "2") val groupType: Int,
    @ColumnInfo(name = "rule_index") val ruleIndex: Int,
    @ColumnInfo(name = "rule_key") val ruleKey: Int? = null,
) {

    val showActivityId by lazy { getShowActivityId(appId, activityId) }

    val date by lazy { ctime.format("MM-dd HH:mm:ss SSS") }

    @DeleteTable.Entries(
        DeleteTable(tableName = "click_log")
    )
    class ActionLogSpec : AutoMigrationSpec


    @Dao
    interface ActionLogDao {

        @Update
        suspend fun update(vararg objects: ActionLog): Int

        @Insert
        suspend fun insert(vararg objects: ActionLog): List<Long>

        @Delete
        suspend fun delete(vararg objects: ActionLog): Int


        @Query("DELETE FROM action_log WHERE subs_id IN (:subsIds)")
        suspend fun deleteBySubsId(vararg subsIds: Long): Int

        @Query("DELETE FROM action_log")
        suspend fun deleteAll()

        @Query("SELECT * FROM action_log ORDER BY id DESC LIMIT 1000")
        fun query(): Flow<List<ActionLog>>

        @Query("SELECT * FROM action_log ORDER BY id DESC ")
        fun pagingSource(): PagingSource<Int, ActionLog>

        @Query("SELECT COUNT(*) FROM action_log")
        fun count(): Flow<Int>


        @Query("SELECT * FROM action_log ORDER BY id DESC LIMIT 1")
        fun queryLatest(): Flow<ActionLog?>

        @Query(
            """
            SELECT cl.* FROM action_log AS cl
            INNER JOIN (
                SELECT subs_id, group_key, MAX(ctime) AS max_id FROM action_log
                WHERE app_id = :appId
                  AND group_type = :groupType
                  AND subs_id IN (SELECT si.id FROM subs_item si WHERE si.enable = 1)
                GROUP BY subs_id, group_key
            ) AS latest_logs ON cl.subs_id = latest_logs.subs_id 
            AND cl.group_key = latest_logs.group_key 
            AND cl.id = latest_logs.max_id
        """
        )
        fun queryAppLatest(appId: String, groupType: Int): Flow<List<ActionLog>>


        @Query(
            """
            DELETE FROM action_log
            WHERE (
                    SELECT COUNT(*)
                    FROM action_log
                ) > 1000
                AND id <= (
                    SELECT id
                    FROM action_log
                    ORDER BY id DESC
                    LIMIT 1 OFFSET 1000
                )
        """
        )
        suspend fun deleteKeepLatest(): Int

        @Query("SELECT DISTINCT app_id FROM action_log ORDER BY id DESC")
        fun queryLatestUniqueAppIds(): Flow<List<String>>

        @Query("SELECT DISTINCT app_id FROM action_log WHERE subs_id=:subsItemId AND group_type=${SubsConfig.AppGroupType} ORDER BY id DESC")
        fun queryLatestUniqueAppIds(subsItemId: Long): Flow<List<String>>

        @Query("SELECT DISTINCT app_id FROM action_log WHERE subs_id=:subsItemId AND group_key=:globalGroupKey AND group_type=${SubsConfig.GlobalGroupType} ORDER BY id DESC")
        fun queryLatestUniqueAppIds(subsItemId: Long, globalGroupKey: Int): Flow<List<String>>
    }
}
