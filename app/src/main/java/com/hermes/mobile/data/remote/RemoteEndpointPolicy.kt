/*
 * privateRemoteAddress adapted from Mika RemoteAccess.privateAddress.
 * Copyright 2026 wangzan101. Licensed under the Apache License, Version 2.0.
 * Source: wangzan101/Mika, ebb0e44750d80aabb6b642c69908825d6b1a8107,
 * core/src/main/kotlin/com/wangzan101/mika/core/remote/RemoteAccess.kt.
 * Changes: named for Hermes; used as an outbound HTTP endpoint guard.
 * The full license and notice are in third_party/mika/.
 */
package com.hermes.mobile.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

internal fun privateRemoteAddress(address: InetAddress): Boolean {
    val v4 = when (address) {
        is Inet4Address -> address.address
        is Inet6Address -> address.address.let { bytes ->
            if ((0 until 10).all { bytes[it] == 0.toByte() } && bytes[10] == 0xff.toByte() && bytes[11] == 0xff.toByte())
                bytes.copyOfRange(12, 16) else null
        }
        else -> null
    }
    if (v4 != null) {
        val first = v4[0].toInt() and 0xff
        val second = v4[1].toInt() and 0xff
        return first == 127 || first == 10 || (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) || (first == 100 && second in 64..127) ||
            (first == 169 && second == 254)
    }
    if (address.isLoopbackAddress) return true
    val first = address.address[0].toInt() and 0xff
    val second = address.address[1].toInt() and 0xff
    return (first and 0xfe) == 0xfc || (first == 0xfe && (second and 0xc0) == 0x80)
}

/** Keep a reverse proxy path prefix; credentials and query strings never belong in this URL. */
fun normalizeRemoteEndpoint(raw: String): String {
    val url = raw.trim().toHttpUrlOrNull() ?: throw IllegalArgumentException("Geçerli bir HTTP/HTTPS adresi girin.")
    require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
        "Adreste kullanıcı bilgisi, sorgu veya çapa kullanmayın; tokenı ayrı alana girin."
    }
    require(url.encodedPathSegments.none { it.equals("..", true) || it.equals("%2e%2e", true) }) { "Geçersiz adres yolu." }
    if (!url.isHttps) {
        // Avoid both main-thread DNS and DNS rebinding. HTTP hosts must be literal private IPs or localhost.
        val host = url.host
        val address = when {
            host.equals("localhost", true) -> InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
            ':' in host || host.matches(Regex("[0-9.]+")) -> runCatching { InetAddress.getByName(host) }.getOrNull()
            else -> null
        }
        require(address != null && privateRemoteAddress(address)) {
            "HTTP için özel ağ IP adresi veya localhost kullanın; alan adları için HTTPS gerekir."
        }
    }
    return url.toString().trimEnd('/')
}
