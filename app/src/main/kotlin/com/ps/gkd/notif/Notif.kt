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
package com.ps.gkd.notif

import com.ps.gkd.R
import com.ps.gkd.app
import com.ps.gkd.getSafeString
import com.ps.gkd.util.SafeR


data class Notif(
    val channel: NotifChannel,
    val id: Int,
    val smallIcon: Int = SafeR.ic_status,
    val title: String = app.getString(SafeR.app_name),
    val text: String,
    val ongoing: Boolean,
    val autoCancel: Boolean,
    val uri: String? = null,
)

val abNotif by lazy {
    Notif(
        channel = defaultChannel,
        id = 100,
        text = getSafeString(R.string.accessibility_running),
        ongoing = true,
        autoCancel = false,
    )
}

val screenshotNotif by lazy {
    Notif(
        channel = screenshotChannel,
        id = 101,
        text = getSafeString(R.string.screenshot_service_running),
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
    )
}

val floatingNotif by lazy {
    Notif(
        channel = floatingChannel,
        id = 102,
        text = getSafeString(R.string.floating_window_button_showing),
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
    )
}

val httpNotif by lazy {
    Notif(
        channel = httpChannel,
        id = 103,
        text = getSafeString(R.string.http_service_running),
        ongoing = true,
        autoCancel = false,
        uri = "gkd://page/1",
    )
}

val snapshotNotif by lazy {
    Notif(
        channel = snapshotChannel,
        id = 104,
        text = getSafeString(R.string.snapshot_saved),
        ongoing = false,
        autoCancel = true,
        uri = "gkd://page/2",
    )
}

val snapshotActionNotif by lazy {
    Notif(
        channel = snapshotActionChannel,
        id = 105,
        text = getSafeString(R.string.snapshot_service_running),
        ongoing = true,
        autoCancel = false,
    )
}