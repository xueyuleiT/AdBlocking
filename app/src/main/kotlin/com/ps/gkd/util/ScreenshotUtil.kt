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
package com.ps.gkd.util

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import com.blankj.utilcode.util.ScreenUtils
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// https://github.com/npes87184/ScreenShareTile/blob/master/app/src/main/java/com/npes87184/screenshottile/ScreenshotService.kt

class ScreenshotUtil(
    private val context: Context,
    private val screenshotIntent: Intent
) {

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var mediaProjection: MediaProjection? = null


    private val mediaProjectionManager by lazy {
        context.getSystemService(
            Activity.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
    }

    private val width: Int
        get() = ScreenUtils.getScreenWidth()
    private val height: Int
        get() = ScreenUtils.getScreenHeight()
    private val dpi: Int
        get() = ScreenUtils.getScreenDensityDpi()

    fun destroy() {
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    //    TODO android13 上一半概率获取到全透明图片, android12 暂无此问题
    suspend fun execute() = suspendCoroutine { block ->
        imageReader = ImageReader.newInstance(
            width, height,
            PixelFormat.RGBA_8888, 2
        )
        if (mediaProjection == null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(
                RESULT_OK,
                screenshotIntent
            )
        }
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "screenshot",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler
        )
        var resumed = false
        imageReader!!.setOnImageAvailableListener({ reader ->
            if (resumed) return@setOnImageAvailableListener
            var image: Image? = null
            var bitmapWithStride: Bitmap? = null
            val bitmap: Bitmap?
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    bitmapWithStride = Bitmap.createBitmap(
                        rowStride / pixelStride,
                        height, Bitmap.Config.ARGB_8888
                    )
                    bitmapWithStride.copyPixelsFromBuffer(buffer)
                    bitmap = Bitmap.createBitmap(bitmapWithStride, 0, 0, width, height)
                    if (!bitmap.isEmptyBitmap()) {
                        imageReader?.setOnImageAvailableListener(null, null)
                        block.resume(bitmap)
                        resumed = true
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                imageReader?.setOnImageAvailableListener(null, null)
                block.resumeWithException(e)
            } finally {
                bitmapWithStride?.recycle()
                image?.close()
            }
        }, handler)
    }
}