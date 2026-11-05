package com.hermes.mobile.data

/**
 * B3 (v6.3) — WebSocket yaşam döngüsü politikası: keepalive, backoff, kapanış kodu.
 *
 * Üçü de saf (Android'siz) tutuldu ki emülatör olmadan birim testle kilitlenebilsin.
 */
object WsBackoff {

    /** İlk yeniden bağlanma gecikmesi. */
    const val FIRST_MS = 1_000L

    /** Üst sınır — sonsuz denemede bu gecikmeyle döner. */
    const val MAX_MS = 15_000L

    /** Sabit dizi: 1 → 2 → 4 → 8 → 15 → 15 … (sonsuz, deneme sayısı sınırsız). */
    private val STEPS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L)

    /**
     * [attempt] 1'den başlar (ilk yeniden bağlanma denemesi). Deneme sayısı
     * sınırsızdır: 5 ve sonrası 15 sn'de sabitlenir.
     */
    fun delayMs(attempt: Int): Long {
        if (attempt <= 0) return FIRST_MS
        return STEPS[minOf(attempt, STEPS.size) - 1]
    }

    /** "Yeniden bağlanıyor (3. deneme, 4 sn)" metni. */
    fun describe(attempt: Int): String =
        "Yeniden bağlanıyor ($attempt. deneme, ${delayMs(attempt) / 1000} sn)"
}

/**
 * WebSocket kapanış kodlarının Türkçe karşılığı.
 *
 * Sahada "Bağlantı kapandı" diyen tek satır vardı; token reddi (4401/1008) ile
 * sunucu çökmesi (1011) aynı görünüyordu. 1006 = pong gelmedi/ağ koptu.
 */
object WsCloseCodes {

    const val NORMAL = 1000
    const val GOING_AWAY = 1001
    const val ABNORMAL = 1006
    const val POLICY = 1008
    const val SERVER_ERROR = 1011
    const val HERMES_TOKEN = 4401

    fun describe(code: Int): String = when (code) {
        NORMAL -> "Bağlantı kapandı"
        GOING_AWAY -> "Sunucu kapanıyor"
        ABNORMAL -> "Ağ koptu (1006)"
        HERMES_TOKEN -> "Token reddedildi (4401)"
        POLICY -> "Token reddedildi (1008)"
        SERVER_ERROR -> "Sunucu hatası (1011)"
        else -> "Bağlantı kapandı (kod $code)"
    }

    /**
     * Aynı token'la yeniden denemek boşuna: kullanıcı Ayarlar'dan token'ı
     * düzeltmeli. Bu kodlarda yeniden bağlanma döngüsü başlatılmaz.
     */
    fun isFatalForRetry(code: Int): Boolean = code == HERMES_TOKEN || code == POLICY
}

/**
 * Uygulama düzeyi keepalive sayacı.
 *
 * Kural: 25 sn'de bir ping; **2 ardışık** yanıtsız ping → soketi kapat ve
 * yeniden bağlan. Tek kayıp ping toleranslıdır (mobil ağda tek paket kaybı
 * normaldir), iki ardışık kayıp gerçek kopmadır.
 */
class KeepalivePolicy(
    private val intervalMs: Long = PING_INTERVAL_MS,
    private val maxMisses: Int = MAX_CONSECUTIVE_MISSES,
) {
    var consecutiveMisses: Int = 0
        private set

    val intervalMillis: Long get() = intervalMs

    fun onAnswered() {
        consecutiveMisses = 0
    }

    /** @return true → soket kapatılıp yeniden bağlanılmalı. */
    fun onMissed(): Boolean {
        consecutiveMisses += 1
        return consecutiveMisses >= maxMisses
    }

    fun reset() {
        consecutiveMisses = 0
    }

    companion object {
        const val PING_INTERVAL_MS = 25_000L

        /** Tek kaynak: [SocketTuning.GATEWAY_PONG_TIMEOUT_MS]. */
        const val PONG_TIMEOUT_MS = SocketTuning.GATEWAY_PONG_TIMEOUT_MS
        const val MAX_CONSECUTIVE_MISSES = 2
    }
}
