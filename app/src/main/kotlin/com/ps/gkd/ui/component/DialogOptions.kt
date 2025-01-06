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
package com.ps.gkd.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield
import com.ps.gkd.util.throttle
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class AlertDialogOptions(
    val title: @Composable (() -> Unit)? = null,
    val text: @Composable (() -> Unit)? = null,
    val onDismissRequest: (() -> Unit)? = null,
    val confirmButton: @Composable () -> Unit,
    val dismissButton: @Composable (() -> Unit)? = null,
)

private fun buildDialogOptions(
    title: @Composable (() -> Unit),
    text: @Composable (() -> Unit),
    confirmText: String,
    confirmAction: () -> Unit,
    dismissText: String? = null,
    dismissAction: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    error: Boolean = false,
): AlertDialogOptions {
    return AlertDialogOptions(
        title = title,
        text = text,
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = throttle(fn = confirmAction),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (error) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            ) {
                Text(text = confirmText)
            }
        },
        dismissButton = if (dismissText != null && dismissAction != null) {
            {
                TextButton(
                    onClick = throttle(fn = dismissAction),
                ) {
                    Text(text = dismissText)
                }
            }
        } else {
            null
        },
    )
}

@Composable
fun BuildDialog(stateFlow: MutableStateFlow<AlertDialogOptions?>) {
    val options by stateFlow.collectAsState()
    options?.let {
        AlertDialog(
            text = it.text,
            title = it.title,
            onDismissRequest = it.onDismissRequest ?: { stateFlow.value = null },
            confirmButton = it.confirmButton,
            dismissButton = it.dismissButton,
        )
    }
}

fun MutableStateFlow<AlertDialogOptions?>.updateDialogOptions(
    title: String,
    text: String? = null,
    textContent: (@Composable (() -> Unit))? = null,
    confirmText: String = DEFAULT_IK_TEXT,
    confirmAction: (() -> Unit)? = null,
    dismissText: String? = null,
    dismissAction: (() -> Unit)? = null,
    onDismissRequest: (() -> Unit)? = null,
    error: Boolean = false,
) {
    value = buildDialogOptions(
        title = { Text(text = title) },
        text = textContent ?: { Text(text = text ?: error("miss text")) },
        confirmText = confirmText,
        confirmAction = confirmAction ?: { value = null },
        dismissText = dismissText,
        dismissAction = dismissAction,
        onDismissRequest = onDismissRequest,
        error = error,
    )
}

private val DEFAULT_IK_TEXT = getSafeString(R.string.i_know)
private val DEFAULT_CONFIRM_TEXT = getSafeString(R.string.confirm)
private val DEFAULT_DISMISS_TEXT = getSafeString(R.string.cancel)

private suspend fun MutableStateFlow<AlertDialogOptions?>.getResult(
    title: String,
    text: String? = null,
    textContent: (@Composable (() -> Unit))? = null,
    confirmText: String = DEFAULT_CONFIRM_TEXT,
    dismissText: String = DEFAULT_DISMISS_TEXT,
    error: Boolean = false,
): Boolean {
    return suspendCoroutine { s ->
        updateDialogOptions(
            title = title,
            text = text,
            textContent = textContent,
            onDismissRequest = {},
            confirmText = confirmText,
            confirmAction = {
                s.resume(true)
                this.value = null
            },
            dismissText = dismissText,
            dismissAction = {
                s.resume(false)
                this.value = null
            },
            error = error,
        )
    }
}

suspend fun MutableStateFlow<AlertDialogOptions?>.waitResult(
    title: String,
    text: String? = null,
    textContent: (@Composable (() -> Unit))? = null,
    confirmText: String = DEFAULT_CONFIRM_TEXT,
    dismissText: String = DEFAULT_DISMISS_TEXT,
    error: Boolean = false,
) {
    val r = getResult(
        title = title,
        text = text,
        textContent = textContent,
        confirmText = confirmText,
        dismissText = dismissText,
        error = error,
    )
    if (!r) {
        coroutineContext[Job]?.cancel()
        yield()
    }
}