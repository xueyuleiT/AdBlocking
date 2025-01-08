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
package com.ps.gkd.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ps.gkd.R
import com.ps.gkd.data.AppInfo
import com.ps.gkd.getSafeString
import com.ps.gkd.util.appInfoCacheFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast

@Composable
fun AppNameText(
    appId: String? = null,
    appInfo: AppInfo? = null,
    fallbackName: String? = null,
) {
    val info = appInfo ?: com.ps.gkd.util.appInfoCacheFlow.collectAsState().value[appId]
    if (info?.isSystem == true) {
        val style = LocalTextStyle.current
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                modifier = Modifier
                    .clickable(onClick = throttle(fn = { toast("${info.name} ${getSafeString(R.string.system_app)}") }))
                    .size(style.fontSize.value.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = info.name,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        Text(
            text = info?.name ?: fallbackName ?: appId ?: error("appId is required"),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}