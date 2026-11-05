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
 * Duvarın üstündeki süzgeç çipleri.
 *
 * J2 (patron): varsayılan liste artık "Tüm İşlemler" — **yalnız devam eden
 * işleri** (çalışan oturumlar + kullanıcının sabitledikleri) gösterir; cron /
 * sistem bakım bildirimleri buraya karışmaz, ayrı "Bildirimler" sekmesindedir.
 * Eski "Canlı" çipi bu kuralla aynı kümeyi verdiği için kaldırıldı.
 */
enum class HomeFilter(val labelTr: String, val labelEn: String) {
    All("Tüm İşlemler", "All operations"),
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
 * "Tüm İşlemler" (J2): yalnız **devam eden** işler — çalışan satırlar ve
 * kullanıcının sabitledikleri. Biten sohbetler "Sohbetler", cron/sistem
 * bildirimleri "Zamanlanmış" + Bildirimler sekmesindedir.
 */
fun homeFeed(
    rows: List<DrawerRow>,
    filter: HomeFilter,
    query: String,
    sourceOf: (DrawerRow) -> String?,
): List<DrawerRow> {
    val q = query.trim().lowercase()
    return rows
        .asSequence()
        .filter { !it.archived }
        .filter { row ->
            when (filter) {
                HomeFilter.All -> isLiveRow(row) || row.pinned
                HomeFilter.Pinned -> row.pinned
                HomeFilter.Chats -> !isNoticeSource(sourceOf(row))
                HomeFilter.Scheduled -> isScheduledSource(sourceOf(row))
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
): Int = homeFeed(rows, filter, "", sourceOf).size

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
