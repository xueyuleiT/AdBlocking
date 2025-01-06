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

data class NotifChannel(
    val id: String,
    val name: String,
    val desc: String,
)

val defaultChannel by lazy {
    NotifChannel(
        id = "default", name = getSafeString(R.string.app_name), desc = getSafeString(R.string.show_service_status)
    )
}

val floatingChannel by lazy {
    NotifChannel(
        id = "floating", name = getSafeString(R.string.floating_window_button_service), desc = getSafeString(R.string.floating_window_button_service_tip)
    )
}
val screenshotChannel by lazy {
    NotifChannel(
        id = "screenshot", name = getSafeString(R.string.screenshot_service), desc = getSafeString(R.string.screenshot_service_tip)
    )
}
val httpChannel by lazy {
    NotifChannel(
        id = "http", name = getSafeString(R.string.http_service), desc = getSafeString(R.string.http_service_tip)
    )
}
val snapshotChannel by lazy {
    NotifChannel(
        id = "snapshot", name = getSafeString(R.string.snapshot_notification), desc = getSafeString(R.string.snapshot_notification_tip)
    )
}
val snapshotActionChannel by lazy {
    NotifChannel(
        id = "snapshotAction", name = getSafeString(R.string.snapshot_service_1), desc = getSafeString(R.string.snapshot_service_1_tip)
    )
}

fun initChannel() {
    arrayOf(
        defaultChannel,
        floatingChannel,
        screenshotChannel,
        httpChannel,
        snapshotChannel,
        snapshotActionChannel,
    ).forEach {
        createChannel(app, it)
    }
}