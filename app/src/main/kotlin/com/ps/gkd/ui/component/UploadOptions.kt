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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.ClipboardUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import com.ps.gkd.MainViewModel
import com.ps.gkd.R
import com.ps.gkd.data.GithubPoliciesAsset
import com.ps.gkd.getSafeString
import com.ps.gkd.util.GithubCookieException
import com.ps.gkd.util.LoadStatus
import com.ps.gkd.util.launchTry
import com.ps.gkd.util.privacyStoreFlow
import com.ps.gkd.util.toast
import com.ps.gkd.util.uploadFileToGithub
import java.io.File

class UploadOptions(
    private val mainVm: MainViewModel,
) {
    private val statusFlow = MutableStateFlow<LoadStatus<GithubPoliciesAsset>?>(null)
    private var job: Job? = null
    private fun buildTask(
        cookie: String,
        getFile: suspend () -> File,
        onSuccessResult: ((GithubPoliciesAsset) -> Unit)?
    ) = mainVm.viewModelScope.launchTry(Dispatchers.IO) {
        statusFlow.value = LoadStatus.Loading()
        try {
            val policiesAsset = uploadFileToGithub(cookie, getFile()) {
                if (statusFlow.value is LoadStatus.Loading) {
                    statusFlow.value = LoadStatus.Loading(it)
                }
            }
            statusFlow.value = LoadStatus.Success(policiesAsset)
            onSuccessResult?.invoke(policiesAsset)
        } catch (e: Exception) {
            statusFlow.value = LoadStatus.Failure(e)
        } finally {
            job = null
        }
    }


    private var showHref: (GithubPoliciesAsset) -> String = { it.shortHref }
    fun startTask(
        getFile: suspend () -> File,
        showHref: (GithubPoliciesAsset) -> String = { it.shortHref },
        onSuccessResult: ((GithubPoliciesAsset) -> Unit)? = null
    ) {
        val cookie = privacyStoreFlow.value.githubCookie
        if (cookie.isNullOrBlank()) {
            toast(getSafeString(R.string.please_set_cookie_before_uploading))
            mainVm.showEditCookieDlgFlow.value = true
            return
        }
        if (job != null || statusFlow.value is LoadStatus.Loading) {
            return
        }
        this.showHref = showHref
        job = buildTask(cookie, getFile, onSuccessResult)
    }

    private fun stopTask() {
        if (statusFlow.value is LoadStatus.Loading && job != null) {
            job?.cancel(getSafeString(R.string.you_cancelled_the_upload))
            job = null
        }
    }


    @Composable
    fun ShowDialog() {
        when (val status = statusFlow.collectAsState().value) {
            null -> {}
            is LoadStatus.Loading -> {
                AlertDialog(
                    title = { Text(text = getSafeString(R.string.uploading_file)) },
                    text = {
                        LinearProgressIndicator(
                            progress = { status.progress },
                        )
                    },
                    onDismissRequest = { },
                    confirmButton = {
                        TextButton(onClick = {
                            stopTask()
                        }) {
                            Text(text = getSafeString(R.string.cancel_upload))
                        }
                    },
                )
            }

            is LoadStatus.Success -> {
                val href = showHref(status.result)
                AlertDialog(title = { Text(text = getSafeString(R.string.upload_complete)) }, text = {
                    Text(text = href)
                }, onDismissRequest = {}, dismissButton = {
                    TextButton(onClick = {
                        statusFlow.value = null
                    }) {
                        Text(text = getSafeString(R.string.close))
                    }
                }, confirmButton = {
                    TextButton(onClick = {
                        ClipboardUtils.copyText(href)
                        toast(getSafeString(R.string.copy_success))
                        statusFlow.value = null
                    }) {
                        Text(text = getSafeString(R.string.copy_and_close))
                    }
                })
            }

            is LoadStatus.Failure -> {
                AlertDialog(
                    title = { Text(text = getSafeString(R.string.upload_failed)) },
                    text = {
                        Text(text = status.exception.let {
                            it.message ?: it.toString()
                        })
                    },
                    onDismissRequest = { statusFlow.value = null },
                    dismissButton = if (status.exception is GithubCookieException) ({
                        TextButton(onClick = {
                            statusFlow.value = null
                            mainVm.showEditCookieDlgFlow.value = true
                        }) {
                            Text(text = getSafeString(R.string.change_cookie))
                        }
                    }) else {
                        null
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            statusFlow.value = null
                        }) {
                            Text(text = getSafeString(R.string.close))
                        }
                    },
                )
            }
        }
    }
}
