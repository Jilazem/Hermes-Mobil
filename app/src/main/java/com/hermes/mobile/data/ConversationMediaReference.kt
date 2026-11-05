package com.hermes.mobile.data

import java.net.URI
import java.net.URLEncoder

internal fun isInlineConversationImage(raw: String): Boolean {
    if (raw.length > 11_184_850) return false
    val comma = raw.indexOf(',')
    if (comma < 0 || raw.substring(0, comma).lowercase() !in setOf("data:image/png;base64", "data:image/jpeg;base64", "data:image/jpg;base64", "data:image/webp;base64", "data:image/gif;base64")) return false
    return raw.length > comma + 1 && raw.substring(comma + 1).all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "+/=" }
}

/** Server file paths are read through the existing authenticated, root-checked media endpoint. */
internal fun isServerMediaPath(raw: String): Boolean = raw.startsWith('/') && !raw.startsWith("//") &&
    raw.length <= 8192 && raw.none { it.isISOControl() || it == '\\' } &&
    raw.split('/').none { it == ".." } && mediaKindOf(raw) != MediaKind.NONE

internal fun serverMediaUrl(raw: String, server: ServerProfile): String? {
    val base = safeExternalUrl(server.activeUrl ?: server.normalizedUrl)?.trimEnd('/') ?: return null
    if (isServerMediaPath(raw)) return "$base/api/files/download?path=${URLEncoder.encode(raw, "UTF-8") }"
    val safe = safeExternalUrl(raw) ?: return null
    val origin = URI(base); val target = URI(safe)
    fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
    if (!origin.scheme.equals(target.scheme, true) || !origin.host.equals(target.host, true) || port(origin) != port(target)) return null
    if (target.path == "/api/media") {
        // /api/media returns a JSON data_url envelope, not file bytes.
        val path = target.rawQuery?.split('&')?.firstOrNull { it.startsWith("path=") }?.substringAfter('=') ?: return null
        val decoded = runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrNull() ?: return null
        return "$base/api/files/download?path=${URLEncoder.encode(decoded, "UTF-8") }"
    }
    return safe.takeIf { target.path in setOf("/api/files/download", "/api/files/stream") }
}

internal fun conversationMediaKind(raw: String): MediaKind {
    val direct = mediaKindOf(raw)
    if (direct != MediaKind.NONE) return direct
    val uri = runCatching { URI(raw) }.getOrNull() ?: return MediaKind.NONE
    if (uri.path !in setOf("/api/media", "/api/files/download", "/api/files/stream")) return MediaKind.NONE
    val path = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("path=") }?.substringAfter('=') ?: return MediaKind.NONE
    return mediaKindOf(runCatching { java.net.URLDecoder.decode(path, "UTF-8") }.getOrNull())
}
