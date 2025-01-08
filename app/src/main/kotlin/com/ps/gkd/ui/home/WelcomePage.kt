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
package com.ps.gkd.ui.home

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.MobileAds
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.mainActivity
import com.ps.gkd.util.AppOpenAdManager
import com.ps.gkd.util.HandleCallback
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.OnShowAdCompleteListener
import com.ps.gkd.util.ProfileTransitions
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.HomePageDestination
import com.ramcosta.composedestinations.utils.toDestinationsNavigator


@Destination<RootGraph>(style = ProfileTransitions::class, start = true)
@Composable
fun WelcomePage() {
    val context = LocalContext.current as MainActivity
    val navController = LocalNavController.current
    val enableDarkTheme by context.mainVm.enableDarkThemeFlow.collectAsState()
    val darkTheme = enableDarkTheme ?: isSystemInDarkTheme()
    val mAppOpenAdManager by remember {
        mutableStateOf(AppOpenAdManager())
    }

    fun initSdk(handleCallBack: HandleCallback) {
        Thread(Runnable { MobileAds.initialize(context) { p0 ->
            Log.d("ad", p0.toString())
            context.runOnUiThread {
                mAppOpenAdManager.loadAd(context,handleCallBack)
            }
        } }).start()
    }

    initSdk(object :HandleCallback{
        override fun onHandle(s: String) {
            if (s == "ok") {
                mAppOpenAdManager.showAdIfAvailable(mainActivity!!,object :
                    OnShowAdCompleteListener {
                    override fun onShowAdComplete() {
                        navController.popBackStack()
                        navController.toDestinationsNavigator().navigate(HomePageDestination)
                    }
                })
            }
        }

    })


    Box(modifier = Modifier.fillMaxWidth().fillMaxHeight().wrapContentSize(Alignment.Center).background(color = if(darkTheme) Color(context.getColor(R.color.better_black)) else Color(context.getColor(R.color.better_white)))) {
        Image(bitmap = BitmapFactory.decodeResource(context.resources, if(!darkTheme) R.mipmap.ad_blocking_black else R.mipmap.ad_blocking_white).asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.width(120.dp).height(120.dp).clip(RoundedCornerShape(10.dp)),
            alignment = Alignment.Center)
    }

}
