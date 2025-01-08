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

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.blankj.utilcode.util.LogUtils
import com.ps.gkd.META
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import li.songe.selector.MismatchExpressionTypeException
import li.songe.selector.MismatchOperatorTypeException
import li.songe.selector.MismatchParamTypeException
import li.songe.selector.Selector
import li.songe.selector.UnknownIdentifierException
import li.songe.selector.UnknownIdentifierMethodException
import li.songe.selector.UnknownIdentifierMethodParamsException
import li.songe.selector.UnknownMemberException
import li.songe.selector.UnknownMemberMethodException
import li.songe.selector.UnknownMemberMethodParamsException
import li.songe.selector.initDefaultTypeInfo

// 在主线程调用任意获取新节点或刷新节点的API会阻塞界面导致卡顿

// 某些应用耗时 554ms
val AccessibilityService.safeActiveWindow: AccessibilityNodeInfo?
    get() = try {
        // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
        rootInActiveWindow?.apply {
            // https://github.com/gkd-kit/gkd/issues/759
            setGeneratedTime()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }.apply {
        a11yContext.rootCache = this
    }

val AccessibilityService.safeActiveWindowAppId: String?
    get() = safeActiveWindow?.packageName?.toString()

// 某些应用耗时 300ms
val AccessibilityEvent.safeSource: AccessibilityNodeInfo?
    get() = if (className == null) {
        null // https://github.com/gkd-kit/gkd/issues/426 event.clear 已被系统调用
    } else {
        try {
            // 原因未知, 仍然报错 Cannot perform this action on a not sealed instance.
            source?.apply {
                setGeneratedTime()
            }
        } catch (_: Exception) {
            null
        }
    }

fun AccessibilityNodeInfo.getVid(): CharSequence? {
    val id = viewIdResourceName ?: return null
    val appId = packageName ?: return null
    if (id.startsWith(appId) && id.startsWith(":id/", appId.length)) {
        return id.subSequence(
            appId.length + ":id/".length,
            id.length
        )
    }
    return null
}

// https://github.com/gkd-kit/gkd/issues/115
// https://github.com/gkd-kit/gkd/issues/650
// 限制节点遍历的数量避免内存溢出
const val MAX_CHILD_SIZE = 512
const val MAX_DESCENDANTS_SIZE = 4096

private const val A11Y_NODE_TIME_KEY = "generatedTime"
fun AccessibilityNodeInfo.setGeneratedTime() {
    extras.putLong(A11Y_NODE_TIME_KEY, System.currentTimeMillis())
}

fun AccessibilityNodeInfo.isExpired(expiryMillis: Long): Boolean {
    val generatedTime = extras.getLong(A11Y_NODE_TIME_KEY, -1)
    if (generatedTime == -1L) {
        // https://github.com/gkd-kit/gkd/issues/759
        return true
    }
    return (System.currentTimeMillis() - generatedTime) > expiryMillis
}

private val typeInfo by lazy { initDefaultTypeInfo().globalType }

fun Selector.checkSelector(): String? {
    val error = checkType(typeInfo) ?: return null
    if (META.debuggable) {
        LogUtils.d(
            "Selector check error",
            source,
            error.message
        )
    }
    return when (error) {
        is MismatchExpressionTypeException -> getSafeString(R.string.expression_type_mismatch)+
        ":${error.exception.stringify()}"
        is MismatchOperatorTypeException -> getSafeString(R.string.operator_type_mismatch)+":${error.exception.stringify()}"
        is MismatchParamTypeException -> getSafeString(R.string.argument_type_mismatch)+":${error.call.stringify()}"
        is UnknownIdentifierException -> getSafeString(R.string.unknown_attribute)+":${error.value.stringify()}"
        is UnknownIdentifierMethodException -> getSafeString(R.string.unknown_method)+":${error.value.stringify()}"
        is UnknownMemberException -> getSafeString(R.string.unknown_attribute)+":${error.value.stringify()}"
        is UnknownMemberMethodException -> getSafeString(R.string.unknown_method)+":${error.value.stringify()}"
        is UnknownIdentifierMethodParamsException -> getSafeString(R.string.unknown_method_argument)+":${error.value.stringify()}"
        is UnknownMemberMethodParamsException -> getSafeString(R.string.unknown_method_argument)+":${error.value.stringify()}"
    }
}