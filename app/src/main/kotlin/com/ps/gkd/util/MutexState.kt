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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MutexState {
    val mutex = Mutex()
    val state = MutableStateFlow(false)
    suspend inline fun withLock(block: () -> Unit): Unit = mutex.withLock {
        state.value = true
        try {
            block()
        } finally {
            state.value = false
        }
    }
}
