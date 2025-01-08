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
package com.ps.gkd

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.annotation.StringRes
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.hjq.toast.Toaster
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import com.ps.gkd.data.selfAppInfo
import com.ps.gkd.debug.clearHttpSubs
import com.ps.gkd.notif.initChannel
import com.ps.gkd.permission.shizukuOkState
import com.ps.gkd.service.A11yService
import com.ps.gkd.shizuku.initShizuku
import com.ps.gkd.util.SafeR
import com.ps.gkd.util.componentName
import com.ps.gkd.util.initAppState
import com.ps.gkd.util.initFolder
import com.ps.gkd.util.initStore
import com.ps.gkd.util.initSubsState
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.setReactiveToastStyle
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku


val appScope by lazy { MainScope() }

private lateinit var innerApp: Application
val app: Application
    get() = innerApp

var mainActivity: MainActivity? = null

fun getSafeString(id: Int):String {
   return app.getString(id)
}


private val applicationInfo by lazy {
    app.packageManager.getApplicationInfo(
        app.packageName,
        PackageManager.GET_META_DATA
    )
}

data class AppMeta(
    val channel: String = applicationInfo.metaData.getString("channel")!!,
    val commitId: String = applicationInfo.metaData.getString("commitId")!!,
    val commitUrl: String = "https://github.com/gkd-kit/gkd/commit/${commitId}",
    val commitTime: Long = applicationInfo.metaData.getString("commitTime")!!.toLong(),
    val updateEnabled: Boolean = applicationInfo.metaData.getBoolean("updateEnabled"),
    val debuggable: Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    val versionCode: Int = selfAppInfo.versionCode.toInt(),
    val versionName: String = selfAppInfo.versionName!!,
    val appId: String = app.packageName!!,
    val appName: String = app.getString(SafeR.app_name)
)

val META by lazy { AppMeta() }

class App : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("L")
        }
    }

    override fun onCreate() {
        super.onCreate()
        innerApp = this
        Utils.init(this)

        val errorHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            LogUtils.d("UncaughtExceptionHandler", t, e)
            errorHandler?.uncaughtException(t, e)
        }
        MMKV.initialize(this)

        Toaster.init(this)
        setReactiveToastStyle()

        LogUtils.getConfig().apply {
            setConsoleSwitch(META.debuggable)
            saveDays = 7
            isLog2FileSwitch = true
        }
        LogUtils.d(
            "META",
            META,
        )
        initFolder()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MainActivity) {
                    mainActivity = activity
                }
                LogUtils.d("onActivityCreated", activity, savedInstanceState)
            }

            override fun onActivityStarted(activity: Activity) {
                LogUtils.d("onActivityStarted", activity)
            }

            override fun onActivityResumed(activity: Activity) {
                LogUtils.d("onActivityResumed", activity)
            }

            override fun onActivityPaused(activity: Activity) {
                LogUtils.d("onActivityPaused", activity)
            }

            override fun onActivityStopped(activity: Activity) {
                LogUtils.d("onActivityStopped", activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                LogUtils.d("onActivitySaveInstanceState", activity, outState)
            }

            override fun onActivityDestroyed(activity: Activity) {
                LogUtils.d("onActivityDestroyed", activity)
            }
        })
        app.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    a11yServiceEnabledFlow.value = getA11yServiceEnabled()
                }
            }
        )
        Shizuku.addBinderReceivedListener {
            appScope.launchTry(Dispatchers.IO) {
                shizukuOkState.updateAndGet()
            }
        }
        Shizuku.addBinderDeadListener {
            shizukuOkState.stateFlow.value = false
        }
        appScope.launchTry(Dispatchers.IO) {
            initStore()
            initAppState()
            initSubsState()
            initChannel()
            initShizuku()
            clearHttpSubs()
            syncFixState()
        }
    }
}

val a11yServiceEnabledFlow by lazy { MutableStateFlow(getA11yServiceEnabled()) }
private fun getA11yServiceEnabled(): Boolean {
    val value = try {
        Settings.Secure.getString(
            app.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    } catch (_: Exception) {
        null
    }
    if (value.isNullOrEmpty()) return false
    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(value)
    val name = A11yService::class.componentName
    while (colonSplitter.hasNext()) {
        if (ComponentName.unflattenFromString(colonSplitter.next()) == name) {
            return true
        }
    }
    return false
}
