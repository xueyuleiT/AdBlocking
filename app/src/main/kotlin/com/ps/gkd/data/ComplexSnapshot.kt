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

import com.blankj.utilcode.util.ScreenUtils
import kotlinx.serialization.Serializable
import com.ps.gkd.app
import com.ps.gkd.service.A11yService
import com.ps.gkd.service.getAndUpdateCurrentRules
import com.ps.gkd.service.safeActiveWindow

@Serializable
data class ComplexSnapshot(
    override val id: Long,

    override val appId: String?,
    override val activityId: String?,

    override val screenHeight: Int,
    override val screenWidth: Int,
    override val isLandscape: Boolean,

    val appInfo: AppInfo? = appId?.let { app.packageManager.getPackageInfo(appId, 0)?.toAppInfo() },
    val gkdAppInfo: AppInfo? = selfAppInfo,
    val device: DeviceInfo = DeviceInfo.instance,

    @Deprecated("use appInfo")
    override val appName: String? = appInfo?.name,
    @Deprecated("use appInfo")
    override val appVersionCode: Long? = appInfo?.versionCode,
    @Deprecated("use appInfo")
    override val appVersionName: String? = appInfo?.versionName,

    val nodes: List<NodeInfo>,
) : BaseSnapshot


fun createComplexSnapshot(): ComplexSnapshot {
    val currentAbNode = A11yService.instance?.safeActiveWindow
    val appId = currentAbNode?.packageName?.toString()
    val currentActivityId = getAndUpdateCurrentRules().topActivity.activityId

    return ComplexSnapshot(
        id = System.currentTimeMillis(),

        appId = appId,
        activityId = currentActivityId,

        screenHeight = ScreenUtils.getScreenHeight(),
        screenWidth = ScreenUtils.getScreenWidth(),
        isLandscape = ScreenUtils.isLandscape(),

        nodes = info2nodeList(currentAbNode)
    )
}

fun ComplexSnapshot.toSnapshot(): Snapshot {
    return Snapshot(
        id = id,

        appId = appId,
        activityId = activityId,

        screenHeight = screenHeight,
        screenWidth = screenWidth,
        isLandscape = isLandscape,

        appName = appInfo?.name,
        appVersionCode = appInfo?.versionCode,
        appVersionName = appInfo?.versionName,
    )
}


