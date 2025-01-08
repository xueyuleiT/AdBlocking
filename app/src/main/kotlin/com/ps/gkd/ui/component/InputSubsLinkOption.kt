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
package com.ps.gkd.ui.component

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import kotlinx.coroutines.flow.MutableStateFlow
import com.ps.gkd.util.openUri
import com.ps.gkd.util.subsItemsFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.toast
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class InputSubsLinkOption {
    private val showFlow = MutableStateFlow(false)
    private val valueFlow = MutableStateFlow("")
    private val initValueFlow = MutableStateFlow("")
    private var continuation: Continuation<String?>? = null

    private fun resume(value: String?) {
        showFlow.value = false
        valueFlow.value = ""
        initValueFlow.value = ""
        continuation?.resume(value)
        continuation = null
    }

    private fun submit() {
        val value = valueFlow.value
        if (!URLUtil.isNetworkUrl(value)) {
            toast(getSafeString(R.string.illegal_link))
            return
        }
        val initValue = initValueFlow.value
        if (initValue.isNotEmpty() && initValue == value) {
            toast(getSafeString(R.string.no_modification))
            resume(null)
            return
        }
        if (subsItemsFlow.value.any { it.updateUrl == value }) {
            toast(getSafeString(R.string.subscription_already_exists_with_the_same_link))
            return
        }
        resume(value)
    }

    private fun cancel() = resume(null)

    suspend fun getResult(initValue: String = ""): String? {
        initValueFlow.value = initValue
        valueFlow.value = initValue
        showFlow.value = true
        return suspendCoroutine {
            continuation = it
        }
    }

    @Composable
    fun ContentDialog() {
        val show by showFlow.collectAsState()
        if (show) {
            val value by valueFlow.collectAsState()
            val initValue by initValueFlow.collectAsState()
            AlertDialog(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = if (initValue.isNotEmpty()) getSafeString(R.string.edit_subscription) else getSafeString(R.string.add_subscription))
//                        IconButton(onClick = throttle {
//                            openUri("https://gkd.li?r=5")
//                        }) {
//                            Icon(
//                                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
//                                contentDescription = null,
//                            )
//                        }
                    }
                },
                text = {
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            valueFlow.value = it.trim()
                        },
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(text = getSafeString(R.string.please_enter_subscription_link))
                        },
                        isError = value.isNotEmpty() && !URLUtil.isNetworkUrl(value),
                    )
                },
                onDismissRequest = {
                    if (valueFlow.value.isEmpty()) {
                        cancel()
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = value.isNotEmpty(),
                        onClick = throttle(fn = {
                            submit()
                        }),
                    ) {
                        Text(text = getSafeString(R.string.confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::cancel) {
                        Text(text = getSafeString(R.string.cancel))
                    }
                },
            )
        }
    }
}

