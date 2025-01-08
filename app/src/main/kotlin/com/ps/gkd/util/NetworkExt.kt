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
package com.ps.gkd.util

import com.ps.gkd.R
import com.ps.gkd.getSafeString
import java.net.NetworkInterface
import java.net.ServerSocket

fun getIpAddressInLocalNetwork(): List<String> {
    val networkInterfaces = try {
        NetworkInterface.getNetworkInterfaces().asSequence()
    } catch (e: Exception) {
        // android.system.ErrnoException: getifaddrs failed: EACCES (Permission denied)
        toast(getSafeString(R.string.get_host_failed) + e.message)
        return emptyList()
    }
    val localAddresses = networkInterfaces.flatMap {
        it.inetAddresses.asSequence().filter { inetAddress ->
            inetAddress.isSiteLocalAddress && !(inetAddress.hostAddress?.contains(":")
                ?: false) && inetAddress.hostAddress != "127.0.0.1"
        }.map { inetAddress -> inetAddress.hostAddress }
    }
    return localAddresses.toList()
}


fun isPortAvailable(port: Int): Boolean {
    var serverSocket: ServerSocket? = null
    return try {
        serverSocket = ServerSocket(port)
        serverSocket.reuseAddress = true
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    } finally {
        serverSocket?.close()
    }
}