package com.hermes.mobile.ui

import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.LiveSession

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
            preview = l.preview.ifBlank { rest?.displayName.orEmpty() },
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
                title = s.title,
                preview = "",
                lastActive = s.startedAt ?: 0.0,
                liveId = "",
                liveSession = null,
                pastSession = s,
            )
        }
    return liveRows + pastRows
}

/** Kaynak etiketi → insan okunur bot/kanal adı. */
fun feedSourceLabel(source: String?): String = when (source) {
    "telegram" -> "Telegram"
    "whatsapp" -> "WhatsApp"
    "cron" -> "Zamanlanmış görev"
    "cli" -> "CLI"
    "desktop" -> "Masaüstü"
    "api" -> "API"
    null, "" -> "Bilinmeyen kaynak"
    else -> source
}
