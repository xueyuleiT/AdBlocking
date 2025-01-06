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
package li.songe.selector

import kotlin.js.RegExp

actual fun String.toMatches(): (input: CharSequence) -> Boolean {
    if (wasmMatchesTemp !== null) {
        val matches = wasmMatchesTemp!!(this)
        return { input -> matches(input.toString()) }
    }
    if (length >= 4 && startsWith("(?")) {
        for (i in 2 until length) {
            when (get(i)) {
                in 'a'..'z' -> {}
                in 'A'..'Z' -> {}
                ')' -> {
                    val flags = subSequence(2, i).toMutableList()
                        .apply { add('g'); add('u') }
                        .joinToString("")
                    val nativePattern = RegExp(substring(i + 1), flags)
                    return { input ->
                        // // https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/js/src/kotlin/text/regex.kt
                        nativePattern.reset()
                        val match = nativePattern.exec(input.toString())
                        match != null && match.index == 0 && nativePattern.lastIndex == input.length
                    }
                }

                else -> break
            }
        }
    }
    val regex = Regex(this)
    return { input -> regex.matches(input) }
}

private var wasmMatchesTemp: ((String) -> (String) -> Boolean)? = null
actual fun setWasmToMatches(wasmToMatches: (String) -> (String) -> Boolean) {
    wasmMatchesTemp = wasmToMatches
}