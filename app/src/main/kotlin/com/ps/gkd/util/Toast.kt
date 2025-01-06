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

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blankj.utilcode.util.ScreenUtils
import com.hjq.toast.Toaster
import com.hjq.toast.style.WhiteToastStyle
import kotlinx.coroutines.Dispatchers
import com.ps.gkd.app
import com.ps.gkd.appScope


fun toast(text: CharSequence) {
    Toaster.show(text)
}

private val darkTheme: Boolean
    get() = storeFlow.value.enableDarkTheme ?: app.resources.configuration.let {
        it.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

private val toastYOffset: Int
    get() = (ScreenUtils.getScreenHeight() * 0.12f).toInt()

private val circleOutlineProvider by lazy {
    object : ViewOutlineProvider() {
        override fun getOutline(view: View?, outline: Outline?) {
            if (view != null && outline != null) {
                // 20.sp : line height, 12.dp : top/bottom padding
                outline.setRoundRect(
                    0,
                    0,
                    view.width,
                    view.height,
                    (12.dp.px * 2 + 20.sp.px) / 2f
                )
            }
        }
    }
}

private fun View.updateToastView() {
    setPaddingRelative(
        16.dp.px.toInt(),
        12.dp.px.toInt(),
        16.dp.px.toInt(),
        12.dp.px.toInt(),
    )
    layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    if (this is TextView) {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 14.sp.px)
        setTextColor(if (darkTheme) Color.WHITE else Color.BLACK)
    }
    background = GradientDrawable().apply {
        setColor(Color.parseColor(if (darkTheme) "#303030" else "#fafafa"))
    }
    outlineProvider = circleOutlineProvider
    clipToOutline = true
    elevation = 2.dp.px
    outlineProvider = circleOutlineProvider
    clipToOutline = true
}

fun setReactiveToastStyle() {
    Toaster.setStyle(object : WhiteToastStyle() {
        override fun getGravity() = Gravity.BOTTOM
        override fun getYOffset() = toastYOffset
        override fun getTranslationZ(context: Context?) = 0f
        override fun createView(context: Context?): View {
            return super.createView(context).apply {
                updateToastView()
            }
        }
    })
}

private var triggerTime = 0L
private const val triggerInterval = 2000L
fun showActionToast(context: AccessibilityService) {
    if (!storeFlow.value.toastWhenClick) return
    appScope.launchTry(Dispatchers.Main) {
        val t = System.currentTimeMillis()
        if (t - triggerTime > triggerInterval + 100) { // 100ms 保证二次显示的时候上一次已经完全消失
            triggerTime = t
            if (storeFlow.value.useSystemToast) {
                showSystemToast(storeFlow.value.clickToast)
            } else {
                showAccessibilityToast(
                    context,
                    storeFlow.value.clickToast
                )
            }
        }
    }
}

private var cacheToast: Toast? = null
private fun showSystemToast(message: CharSequence) {
    cacheToast?.cancel()
    cacheToast = Toast.makeText(app, message, Toast.LENGTH_SHORT).apply {
        show()
    }
}

// 1.使用 WeakReference<View> 在某些机型上导致无法取消
// 2.使用协程 delay + cacheView 也可能导致无法取消
// https://github.com/gkd-kit/gkd/issues/697
// https://github.com/gkd-kit/gkd/issues/698
private fun showAccessibilityToast(context: AccessibilityService, message: CharSequence) {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val textView = TextView(context).apply {
        text = message
        id = android.R.id.message
        gravity = Gravity.CENTER
        updateToastView()
    }

    val layoutParams = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = arrayOf(
            flags,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        ).reduce { acc, i -> acc or i }
        packageName = context.packageName
        width = WindowManager.LayoutParams.WRAP_CONTENT
        height = WindowManager.LayoutParams.WRAP_CONTENT
        gravity = Gravity.BOTTOM
        y = toastYOffset
        windowAnimations = android.R.style.Animation_Toast
    }
    wm.addView(textView, layoutParams)
    Handler(Looper.getMainLooper()).postDelayed({
        try {
            wm.removeViewImmediate(textView)
        } catch (_: Exception) {
        }
    }, triggerInterval)
}
