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

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.ramcosta.composedestinations.spec.DestinationStyle

typealias EnterTransitionType = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?
typealias ExitTransitionType = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?

object ProfileTransitions : DestinationStyle.Animated() {
    override val enterTransition: EnterTransitionType = {
        slideInHorizontally(tween()) { it }
    }

    override val exitTransition: ExitTransitionType = {
        slideOutHorizontally(tween()) { -it } + fadeOut(tween())
    }

    override val popEnterTransition: EnterTransitionType = {
        slideInHorizontally(tween()) { -it }
    }

    override val popExitTransition: ExitTransitionType = {
        slideOutHorizontally(tween()) { it }
    }
}