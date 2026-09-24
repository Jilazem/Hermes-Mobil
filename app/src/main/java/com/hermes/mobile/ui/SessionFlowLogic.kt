package com.hermes.mobile.ui

import com.hermes.mobile.SpeedFormat

/**
 * Tur-19 — "Tümü" genel akışı, hızlı yanıt durum makinesi ve eşzamanlılık
 * istatistiği için SAF karar mantığı (Compose'suz, birim testli).
 *
 * FR-001: Tümü sekmesi — tüm oturumların SON mesajları kronolojik tek listede
 *   (Telegram sohbet listesi gibi). Grup başlığı YOK; salt zaman sırası.
 *   Veri kaynağı Tur-16 `drawerRows` çıktısıdır — aynı durum noktaları, aynı
 *   `displayLabel` ham-id filtresi (sızdırma yasağı devam eder).
 * FR-002: Hızlı yanıt — yalnız gateway'de canlı ve müdahaleye açık
 *   (`canIntervene`) oturumlarda mümkündür; yeni gateway API'si icat edilmez,
 *   mevcut `session.steer` sözleşmesi yeniden kullanılır.
 * FR-003: İstatistik — ölçülmeyen değer UYDURULMAZ: örnek yoksa null ("—"
 *   olarak çizilir), tahmin/interpolasyon yok.
 */

// ---- FR-001: genel akış ----------------------------------------------------

/**
 * "Tümü" akışı — satırları salt kronolojik (en yeni önce) tek düz listeye
 * çevirir; `query` boş değilken başlık + önizlemede anlık süzer.
 *
 * Durum noktası ve başlık `rows` içinde zaten çözümlenmiştir (drawerRows);
 * burada ham id ASLA üretilmez — giriş zaten `displayLabel`'den geçmiştir.
 */
fun drawerFeed(rows: List<DrawerRow>, query: String): List<DrawerRow> {
    val q = query.trim().lowercase()
    val list = if (q.isBlank()) rows else rows.filter {
        it.title.lowercase().contains(q) || it.preview.lowercase().contains(q)
    }
    return list.sortedByDescending { it.epochSeconds }
}

// ---- FR-002: hızlı yanıt durum makinesi -------------------------------------

/** Mini composer fazları. */
enum class QuickReplyPhase { Idle, Editing, Sending, Sent, Failed }

/**
 * Satır hızlı yanıt verilebilir mi? Yalnızca gateway belleğinde ve
 * müdahaleye açık (`isWorking/isWaiting/isStarting`) oturumlar — REST-only
 * kapalı oturumlar için yakın yetenek "Sohbete git"tir (yeni API yok).
 */
fun canQuickReply(row: DrawerRow): Boolean = row.liveSession?.canIntervene == true

/** Gönder düğmesi: boş metin ve sürmekte olan gönderim ENGEL. */
fun quickReplySendEnabled(phase: QuickReplyPhase, text: String): Boolean =
    text.isNotBlank() && phase != QuickReplyPhase.Sending

/** Durum geçişleri — dış dünyadan gelen olaylar tek yerde haritalanır. */
fun quickReplyOnType(phase: QuickReplyPhase): QuickReplyPhase =
    if (phase == QuickReplyPhase.Sent || phase == QuickReplyPhase.Failed) {
        QuickReplyPhase.Editing
    } else {
        phase
    }

fun quickReplyOnSubmit(phase: QuickReplyPhase, text: String): QuickReplyPhase =
    if (quickReplySendEnabled(phase, text)) QuickReplyPhase.Sending else phase

fun quickReplyOnResult(success: Boolean): QuickReplyPhase =
    if (success) QuickReplyPhase.Sent else QuickReplyPhase.Failed

/** Hata/sonuç metninden başarı hükmü (gateway durum dizisi sözleşmesi). */
fun quickReplyIsSuccess(notice: String): Boolean =
    !notice.contains("başarısız", ignoreCase = true) &&
        !notice.contains("Durdurulamadı") &&
        !notice.contains("kabul etmedi", ignoreCase = true)

// ---- FR-003: eşzamanlılık istatistiği ----------------------------------------

/**
 * Aktif ajan sayısı — Tur-16 "Canlı" filtresiyle AYNI yargı:
 * `working` ya da durum noktası gri-dışı (çalışan/bekleyen/REST-açık).
 */
fun activeAgentCount(rows: List<DrawerRow>): Int =
    rows.count { it.working || (it.dot.isNotEmpty() && it.dot != "grey") }

/**
 * Sparkline penceresi — son [maxPoints] ölçüm, eski→yeni tertibi korunur.
 * Pencere dışına taşan KESİLİR; dolgu/interpolasyon YOK.
 */
fun sparkWindow(samples: List<Double>, maxPoints: Int = 60): List<Double> =
    if (samples.size <= maxPoints) samples
    else samples.subList(samples.size - maxPoints, samples.size)

/**
 * Pencere ortalaması ("≈Xt/s") — boş/ölçümsüz pencere null döner ("—").
 * Yalnız GERÇEK ölçüm ortalaması; sıfır örnek varsayılan 0 DEĞİLDİR.
 */
fun sparkRate(samples: List<Double>): Double? {
    if (samples.isEmpty()) return null
    return samples.sum() / samples.size
}

/** Sayısal gösterim: null → "—"; <100 bir basamak, üstü tam sayı. */
fun rateLabel(rate: Double?, decimalSeparator: Char = '.'): String {
    if (rate == null) return "—"
    return SpeedFormat.rate(rate, decimalSeparator)
}
