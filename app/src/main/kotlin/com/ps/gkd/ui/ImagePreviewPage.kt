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
package com.ps.gkd.ui

import android.webkit.URLUtil
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.imageLoader
import com.ps.gkd.util.openUri
import com.ps.gkd.util.throttle

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun ImagePreviewPage(
    title: String? = null,
    uri: String? = null,
    uris: Array<String> = emptyArray(),
) {
    val navController = LocalNavController.current
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
    ) {
        val showUri = uri ?: if (uris.size == 1) uris.first() else null
        val state = rememberPagerState { uris.size }
        TopAppBar(
            modifier = Modifier
                .zIndex(1f)
                .fillMaxWidth(),
            navigationIcon = {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                    )
                }
            },
            title = {
                if (title != null) {
                    Text(text = title)
                }
            },
            actions = {
                val currentUri = showUri ?: uris.getOrNull(state.currentPage)
                if (currentUri != null && URLUtil.isNetworkUrl(currentUri)) {
                    IconButton(onClick = throttle(fn = {
                        openUri(currentUri)
                    })) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.3f)
            )
        )
        if (showUri != null) {
            UriImage(showUri)
        } else if (uris.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                HorizontalPager(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    pageContent = {
                        UriImage(uris[it])
                    }
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 150.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Text(
                        text = "${state.currentPage + 1}/${uris.size}",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun UriImage(uri: String) {
    val context = LocalContext.current
    val model = remember(uri) {
        ImageRequest.Builder(context).data(uri)
            .crossfade(DefaultDurationMillis)
            .build().apply {
                imageLoader.enqueue(this)
            }
    }
    val painter = rememberAsyncImagePainter(model)
    val state by painter.state.collectAsState()
    when (state) {
        AsyncImagePainter.State.Empty -> {}
        is AsyncImagePainter.State.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }

        is AsyncImagePainter.State.Success -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painter,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth,
                    alignment = Alignment.Center,
                )
            }
        }

        is AsyncImagePainter.State.Error -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    modifier = Modifier.clickable(onClick = throttle { painter.restart() }),
                    text = getSafeString(R.string.load_failed_retry),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
