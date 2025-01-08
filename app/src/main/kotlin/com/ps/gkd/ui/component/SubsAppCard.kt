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

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.blankj.utilcode.util.ClipboardUtils
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.ps.gkd.R
import com.ps.gkd.data.AppInfo
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.SubsConfig
import com.ps.gkd.getSafeString
import com.ps.gkd.ui.style.appItemPadding
import com.ps.gkd.util.json
import com.ps.gkd.util.toast
import li.songe.json5.encodeToJson5String


@Composable
fun SubsAppCard(
    rawApp: RawSubscription.RawApp,
    appInfo: AppInfo? = null,
    subsConfig: SubsConfig? = null,
    enableSize: Int = rawApp.groups.count { g -> g.enable ?: true },
    onClick: (() -> Unit)? = null,
    showMenu: Boolean = false,
    onDelClick: (() -> Unit)? = null,
    onValueChange: ((Boolean) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clickable {
                onClick?.invoke()
            }
            .height(IntrinsicSize.Min)
            .appItemPadding(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (appInfo?.icon != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            ) {
                Image(
                    painter = rememberDrawablePainter(appInfo.icon),
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(4.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Android,
                contentDescription = null,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxHeight()
                    .padding(4.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .padding(2.dp)
                .fillMaxHeight()
                .weight(1f),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AppNameText(
                appId = rawApp.id,
                appInfo = appInfo,
                fallbackName = rawApp.name,
            )

            if (rawApp.groups.isNotEmpty()) {
                val enableDesc = when (enableSize) {
                    0 -> "${rawApp.groups.size}${getSafeString(R.string.group_rule)}/${rawApp.groups.size}${getSafeString(R.string.close)}"
                    rawApp.groups.size -> "${rawApp.groups.size}${getSafeString(R.string.group_rule)}"
                    else -> "${rawApp.groups.size}${getSafeString(R.string.group_rule)}/${enableSize}${getSafeString(R.string.enable)}/${rawApp.groups.size - enableSize}${getSafeString(R.string.close)}"
                }
                Text(
                    text = enableDesc,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = getSafeString(R.string.no_rules),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))

        if (showMenu) {
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart)
            ) {
                IconButton(onClick = {
                    expanded = true
                }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "more",
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(text = getSafeString(R.string.copy))
                        },
                        onClick = {
                            ClipboardUtils.copyText(
                                json.encodeToJson5String(rawApp)
                            )
                            toast(getSafeString(R.string.copy_success))
                            expanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(text = getSafeString(R.string.delete), color = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            onDelClick?.invoke()
                            expanded = false
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Switch(
            checked = subsConfig?.enable ?: (appInfo != null),
            onCheckedChange = onValueChange,
        )
    }
}


