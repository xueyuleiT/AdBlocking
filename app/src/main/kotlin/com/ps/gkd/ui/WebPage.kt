package com.ps.gkd.ui

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ps.gkd.MainActivity
import com.ps.gkd.data.WebParams
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun WebPage(webParams: WebParams) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val navController = LocalNavController.current
    val context = LocalContext.current as MainActivity
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            }, title = { Text(text = webParams.title) }, actions = {})
        }
    ) { contentPadding ->

            AndroidView(factory = {
                WebView(context).apply {
                    // 设置WebView的属性，如是否支持JavaScript等
                    settings.javaScriptEnabled = true
                    settings.allowContentAccess = true
                    settings.defaultTextEncodingName = "UTF-8"
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.allowFileAccess = true
                    settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NARROW_COLUMNS
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.useWideViewPort = true
                    settings.setSupportMultipleWindows(true)
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.loadWithOverviewMode = true

                    // 加载URL
                    loadUrl(webParams.url)
                }
            }, modifier = Modifier.fillMaxSize().padding(contentPadding))

    }
}
