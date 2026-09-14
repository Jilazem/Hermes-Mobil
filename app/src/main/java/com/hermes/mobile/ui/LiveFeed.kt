package com.hermes.mobile.ui

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.SessionFlags

/**
 * Canlı akış "Tümü" görünümü — Telegram'daki oturum listesi gibi: hangi bot
 * (kaynak) hangi oturumda şu an ne yapıyor.
 *
 * İki veri kaynağı birleşir:
 *  - `live`  : gateway belleğindeki aktif ajanlar (durum + önizleme + steer id)
 *  - `sessions`: REST /api/sessions — tüm geçmiş, kaynak etiketiyle
 *
 * Canlı oturumlar REST kaydının üstüne BİNİR (aynı dbId), mükerrer satır olmaz;
 * canlı olanlar en üstte, kalanı en yeni önce. Saf fonksiyon — Compose'suz
 * test edilir.
 */
data class LiveFeedEntry(
    /** Birleştirme anahtarı: dbId (REST) == sessionKey (canlı). */
    val dbId: String,
    val source: String?,
    val live: Boolean,
    /** working · waiting · starting · idle · done */
    val status: String,
    val title: String,
    val preview: String,
    val lastActive: Double,
    /** Canlıysa gateway süreç içi id'si (steer/interrupt/activate için). */
    val liveId: String,
    /** Eylemler altta hangi kaynağa yönlensin: canlı ya da geçmiş oturumu. */
    val liveSession: LiveSession?,
    val pastSession: HermesSession?,
)

fun liveFeed(
    live: List<LiveSession>,
    sessions: List<HermesSession>,
): List<LiveFeedEntry> {
    // REST kaydı dbId → kayıt; canlı oturumun kaynağı (bot) burada.
    val byId = sessions.associateBy { it.id }
    val liveIds = live.map { it.dbId }.toSet()

    // Aynı dbId'yi paylaşan birden çok canlı kayıt olursa (yeniden bağlanmada
    // gateway iki süreç içi kayıt bırakabilir) LazyColumn key çakışması çökme
    // verir: en hareketli olan kalır, mükerrer satır atılır.
    val distinctLive = live
        .sortedByDescending { it.lastActive }
        .distinctBy { it.dbId }

    // Canlı satırlar en son etkinlik sırasıyla: Telegram'daki gibi en
    // hareketli en üstte.
    val liveRows = distinctLive.map { l ->
        val rest = byId[l.dbId]
        LiveFeedEntry(
            dbId = l.dbId,
            source = rest?.source,
            live = true,
            status = if (l.isWorking) "working" else if (l.isWaiting) "waiting"
                else if (l.isStarting) "starting" else "idle",
            title = l.title.ifBlank { rest?.title ?: l.id },
            // Tur-4 (kusur A/B): canlı çerçevenin önizlemesi ham tool/JSON
            // çıktısı olabilir. Makine çıktısı ATILIR; bir önceki anlamlı
            // metne — REST kaydının ilk mesajına — düşülür; o da yoksa ""
            // (kart önizlemesiz çizilir, çağıran çalışıyorsa "yazıyor…" yazar).
            preview = meaningfulPreview(l.preview).ifBlank {
                meaningfulPreview(rest?.preview)
            },
            lastActive = if (l.lastActive > 0) l.lastActive else (rest?.startedAt ?: 0.0),
            liveId = l.id,
            liveSession = l,
            pastSession = rest,
        )
    }
    // Canlı listede olmayan geçmiş kayıtları: en yeni önce.
    val pastRows = sessions
        .filter { it.id !in liveIds }
        .sortedByDescending { it.startedAt ?: 0.0 }
        .map { s ->
            LiveFeedEntry(
                dbId = s.id,
                source = s.source,
                live = false,
                status = if (s.isActive) "idle" else "done",
                title = readableTitle(s, SessionFlags(), emptyMap()),
                // Tur-4 (kusur A/B): sistem/cron promptu ve tool çıktısı kart
                // önizlemesine çıkmaz; anlamlı satır yoksa önizleme boş kalır.
                preview = meaningfulPreview(s.preview),
                lastActive = s.startedAt ?: 0.0,
                liveId = "",
                liveSession = null,
                pastSession = s,
            )
        }
    return liveRows + pastRows
}

/**
 * TUI/CLI'nın ürettiği ham oturum kimliği kalıbı: `20260913_184051_52f76a`
 * (yyyyMMdd_HHmmss_hex). Başlık olarak hiçbir şey gösterilmez; yalnız
 * buradaki zaman damgası çözülür.
 */
private val RAW_SESSION_ID = Regex("""^(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})\d{2}_([0-9a-f]+)$""")

/** `20260913_184051_52f76a` → "13.09 18:40" (gün.ay saat:dakika, saniyesiz). */
fun stampFromRawId(id: String): String? {
    val m = RAW_SESSION_ID.matchEntire(id.trim().lowercase()) ?: return null
    val (mo, d) = m.groupValues[2].toInt() to m.groupValues[3].toInt()
    val (hh, mm) = m.groupValues[4].toInt() to m.groupValues[5].toInt()
    // Ay/gün/saat geçerli aralıkta değilse damga uydurma sayılır → null.
    if (mo !in 1..12 || d !in 1..31 || hh !in 0..23 || mm !in 0..59) return null
    return "${m.groupValues[3]}.${m.groupValues[2]} ${m.groupValues[4]}:${m.groupValues[5]}"
}

/** Kaynak → kullanıcıya gösterilecek kısa etiket. Tur-4: TR/EN ayrıldı —
 *  İngilizce arayüzde "Masaüstü"/"Zamanlanmış görev" sızıntısı olmaz. */
private val SESSION_SOURCE_LABELS = mapOf(
    "tui" to ("TUI" to "TUI"),
    "cli" to ("CLI" to "CLI"),
    "telegram" to ("Telegram" to "Telegram"),
    "whatsapp" to ("WhatsApp" to "WhatsApp"),
    "api_server" to ("API" to "API"),
    "api" to ("API" to "API"),
    "web" to ("Web" to "Web"),
    "desktop" to ("Masaüstü" to "Desktop"),
    "cron" to ("Zamanlanmış görev" to "Scheduled job"),
)

fun sessionSourceLabel(source: String?, en: Boolean = false): String? =
    source?.trim()?.lowercase()?.let { key ->
        SESSION_SOURCE_LABELS[key]?.let { (tr, enLabel) -> if (en) enLabel else tr }
    }

/**
 * Canlı oturum başlığı — Oturumlar listesinin `readableTitle`ı ile AYNI zincir:
 * rename > gateway başlığı (= canlı `title`, ham id değilse) > REST kaydının
 * çözümü (cron iş adı / kaynak + zaman). Canlı tarafın REST karşılığı yoksa
 * (henüz yazılmamış yeni oturum) canlı `title` ve dbId kalıbı çözülür; hiçbir
 * şey bulunamazsa "Oturum" — ham süreç içi id hiçbir koşulda başlık olmaz.
 */
fun liveSessionTitle(
    live: LiveSession,
    rest: HermesSession?,
    flags: SessionFlags,
    cronNames: Map<String, String>,
    en: Boolean = false,
): String {
    (flags.renames[live.dbId] ?: flags.renames[live.id])
        ?.takeIf { it.isNotBlank() }?.let { return it }
    val gatewayTitle = live.title.takeIf {
        it.isNotBlank() && it != live.id && it != live.dbId
    }
    return readableTitle(
        HermesSession(
            id = live.dbId,
            source = rest?.source,
            displayName = gatewayTitle ?: rest?.displayName,
            preview = rest?.preview,
            serverTitle = rest?.serverTitle,
        ),
        flags,
        cronNames,
        en,
    )
}

/** Kaynak etiketi → insan okunur bot/kanal adı (TR/EN). */
fun feedSourceLabel(source: String?, en: Boolean = false): String = when (source) {
    "telegram" -> "Telegram"
    "whatsapp" -> "WhatsApp"
    "cron" -> if (en) "Scheduled job" else "Zamanlanmış görev"
    "cli" -> "CLI"
    "tui" -> "TUI"
    "web" -> "Web"
    "api", "api_server" -> "API"
    "desktop" -> if (en) "Desktop" else "Masaüstü"
    null, "" -> if (en) "Unknown source" else "Bilinmeyen kaynak"
    else -> source
}
