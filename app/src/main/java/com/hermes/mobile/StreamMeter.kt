package com.hermes.mobile

/**
 * Yazma hızı ölçerin saf hali — Android'den bağımsız, test edilebilir.
 *
 * Akış boyunca `delta(metin, nowNanos)` ile parçalar beslenir; sayaç ~4
 * karakter = 1 token varsayımıyla token üretir, 500 ms'lik pencerelerde ham
 * token/saniye hesaplar ve bu ham değeri EMA (alpha 0.25) ile yumuşatır.
 * Saat ilk deltada sıfırlanır; `finish(nowNanos)` değerleri dondurur —
 * sonrası okuma-only. Zaman dışarıdan enjekte edilir ki testler sahte
 * nanos ile oynayıp davranışı sabitleyebilsin.
 *
 * `delta` yalnız bir pencere kapandığında (≈500 ms'de bir) güncel snapshot
 * döndürür; aradaki deltalar `null` döndürüp UI recomposition'ını bastırır.
 * `finish` donmuş snapshot'ı döndürür; UI 600 ms'lik sönüşle satırı kaldırır.
 */
class StreamMeter {

    companion object {
        /** Kabaca 4 karakter ≈ 1 token (i18n için makul tek varsayım). */
        const val CHARS_PER_TOKEN = 4

        /** Ham hız pencerelerinin genişliği (500 ms). */
        const val WINDOW_NANOS = 500_000_000L

        /** EMA düzleştirme katsayısı — küçükse hız daha az hoplar. */
        const val EMA_ALPHA = 0.25
    }

    /** Donmuş okuma anı. UI yalnız bunu görür; iç durum dışarı sızmaz. */
    data class Snapshot(
        /** Yumuşatılmış token/saniye (EMA). */
        val tokensPerSecond: Double,
        /** Akışta biriken toplam tahmini token (~4 kr = 1 token). */
        val tokens: Int,
        /** Akışın başlangıcından bu yana geçen süre (ms). */
        val elapsedMs: Long,
        /** message.complete geldi mi — true ise değerler donmuştur. */
        val finished: Boolean = false,
    ) {
        /** Gösterilmeye değer bir akış var mı? */
        val active: Boolean get() = tokens > 0
    }

    private var started = false
    private var startNanos = 0L
    private var chars = 0
    private var windowStartNanos = 0L
    private var windowChars = 0
    private var ema = 0.0
    private var frozen = false
    private var frozenElapsedMs = 0L

    /** Akışı sıfırlar — yeni yanıt, yeni saat. */
    fun reset() {
        started = false
        startNanos = 0L
        chars = 0
        windowStartNanos = 0L
        windowChars = 0
        ema = 0.0
        frozen = false
        frozenElapsedMs = 0L
    }

    /**
     * Bir delta parçası besler. İlk çağrı saati ve pencereyi başlatır.
     * Donmuş ölçerde (finish sonrası geç gelen delta) sessizce yok sayılır.
     * Yalnız bir pencere kapanıp EMA güncellendiğinde snapshot döner.
     */
    fun delta(text: String, nowNanos: Long): Snapshot? {
        if (frozen || text.isEmpty()) return null
        if (!started) {
            started = true
            startNanos = nowNanos
            windowStartNanos = nowNanos
        }
        chars += text.length
        // Kapanan pencereler varsa her birini ham hıza çevirip EMA'ya karıştır.
        // Pencere chars'ı bu deltayı İÇERMEZ (eşik sonrası eklenir) — sınırda
        // çift sayım olmaz.
        var closed = false
        while (nowNanos - windowStartNanos >= WINDOW_NANOS) {
            if (windowChars > 0) {
                val windowSec = (nowNanos - windowStartNanos).toDouble() / 1_000_000_000.0
                val raw = windowChars / CHARS_PER_TOKEN / windowSec
                ema = if (ema <= 0.0) raw else ema + EMA_ALPHA * (raw - ema)
                closed = true
            }
            windowStartNanos += WINDOW_NANOS
            windowChars = 0
        }
        windowChars += text.length
        return if (closed) snapshot(nowNanos) else null
    }

    /** message.complete anı: son yarım pencereyi de katar, değerleri dondurur. */
    fun finish(nowNanos: Long): Snapshot {
        if (started && !frozen) {
            if (windowChars > 0) {
                val span = nowNanos - windowStartNanos
                if (span > 0L) {
                    val raw = windowChars / CHARS_PER_TOKEN / (span.toDouble() / 1_000_000_000.0)
                    ema = if (ema <= 0.0) raw else ema + EMA_ALPHA * (raw - ema)
                }
            }
            windowChars = 0
            frozenElapsedMs = (nowNanos - startNanos) / 1_000_000L
            frozen = true
        }
        return snapshot(nowNanos)
    }

    /** Akışın canlı anlık görüntüsü — bitmemişse akan süre, bitmişse donmuş değerler. */
    fun snapshot(nowNanos: Long): Snapshot = Snapshot(
        tokensPerSecond = ema,
        tokens = chars / CHARS_PER_TOKEN,
        elapsedMs = when {
            !started -> 0L
            frozen -> frozenElapsedMs
            else -> (nowNanos - startNanos) / 1_000_000L
        },
        finished = frozen,
    )
}

/** Hız satırının saf biçimlendiricisi — Compose'dan bağımsız, test edilebilir. */
object SpeedFormat {

    /** 1234 → "1.2k" (TR'de ondalık virgül: "1,2k"); 940 → "940". */
    fun compactTokens(n: Int, decimalSeparator: Char = '.'): String =
        if (n < 1000) n.toString()
        else {
            val k = n / 100.0 // → 12.34 biçiminde, virgüle yer bırakır
            val rounded = kotlin.math.round(k) / 10.0
            if (rounded >= 100.0) "${rounded.toInt()}k"
            else {
                val intPart = rounded.toLong()
                val frac = ((rounded * 10).toLong() % 10).toInt()
                if (frac == 0) "${intPart}k" else "$intPart$decimalSeparator$frac" + "k"
            }
        }

    /** 14_400 ms → "0:14"; 75_000 ms → "1:15". */
    fun clock(elapsedMs: Long): String {
        val totalSec = elapsedMs / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    /** Hız sayısı: 1208 → "1.2k", 8.4 → "8,4" (TR virgülü). */
    fun rate(ticksPerSecond: Double, decimalSeparator: Char = '.'): String =
        if (ticksPerSecond >= 1000) compactTokens(ticksPerSecond.toInt(), decimalSeparator)
        else if (ticksPerSecond >= 100) ticksPerSecond.toInt().toString()
        else {
            val rounded = kotlin.math.round(ticksPerSecond * 10) / 10.0
            val intPart = rounded.toLong()
            val frac = ((rounded * 10).toLong() % 10).toInt()
            if (frac == 0) intPart.toString() else "$intPart$decimalSeparator$frac"
        }
}
