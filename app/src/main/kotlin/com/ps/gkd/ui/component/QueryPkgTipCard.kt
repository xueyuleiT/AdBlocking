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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.ps.gkd.MainActivity
import com.ps.gkd.R
import com.ps.gkd.getSafeString
import com.ps.gkd.permission.canQueryPkgState
import com.ps.gkd.permission.requiredPermission
import com.ps.gkd.permission.startQueryPkgSettingActivity
import com.ps.gkd.ui.style.EmptyHeight
import com.ps.gkd.util.launchAsFn
import com.ps.gkd.util.mayQueryPkgNoAccessFlow
import com.ps.gkd.util.throttle
import com.ps.gkd.util.updateAppMutex

@Composable
fun QueryPkgAuthCard() {
    val canQueryPkg by canQueryPkgState.stateFlow.collectAsState()
    val mayQueryPkgNoAccess by com.ps.gkd.util.mayQueryPkgNoAccessFlow.collectAsState()
    val appRefreshing by com.ps.gkd.util.updateAppMutex.state.collectAsState()
    if (appRefreshing) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(EmptyHeight / 2))
            CircularProgressIndicator()
        }
    } else if (!canQueryPkg || mayQueryPkgNoAccess) {
        val context = LocalContext.current as MainActivity
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (!canQueryPkg) getSafeString(R.string.to_display_all_applications_please_grant_permission_to_read_the_application_list) else getSafeString(R.string.detected_fewer_applications_than_expected_you_can_try_granting_permission_to_read_the_application_list_or_reauthorize_after_revoking_permission),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = throttle(fn = context.mainVm.viewModelScope.launchAsFn {
                if (!canQueryPkg) {
                    requiredPermission(context, canQueryPkgState)
                } else {
                    startQueryPkgSettingActivity(context)
                }
            })) {
                Text(text = getSafeString(R.string.request_permission))
            }
            Spacer(modifier = Modifier.height(EmptyHeight / 2))
        }
    }
}