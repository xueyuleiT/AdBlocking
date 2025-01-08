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
package com.ps.gkd.shizuku


import android.app.ActivityManager
import android.app.IActivityTaskManager
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.view.Display
import com.blankj.utilcode.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import com.ps.gkd.META
import com.ps.gkd.R
import com.ps.gkd.appScope
import com.ps.gkd.data.DeviceInfo
import com.ps.gkd.getSafeString
import com.ps.gkd.permission.shizukuOkState
import com.ps.gkd.service.TopActivity
import com.ps.gkd.shizuku.IUserService
import com.ps.gkd.util.componentName
import com.ps.gkd.util.json
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.typeOf

fun shizukuCheckGranted(): Boolean {
    val granted = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
    if (!granted) return false
    if (storeFlow.value.enableShizukuActivity) {
        return safeGetTopActivity() != null || shizukuCheckActivity()
    }
    return true
}

fun shizukuCheckActivity(): Boolean {
    return (try {
        newActivityTaskManager()?.safeGetTasks(log = false)?.isNotEmpty() == true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    })
}

/**
 * https://github.com/gkd-kit/gkd/issues/44
 */

// Context.ACTIVITY_TASK_SERVICE
private const val ACTIVITY_TASK_SERVICE = "activity_task"

private fun newActivityTaskManager(): IActivityTaskManager? {
    val service = SystemServiceHelper.getSystemService(ACTIVITY_TASK_SERVICE)
    if (service == null) {
        return null
    }
    return service.let(::ShizukuBinderWrapper).let(IActivityTaskManager.Stub::asInterface)
}

private val shizukuActivityUsedFlow by lazy {
    combine(shizukuOkState.stateFlow, storeFlow) { shizukuOk, store ->
        shizukuOk && store.enableShizukuActivity
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}
private val taskManagerFlow by lazy<StateFlow<IActivityTaskManager?>> {
    val stateFlow = MutableStateFlow<IActivityTaskManager?>(null)
    appScope.launch(Dispatchers.IO) {
        shizukuActivityUsedFlow.collect {
            stateFlow.value = if (it) newActivityTaskManager() else null
        }
    }
    stateFlow
}

/**
 * -1: invalid fc
 * 1: (int) -> List<Task>
 * 3: (int, boolean, boolean) -> List<Task>
 * 4: (int, boolean, boolean, int) -> List<Task>
 */
private var getTasksFcType: Int? = null
private fun IActivityTaskManager.safeGetTasks(log: Boolean = true): List<ActivityManager.RunningTaskInfo>? {
    if (getTasksFcType == null) {
        val fcs = this::class.declaredMemberFunctions
        val parameters = fcs.find { d -> d.name == "getTasks" }?.parameters
        if (parameters != null) {
            if (parameters.size == 5 && parameters[1].type == typeOf<Int>() && parameters[2].type == typeOf<Boolean>() && parameters[3].type == typeOf<Boolean>() && parameters[4].type == typeOf<Int>()) {
                getTasksFcType = 4
            } else if (parameters.size == 4 && parameters[1].type == typeOf<Int>() && parameters[2].type == typeOf<Boolean>() && parameters[3].type == typeOf<Boolean>()) {
                getTasksFcType = 3
            } else if (parameters.size == 2 && parameters[1].type == typeOf<Int>()) {
                getTasksFcType = 1
            } else {
                getTasksFcType = -1
                LogUtils.d(DeviceInfo.instance)
                LogUtils.d(fcs)
                toast(getSafeString(R.string.shizuku_get_method_signature_error))
            }
        }
    }
    return try {
        // https://bugly.qq.com/v2/crash-reporting/crashes/d0ce46b353/106137?pid=1
        // binder haven't been received
        when (getTasksFcType) {
            1 -> this.getTasks(1)
            3 -> this.getTasks(1, false, true)
            4 -> this.getTasks(1, false, true, Display.DEFAULT_DISPLAY)
            else -> null
        }
    } catch (e: Exception) {
        if (log) {
            LogUtils.d(e)
        }
        null
    }
}

fun safeGetTopActivity(): TopActivity? {
    if (!shizukuActivityUsedFlow.value) return null
    try {
        // 避免直接访问方法校验 android.app.IActivityTaskManager 类型
        // 否则某些机型会报错 java.lang.ClassNotFoundException:Didn't find class "android.app.IActivityTaskManager" on path: DexPathList
        val taskManager = taskManagerFlow.value ?: return null
        val top = taskManager.safeGetTasks()?.lastOrNull()?.topActivity ?: return null
        return TopActivity(appId = top.packageName, activityId = top.className)
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

//fun newPackageManager(): IPackageManager? {
//    val service = SystemServiceHelper.getSystemService("package")
//    if (service == null) {
//        LogUtils.d("shizuku 无法获取 package")
//        return null
//    }
//    return service.let(::ShizukuBinderWrapper).let(IPackageManager.Stub::asInterface)
//}

private fun unbindUserService(serviceArgs: Shizuku.UserServiceArgs, connection: ServiceConnection) {
    LogUtils.d("unbindUserService", serviceArgs)
    // https://github.com/RikkaApps/Shizuku-API/blob/master/server-shared/src/main/java/rikka/shizuku/server/UserServiceManager.java#L62
    try {
        Shizuku.unbindUserService(serviceArgs, connection, false)
        Shizuku.unbindUserService(serviceArgs, connection, true)
    } catch (e: Exception) {
        // binder haven't been received
        e.printStackTrace()
        LogUtils.d(e)
    }
}

data class UserServiceWrapper(
    val userService: IUserService,
    val connection: ServiceConnection,
    val serviceArgs: Shizuku.UserServiceArgs
) {
    fun destroy() {
        unbindUserService(serviceArgs, connection)
    }

    fun execCommandForResult(command: String): Boolean? {
        return userService.execCommandForResult(command)
    }
}

private val bindServiceMutex by lazy { Mutex() }
private suspend fun buildServiceWrapper(): UserServiceWrapper? {
    val serviceArgs = Shizuku
        .UserServiceArgs(UserService::class.componentName)
        .daemon(false)
        .processNameSuffix("shizuku-user-service")
        .debuggable(META.debuggable)
        .version(META.versionCode)
    LogUtils.d("buildServiceWrapper", serviceArgs)
    var resumeCallback: ((UserServiceWrapper) -> Unit)? = null
    val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, binder: IBinder?) {
            LogUtils.d("onServiceConnected", componentName)
            resumeCallback ?: return
            if (binder?.pingBinder() == true) {
                resumeCallback?.invoke(
                    UserServiceWrapper(
                        IUserService.Stub.asInterface(binder),
                        this,
                        serviceArgs
                    )
                )
                resumeCallback = null
            } else {
                LogUtils.d("invalid binder for $componentName received")
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            LogUtils.d("onServiceDisconnected", componentName)
        }
    }
    bindServiceMutex.withLock {
        return withTimeoutOrNull(3000) {
            suspendCoroutine { continuation ->
                resumeCallback = { continuation.resume(it) }
                Shizuku.bindUserService(serviceArgs, connection)
            }
        }.apply {
            if (this == null) {
                toast(getSafeString(R.string.shizuku_bind_service_timeout_failed))
                unbindUserService(serviceArgs, connection)
            }
        }
    }
}

private val shizukuServiceUsedFlow by lazy {
    combine(shizukuOkState.stateFlow, storeFlow) { shizukuOk, store ->
        shizukuOk && store.enableShizukuClick
    }.stateIn(appScope, SharingStarted.Eagerly, false)
}

val serviceWrapperFlow by lazy {
    val stateFlow = MutableStateFlow<UserServiceWrapper?>(null)
    appScope.launch(Dispatchers.IO) {
        shizukuServiceUsedFlow.collect {
            if (it) {
                stateFlow.update { it ?: buildServiceWrapper() }
            } else {
                stateFlow.update { it?.destroy(); null }
            }
        }
    }
    stateFlow
}

suspend fun shizukuCheckUserService(): Boolean {
    return try {
        execCommandForResult("input tap 0 0")
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

suspend fun execCommandForResult(command: String): Boolean {
    return serviceWrapperFlow.updateAndGet {
        it ?: buildServiceWrapper()
    }?.execCommandForResult(command) == true
}

// 在 大麦 https://i.gkd.li/i/14605104 上测试产生如下 3 种情况
// 1. 点击不生效: 使用传统无障碍屏幕点击, 此种点击可被 大麦 通过 View.setAccessibilityDelegate 屏蔽
// 2. 点击概率生效: 使用 Shizuku 获取到的 InputManager.injectInputEvent 发出点击, 概率失效/生效, 原因未知
// 3. 点击生效: 使用 Shizuku 获取到的 shell input tap x y 发出点击 by safeTap, 暂未找到屏蔽方案
fun safeTap(x: Float, y: Float): Boolean? {
    return serviceWrapperFlow.value?.execCommandForResult("input tap $x $y")
}

private fun IUserService.execCommandForResult(command: String): Boolean? {
    return try {
        val result = execCommand(command)
        if (result != null) {
            json.decodeFromString<CommandResult>(result).code == 0
        } else {
            null
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun initShizuku() {
    serviceWrapperFlow.value
}
