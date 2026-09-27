package com.hermes.mobile.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Tur-21 yerel TTS indirme yöneticisi — SHA256 doğrulamalı, atomik.
 *
 * Akış (dosya başına): `{rel}.part` indir → sha256 doğrula → `{rel}`e
 * adlandır. Hash tutmazsa `.part` SİLİNİR ve hata fırlatılır (fail-closed;
 * bozuk model motora asla gitmez). Yarım kalan `.part` sonraki denemede
 * silinip yeniden indirilir — resume YOK (basitlik; 63MB tek parça).
 *
 * İlerleme: tamamlanan dosyaların BİRİKMİŞ baytı + bayrak dosyalar
 * ([progressOf]) diskten okunur — süreç yeniden başlasa kaldığı yerden
 * devam eder (tam dosyalar tekrar inmez).
 */
class LocalTtsDownloader(
    context: Context,
    private val onProgress: (doneBytes: Long, currentFile: String) -> Unit = { _, _ -> },
) {

    private val modelDir = LocalTtsLogic.modelDir(context.filesDir)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // 63MB'lık tek dosya — 15 dk tavan (yavaş hatta bile bitsin).
        .readTimeout(900, TimeUnit.SECONDS)
        .build()

    private val lock = Mutex()

    /** Diskteki durum: (dosyalar-var mı, hash'ler-doğru mu, birikmiş bayt). */
    data class DiskState(val present: Boolean, val shaOk: Boolean, val doneBytes: Long)

    /** Diski yoklar — eksik/bozuk dosyaları siler ki indirme tamamlansın. */
    suspend fun inspect(): DiskState = withContext(Dispatchers.IO) {
        val files = LocalTtsLogic.fileList(modelDir)
        var present = true
        var done = 0L
        val expected = LocalTtsLogic.FILES
        for ((i, f) in files.withIndex()) {
            val meta = expected[i]
            if (f.exists() && f.length() == meta.bytes) {
                done += meta.bytes
            } else {
                present = false
                runCatching { if (f.exists()) f.delete() }
                // Fazla boyutlu bozuk dosya da temizlendi → done sayılmaz.
            }
        }
        // Boyutu doğru ama içi bozuk olabilir: hash yalnız hepsi boyutça doğruysa sorulur.
        val shaOk = present && LocalTtsLogic.firstShaMismatch(modelDir) { sha256Of(it) } == null
        if (!shaOk && present) {
            // Boyutlar tuttu ama hash patladı — bozuk dosyaları sil, yeniden indir.
            val bad = LocalTtsLogic.firstShaMismatch(modelDir) { sha256Of(it) }
            bad?.let { rel ->
                DiagLog.w("localtts", "sha uyusmadi - siliniyor: $rel")
                runCatching { File(modelDir, rel).delete() }
            }
        }
        DiskState(present = present && shaOk, shaOk = shaOk, doneBytes = done)
    }

    /**
     * Eksik dosyaları indirir — hepsi tamamsa sessiz döner.
     *
     * @throws IOException ağ/hash hatası; mesaj kullanıcıya gösterilebilir.
     */
    suspend fun ensureModel(): Unit = lock.withLock {
        // Tüm ağ/disk işi IO'da: önceden downloadOne() çağıranın dispatcher'ında
        // (viewModelScope = Main) koşuyordu → NetworkOnMainThreadException,
        // indirme telefonda hiç başlamıyordu.
        withContext(Dispatchers.IO) { ensureModelBlocking() }
    }

    private suspend fun ensureModelBlocking() {
        val disk = inspect()
        if (disk.present) {
            onProgress(disk.doneBytes, "")
            return
        }
        modelDir.mkdirs()
        var done = 0L
        for (meta in LocalTtsLogic.FILES) {
            val target = File(modelDir, meta.relPath)
            val alreadyOk = target.exists() && target.length() == meta.bytes &&
                sha256Of(target) == meta.sha256
            if (alreadyOk) {
                done += meta.bytes
                onProgress(done, meta.relPath)
                continue
            }
            runCatching { if (target.exists()) target.delete() }
            val base = done
            downloadOne(meta, target) { fileBytes -> onProgress(base + fileBytes, meta.relPath) }
            done += meta.bytes
            onProgress(done, meta.relPath)
        }
        val bad = LocalTtsLogic.firstShaMismatch(modelDir) { sha256Of(it) }
        if (bad != null) throw IOException(LocalTtsLogic.shaMismatchMessage(bad, "?", null))
    }

    private fun downloadOne(
        meta: LocalTtsLogic.ModelFile,
        target: File,
        onBytes: (Long) -> Unit = {},
    ) {
        val tmp = File(target.parentFile, target.name + ".part")
        runCatching { tmp.delete() }
        val req = Request.Builder().url(meta.url).get().build()
        try {
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    val snip = runCatching { res.body?.string()?.take(120) }.getOrNull()
                    throw IOException(
                        "İndirme HTTP ${res.code}: ${meta.relPath}" +
                            (snip?.let { " · $it" } ?: ""),
                    )
                }
                val stream = res.body?.byteStream()
                    ?: throw IOException("Boş yanıt gövdesi: ${meta.relPath}")
                tmp.parentFile?.mkdirs()
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var written = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = stream.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        written += n
                        // Büyük dosyada (63 MB) ilerleme dosya bitene kadar %0'da
                        // donuk görünmesin: ~512 KB'de bir bildir.
                        if (written - lastReport >= 512 * 1024) {
                            lastReport = written
                            onBytes(written)
                        }
                    }
                }
            }
            val actual = sha256Of(tmp)
            LocalTtsLogic.requireSha(meta.sha256, actual, meta.relPath)
            if (tmp.length() != meta.bytes) {
                throw IOException("Boyut tutmadı ${meta.relPath}: ${tmp.length()} != ${meta.bytes}")
            }
            if (!tmp.renameTo(target)) throw IOException("Adlandırılamadı: ${meta.relPath}")
            DiagLog.i("localtts", "indirildi ${meta.relPath} (${meta.bytes} bayt, sha ✓)")
        } catch (e: CancellationException) {
            runCatching { tmp.delete() }
            throw e
        } catch (e: Exception) {
            runCatching { tmp.delete() }
            if (e is IOException) throw e
            throw IOException("İndirme başarısız ${meta.relPath}: ${e.message}", e)
        }
    }

    companion object {

        /** Dosyanın sha256 hex özeti (streaming — 63MB belleğe alınmaz). */
        fun sha256Of(file: File): String? = runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { inp ->
                val buf = ByteArray(128 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
