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
package com.ps.gkd.debug

import android.app.Service
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import com.ps.gkd.R
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import com.ps.gkd.app
import com.ps.gkd.appScope
import com.ps.gkd.data.AppInfo
import com.ps.gkd.data.DeviceInfo
import com.ps.gkd.data.GkdAction
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.RpcError
import com.ps.gkd.data.SubsItem
import com.ps.gkd.data.deleteSubscription
import com.ps.gkd.data.selfAppInfo
import com.ps.gkd.db.DbSet
import com.ps.gkd.debug.SnapshotExt.captureSnapshot
import com.ps.gkd.getSafeString
import com.ps.gkd.notif.httpNotif
import com.ps.gkd.notif.notifyService
import com.ps.gkd.permission.notificationState
import com.ps.gkd.service.A11yService
import com.ps.gkd.util.LOCAL_HTTP_SUBS_ID
import com.ps.gkd.util.OnCreate
import com.ps.gkd.util.OnDestroy
import com.ps.gkd.util.SERVER_SCRIPT_URL
import com.ps.gkd.util.getIpAddressInLocalNetwork
import com.ps.gkd.util.isPortAvailable
import com.ps.gkd.util.keepNullJson
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.map
import com.ps.gkd.util.storeFlow
import com.ps.gkd.util.subsItemsFlow
import com.ps.gkd.util.toast
import com.ps.gkd.util.updateSubscription
import com.ps.gkd.util.useAliveFlow
import com.ps.gkd.util.useLogLifecycle
import java.io.File


class HttpService : Service(), OnCreate, OnDestroy {
    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        onCreated()
    }

    override fun onDestroy() {
        super.onDestroy()
        onDestroyed()
    }

    val scope = MainScope().apply { onDestroyed { cancel() } }

    private val httpServerPortFlow = storeFlow.map(scope) { s -> s.httpServerPort }

    init {
        useLogLifecycle()
        useAliveFlow(isRunning)

        onCreated { localNetworkIpsFlow.value = getIpAddressInLocalNetwork() }
        onDestroyed { localNetworkIpsFlow.value = emptyList() }

        onDestroyed {
            if (storeFlow.value.autoClearMemorySubs) {
                deleteSubscription(LOCAL_HTTP_SUBS_ID)
            }
            httpServerFlow.value = null
        }

        onCreated {
            httpNotif.notifyService(this)
            scope.launchTry(Dispatchers.IO) {
                httpServerPortFlow.collect { port ->
                    httpServerFlow.value?.stop()
                    httpServerFlow.value = null
                    if (!isPortAvailable(port)) {
                        toast(String.format(getSafeString(R.string.port_occupied),port))
                        stopSelf()
                        return@collect
                    }
                    httpServerFlow.value = try {
                        scope.createServer(port).apply { start() }
                    } catch (e: Exception) {
                        toast(getSafeString(R.string.http_service_start_failed)+":${e.stackTraceToString()}")
                        LogUtils.d(getSafeString(R.string.http_service_start_failed), e)
                        null
                    }
                    if (httpServerFlow.value == null) {
                        stopSelf()
                        return@collect
                    }
                    httpNotif.copy(text = getSafeString(R.string.http_service)+"-$port").notifyService(this@HttpService)
                }
            }
        }
    }

    companion object {
        val httpServerFlow = MutableStateFlow<ServerType?>(null)
        val isRunning = MutableStateFlow(false)
        val localNetworkIpsFlow = MutableStateFlow(emptyList<String>())
        fun stop() {
            app.stopService(Intent(app, HttpService::class.java))
        }

        fun start() {
            if (!notificationState.checkOrToast()) return
            app.startForegroundService(Intent(app, HttpService::class.java))
        }
    }
}

typealias ServerType = EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>


@Serializable
data class RpcOk(
    val message: String? = null,
)

@Serializable
data class ReqId(
    val id: Long,
)

@Serializable
data class ServerInfo(
    val device: DeviceInfo = DeviceInfo.instance,
    val gkdAppInfo: AppInfo = selfAppInfo
)

fun clearHttpSubs() {
    // 如果 app 被直接在任务列表划掉, HTTP订阅会没有清除, 所以在后续的第一次启动时清除
    if (HttpService.isRunning.value) return
    appScope.launchTry(Dispatchers.IO) {
        delay(1000)
        if (storeFlow.value.autoClearMemorySubs) {
            deleteSubscription(LOCAL_HTTP_SUBS_ID)
        }
    }
}

private val httpSubsItem by lazy {
    SubsItem(
        id = LOCAL_HTTP_SUBS_ID,
        order = -1,
        enableUpdate = false,
    )
}

private fun CoroutineScope.createServer(port: Int) = embeddedServer(CIO, port) {
    install(KtorCorsPlugin)
    install(KtorErrorPlugin)
    install(ContentNegotiation) { json(keepNullJson) }
    routing {
        get("/") { call.respondText(ContentType.Text.Html) { "<script type='module' src='$SERVER_SCRIPT_URL'></script>" } }
        route("/api") {
            // Deprecated
            get("/device") { call.respond(DeviceInfo.instance) }

            post("/getServerInfo") { call.respond(ServerInfo()) }

            // Deprecated
            get("/snapshot") {
                val id = call.request.queryParameters["id"]?.toLongOrNull()
                    ?: throw RpcError("miss id")
                val fp = File(SnapshotExt.getSnapshotPath(id))
                if (!fp.exists()) {
                    throw RpcError(getSafeString(R.string.snapshot_not_found))
                }
                call.respondFile(fp)
            }
            post("/getSnapshot") {
                val data = call.receive<ReqId>()
                val fp = File(SnapshotExt.getSnapshotPath(data.id))
                if (!fp.exists()) {
                    throw RpcError(getSafeString(R.string.snapshot_not_found))
                }
                call.respond(fp)
            }

            // Deprecated
            get("/screenshot") {
                val id = call.request.queryParameters["id"]?.toLongOrNull()
                    ?: throw RpcError("miss id")
                val fp = File(SnapshotExt.getScreenshotPath(id))
                if (!fp.exists()) {
                    throw RpcError(getSafeString(R.string.screenshot_not_found))
                }
                call.respondFile(fp)
            }
            post("/getScreenshot") {
                val data = call.receive<ReqId>()
                val fp = File(SnapshotExt.getScreenshotPath(data.id))
                if (!fp.exists()) {
                    throw RpcError(getSafeString(R.string.screenshot_not_found))
                }
                call.respondFile(fp)
            }

            // Deprecated
            get("/captureSnapshot") {
                call.respond(captureSnapshot())
            }
            post("/captureSnapshot") {
                call.respond(captureSnapshot())
            }

            // Deprecated
            get("/snapshots") {
                call.respond(DbSet.snapshotDao.query().first())
            }
            post("/getSnapshots") {
                call.respond(DbSet.snapshotDao.query().first())
            }

            post("/updateSubscription") {
                val subscription =
                    RawSubscription.parse(call.receiveText(), json5 = false)
                        .copy(
                            id = LOCAL_HTTP_SUBS_ID,
                            name = getSafeString(R.string.memory_subscription),
                            version = 0,
                            author = "@gkd-kit/inspect"
                        )
                updateSubscription(subscription)
                DbSet.subsItemDao.insert((subsItemsFlow.value.find { s -> s.id == httpSubsItem.id }
                    ?: httpSubsItem).copy(mtime = System.currentTimeMillis()))
                call.respond(RpcOk())
            }
            post("/execSelector") {
                if (!A11yService.isRunning.value) {
                    throw RpcError(getSafeString(R.string.accessibility_not_running))
                }
                val gkdAction = call.receive<GkdAction>()
                call.respond(A11yService.execAction(gkdAction))
            }
        }
    }
}
