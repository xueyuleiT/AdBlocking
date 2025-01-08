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
package com.ps.gkd.service

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class A11yEvent(
    val type: Int,
    val time: Long,
    val appId: String,
    val className: String,
    val event: AccessibilityEvent,
) {
    val safeSource: AccessibilityNodeInfo?
        get() = event.safeSource
}

fun A11yEvent.sameAs(other: A11yEvent): Boolean {
    if (other === this) return true
    return type == other.type && appId == other.appId && className == other.className
}

fun AccessibilityEvent.toA11yEvent(): A11yEvent? {
    return A11yEvent(
        type = eventType,
        time = System.currentTimeMillis(),
        appId = packageName?.toString() ?: return null,
        className = className?.toString() ?: return null,
        event = this,
    )
}