package com.hermes.mobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import com.hermes.mobile.data.ShareHandoff
import com.hermes.mobile.data.ShareStaging

/**
 * Şeffaf bir "paylaşım vekili" activity.
 *
 * Başka uygulamadan (ör. WhatsApp) gelen `ACTION_SEND` niyeti AndroidManifest'te
 * bu activity'ye bağlanır. Burada gelen niyet `MainActivity`'e iletilir;
 * `handleShareHandoff` ekstrapları okuyup hedef seçim ekranını
 * ([ShareTargetScreen]) açar. Ekstrap anahtarlarının TEK kaynağı
 * [ShareHandoff] (sözleşme testi: ShareHandoffContractTest).
 *
 * Dosya paylaşımında (HIGH-1 teli): EXTRA_STREAM içeriği
 * cacheDir/share_inbox'a AKIŞ kopyalanır ([ShareStaging.stage] — readBytes
 * yok, büyük dosyada OOM riski yok) ve kopyanın yolu EXTRA_STAGED_FILE ile
 * taşınır. Yükleme burada DEĞİL, kullanıcı hedefi seçtikten sonra
 * ChatViewModel.attachShareFile → HermesClient.uploadManagedFile(File) →
 * POST /api/files/upload-stream ile yapılır; staging kopyası gönderim
 * sonrası plan.cleanupPaths() ile silinir.
 *
 * `taskAffinity=""` + `excludeFromRecents` kombinasyonu ile bu activity
 * geçici bir köprü; kendi UI'ı yok, yalnız niyeti taşır.
 */
class ShareProxyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent

        // Paylaşım gelen kutusu girişte süpürülür (denetmen ONERI-1): süreç
        // günlerce ayakta kalabilir; HermesApp.onCreate tek başına yetmez.
        ShareStaging.purgeStale(ShareStaging.inboxDir(cacheDir), olderThanMs = 3_600_000L)

        val isSend = source?.action == Intent.ACTION_SEND
        val sharedText = source?.getStringExtra(Intent.EXTRA_TEXT)
        var sharedFileUri: Uri? = null
        if (isSend) {
            @Suppress("DEPRECATION")
            sharedFileUri = source?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }

        // Staging dosya adı için taze nonce (kopya adı çakışmasın); el
        // sıkışması token'ı ise süreç-ömrü ShareHandoff.secret'tir —
        // MainActivity token == secret karşılaştırır (denetmen3 fast-follow).
        val stagingNonce = java.util.UUID.randomUUID().toString()

        // Dosya: önce meta (ad+boyut) sonra staging. Sıra önemli: vekil
        // süreci finish() sonrası ölebilir, bu yüzden kopya BURADA bitmeli
        // (denetmen #6 — GlobalScope'a savurmak yok; onCreate içinde koşan
        // küçük bir IO bloğu, paylaşım dosyaları için kabul edilebilir).
        var fileNote: String? = null
        var stagedPath: String? = null
        sharedFileUri?.let { uri ->
            val (name, size) = queryMeta(uri)
            fileNote = "$name (${formatBytes(size)})"
            val staged = runCatching {
                ShareStaging.stage(
                    input = contentResolver.openInputStream(uri),
                    dir = ShareStaging.inboxDir(cacheDir),
                    token = stagingNonce,
                    displayName = name,
                )
            }.getOrNull()
            stagedPath = staged?.absolutePath
            if (staged == null) {
                // Kopyalanamadı: plan ShareUploadPlan.Unreadable üretecek —
                // UI'da not görünür ama sessiz yükleme denemesi olmaz,
                // MainActivity DiagLog'a yazar (sessiz kayıp yok).
                com.hermes.mobile.data.DiagLog.w(TAG, "paylasim staging başarısız: $fileNote")
            }
        }

        // Varsayılan hedef: "Yeni konu". Kullanıcı oturum seçim ekranında
        // son 10 aktifi görüp farklı seçebilir.
        val handoff = Intent(this, MainActivity::class.java).apply {
            putExtra(ShareHandoff.EXTRA_IS_SHARE, isSend)
            putExtra(ShareHandoff.EXTRA_SHARED_TEXT, sharedText)
            putExtra(ShareHandoff.EXTRA_SHARED_FILE, fileNote)
            putExtra(ShareHandoff.EXTRA_SHARE_TOKEN, ShareHandoff.secret)
            stagedPath?.let { putExtra(ShareHandoff.EXTRA_STAGED_FILE, it) }
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(handoff)
        finish()
    }

    /** İçerik sağlayıcıdan ad + boyut sorar (OpenableColumns; yedek AFD). */
    private fun queryMeta(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "paylasilan-dosya"
        var bytes = -1L
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) bytes = c.getLong(sizeIdx)
                }
            }
        }
        if (bytes < 0L) {
            runCatching {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    if (fd.declaredLength >= 0) bytes = fd.declaredLength
                }
            }
        }
        return name to bytes
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "boyut bilinmiyor"
        val kb = bytes / 1024.0
        return if (kb < 1024) String.format(java.util.Locale.US, "%.0f KB", kb)
        else String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
    }

    private companion object {
        const val TAG = "ShareProxy"
    }
}
