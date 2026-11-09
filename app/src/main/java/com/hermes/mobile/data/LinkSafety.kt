package com.hermes.mobile.data

/**
 * Bağlantı güvenliği — V5-2 madde 7 (interaktif markdown).
 *
 * Sohbetten dışarı yalnız `http/https` çıkılır; `javascript:`, `data:`,
 * `file:`, `content:`, `intent:`, `vbscript:` ve şema gizleme denemeleri
 * açılmadan reddedilir. Saf fonksiyon — JVM testi görür.
 */
private val ALLOWED_SCHEMES = setOf("http", "https")

private val FORBIDDEN_PREFIXES = listOf(
    "javascript:",
    "data:",
    "file:",
    "content:",
    "intent:",
    "vbscript:",
    "jar:",
)

/**
 * Açılabilir bir dış URL döndürür; güvenli değilse null.
 *
 * Bilinçli olarak katı: şemasız, boşluklu ya da kontrol karakteri taşıyan
 * hiçbir girdi geçmez. Girdi kaynaklanıyor — model çıktısı olduğu için
 * "muhtemelen temiz" varsayımı yok.
 */
fun safeExternalUrl(raw: String?): String? {
    val url = raw?.trim().orEmpty()
    if (url.isEmpty()) return null
    val lower = url.lowercase()
    if (FORBIDDEN_PREFIXES.any { lower.startsWith(it) }) return null
    // "ja\nvascript:" gibi şema karıştırma denemeleri.
    if (url.any { it.isWhitespace() || it.isISOControl() }) return null
    val scheme = lower.substringBefore(':', "")
    if (scheme.isEmpty() || scheme !in ALLOWED_SCHEMES) return null
    // "http:" tek başına adres değil; bariz bozuk girdiyi de ele.
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null
    val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return null
    if (parsed.rawAuthority.isNullOrBlank() || parsed.rawUserInfo != null || '\\' in url) return null
    return url
}

enum class MediaKind { NONE, IMAGE, VIDEO, AUDIO, HTML }

private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif")
private val VIDEO_EXT = setOf("mp4", "webm", "mov", "m4v", "m3u8")

/** URL'nin taşıdığı medya türü (uzantıdan; sorgu/çapa soyulur). */
fun mediaKindOf(url: String?): MediaKind {
    val clean = url?.trim()?.substringBefore('?')?.substringBefore('#') ?: return MediaKind.NONE
    val ext = clean.substringAfterLast('.', "").lowercase()
    return when {
        ext in IMAGE_EXT -> MediaKind.IMAGE
        ext in VIDEO_EXT -> MediaKind.VIDEO
        ext in setOf("mp3", "wav", "ogg", "oga", "m4a", "aac", "flac", "opus") -> MediaKind.AUDIO
        ext in setOf("html", "htm") -> MediaKind.HTML
        else -> MediaKind.NONE
    }
}
