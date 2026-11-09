package com.hermes.mobile.ui

/**
 * Ana duvar akışı (V3) — ChatGPT/Claude mobil tarzı açılış ekranının SAF
 * karar mantığı (Compose'suz, birim testli).
 *
 * Uygulama artık doğrudan bir sohbetin içinde açılmıyor: önce tüm konuların
 * kart kart aktığı "duvar" görünür, kullanıcı konuyu seçip içine girer ya da
 * alttaki kutudan yeni konu başlatır. Satır verisi Tur-16 `drawerRows`
 * çıktısıdır — aynı başlık/önizleme/durum kuralları (ham id sızmaz).
 */

/**
 * Oturum sınıflandırma kaynağı (v7.7.1).
 *
 * Ana akışta yalnızca kullanıcının açtığı ya da açıkça başlattığı konuların
 * görünmesini sağlar; alt ajanlar ve sistem testleri bağımsız konu oluşturmaz.
 */
enum class SessionOrigin {
    USER_CREATED,
    USER_INITIATED_BOT_TASK,
    SUBAGENT_CHILD,
    SYSTEM_INTERNAL,
    SCHEDULED_AUTOMATION,
    UNKNOWN;

    val isUserTopic: Boolean
        get() = this == USER_CREATED || this == USER_INITIATED_BOT_TASK
}

/**
 * Bir oturum satırının kaynak ve ebeveynlik kanıtlarına göre kökenini belirler.
 */
fun classifySessionOrigin(
    row: DrawerRow,
    source: String?,
): SessionOrigin {
    if (row.isChild || !row.parentId.isNullOrBlank() ||
        com.hermes.mobile.data.SubagentTreeStore.parentOf(row.dbId) != null ||
        com.hermes.mobile.data.SubagentTreeStore.parentOf(row.liveId) != null
    ) {
        return SessionOrigin.SUBAGENT_CHILD
    }

    val s = source?.trim()?.lowercase()

    if (s in setOf("cron", "scheduled", "scheduler", "automation", "timer")) {
        return SessionOrigin.SCHEDULED_AUTOMATION
    }

    if (s in setOf("system", "doctor", "maintenance", "watchdog", "health", "test", "internal", "daemon")) {
        return SessionOrigin.SYSTEM_INTERNAL
    }

    if (s in setOf("user", "chat", "direct", "manual", "gui", "web", "android", "mobile", "telegram", "cli")) {
        return if (!row.jev.isNullOrBlank() || row.title.contains("Görevi") || (s != null && s != "user" && s != "chat" && s != "direct")) {
            SessionOrigin.USER_INITIATED_BOT_TASK
        } else {
            SessionOrigin.USER_CREATED
        }
    }

    if (s == null || s.isEmpty()) {
        return if (!SmartSessionTitle.isGeneric(row.title)) {
            SessionOrigin.USER_CREATED
        } else {
            SessionOrigin.UNKNOWN
        }
    }

    return SessionOrigin.UNKNOWN
}

/**
 * Duvarın üstündeki süzgeç çipleri.
 *
 * J2 (patron): varsayılan liste artık "Tüm İşlemler" — **yalnız devam eden
 * işleri** (çalışan oturumlar + kullanıcının sabitledikleri) gösterir; cron /
 * sistem bakım bildirimleri buraya karışmaz, ayrı "Bildirimler" sekmesindedir.
 * Eski "Canlı" çipi bu kuralla aynı kümeyi verdiği için kaldırıldı.
 */
enum class HomeFilter(val labelTr: String, val labelEn: String) {
    All("Tümü", "All"),
    Working("Çalışanlar", "Working"),
    Completed("Tamamlananlar", "Completed"),
    Errors("Hatalar", "Errors"),
    MyBots("Botlarım", "My Bots"),
    Pinned("Sabit", "Pinned"),
    Chats("Sohbetler", "Chats"),
    Scheduled("Zamanlanmış", "Scheduled"),
}

/** Bir satır "şu an canlı" mı — çalışıyor ya da durum noktası gri-dışı. */
fun isLiveRow(row: DrawerRow): Boolean =
    row.working || (row.dot.isNotEmpty() && row.dot != "grey")

/** Cron kaynaklı mı (zamanlanmış görev çıktısı). */
fun isScheduledSource(source: String?): Boolean =
    source?.trim()?.equals("cron", ignoreCase = true) == true

/**
 * Bildirim kaynaklı oturumlar (cron / sistem bakımı / bekçi görevleri).
 * Bunlar "iş" değil "bildirim"dir: Bildirimler sekmesinde ve "Zamanlanmış"
 * çipinde görünür, "Tüm İşlemler" ile "Sohbetler" listesini kirletmez.
 */
private val NOTICE_SOURCES = setOf("cron", "system", "maintenance", "watchdog", "heartbeat")

fun isNoticeSource(source: String?): Boolean {
    val s = source?.trim()?.lowercase() ?: return false
    return s in NOTICE_SOURCES
}

/**
 * Duvar listesi: süzgeç + arama, sonra sıralama.
 *
 * Sıra: sabitlenmiş → canlı (çalışan) → en yeni. Canlı olanın üstte olması
 * bilinçli: duvarın asıl işi "şu an ne oluyor"u göstermek.
 *
 * v7.7.1: `showSystemAndSubagents` kapalıyken ana akışta yalnız kullanıcının
 * başlattığı konular yer alır; alt ajanlar ebeveyn konuların içinde ağaç olarak
 * toplanır, sistem testleri/sağlık kontrolleri bağımsız konu oluşturmaz.
 */
fun homeFeed(
    rows: List<DrawerRow>,
    filter: HomeFilter,
    query: String,
    sourceOf: (DrawerRow) -> String?,
    showSystemAndSubagents: Boolean = false,
): List<DrawerRow> {
    val q = query.trim().lowercase()
    return rows
        .asSequence()
        .filter { !it.archived }
        .filter { row ->
            // Alt ajanlar hiçbir zaman en üst düzey bağımsız konu kartı olamaz (ebeveynin altında yaşarlar)
            if (row.isChild) return@filter false

            val origin = classifySessionOrigin(row, sourceOf(row))

            // Kullanıcı ayarı kapalıyken sistem ve alt ajanlar ana akıştan filtrelenir
            if (!showSystemAndSubagents) {
                val isAllowed = row.pinned ||
                    row.followed ||
                    isLiveRow(row) ||
                    origin.isUserTopic ||
                    (filter == HomeFilter.Scheduled && origin == SessionOrigin.SCHEDULED_AUTOMATION)

                if (!isAllowed && filter != HomeFilter.Scheduled) {
                    return@filter false
                }
            }

            when (filter) {
                HomeFilter.All -> isLiveRow(row) || row.pinned || showSystemAndSubagents
                HomeFilter.Working -> isLiveRow(row)
                HomeFilter.Completed -> !isLiveRow(row) && !row.archived
                HomeFilter.Errors -> row.dot == "red"
                HomeFilter.MyBots -> origin == SessionOrigin.USER_INITIATED_BOT_TASK || !isNoticeSource(sourceOf(row))
                HomeFilter.Pinned -> row.pinned
                HomeFilter.Chats -> origin == SessionOrigin.USER_CREATED || !isNoticeSource(sourceOf(row))
                HomeFilter.Scheduled -> isScheduledSource(sourceOf(row)) || origin == SessionOrigin.SCHEDULED_AUTOMATION
            }
        }
        .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.preview.lowercase().contains(q) }
        .sortedWith(
            compareByDescending<DrawerRow> { it.pinned }
                .thenByDescending { it.working }
                .thenByDescending { it.epochSeconds },
        )
        .toList()
}

/** Çip sayaçları — "Canlı (2)" gibi; 0 ise sayı yazılmaz. */
fun homeFilterCount(
    rows: List<DrawerRow>,
    filter: HomeFilter,
    sourceOf: (DrawerRow) -> String?,
    showSystemAndSubagents: Boolean = false,
): Int = homeFeed(rows, filter, "", sourceOf, showSystemAndSubagents).size

/** Saate göre selamlama (yerel saat, 0-23). */
fun homeGreeting(hour: Int, en: Boolean): String = when (hour) {
    in 5..11 -> if (en) "Good morning" else "Günaydın"
    in 12..17 -> if (en) "Good afternoon" else "İyi günler"
    in 18..22 -> if (en) "Good evening" else "İyi akşamlar"
    else -> if (en) "Up late?" else "İyi geceler"
}

/**
 * Czip önerisi eşiği: bu kadar iletiyi aşan oturum "uzun" sayılır.
 * Uzun oturumu sürdürmek tüm geçmişi bağlama geri yükler (token faturası);
 * czip paketleyip yeni oturumda harita üzerinden devam ettirir.
 */
const val CZIP_LONG_SESSION_MESSAGES = 200

fun isLongSession(messageCount: Int): Boolean = messageCount >= CZIP_LONG_SESSION_MESSAGES

/**
 * `/czip <id>` çıktısından paket yolunu çıkarır (czip plugin'i
 * "   paket: /home/…/x.hkp" satırı yazar). Bulunamazsa null — çağıran hata
 * olarak gösterir, tahmin yürütmez.
 */
fun czipPackPath(output: String): String? =
    Regex("""paket:\s*(\S+\.hkp)""").find(output)?.groupValues?.get(1)

/**
 * Yeni oturumun ilk mesajı: ajana paketi AÇMADAN haritadan okumasını söyler
 * (czip skill'inin kuralı — tüm paket asla bağlama yüklenmez).
 */
fun czipHandoffPrompt(path: String, title: String): String =
    "[CZIP BAĞLANTISI] \"$title\" oturumu czip ile paketlendi: $path\n" +
        "Paketi AÇMA. Önce `czip oku $path` ile haritayı ve son iletileri oku; " +
        "gerekirse yalnız ilgili aralığı `czip aralik $path <bas-bit>` ile çek. " +
        "Sonra kısa bir özetle kaldığımız yerden devam et."

/**
 * Canlı bot akışı filtreleme mantığı (v7.7.0).
 */
fun filterLiveEvents(
    events: List<com.hermes.mobile.data.LiveBotEvent>,
    filter: HomeFilter,
    query: String,
    selectedBot: String? = null,
): List<com.hermes.mobile.data.LiveBotEvent> {
    val q = query.trim().lowercase()
    return events.filter { ev ->
        val matchesFilter = when (filter) {
            HomeFilter.All -> true
            HomeFilter.Working -> ev.status == "working"
            HomeFilter.Completed -> ev.status == "completed"
            HomeFilter.Errors -> ev.status == "failed"
            HomeFilter.MyBots -> ev.botName != "Hermes"
            HomeFilter.Pinned -> true
            HomeFilter.Chats -> true
            HomeFilter.Scheduled -> ev.type == com.hermes.mobile.data.BotEventType.TASK_STARTED || ev.type == com.hermes.mobile.data.BotEventType.TASK_COMPLETED
        }
        val matchesBot = selectedBot == null || ev.botName.equals(selectedBot, ignoreCase = true)
        val matchesQuery = q.isEmpty() || ev.title.lowercase().contains(q) || ev.content.lowercase().contains(q) || ev.botName.lowercase().contains(q)
        matchesFilter && matchesBot && matchesQuery
    }
}
