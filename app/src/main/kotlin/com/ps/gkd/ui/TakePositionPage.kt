package com.ps.gkd.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ps.gkd.R
import com.ps.gkd.data.ComplexSnapshot
import com.ps.gkd.data.RawSubscription
import com.ps.gkd.data.TakePositionEvent
import com.ps.gkd.debug.SnapshotExt.getScreenshotPath
import com.ps.gkd.getSafeString
import com.ps.gkd.mainActivity
import com.ps.gkd.ui.home.HomeVm
import com.ps.gkd.util.LocalNavController
import com.ps.gkd.util.ProfileTransitions
import com.ps.gkd.util.imageLoader
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import kotlinx.coroutines.launch

@Destination<RootGraph>(style = ProfileTransitions::class)
@Composable
fun TakePositionPage(snapshot: ComplexSnapshot) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val navController = LocalNavController.current
    var showPositionConfirmActionPrompt by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(true) }

    val takePositionEvent by remember { mutableStateOf(TakePositionEvent(snapshot.id,RawSubscription.Position(null,null,null,null))) }

    val scope = rememberCoroutineScope()

    if (showPrompt) {
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = { Text(text = getSafeString(R.string.prompt)) },
            text = {
                Text(text = getSafeString(R.string.get_ad_position))
            },
            confirmButton = {
                TextButton(onClick = {
                    showPrompt = false
                }) {
                    Text(text = getSafeString(R.string.i_know))
                }
            }
        )
    }


    if (showPositionConfirmActionPrompt) {
        AlertDialog(
            onDismissRequest = { showPositionConfirmActionPrompt = false },
            title = { Text(text = getSafeString(R.string.prompt)) },
            text = {
                Text(text = getSafeString(R.string.confirm_close_ad))
            },
            confirmButton = {

                    TextButton(onClick = {
                        showPositionConfirmActionPrompt = false
                        scope.launch {
                            mainActivity!!.snapshot.emit(takePositionEvent)
                        }
                        navController.popBackStack()
                    }) {
                        Text(text = getSafeString(R.string.confirm))
                    }
            },
            dismissButton = {
                TextButton(onClick = { showPositionConfirmActionPrompt = false }) {
                    Text(text = getSafeString(R.string.back))
                }
            }
        )
    }



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
            }, title = { Text(text = getSafeString(R.string.click_close_ad_button)) }, actions = {})
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            val bmp = BitmapFactory.decodeFile(getScreenshotPath(snapshot.id))
            Image(bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                            offset: Offset ->
                        takePositionEvent.position = RawSubscription.Position("width * ${offset.x * 1f / bmp.width}","height * ${offset.y * 1f / bmp.height}",null,null)
                        showPositionConfirmActionPrompt = true
                    })
                })
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
                    modifier = Modifier.fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                offset: Offset ->

                                offset.x
                                offset.y
                            })
                        }
                        .clickable(
                            onClick = {

                            }
                        ),
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