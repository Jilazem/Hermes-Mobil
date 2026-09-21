package com.hermes.mobile.ui

/**
 * Tur22 madde-2 (r1 düzeltme) — gönderme anı geri bildirimi: gönder düğmesine
 * basınca 1 saniye boyunca geçici "gönderildi" işareti (✓) gösterilir, sonra
 * normal ikona dönülür.
 *
 * Karar mantığı SAF katmanda (tur22 kararı: bu repo'da Compose-test altyapısı
 * yok — JUnit ile kilitlenir; görsel kanıt emülatör burst kareleriyle verilir,
 * kanit/45-gonder-burst). Fail-closed kural: saat geri alırsa (elapsed < 0)
 * geri bildirim TAKILI KALMAZ — hemen temizlenir; kullanıcıya sonsuz ✓ göstermek,
 * hiç göstermemekten kötüdür.
 */
object SendFeedbackLogic {

    /** Geçici geri bildirimin görünür kalma süresi (kart: 1sn). */
    const val FLASH_MS = 1000L

    /**
     * Geri bildirim hâlâ görünsün mü? [nowMs] ve [sentAtMs] aynı zaman
     * tabanından (SystemClock.elapsedRealtime veya test saati) gelmeli.
     * - 0 <= elapsed < FLASH_MS → true (pencere açık)
     * - elapsed >= FLASH_MS     → false (süre doldu, temizle)
     * - elapsed < 0             → false (FAİL-CLOSED: saat geri sardıysa takılma)
     */
    fun visible(nowMs: Long, sentAtMs: Long): Boolean {
        val elapsed = nowMs - sentAtMs
        if (elapsed < 0L) return false
        return elapsed < FLASH_MS
    }
}
