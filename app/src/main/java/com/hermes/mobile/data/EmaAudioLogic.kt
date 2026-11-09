package com.hermes.mobile.data

/**
 * EMA ses hattının saf (Android'siz) yardımcıları — JVM birim testinde
 * doğrudan sınanır: WAV'dan PCM ayıklama, 2-bayt hizalı parçalama, kare ve
 * boşaltma süresi hesabı.
 *
 * Neden ayrı: çalma hattındaki iki gerçek hata burada kapatılıyor.
 * 1) WAV başlığı HER ZAMAN 44 bayt değildir (LIST/INFO ek blokları başlığı
 *    uzatır). Sabit `offset = 44` metadata baytlarını hoparlöre yazıyordu.
 * 2) 16-bit PCM'de tek bayt sınırı ortadan böler: parça boyu çift olmazsa
 *    sonraki parçanın ilk örneği kayar ve ses bozuk/kesik çıkar.
 */
object EmaAudioLogic {

    /** Mono 16-bit PCM + gerçek örnek hızı (WAV başlığından). */
    data class Pcm(val rate: Int, val bytes: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Pcm && other.rate == rate && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * rate + bytes.contentHashCode()
    }

    /**
     * WAV'dan mono 16-bit PCM ayıklar. Başlık 44 bayttan uzun olabilir; bu
     * yüzden sabit ofset KULLANILMAZ — [SpeakLevelLogic.parseWavPcm] RIFF
     * bloklarını yürüyerek gerçek `data` gövdesini bulur ve çift kanalı
     * mono'ya indirir. Çözümlenemeyen dosyada null döner (çağıran hata
     * verir; sessizce çöp çalmaz).
     */
    fun pcmFromWav(wav: ByteArray): Pcm? {
        val (samples, rate) = SpeakLevelLogic.parseWavPcm(wav) ?: return null
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = samples[i].toInt()
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return Pcm(rate, out)
    }

    /**
     * PCM'i çift bayt sınırından böler: istenen boy tek sayıysa bir eksiğine
     * inilir, böylece hiçbir parça 16-bit örneği ortadan kesmez. Giriş tek
     * baytla bitiyorsa son yarım örnek atılır (çalınamaz; hizalı olmayan
     * baytı AudioTrack'e yazmak sonraki örneği kaydırır).
     */
    fun evenChunks(pcm: ByteArray, size: Int): List<ByteArray> {
        require(size >= 2) { "parça boyu en az 2 bayt olmalı" }
        val step = if (size % 2 == 0) size else size - 1
        val total = pcm.size - (pcm.size % 2)
        val out = ArrayList<ByteArray>((total + step - 1) / step)
        var off = 0
        while (off < total) {
            val end = minOf(total, off + step)
            out.add(pcm.copyOfRange(off, end))
            off = end
        }
        return out
    }

    /** 16-bit mono kare sayısı. */
    fun frames(bytes: Int): Int = bytes / 2

    /**
     * Çalma hattını boşaltmak için üst sınır: ses süresi + pay. Ağ tarafı
     * bittiğinde hoparlör kuyruğu hâlâ doludur; sabit kısa süre keser,
     * pay bırakmayan süre ise sonsuz bekler.
     */
    fun drainDeadlineMs(frames: Long, rate: Int, slackMs: Long = 10_000): Long =
        frames * 1000 / rate.coerceAtLeast(1) + slackMs
}
