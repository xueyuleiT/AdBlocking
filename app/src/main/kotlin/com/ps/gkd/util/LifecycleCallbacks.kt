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


import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.WeakHashMap

private val callbacksMap by lazy { WeakHashMap<Any, HashMap<Int, MutableList<Any>>>() }

@Suppress("UNCHECKED_CAST")
private fun <T> Any.getCallbacks(method: Int): MutableList<T> {
    return callbacksMap.getOrPut(this) { hashMapOf() }
        .getOrPut(method) { mutableListOf() } as MutableList<T>
}

interface CanOnCallback

interface OnCreate : CanOnCallback {
//    fun onBeforeCreate(f: () -> Unit) {
//        getCallbacks<() -> Unit>(1).add(f)
//    }
//
//    fun onBeforeCreate() {
//        getCallbacks<() -> Unit>(1).forEach { it() }
//    }

    fun onCreated(f: () -> Unit) {
        getCallbacks<() -> Unit>(2).add(f)
    }

    fun onCreated() {
        getCallbacks<() -> Unit>(2).forEach { it() }
    }
}

interface OnDestroy : CanOnCallback {
    fun onDestroyed(f: () -> Unit) {
        getCallbacks<() -> Unit>(4).add(f)
    }

    fun onDestroyed() {
        getCallbacks<() -> Unit>(4).forEach { it() }
    }
}

interface OnA11yEvent : CanOnCallback {
    val a11yEventCallbacks: MutableList<(AccessibilityEvent) -> Unit>
        get() = getCallbacks(6)

    fun onA11yEvent(f: (AccessibilityEvent) -> Unit) {
        a11yEventCallbacks.add(f)
    }

    fun onA11yEvent(event: AccessibilityEvent) {
        a11yEventCallbacks.forEach { it(event) }
    }
}

interface OnA11yConnected : CanOnCallback {
    fun onA11yConnected(f: () -> Unit) {
        getCallbacks<() -> Unit>(8).add(f)
    }

    fun onA11yConnected() {
        getCallbacks<() -> Unit>(8).forEach { it() }
    }
}

interface OnChangeListen : CanOnCallback {
    fun onStartListened(f: () -> Unit) {
        getCallbacks<() -> Unit>(10).add(f)
    }

    fun onStartListened() {
        getCallbacks<() -> Unit>(10).forEach { it() }
    }

    fun onStopListened(f: () -> Unit) {
        getCallbacks<() -> Unit>(12).add(f)
    }

    fun onStopListened() {
        getCallbacks<() -> Unit>(12).forEach { it() }
    }
}

interface OnTileClick : CanOnCallback {
    fun onTileClicked(f: () -> Unit) {
        getCallbacks<() -> Unit>(14).add(f)
    }

    fun onTileClicked() {
        getCallbacks<() -> Unit>(14).forEach { it() }
    }
}

fun CanOnCallback.useAliveFlow(stateFlow: MutableStateFlow<Boolean>) {
    if (this is OnCreate) {
        onCreated { stateFlow.value = true }
    }
    if (this is OnDestroy) {
        onDestroyed { stateFlow.value = false }
    }
}

fun CanOnCallback.useLogLifecycle() {
    LogUtils.d("useLogLifecycle", this)
    if (this is OnCreate) {
        onCreated { LogUtils.d("onCreated", this) }
    }
    if (this is OnDestroy) {
        onDestroyed { LogUtils.d("onDestroyed", this) }
    }
    if (this is OnA11yConnected) {
        onA11yConnected { LogUtils.d("onA11yConnected", this) }
    }
    if (this is OnChangeListen) {
        onStartListened { LogUtils.d("onStartListened", this) }
        onStopListened { LogUtils.d("onStopListened", this) }
    }
    if (this is OnTileClick) {
        onTileClicked { LogUtils.d("onTileClicked", this) }
    }
}