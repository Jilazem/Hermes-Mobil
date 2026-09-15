package com.hermes.mobile.ui

/**
 * Kart/önizleme metnini İNSAN-OKUR hale getiren saf katman (tur-4).
 *
 * Boss şikâyeti: "oturum kartlarında ham JSON/tool çıktısı ve sistem mesajı
 * görünüyor". Sunucu `preview` alanı oturumun İLK mesajını, canlı akış ise
 * SON ETKİNLİĞİ taşır; ikisi de ham olabilir:
 *
 *   {"status": "success", "output": "=== ESBLESME: … len 11050…"}
 *   [IMPORTANT: You are running as a scheduled cron job. DELIVER…]
 *   ```json {"tool_call_id": …} ```
 *
 * Telegram sözleşmesi: kartın altında SON ANLAMLI mesaj tek satır görünür.
 * Makine çıktısı gösterilmez; bir önceki anlamlı metne düşülür (çağıran
 * taraf REST önizlemesine düşer), o da yoksa satır hiç çizilmez.
 *
 * Hepsi saf fonksiyon — Compose'suz JVM testi (PreviewSanitizeTest).
 */

/** JSON gövdesi başlangıcı — `{…}` ya da `[…]`. */
private val JSON_START = Regex("""^\s*[\[{]""")

/** `"anahtar": değer` biçimli ham JSON satırı. */
private val JSON_KV_LINE = Regex("""^\s*"?[\w.\-]+"?\s*:\s*.*$""")

/** Sistem/cron promptu işaretleri — önizlemede asla görünmez. */
private val SYSTEM_MARKERS = listOf(
    "[important:",
    "[system",
    "[cron",
    "<system",
    "</system",
    "<tool_call",
    "<function",
    "you are running as a scheduled cron job",
    "you are hermes",
    "you are a helpful",
    "you are an ai",
    "deliver this",
    "system prompt",
    "sistem promptu",
    "tool_call_id",
    "\"tool_calls\"",
    "function_calls",
)

/** Araç/sonuç gövdesi işaretleri (`{"status": "success", "output": …}`). */
private val TOOL_MARKERS = listOf(
    "\"status\":",
    "\"output\":",
    "\"success\":",
    "\"error\":",
    "\"result\":",
    "\"stdout\":",
    "\"stderr\":",
    "traceback (most recent call last)",
    "=== eslesme",
    "len ",
)

/** Markdown süsleri — önizlemede düz metin istenir. */
private val MD_LEAD = Regex("""^\s*(#{1,6}\s+|[-*+]\s+|\d+[.)]\s+|>\s+|`{1,3}\s*)""")

/** Satırın tamamı markdown çiti ise (``` veya ~~~) atlanır. */
private fun isFenceLine(line: String): Boolean {
    val t = line.trim()
    return t.startsWith("```") || t.startsWith("~~~")
}

/**
 * Metin makine çıktısı mı? Doğruysa önizleme/başlık olarak KULLANILMAZ.
 * Boş metin de "kullanılamaz" sayılır.
 */
fun isMachineNoise(raw: String?): Boolean {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return true
    val lower = t.lowercase()
    if (SYSTEM_MARKERS.any { lower.contains(it) }) return true
    if (JSON_START.containsMatchIn(t)) return true
    if (TOOL_MARKERS.any { lower.contains(it) }) return true
    // Markdown çitine gömülü içerik de makine çıktısıdır.
    if (isFenceLine(t)) return true
    if (lower.startsWith("blocked:")) return true
    return false
}

/** Satır düzeyi gürültü: JSON anahtar-değer, çit, yalnız noktalama. */
private fun isNoiseLine(line: String): Boolean {
    val t = line.trim()
    if (t.isEmpty()) return true
    if (isFenceLine(t)) return true
    if (JSON_START.containsMatchIn(t)) return true
    if (SYSTEM_MARKERS.any { t.lowercase().contains(it) }) return true
    if (t.length < 2) return true
    // Yalnız süs/noktalama satırı (ör. "---", "===", "•••").
    if (t.none { it.isLetterOrDigit() }) return true
    // `"anahtar": değer` gibi ham JSON satırı — ama "Saat: 14:30" gibi
    // insan cümlesi de bu kalıba uyar; iki kelimeden azsa gürültü say.
    if (JSON_KV_LINE.matches(t) && t.count { it == ' ' } == 0) return true
    return false
}

/** Markdown süslerini ve fazla boşluğu temizler. */
private fun plainLine(line: String): String =
    line.replace(MD_LEAD, "").replace(Regex("""\s+"""), " ").trim()

/**
 * Metnin ilk ANLAMLI satırı (ham hâliyle gürültü kontrolü, süsleri soyulmuş
 * hâliyle döndürülür). Gürültü kontrolü SOYULMADAN yapılır — aksi hâlde
 * "```kotlin" satırı soyulup "kotlin" olarak anlamlı sanılırdı.
 */
private fun firstMeaningfulLine(text: String): String? =
    text.lines()
        .firstOrNull { !isNoiseLine(it) }
        ?.let { plainLine(it).takeIf { p -> p.isNotBlank() } }

/** Kelime sınırında kırp; sınır çok gerideyse sert kırp. Kabarcık taş eklenir. */
internal fun cutAtWord(text: String, maxLen: Int): String {
    if (text.length <= maxLen) return text
    val window = text.take(maxLen + 1)
    val cut = window.lastIndexOf(' ')
    val body = if (cut >= maxLen / 2) window.take(cut) else text.take(maxLen)
    return body.trimEnd().trimEnd(',', ';', ':') + "…"
}

/**
 * Önizleme metni: ilk ANLAMLI satır, tek satır, soluk kart altı için.
 * Hepsi makine çıktısıysa "" döner — kart önizlemesiz çizilir (çağıran
 * isterse bir sonraki kaynağa düşer).
 */
fun meaningfulPreview(raw: String?, maxLen: Int = 90): String {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return ""
    return firstMeaningfulLine(t)?.let { cutAtWord(it, maxLen) } ?: ""
}

/**
 * KONU (başlık) için önizlemeden türetilmiş kısa cümle — Telegram satırı.
 * Önizleme makine çıktısıysa "" döner; başlık zinciri bir sonraki kurala düşer.
 */
fun topicFromPreview(raw: String?, maxLen: Int = 40): String =
    meaningfulPreview(raw, maxLen)

/**
 * Kişi/kanal/kaynak adı — KONU değildir (boss: "Gökhan Uzman" başlık olmasın).
 * Telegram `display_name` alanı buraya düşer; kart konu bulamazsa bir sonraki
 * kurala (ilk anlamlı kullanıcı cümlesi → zaman damgası) geçer.
 */
fun isGenericIdentityTitle(title: String?): Boolean {
    val t = title?.trim().orEmpty()
    if (t.isEmpty()) return true
    if (t == "-" || t == "—" || t == "?") return true
    // Harf içermeyen "başlık" (".", "...", ". #2") konu değildir — sunucu
    // `title_source=derived` ile böyle bir kırıntı gönderebiliyor.
    if (t.none { it.isLetter() }) return true
    val lower = t.lowercase()
    if (lower in GENERIC_IDENTITIES) return true
    // Kaynak kelimesinin kendisi ("telegram", "masaüstü") de konu değildir.
    if (lower in SOURCE_WORDS) return true
    // "telegram desktop", "desktop telegram", "telegram dm" gibi kaynak adları.
    return lower.split(' ', '-', '_').all { it in SOURCE_WORDS } && lower.any { it == ' ' || it == '-' || it == '_' }
}

private val GENERIC_IDENTITIES = setOf(
    "gökhan uzman", "gokhan uzman", "gökhan", "gokhan",
    "user", "kullanıcı", "kullanici", "me", "ben",
    "hermes", "hermes agent", "hermes mobil", "hermes mobile",
    "unknown", "bilinmiyor", "null", "none", "default", "varsayılan",
    "session", "oturum", "chat", "sohbet", "conversation",
)

private val SOURCE_WORDS = setOf(
    "telegram", "whatsapp", "desktop", "masaüstü", "masaustu", "cli", "tui",
    "cron", "api", "api_server", "web", "dm", "thread", "bot", "user", "chat",
)

/** Kart durum etiketi (tur-4): boşta ve bitmiş oturum ETİKETSİZ kalır. */
enum class StatusTone { Live, Waiting, Starting }

data class StatusPill(val labelTr: String, val labelEn: String, val tone: StatusTone)

/**
 * Durum = sinyal, cümle değil. `idle`/`done` için null döner (Telegram
 * "boşta" yazmaz); "Canlı" kelimesi hiçbir yerde kullanılmaz — çalışan ajan
 * zaten "yazıyor…" ile anlatılır.
 */
fun statusPill(status: String): StatusPill? = when (status) {
    "working" -> StatusPill("yazıyor…", "typing…", StatusTone.Live)
    "waiting" -> StatusPill("onay bekliyor", "waiting for approval", StatusTone.Waiting)
    "starting" -> StatusPill("başlıyor", "starting", StatusTone.Starting)
    else -> null
}

/**
 * Oturum kartının eylem kuralı (tek kural, iki ekran da aynı):
 *  - liste kartında buton YIĞINI yok; satırın tamamı sohbeti açar,
 *  - çalışan oturumda tek birincil eylem "Dur" (+ ikincil "Müdahale"),
 *  - "Döküm" her durumda SABİT konumda (sağdaki ilk ikon).
 */
data class CardActions(
    val continuePrimary: Boolean,
    val steer: Boolean,
    val stop: Boolean,
    val transcript: Boolean = true,
)

fun cardActions(working: Boolean): CardActions =
    if (working) CardActions(continuePrimary = false, steer = true, stop = true)
    else CardActions(continuePrimary = true, steer = false, stop = false)

/**
 * Oturum detayı sayaç kuralı (kusur D): kartta "39 mesaj" yazarken detayda
 * "0 mesaj" yazılMASI güven kırığıydı. Sayaç YÜKLENEN konuşmayı sayar;
 * yüklenemediyse/henüz yüklenmediyse "0" yazılmaz — null döner ve çağıran
 * satırı gizler (ya da "—" yazar).
 */
fun loadedMessageCount(loading: Boolean, error: String?, loaded: Int): Int? {
    if (loading || error != null) return null
    return loaded.takeIf { it > 0 }
}

/**
 * Liste başlığı sayacı (KALAN-6 / FR-004).
 *
 * Eski hâli iki farklı adı yan yana koyuyordu: "14 açık kayıt · 26 toplam".
 * İki sayı da AYNI kümeden geliyor ama farklı sözcüklerle anıldığı için
 * "kayıt" ve "toplam" iki ayrı liste gibi okunuyordu (Telegram'da tek sayaç
 * vardır). Yeni ifade tek cümledir, önce TOPLAM sonra açık olanı söyler ve
 * ikisi de aynı adı taşır:
 *
 *   TR: "26 oturum · 14 açık"
 *   EN: "26 sessions · 14 open"
 *
 * Açık oturum yoksa ikinci sayı yazılmaz (gürültü olur); liste boşsa null
 * döner ve başlık altı satır hiç çizilmez.
 */
fun sessionCounter(total: Int, open: Int, en: Boolean = false): String? {
    if (total <= 0) return null
    val safeOpen = open.coerceIn(0, total)
    val totalText = if (en) "$total sessions" else "$total oturum"
    if (safeOpen <= 0) return totalText
    return if (en) "$totalText · $safeOpen open" else "$totalText · $safeOpen açık"
}

/**
 * Döküme ÇİZİLECEK kullanıcı mesajı mı? (tur-5 kusur I)
 *
 * Cron/otomasyon oturumlarının geçmişinde sistem istemi `isUser = true` bir
 * kayıt olarak dönüyor:
 *
 *   [IMPORTANT: You are running as a scheduled cron job. DELIVERY: Your final
 *    response will be automatically delivered …]
 *
 * Tur-4'te bu metin KART ÖNİZLEMESİNDEN atılmıştı (`isMachineNoise`) ama
 * DÖKÜM/sohbet çizimi ham hâliyle balon basıyordu — ekranda kullanıcıya ait
 * olmayan bir sistem istemi, üstelik İngilizce. Sistem istemi sohbet değildir:
 * balon çizilmez.
 */
fun visibleUserMessage(text: String?): Boolean = !isMachineNoise(text)
