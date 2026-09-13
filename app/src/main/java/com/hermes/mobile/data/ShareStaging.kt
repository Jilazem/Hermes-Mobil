package com.hermes.mobile.data

import java.io.File
import java.io.InputStream

/**
 * Paylaşım dosyasının cache'e AKIŞ kopyası (HIGH-1 teli, denetmen #5).
 *
 * Saf java.io: Android'a dokunmaz, JVM testinde gerçek dosyayla doğrulanır.
 *  - readBytes YOK: 8KB tamponla akıtır — büyük video/foto OOM üretmez.
 *  - hedef adı token + temizlenmiş ad taşır; çakışma olmaz.
 *  - kopya sonrası boyut doğrulanır; kaynak akıştan okunan bayt sayısı
 *    [maxBytes] sınırını aşarsa yarıda kesilir ve null döner (cache şişmez).
 *  - başarısızlıkta yarım dosya SİLİNİR — çağırana "okunamadı" bildirilir,
 *    böylece planShareUpload Unreadable üretir, sessiz kayıp olmaz.
 */
object ShareStaging {

    const val DIR_NAME = "share_inbox"

    /** Tek kopya için üst sınır (256MB); üstü paylaşım değil yük demektir. */
    const val MAX_BYTES = 256L * 1024 * 1024

    /** cacheDir altında paylaşım gelen kutusu dizini (yoksa oluşturulur). */
    fun inboxDir(cacheDir: File): File =
        File(cacheDir, DIR_NAME).apply { mkdirs() }

    /**
     * [input] akışını [dir] içine kopyalar. Dönen: kopya dosyası; hata ya da
     * sınır aşımında null (yarım kopya temizlenmiş olur). [maxBytes] üretimde
     * [MAX_BYTES]; test için küçültülebilir.
     */
    fun stage(
        input: InputStream?,
        dir: File,
        token: String,
        displayName: String,
        maxBytes: Long = MAX_BYTES,
    ): File? {
        if (input == null) return null
        val target = File(dir, "${token.take(8)}_${sanitize(displayName)}")
        var copied = 0L
        return runCatching {
            input.use { src ->
                target.outputStream().use { out ->
                    val buf = ByteArray(8 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        copied += n
                        if (copied > maxBytes) error("paylasim boyut sinirini asiyor")
                        out.write(buf, 0, n)
                    }
                }
            }
            if (copied == 0L) error("bos akis")
            target
        }.getOrElse {
            // Yarım/bozuk kopya cache'te kalmasın.
            runCatching { target.delete() }
            null
        }
    }

    /**
     * Bayat kopyaları süpürür ( uygulama paylaşım ortasında öldürülürse
     * consume temizliği hiç çalışmaz — cache birikmesin, denetmen #1).
     * [olderThanMs] eşiğinden eskiler silinir; dönen silinen sayısı.
     */
    fun purgeStale(dir: File, olderThanMs: Long, now: Long = System.currentTimeMillis()): Int {
        if (!dir.isDirectory) return 0
        val cutoff = now - olderThanMs
        return dir.listFiles()?.fold(0) { acc, f ->
            if (f.isFile && f.lastModified() < cutoff && runCatching { f.delete() }.getOrDefault(false))
                acc + 1 else acc
        } ?: 0
    }

    /** Dosya adını güvenli bileşene çevirir (yol enjeksiyonu yok). */
    fun sanitize(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("""[^A-Za-z0-9._-]+"""), "_")
            .take(80).ifBlank { "dosya.bin" }
}
