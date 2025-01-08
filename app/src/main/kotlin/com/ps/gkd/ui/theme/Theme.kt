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
package com.ps.gkd.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.ps.gkd.MainActivity

val LightColorScheme = lightColorScheme()
val DarkColorScheme = darkColorScheme()
val supportDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun AppTheme(
    content: @Composable () -> Unit,
) {
    // https://developer.android.com/jetpack/compose/designsystems/material3?hl=zh-cn
    val context = LocalContext.current as MainActivity
    val enableDarkTheme by context.mainVm.enableDarkThemeFlow.collectAsState()
    val enableDynamicColor by context.mainVm.enableDynamicColorFlow.collectAsState()
    val systemInDarkTheme = isSystemInDarkTheme()
    val darkTheme = enableDarkTheme ?: systemInDarkTheme
    val colorScheme = when {
        supportDynamicColor && enableDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        supportDynamicColor && enableDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // https://github.com/gkd-kit/gkd/pull/421
    LaunchedEffect(darkTheme) {
        WindowInsetsControllerCompat(context.window, context.window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme, content = content
    )
}