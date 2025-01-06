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
package com.ps.gkd.util

import com.ps.gkd.R
import com.ps.gkd.getSafeString

sealed interface Option<T> {
    val value: T
    val label: String
}

fun <V, T : Option<V>> Array<T>.findOption(value: V): T {
    return find { it.value == value } ?: first()
}

@Suppress("UNCHECKED_CAST")
val <T> Option<T>.allSubObject: Array<Option<T>>
    get() = when (this) {
        is SortTypeOption -> SortTypeOption.allSubObject
        is UpdateTimeOption -> UpdateTimeOption.allSubObject
        is DarkThemeOption -> DarkThemeOption.allSubObject
        is EnableGroupOption -> EnableGroupOption.allSubObject
        is RuleSortOption -> RuleSortOption.allSubObject
        is UpdateChannelOption -> UpdateChannelOption.allSubObject
    } as Array<Option<T>>

sealed class SortTypeOption(override val value: Int, override val label: String) : Option<Int> {
    data object SortByName : SortTypeOption(0, getSafeString(R.string.sort_by_name))
    data object SortByAppMtime : SortTypeOption(1, getSafeString(R.string.sort_by_update_time))
    data object SortByTriggerTime : SortTypeOption(2, getSafeString(R.string.sort_by_trigger_time))

    companion object {
        // https://stackoverflow.com/questions/47648689
        val allSubObject by lazy { arrayOf(SortByName, SortByAppMtime, SortByTriggerTime) }
    }
}

sealed class UpdateTimeOption(
    override val value: Long,
    override val label: String
) : Option<Long> {
    data object Pause : UpdateTimeOption(-1, getSafeString(R.string.pause))
    data object Everyday : UpdateTimeOption(24 * 60 * 60_000, getSafeString(R.string.every_day))
    data object Every3Days : UpdateTimeOption(24 * 60 * 60_000 * 3,
       getSafeString(R.string.every_3_days)
    )
    data object Every7Days : UpdateTimeOption(24 * 60 * 60_000 * 7,
        getSafeString(R.string.every_7_days)
    )

    companion object {
        val allSubObject by lazy { arrayOf(Pause, Everyday, Every3Days, Every7Days) }
    }
}

sealed class DarkThemeOption(
    override val value: Boolean?,
    override val label: String
) : Option<Boolean?> {
    data object FollowSystem : DarkThemeOption(null, getSafeString(R.string.follow_system))
    data object AlwaysEnable : DarkThemeOption(true, getSafeString(R.string.always_open))
    data object AlwaysDisable : DarkThemeOption(false,  getSafeString(R.string.always_close))

    companion object {
        val allSubObject by lazy { arrayOf(FollowSystem, AlwaysEnable, AlwaysDisable) }
    }
}

sealed class EnableGroupOption(
    override val value: Boolean?,
    override val label: String
) : Option<Boolean?> {
    data object FollowSubs : DarkThemeOption(null, getSafeString(R.string.follow_subscription))
    data object AllEnable : DarkThemeOption(true, getSafeString(R.string.enable_all))
    data object AllDisable : DarkThemeOption(false, getSafeString(R.string.dis_enable_all))

    companion object {
        val allSubObject by lazy { arrayOf(FollowSubs, AllEnable, AllDisable) }
    }
}

sealed class RuleSortOption(override val value: Int, override val label: String) : Option<Int> {
    data object Default : RuleSortOption(0, getSafeString(R.string.sort_by_subscription_order))
    data object ByTime : RuleSortOption(1, getSafeString(R.string.sort_by_trigger_time))
    data object ByName : RuleSortOption(2, getSafeString(R.string.sort_by_name))

    companion object {
        val allSubObject by lazy { arrayOf(Default, ByTime, ByName) }
    }
}

sealed class UpdateChannelOption(
    override val value: Int,
    override val label: String
) : Option<Int> {
    abstract val url: String

    data object Stable : UpdateChannelOption(0, getSafeString(R.string.stable_version)) {
        override val url = "https://registry.npmmirror.com/@gkd-kit/app/latest/files/index.json"
    }

    data object Beta : UpdateChannelOption(1, getSafeString(R.string.beta_version)) {
        override val url =
            "https://registry.npmmirror.com/@gkd-kit/app-beta/latest/files/index.json"
    }

    companion object {
        val allSubObject by lazy { arrayOf(Stable, Beta) }
    }
}
