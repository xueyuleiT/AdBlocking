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

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.DeleteTable
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.AutoMigrationSpec
import kotlinx.coroutines.flow.Flow
import com.ps.gkd.util.format
import com.ps.gkd.util.getShowActivityId

@Entity(
    tableName = "activity_log_v2",
)
data class ActivityLog(
    // 不使用时间戳作为主键的原因
    // https://github.com/gkd-kit/gkd/issues/704
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Int = 0,
    @ColumnInfo(name = "ctime") val ctime: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "app_id") val appId: String,
    @ColumnInfo(name = "activity_id") val activityId: String? = null,
) {
    val showActivityId by lazy { getShowActivityId(appId, activityId) }
    val date by lazy { ctime.format("MM-dd HH:mm:ss SSS") }

    @Dao
    interface ActivityLogDao {
        @Insert
        suspend fun insert(vararg objects: ActivityLog): List<Long>

        @Query("DELETE FROM activity_log_v2")
        suspend fun deleteAll()

        @Query("SELECT * FROM activity_log_v2 ORDER BY ctime DESC ")
        fun pagingSource(): PagingSource<Int, ActivityLog>

        @Query("SELECT COUNT(*) FROM activity_log_v2")
        fun count(): Flow<Int>

        @Query(
            """
            DELETE FROM activity_log_v2
            WHERE (
                    SELECT COUNT(*)
                    FROM activity_log_v2
                ) > 1000
                AND ctime <= (
                    SELECT ctime
                    FROM activity_log_v2
                    ORDER BY ctime DESC
                    LIMIT 1 OFFSET 1000
                )
        """
        )
        suspend fun deleteKeepLatest(): Int
    }


    @DeleteTable.Entries(
        DeleteTable(tableName = "activity_log")
    )
    class ActivityLogV2Spec : AutoMigrationSpec
}
