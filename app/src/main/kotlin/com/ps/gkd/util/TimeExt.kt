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

import com.ps.gkd.R
import com.ps.gkd.getSafeString
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.collections.hashMapOf

fun formatTimeAgo(timestamp: Long): String {
    val currentTime = System.currentTimeMillis()
    val timeDifference = currentTime - timestamp

    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeDifference)
    val hours = TimeUnit.MILLISECONDS.toHours(timeDifference)
    val days = TimeUnit.MILLISECONDS.toDays(timeDifference)
    val weeks = days / 7
    val months = (days / 30)
    val years = (days / 365)
    return when {
        years > 0 -> String.format(getSafeString(R.string.years_ago),years.toString())
        months > 0 -> String.format(getSafeString(R.string.months_ago),months.toString())
        weeks > 0 -> String.format(getSafeString(R.string.weeks_ago),weeks.toString())
        days > 0 -> String.format(getSafeString(R.string.days_ago),days.toString())
        hours > 0 -> String.format(getSafeString(R.string.hours_ago),hours.toString())
        minutes > 0 -> String.format(getSafeString(R.string.minutes_ago),minutes.toString())
        else -> getSafeString(R.string.just_now)
    }
}

private val formatDateMap by lazy { hashMapOf<String, SimpleDateFormat>() }

fun Long.format(formatStr: String): String {
    var df = formatDateMap[formatStr]
    if (df == null) {
        df = SimpleDateFormat(formatStr, Locale.getDefault())
        formatDateMap[formatStr] = df
    }
    return df.format(this)
}

data class ThrottleTimer(
    private val interval: Long = 500L,
    private var value: Long = 0L
) {
    fun expired(): Boolean {
        val t = System.currentTimeMillis()
        if (t - value > interval) {
            value = t
            return true
        }
        return false
    }
}

private val defaultThrottleTimer by lazy { ThrottleTimer() }

fun throttle(
    timer: ThrottleTimer = defaultThrottleTimer,
    fn: (() -> Unit),
): (() -> Unit) {
    return {
        if (timer.expired()) {
            fn.invoke()
        }
    }
}

fun <T> throttle(
    timer: ThrottleTimer = defaultThrottleTimer,
    fn: ((T) -> Unit),
): ((T) -> Unit) {
    return {
        if (timer.expired()) {
            fn.invoke(it)
        }
    }
}
