package com.hermes.mobile.assistant

/**
 * "Hey Jarvis" uyandırma kelimesinin SAF kısmı (Android'siz, testli).
 *
 * Model: openWakeWord `hey_jarvis_v0.1` (melspectrogram → embedding →
 * sınıflandırıcı, üçü de TFLite). Bu ortamda sentetik Türkçe ve İngilizce
 * söyleyişlerle ölçüldü: eşik 0.4 + art arda 2 çerçeve → 52/54 yakalama,
 * 125 sn'lik zorlayıcı konuşmada ("Hey Travis", "Harvey", "Her mesaj"…)
 * 0 yanlış alarm. Ayrıntı: docs/TELEFON-KONTROL.md.
 */
object WakeWordLogic {

    /** 16 kHz'de 80 ms — modelin adım boyu. */
    const val CHUNK = 1280

    /** Mel modeline giden pencere: yeni 1280 + önceki 3×160 örnek. */
    const val MEL_WINDOW = 1760
    const val MEL_FRAMES_PER_CHUNK = 8
    const val MEL_BINS = 32
    const val EMB_WINDOW = 76
    const val EMB_DIM = 96
    const val FEATURE_FRAMES = 16

    /** İlk birkaç çerçeve tamponlar dolmadan anlamsız — yok sayılır. */
    const val WARMUP_FRAMES = 5

    /** Algılamadan sonra ~2 sn sessizlik (aynı söyleyiş iki kez tetiklemesin). */
    const val COOLDOWN_FRAMES = 25

    enum class Sensitivity(val id: String, val threshold: Float, val consecutive: Int) {
        HIGH("yuksek", 0.35f, 2),
        NORMAL("normal", 0.4f, 2),
        STRICT("siki", 0.5f, 2);

        companion object {
            fun fromId(id: String?): Sensitivity = entries.firstOrNull { it.id == id } ?: NORMAL
        }
    }

    /** openWakeWord'ün mel dönüşümü: TF'in speech_embedding ölçeğine yaklaştırır. */
    fun melTransform(v: Float): Float = v / 10f + 2f

    /**
     * Kayan pencere: [window] (boyut [MEL_WINDOW]) sonuna yeni [chunk] eklenir,
     * en eski kısım atılır. Dönüş: pencere dolu mu.
     */
    fun slide(window: FloatArray, filled: Int, chunk: ShortArray): Int {
        val n = chunk.size
        System.arraycopy(window, n, window, 0, window.size - n)
        for (i in 0 until n) window[window.size - n + i] = chunk[i].toFloat()
        return (filled + n).coerceAtMost(window.size)
    }

    /** Satır kaydırmalı tampon: [rows]×[cols] matrisin sonuna yeni satırlar ekler. */
    fun pushRows(buf: Array<FloatArray>, newRows: List<FloatArray>) {
        val k = newRows.size.coerceAtMost(buf.size)
        for (i in 0 until buf.size - k) buf[i] = buf[i + k]
        for (i in 0 until k) buf[buf.size - k + i] = newRows[newRows.size - k + i].copyOf()
    }

    /** Eşik + art arda çerçeve + soğuma. Her 80 ms'de bir [feed]. */
    class Detector(private val threshold: Float, private val consecutive: Int, private val cooldown: Int = COOLDOWN_FRAMES) {
        private var streak = 0
        private var cool = 0

        fun feed(score: Float): Boolean {
            if (cool > 0) { cool--; streak = 0; return false }
            streak = if (score >= threshold) streak + 1 else 0
            if (streak >= consecutive) {
                streak = 0
                cool = cooldown
                return true
            }
            return false
        }

        fun reset() { streak = 0; cool = 0 }
    }
}
