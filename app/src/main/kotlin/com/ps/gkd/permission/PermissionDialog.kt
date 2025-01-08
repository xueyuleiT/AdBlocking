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
package com.ps.gkd.permission

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.yield
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import kotlin.coroutines.coroutineContext

data class AuthReason(
    val text: String,
    val confirm: () -> Unit
)

@Composable
fun AuthDialog(authReasonFlow: MutableStateFlow<AuthReason?>) {
    val authAction = authReasonFlow.collectAsState().value
    if (authAction != null) {
        AlertDialog(
            title = {
                Text(text = getSafeString(R.string.permission_request))
            },
            text = {
                Text(text = authAction.text)
            },
            onDismissRequest = { authReasonFlow.value = null },
            confirmButton = {
                TextButton(onClick = {
                    authReasonFlow.value = null
                    authAction.confirm()
                }) {
                    Text(text = getSafeString(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { authReasonFlow.value = null }) {
                    Text(text = getSafeString(R.string.cancel))
                }
            }
        )
    }
}

sealed class PermissionResult {
    data object Granted : PermissionResult()
    data class Denied(val doNotAskAgain: Boolean) : PermissionResult()
}

private suspend fun checkOrRequestPermission(
    context: MainActivity,
    permissionState: PermissionState
): Boolean {
    if (!permissionState.updateAndGet()) {
        val result = permissionState.request?.let { it(context) } ?: return false
        if (result is PermissionResult.Denied) {
            if (result.doNotAskAgain) {
                context.mainVm.authReasonFlow.value = permissionState.reason
            }
            return false
        }
    }
    return true
}

suspend fun requiredPermission(
    context: MainActivity,
    permissionState: PermissionState
) {
    val r = checkOrRequestPermission(context, permissionState)
    if (!r) {
        coroutineContext[Job]?.cancel()
        yield()
    }
}
