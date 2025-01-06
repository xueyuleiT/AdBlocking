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
package com.ps.gkd.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sin

@Composable
fun RotatingLoadingIcon(loading: Boolean) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(loading) {
        if (loading) {
            rotation.animateTo(
                targetValue = rotation.value + 180f,
                animationSpec = tween(
                    durationMillis = 250,
                    easing = { x -> sin(Math.PI / 2 * (x - 1f)).toFloat() + 1f }
                )
            )
            rotation.animateTo(
                targetValue = rotation.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else if (rotation.value != 0f) {
            rotation.animateTo(
                targetValue = rotation.value + 180f,
                animationSpec = tween(
                    durationMillis = 250,
                    easing = { x -> sin(Math.PI / 2 * x).toFloat() }
                )
            )
        }
    }
    Icon(
        imageVector = Icons.Default.Autorenew,
        contentDescription = null,
        modifier = Modifier.graphicsLayer(rotationZ = rotation.value)
    )
}

