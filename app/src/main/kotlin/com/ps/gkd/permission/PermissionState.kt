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
package com.ps.gkd.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.ps.gkd.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet
import com.ps.gkd.app
import com.ps.gkd.appScope
import com.ps.gkd.getSafeString
import com.ps.gkd.shizuku.shizukuCheckGranted
import com.ps.gkd.util.initOrResetAppInfoCache
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.mayQueryPkgNoAccessFlow
import com.ps.gkd.util.toast
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PermissionState(
    val check: () -> Boolean,
    val request: (suspend (context: Activity) -> PermissionResult)? = null,
    /**
     * show it when user doNotAskAgain
     */
    val reason: AuthReason? = null,
) {
    val stateFlow = MutableStateFlow(false)
    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }

    fun checkOrToast(): Boolean {
        updateAndGet()
        if (!stateFlow.value) {
            reason?.text?.let { toast(it) }
        }
        return stateFlow.value
    }
}

private fun checkSelfPermission(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(
        app,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}

private suspend fun asyncRequestPermission(
    context: Activity,
    permission: String,
): PermissionResult {
    if (XXPermissions.isGranted(context, permission)) {
        return PermissionResult.Granted
    }
    return suspendCoroutine { continuation ->
        XXPermissions.with(context)
            .unchecked()
            .permission(permission)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) {
                        continuation.resume(PermissionResult.Granted)
                    } else {
                        continuation.resume(PermissionResult.Denied(false))
                    }
                }

                override fun onDenied(permissions: MutableList<String>, doNotAskAgain: Boolean) {
                    continuation.resume(PermissionResult.Denied(doNotAskAgain))
                }
            })
    }
}

val notificationState by lazy {
    PermissionState(
        check = {
            XXPermissions.isGranted(app, Permission.POST_NOTIFICATIONS)
        },
        request = {
            asyncRequestPermission(it, Permission.POST_NOTIFICATIONS)
        },
        reason = AuthReason(
            text = getSafeString(R.string.notification_permission_required),
            confirm = {
                XXPermissions.startPermissionActivity(app, Permission.POST_NOTIFICATIONS)
            }
        ),
    )
}

val canQueryPkgState by lazy {
    PermissionState(
        check = {
            XXPermissions.isGranted(app, Permission.GET_INSTALLED_APPS)
        },
        request = {
            asyncRequestPermission(it, Permission.GET_INSTALLED_APPS)
        },
        reason = AuthReason(
            text = getSafeString(R.string.read_app_list_permission_required),
            confirm = {
                XXPermissions.startPermissionActivity(app, Permission.GET_INSTALLED_APPS)
            }
        ),
    )
}

val canDrawOverlaysState by lazy {
    PermissionState(
        check = {
            Settings.canDrawOverlays(app)
        },
        request = {
            // 无法直接请求悬浮窗权限
            if (!Settings.canDrawOverlays(app)) {
                PermissionResult.Denied(true)
            } else {
                PermissionResult.Granted
            }
        },
        reason = AuthReason(
            text = getSafeString(R.string.overlay_permission_required),
            confirm = {
                XXPermissions.startPermissionActivity(app, Manifest.permission.SYSTEM_ALERT_WINDOW)
            }
        ),
    )
}

val canWriteExternalStorage by lazy {
    PermissionState(
        check = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                true
            }
        },
        request = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                asyncRequestPermission(it, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                PermissionResult.Granted
            }
        },
        reason = AuthReason(
            text = getSafeString(R.string.write_external_storage_permission_required),
            confirm = {
                XXPermissions.startPermissionActivity(
                    app,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
        ),
    )
}

val writeSecureSettingsState by lazy {
    PermissionState(
        check = { checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) },
    )
}

val shizukuOkState by lazy {
    PermissionState(
        check = { shizukuCheckGranted() },
    )
}

fun startQueryPkgSettingActivity(context: Activity) {
    XXPermissions.startPermissionActivity(context, Permission.GET_INSTALLED_APPS)
}

fun updatePermissionState() {
    arrayOf(
        notificationState,
        canDrawOverlaysState,
        canWriteExternalStorage,
        writeSecureSettingsState,
        shizukuOkState,
    ).forEach { it.updateAndGet() }
    if (canQueryPkgState.stateFlow.value != canQueryPkgState.updateAndGet() || com.ps.gkd.util.mayQueryPkgNoAccessFlow.value) {
        appScope.launchTry {
            com.ps.gkd.util.initOrResetAppInfoCache()
        }
    }
}