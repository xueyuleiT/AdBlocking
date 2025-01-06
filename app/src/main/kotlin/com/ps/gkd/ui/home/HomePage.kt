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
package com.ps.gkd.ui.home

import android.content.ComponentName
import android.content.Intent
import android.content.res.AssetManager
import android.os.Build
import android.service.quicksettings.TileService
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blankj.utilcode.util.LogUtils
import com.ps.gkd.MainActivity
import com.ps.gkd.OpenFileActivity
import com.ps.gkd.R
import com.ps.gkd.data.importData
import com.ps.gkd.debug.FloatingTileService
import com.ps.gkd.debug.HttpTileService
import com.ps.gkd.debug.SnapshotTileService
import com.ps.gkd.getSafeString
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.componentName
import com.ps.gkd.util.kv
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.toast
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.AdvancedPageDestination
import com.ramcosta.composedestinations.generated.destinations.SnapshotPageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.io.IOException


data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
)

fun loadJSONFromRaw(context: MainActivity): String {
    var json = ""
    try {
        val ips = context.resources.openRawResource(R.raw.gkd)
        val size = ips.available()
        val buffer = ByteArray(size)
        ips.read(buffer)
        ips.close()
        json = String(buffer, charset("UTF-8"))
    } catch (ex: IOException) {
        ex.printStackTrace()
        return json
    }
    return json
}


@Destination<RootGraph>(style = ProfileTransitions::class, start = true)
@Composable
fun HomePage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val vm = viewModel<HomeVm>()
    val tab by vm.tabFlow.collectAsState()

    val controlPage = useControlPage()
    val subsPage = useSubsManagePage()
    val appListPage = useAppListPage()
    val settingsPage = useSettingsPage()

    if (!kv.getBoolean("hasInitSubscription",false)) {
        kv.putBoolean("hasInitSubscription",true)
        vm.addSubs(loadJSONFromRaw(context),"https://pinshengtb.cn/json/gkd.json5")
    }

    val pages = arrayOf(controlPage, subsPage, appListPage, settingsPage)

    val currentPage = pages.find { p -> p.navItem.label == tab.label } ?: controlPage

    LaunchedEffect(key1 = null, block = {
        val intent = context.intent ?: return@LaunchedEffect
        context.intent = null
        LogUtils.d(intent)
        val uri = intent.data?.normalizeScheme()
        if (uri != null && uri.scheme == "gkd" && uri.host == "page") {
            delay(200)
            when (uri.path) {
                "/1" -> {
                    navController.toDestinationsNavigator().navigate(AdvancedPageDestination)
                }

                "/2" -> {
                    navController.toDestinationsNavigator().navigate(SnapshotPageDestination)
                }
            }
        } else if (uri != null && intent.getStringExtra("source") == OpenFileActivity::class.qualifiedName) {
            vm.viewModelScope.launchTry(Dispatchers.IO) {
                toast(getSafeString(R.string.loading_import))
                vm.tabFlow.value = subsPage.navItem
                importData(uri)
            }
        } else if (intent.action == TileService.ACTION_QS_TILE_PREFERENCES) {
            val qsTileCpt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME) as ComponentName?
            } ?: return@LaunchedEffect
            delay(200)
            if (qsTileCpt == HttpTileService::class.componentName || qsTileCpt == FloatingTileService::class.componentName) {
                navController.toDestinationsNavigator().navigate(AdvancedPageDestination)
            } else if (qsTileCpt == SnapshotTileService::class.componentName) {
                navController.toDestinationsNavigator().navigate(SnapshotPageDestination)
            }
        }
    })

    Scaffold(
        modifier = currentPage.modifier,
        topBar = currentPage.topBar,
        floatingActionButton = currentPage.floatingActionButton,
        bottomBar = {
            NavigationBar {
                pages.forEach { page ->
                    NavigationBarItem(
                        selected = tab.label == page.navItem.label,
                        modifier = Modifier,
                        onClick = {
                            vm.tabFlow.value = page.navItem
                        },
                        icon = {
                            Icon(
                                imageVector = page.navItem.icon,
                                contentDescription = page.navItem.label
                            )
                        },
                        label = {
                            Text(text = page.navItem.label)
                        })
                }
            }
        },
        content = currentPage.content
    )
}
