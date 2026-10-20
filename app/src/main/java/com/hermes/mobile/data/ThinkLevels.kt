package com.hermes.mobile.data

/**
 * Düşünme (think) kademeleri — V5-2 madde 8.
 *
 * Gateway `/reasoning` ile yazılan kademeler; [com.hermes.mobile.ui.ReasoningSheet]
 * ile aynı sıra ve etiketler.
 */
val THINK_LEVELS: List<String> = listOf("none", "low", "medium", "high", "xhigh")

/**
 * Modelin desteklediği kademeler için sunucuda bir "yetenek" ucu yok; bu
 * tablo **sezgisel** bir sınıflandırma. Arayüzde "tahmini" olarak etiketlenir.
 * Bilinmeyen modelde TÜM kademeler açık bırakılır: yanlış kısıtlama,
 * gereksiz izin vermekten daha kötüdür (kullanıcıyı kilitler).
 */
private val NO_THINK_MARKERS = listOf("embedding", "reranker", "-asr-", "-ocr", "whisper", "tts")

private val FULL_THINK_MARKERS = listOf(
    "reasoning", "think", "o1", "o3", "o4", "deepseek-r", "-r1", "glm", "qwen3",
    "mimo", "gemini-2.5", "gemini-3", "claude", "gpt-5", "sonnet", "opus",
)

private val LIMITED_THINK_MARKERS = listOf("flash", "mini", "small", "lite", "coder")

/** [model] için gösterilecek kademeler. Boş liste = düşünme desteklenmiyor. */
fun thinkLevelsFor(model: String?): List<String> {
    val m = model?.lowercase().orEmpty()
    if (m.isBlank()) return THINK_LEVELS
    if (NO_THINK_MARKERS.any { m.contains(it) }) return emptyList()
    if (FULL_THINK_MARKERS.any { m.contains(it) }) return THINK_LEVELS
    if (LIMITED_THINK_MARKERS.any { m.contains(it) }) return listOf("none", "low", "medium", "high")
    return THINK_LEVELS
}

/** [level] bu modelde seçilebilir mi? (null = sunucu henüz söylemedi → serbest) */
fun thinkSupported(model: String?, level: String?): Boolean =
    level == null || level in thinkLevelsFor(model)

fun thinkLabel(level: String?): String = when (level) {
    "none" -> "Kapalı"
    "low" -> "Min"
    "medium" -> "Orta"
    "high" -> "Yüksek"
    "xhigh" -> "Maks"
    else -> "—"
}

/**
 * Üst şeritteki aktif model rozeti — "mimo-v2.6-pro · Yüksek".
 * Model yoksa seçime davet; düşünme bilinmiyorsa yalnız model adı.
 */
fun activeModelBadge(model: String?, think: String?): String {
    val m = model?.trim().takeUnless { it.isNullOrBlank() } ?: return "Model seç"
    val level = think?.takeIf { it.isNotBlank() } ?: return m
    return "$m · ${thinkLabel(level)}"
}

/** "Son kullanılan modeller" listesi — en son seçim başta, en fazla [max] kayıt. */
fun withRecentModel(recent: List<String>, key: String, max: Int = 5): List<String> {
    val k = key.trim()
    if (k.isEmpty()) return recent
    return (listOf(k) + recent.filterNot { it == k }).take(max)
}