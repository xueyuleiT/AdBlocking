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
package com.ps.gkd.ui.style

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MenuDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val itemHorizontalPadding = 16.dp
val itemVerticalPadding = 12.dp
val EmptyHeight = 40.dp

fun Modifier.itemPadding() = this.padding(itemHorizontalPadding, itemVerticalPadding)

fun Modifier.titleItemPadding() = this.padding(
    itemHorizontalPadding,
    itemVerticalPadding + itemVerticalPadding / 2,
    itemHorizontalPadding,
    itemVerticalPadding - itemVerticalPadding / 2
)

fun Modifier.appItemPadding() = this.padding(10.dp, 10.dp)

fun Modifier.menuPadding() = this
    .padding(MenuDefaults.DropdownMenuItemContentPadding)
    .padding(vertical = 8.dp)

fun Modifier.scaffoldPadding(values: PaddingValues): Modifier {
    return this.padding(
        top = values.calculateTopPadding(),
        // 被 LazyColumn( 使用时, 移除 bottom padding, 否则 底部导航栏 无法实现透明背景
    )
}
