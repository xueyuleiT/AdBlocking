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
import android.accessibilityservice.AccessibilityService.WINDOW_SERVICE
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ScreenUtils
import com.ps.gkd.META
import com.ps.gkd.R
import com.ps.gkd.app
import com.ps.gkd.appScope
import com.ps.gkd.data.ActionPerformer
import com.ps.gkd.data.ActionResult
import com.ps.gkd.data.AppRule
import com.ps.gkd.data.AttrInfo
import com.ps.gkd.data.GkdAction
import com.ps.gkd.data.ResolvedRule
import com.ps.gkd.data.RpcError
import com.ps.gkd.data.RuleStatus
import com.ps.gkd.debug.SnapshotExt
import com.ps.gkd.getSafeString
import com.ps.gkd.permission.shizukuOkState
import com.ps.gkd.shizuku.safeGetTopActivity
import com.ps.gkd.shizuku.serviceWrapperFlow
import com.ps.gkd.util.OnA11yConnected
import com.ps.gkd.util.OnA11yEvent
import com.ps.gkd.util.OnCreate
import com.ps.gkd.util.OnDestroy
import com.ps.gkd.util.UpdateTimeOption
import com.ps.gkd.util.checkSubsUpdate
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.map
import com.ps.gkd.util.showActionToast
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.toast
import com.ps.gkd.util.useLogLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import li.songe.selector.MatchOption
import li.songe.selector.Selector
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class A11yService : AccessibilityService(), OnCreate, OnA11yConnected, OnA11yEvent, OnDestroy {
    override fun onCreate() {
        super.onCreate()
        onCreated()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        onA11yConnected()
    }

    override val a11yEventCallbacks = mutableListOf<(AccessibilityEvent) -> Unit>()
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !event.isUseful()) return
        onA11yEvent(event)
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
    }

    val scope = CoroutineScope(Dispatchers.Default).apply { onDestroyed { cancel() } }

    init {
        useLogLifecycle()
        useRunningState()
        useAliveView()
        useCaptureVolume()
        onA11yEvent(::handleCaptureScreenshot)
        useAutoUpdateSubs()
        useRuleChangedLog()
        useAutoCheckShizuku()
        serviceWrapperFlow
        useMatchRule()
    }

    companion object {
        internal var weakInstance = WeakReference<A11yService>(null)
        val instance: A11yService?
            get() = weakInstance.get()
        val isRunning = MutableStateFlow(false)

        // AccessibilityInteractionClient.getInstanceForThread(threadId)
        val queryThread by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
        val eventThread by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }
        val actionThread by lazy { Executors.newSingleThreadExecutor().asCoroutineDispatcher() }

        fun execAction(gkdAction: GkdAction): ActionResult {
            val serviceVal = instance ?: throw RpcError(getSafeString(R.string.accessibility_not_running))
            val selector = Selector.parseOrNull(gkdAction.selector) ?: throw RpcError(
                getSafeString(
                    R.string.invalid_selector)
            )
            selector.checkSelector()?.let {
                throw RpcError(it)
            }
            val matchOption = MatchOption(
                quickFind = gkdAction.quickFind,
                fastQuery = gkdAction.fastQuery,
            )
            val cache = A11yContext(true)

            val targetNode = serviceVal.safeActiveWindow?.let {
                cache.querySelector(
                    it,
                    selector,
                    matchOption
                )
            } ?: throw RpcError(getSafeString(R.string.no_node_found))

            if (gkdAction.action == null) {
                // 仅查询
                return ActionResult(
                    action = null, result = true
                )
            }

            return ActionPerformer.getAction(gkdAction.action)
                .perform(serviceVal, targetNode, gkdAction.position)
        }


        suspend fun currentScreenshot(): Bitmap? = suspendCoroutine {
            if (instance == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                it.resume(null)
            } else {
                val callback = object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            it.resume(
                                Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer, screenshot.colorSpace
                                )
                            )
                        } finally {
                            screenshot.hardwareBuffer.close()
                        }
                    }

                    override fun onFailure(errorCode: Int) = it.resume(null)
                }
                instance!!.takeScreenshot(
                    Display.DEFAULT_DISPLAY, instance!!.application.mainExecutor, callback
                )
            }
        }
    }
}

private fun A11yService.useMatchRule() {
    val context = this

    var lastTriggerShizukuTime = 0L
    var lastContentEventTime = 0L
    val queryEvents = mutableListOf<A11yEvent>()
    var queryTaskJob: Job?


    fun newQueryTask(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
        delayRule: ResolvedRule? = null,
    ): Job = scope.launchTry(A11yService.queryThread) launchQuery@{
        queryTaskJob = coroutineContext[Job]
        fun checkFutureJob() {
            val t = System.currentTimeMillis()
            if (t - lastTriggerTime < 3000L || t - appChangeTime < 5000L) {
                scope.launch(A11yService.actionThread) {
                    delay(300)
                    if (queryTaskJob?.isActive != true) {
                        newQueryTask()
                    }
                }
            } else {
                if (getAndUpdateCurrentRules().currentRules.any { r -> r.checkForced() && r.status.let { s -> s == RuleStatus.StatusOk || s == RuleStatus.Status5 } }) {
                    scope.launch(A11yService.actionThread) {
                        delay(300)
                        if (queryTaskJob?.isActive != true) {
                            newQueryTask(byForced = true)
                        }
                    }
                }
            }
        }

        val newEvents = if (delayRule != null) {// 延迟规则不消耗事件
            null
        } else {
            synchronized(queryEvents) {
                // 不能在 synchronized 内获取节点, 否则将阻塞事件处理
                if (byEvent != null && queryEvents.isEmpty()) {
                    return@launchQuery checkFutureJob()
                }
                (if (queryEvents.size > 1) {
                    val hasDiffItem = queryEvents.any { e ->
                        queryEvents.any { e2 -> !e.sameAs(e2) }
                    }
                    if (hasDiffItem) {// 存在不同的事件节点, 全部丢弃使用 root 查询
                        if (META.debuggable) {
                            Log.d(
                                "queryEvents", getSafeString(R.string.discard_all_events)+ ":${queryEvents.size}"
                            )
                        }
                        null
                    } else {
                        // type,appId,className 一致, 需要在 synchronized 外验证是否是同一节点
                        arrayOf(
                            queryEvents[queryEvents.size - 2],
                            queryEvents.last(),
                        ).apply {
                        }
                    }
                } else if (queryEvents.size == 1) {
                    arrayOf(queryEvents.last())
                } else {
                    null
                }).apply {
                    queryEvents.clear()
                }
            }
        }
        val activityRule = getAndUpdateCurrentRules()
        if (activityRule.skipMatch) {
            // 如果当前应用没有规则/暂停匹配, 则不去调用获取事件节点避免阻塞
            return@launchQuery checkFutureJob()
        }
        var lastNode = if (newEvents == null || newEvents.size <= 1) {
            newEvents?.firstOrNull()?.safeSource
        } else {
            // 获取最后两个事件, 如果最后两个事件的节点不一致, 则丢弃
            // 相等则是同一个节点发出的连续事件, 常见于倒计时界面
            val lastNode = newEvents.last().safeSource
            if (lastNode == null || lastNode == newEvents[0].safeSource) {
                lastNode
            } else {
                null
            }
        }
        var lastNodeUsed = false
        if (!a11yContext.clearOldAppNodeCache()) {
            if (byEvent != null) { // 此为多数情况
                // 新事件到来时, 若缓存清理不及时会导致无法查询到节点
                a11yContext.clearNodeCache(lastNode)
            }
        }
        for (rule in activityRule.priorityRules) { // 规则数量有可能过多导致耗时过长
            if (delayRule != null && delayRule !== rule) continue
            val statusCode = rule.status
            if (statusCode == RuleStatus.Status3 && rule.matchDelayJob == null) {
                rule.matchDelayJob = scope.launch(A11yService.actionThread) {
                    delay(rule.matchDelay)
                    rule.matchDelayJob = null
                    newQueryTask(delayRule = rule)
                }
            }
            if (statusCode != RuleStatus.StatusOk) continue
            if (byForced && !rule.checkForced()) continue
            lastNode?.let { n ->
                val refreshOk = (!lastNodeUsed) || (try {
                    val e = n.refresh()
                    if (e) {
                        n.setGeneratedTime()
                    }
                    e
                } catch (_: Exception) {
                    false
                })
                lastNodeUsed = true
                if (!refreshOk) {
                    lastNode = null
                }
            }
            val nodeVal = (lastNode ?: safeActiveWindow) ?: continue
            val rightAppId = nodeVal.packageName?.toString() ?: break
            val matchApp = rule.matchActivity(
                rightAppId
            )
            if (topActivityFlow.value.appId != rightAppId || (!matchApp && rule is AppRule)) {
                scope.launch(A11yService.eventThread) {
                    if (topActivityFlow.value.appId != rightAppId) {
                        val shizukuTop = safeGetTopActivity()
                        if (shizukuTop?.appId == rightAppId) {
                            updateTopActivity(shizukuTop)
                        } else {
                            updateTopActivity(TopActivity(appId = rightAppId))
                        }
                        getAndUpdateCurrentRules()
                        scope.launch(A11yService.actionThread) {
                            delay(300)
                            if (queryTaskJob?.isActive != true) {
                                newQueryTask()
                            }
                        }
                    }
                }
                return@launchQuery checkFutureJob()
            }
            if (!matchApp) continue
            val target = a11yContext.queryRule(rule, nodeVal) ?: continue
            if (activityRule !== getAndUpdateCurrentRules()) break
            if (rule.checkDelay() && rule.actionDelayJob == null) {
                rule.actionDelayJob = scope.launch(A11yService.actionThread) {
                    delay(rule.actionDelay)
                    rule.actionDelayJob = null
                    newQueryTask(delayRule = rule)
                }
                continue
            }
            if (rule.status != RuleStatus.StatusOk) break
            val actionResult = rule.performAction(context, target)
            if (actionResult.result) {
                rule.trigger()
                scope.launch(A11yService.actionThread) {
                    delay(300)
                    if (queryTaskJob?.isActive != true) {
                        newQueryTask()
                    }
                }
                showActionToast(context)
                appScope.launchTry(Dispatchers.IO) {
                    insertClickLog(rule)
                    LogUtils.d(
                        rule.statusText(), AttrInfo.info2data(target, 0, 0), actionResult
                    )
                }
            }
        }
        checkFutureJob()
    }

    var lastGetAppIdTime = 0L
    var lastAppId: String? = null
    suspend fun getAppIdByCache(fixedEvent: A11yEvent): String? {
        if (fixedEvent.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && fixedEvent.appId == META.appId && !fixedEvent.className.endsWith(
                "Activity"
            )
        ) {
            // 剔除非法事件
            return lastAppId
        }
        val t = System.currentTimeMillis()
        if (t - lastGetAppIdTime > 50) {
            // 某些应用获取 safeActiveWindow 耗时长, 导致多个事件连续堆积堵塞, 无法检测到 appId 切换导致状态异常
            // https://github.com/gkd-kit/gkd/issues/622
            lastGetAppIdTime = t
            lastAppId = if (storeFlow.value.enableShizukuActivity) {
                withTimeoutOrNull(100) { safeActiveWindowAppId } ?: safeGetTopActivity()?.appId
            } else {
                null
            } ?: safeActiveWindowAppId
        }
        return lastAppId
    }

    val eventDeque = ArrayDeque<A11yEvent>()
    fun consumeEvent(
        headEvent: A11yEvent,
    ) = scope.launchTry(A11yService.eventThread) launchEvent@{
        val consumedEvents = synchronized(eventDeque) {
            if (eventDeque.firstOrNull() !== headEvent) return@launchEvent
            eventDeque.filter { it.sameAs(headEvent) }.apply {
                // 如果有多个连续的事件, 全部取出
                repeat(size) { eventDeque.removeFirst() }
            }
        }
        val a11yEvent = consumedEvents.last()
        val evAppId = a11yEvent.appId
        val evActivityId = a11yEvent.className
        val oldAppId = topActivityFlow.value.appId
        val rightAppId = if (oldAppId == evAppId) {
            oldAppId
        } else {
            getAppIdByCache(a11yEvent) ?: return@launchEvent
        }
        if (rightAppId == evAppId) {
            if (a11yEvent.type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                // tv.danmaku.bili, com.miui.home, com.miui.home.launcher.Launcher
                if (isActivity(evAppId, evActivityId)) {
                    updateTopActivity(
                        TopActivity(
                            evAppId, evActivityId, topActivityFlow.value.number + 1
                        )
                    )
                }
            } else {
                if (storeFlow.value.enableShizukuActivity && a11yEvent.time - lastTriggerShizukuTime > 300) {
                    val shizukuTop = safeGetTopActivity()
                    if (shizukuTop?.appId == rightAppId) {
                        if (shizukuTop.activityId == evActivityId) {
                            updateTopActivity(
                                TopActivity(
                                    evAppId, evActivityId, topActivityFlow.value.number + 1
                                )
                            )
                        }
                        updateTopActivity(shizukuTop)
                    }
                    lastTriggerShizukuTime = a11yEvent.time
                }
            }
        }
        if (rightAppId != topActivityFlow.value.appId) {
            // 从 锁屏,下拉通知栏 返回等情况, 应用不会发送事件, 但是系统组件会发送事件
            val shizukuTop = safeGetTopActivity()
            if (shizukuTop?.appId == rightAppId) {
                updateTopActivity(shizukuTop)
            } else {
                updateTopActivity(TopActivity(rightAppId))
            }
        }
        val activityRule = getAndUpdateCurrentRules()
        // 放在 evAppId != rightAppId 的前面使得 TopActivity 能借助 lastTopActivity 恢复
        if (evAppId != rightAppId || activityRule.skipConsumeEvent || !storeFlow.value.enableMatch) {
            return@launchEvent
        }

        synchronized(queryEvents) { queryEvents.addAll(consumedEvents) }
        a11yContext.interruptKey++
        newQueryTask(a11yEvent)
    }

    val skipAppId = "com.android.systemui"
    onA11yEvent { event ->
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && skipAppId == event.packageName.toString()) {
            return@onA11yEvent
        }
        // AccessibilityEvent 的 clear 方法会在后续时间被 某些系统 调用导致内部数据丢失, 导致异步子线程获取到的数据不一致
        val a11yEvent = event.toA11yEvent() ?: return@onA11yEvent
        if (a11yEvent.type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (a11yEvent.time - lastContentEventTime < 100 && a11yEvent.time - appChangeTime > 5000 && a11yEvent.time - lastTriggerTime > 3000) {
                return@onA11yEvent
            }
            lastContentEventTime = a11yEvent.time
        }
        if (META.debuggable) {
            Log.d(
                "A11yEvent",
                "type:${event.eventType},app:${event.packageName},cls:${event.className}"
            )
        }
        synchronized(eventDeque) { eventDeque.add(a11yEvent) }
        consumeEvent(a11yEvent)
    }
}

private fun A11yService.useRuleChangedLog() {
    scope.launch(Dispatchers.IO) {
        activityRuleFlow.debounce(300).collect {
            if (storeFlow.value.enableMatch && it.currentRules.isNotEmpty()) {
                LogUtils.d(it.topActivity, *it.currentRules.map { r ->
                    r.statusText()
                }.toTypedArray())
            }
        }
    }
}

private fun A11yService.useRunningState() {
    onCreated {
        A11yService.weakInstance = WeakReference(this)
        A11yService.isRunning.value = true
        if (!storeFlow.value.enableService) {
            // https://github.com/gkd-kit/gkd/issues/754
            storeFlow.update { it.copy(enableService = true) }
        }
        ManageService.autoStart()
    }
    onDestroyed {
        if (storeFlow.value.enableService) {
            storeFlow.update { it.copy(enableService = false) }
        }
        A11yService.weakInstance = WeakReference(null)
        A11yService.isRunning.value = false
    }
}

private fun A11yService.useAutoCheckShizuku() {
    var lastCheckShizukuTime = 0L
    onA11yEvent {
        // 借助无障碍轮询校验 shizuku 权限, 因为 shizuku 可能无故被关闭
        if ((storeFlow.value.enableShizukuActivity || storeFlow.value.enableShizukuClick) && it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && it.packageName == launcherAppId) {// 筛选降低判断频率
            val t = System.currentTimeMillis()
            if (t - lastCheckShizukuTime > 10 * 60_000L) {
                lastCheckShizukuTime = t
                appScope.launchTry(Dispatchers.IO) {
                    shizukuOkState.updateAndGet()
                }
            }
        }
    }
}

private fun A11yService.useAliveView() {
    val context = this
    var aliveView: View? = null
    val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    suspend fun removeA11View() {
        if (aliveView != null) {
            withContext(Dispatchers.Main) {
                wm.removeView(aliveView)
            }
            aliveView = null
        }
    }

    suspend fun addA11View() {
        removeA11View()
        val tempView = View(context)
        val lp = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags =
                flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = 1
            height = 1
            packageName = context.packageName
        }
        withContext(Dispatchers.Main) {
            try {
                // 在某些机型创建失败, 原因未知
                wm.addView(tempView, lp)
                aliveView = tempView
            } catch (e: Exception) {
                toast(getSafeString(R.string.create_accessibility_floating_window_failed))
                storeFlow.update { store ->
                    store.copy(enableAbFloatWindow = false)
                }
            }
        }
    }

    onA11yConnected {
        scope.launchTry {
            storeFlow.map(scope) { s -> s.enableAbFloatWindow }.collect {
                if (it) {
                    addA11View()
                } else {
                    removeA11View()
                }
            }
        }
    }
    onDestroyed {
        if (aliveView != null) {
            wm.removeView(aliveView)
        }
    }
}

private fun A11yService.useAutoUpdateSubs() {
    var lastUpdateSubsTime = System.currentTimeMillis() - 25000
    onA11yEvent {// 借助 无障碍事件 触发自动检测更新
        if (it.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && it.packageName == launcherAppId) {// 筛选降低判断频率
            val i = storeFlow.value.updateSubsInterval
            if (i <= 0) return@onA11yEvent
            val t = System.currentTimeMillis()
            if (t - lastUpdateSubsTime > i.coerceAtLeast(UpdateTimeOption.Everyday.value)) {
                lastUpdateSubsTime = t
                checkSubsUpdate()
            }
        }
    }
}

private const val volumeChangedAction = "android.media.VOLUME_CHANGED_ACTION"
private fun createVolumeReceiver() = object : BroadcastReceiver() {
    var lastTriggerTime = -1L
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == volumeChangedAction) {
            val t = System.currentTimeMillis()
            if (t - lastTriggerTime > 3000 && !ScreenUtils.isScreenLock()) {
                lastTriggerTime = t
                appScope.launchTry {
                    SnapshotExt.captureSnapshot()
                }
            }
        }
    }
}

private fun A11yService.useCaptureVolume() {
    var captureVolumeReceiver: BroadcastReceiver? = null
    onCreated {
        scope.launch {
            storeFlow.map(scope) { s -> s.captureVolumeChange }.collect {
                if (captureVolumeReceiver != null) {
                    unregisterReceiver(captureVolumeReceiver)
                }
                captureVolumeReceiver = if (it) {
                    createVolumeReceiver().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            registerReceiver(
                                this, IntentFilter(volumeChangedAction), Context.RECEIVER_EXPORTED
                            )
                        } else {
                            registerReceiver(this, IntentFilter(volumeChangedAction))
                        }
                    }
                } else {
                    null
                }
            }
        }
    }
    onDestroyed {
        if (captureVolumeReceiver != null) {
            unregisterReceiver(captureVolumeReceiver)
        }
    }
}

private const val interestedEvents =
    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

private fun AccessibilityEvent.isUseful(): Boolean {
    return packageName != null && className != null && eventType.and(interestedEvents) != 0
}

private val activityCache = object : LruCache<Pair<String, String>, Boolean>(128) {
    override fun create(key: Pair<String, String>): Boolean {
        return runCatching {
            app.packageManager.getActivityInfo(
                ComponentName(
                    key.first, key.second
                ), 0
            )
        }.getOrNull() != null
    }
}

private fun isActivity(
    appId: String,
    activityId: String,
): Boolean {
    if (appId == topActivityFlow.value.appId && activityId == topActivityFlow.value.activityId) return true
    val cacheKey = Pair(appId, activityId)
    return activityCache.get(cacheKey)
}

private fun handleCaptureScreenshot(event: AccessibilityEvent) {
    if (!storeFlow.value.captureScreenshot) return
    val appId = event.packageName!!
    val appCls = event.className!!
    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && !event.isFullScreen && appId.contentEquals(
            "com.miui.screenshot"
        ) && appCls.contentEquals(
            "android.widget.RelativeLayout"
        ) && event.text.firstOrNull()?.contentEquals("截屏缩略图") == true // [截屏缩略图, 截长屏, 发送]
    ) {
        LogUtils.d("captureScreenshot", event)
        appScope.launchTry {
            SnapshotExt.captureSnapshot(skipScreenshot = true)
        }
    }
}
