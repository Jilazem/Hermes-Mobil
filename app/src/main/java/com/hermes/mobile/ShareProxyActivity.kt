package com.hermes.mobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope

/**
 * Şeffaf bir "paylaşım vekili" activity.
 *
 * Başka uygulamadan (ör. WhatsApp) gelen `ACTION_SEND` niyeti AndroidManifest'te
 * bu activity'ye bağlanır. Burada gelen niyet, mevcut `MainActivity` tekil
 * yaşam döngüsüne (`singleTask`) iletilir; `handleShareHandoff` ekstraplarını
 * okuyup hedef seçim ekranını ([ShareTargetScreen]) açar.
 *
 * `taskAffinity=""` + `excludeFromRecents` kombinasyonu ile bu activity
 * geçici bir köprü; kendi UI'ı yok, yalnız niyeti taşır.
 */
class ShareProxyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent

        val isSend = source?.action == Intent.ACTION_SEND
        val sharedText = source?.getStringExtra(Intent.EXTRA_TEXT)
        var sharedFileUri: Uri? = null
        if (isSend) {
            @Suppress("DEPRECATION")
            sharedFileUri = source?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }

        // Dosya adı + boyut notu: EXTRA_STREAM varsa içerik sağlayıcıdan sor;
        // boyutu AssetDescriptor'dan al (başarısızsa yalnız ad göster).
        val sharedFile = sharedFileUri?.let { uri -> buildFileNote(uri) }

        val token = java.util.UUID.randomUUID().toString()

        // Varsayılan hedef: "Yeni konu". Kullanıcı oturum seçim ekranında
        // son 10 aktifi görüp farklı seçebilir.
        val handoff = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_IS_SHARE, isSend)
            putExtra(EXTRA_SHARED_TEXT, sharedText)
            putExtra(EXTRA_SHARED_FILE, sharedFile)
            putExtra(EXTRA_SHARE_TOKEN, token)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(handoff)
        finish()
    }

    /**
     * Paylaşılan dosya için "ad (boyut)" notu üretir. Gerçek yükleme
     * [com.hermes.mobile.data.HermesClient.uploadManagedFile] ucundan
     * yapılır; burada yalnız gösterim amaçlı meta bilgi eklenir.
     */
    private fun buildFileNote(uri: Uri): String {
        var name = uri.lastPathSegment ?: "paylaşılan-dosya"
        var bytes: Long = -1L
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameIdx >= 0 && !c.isNull(nameIdx)) name = c.getString(nameIdx)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) bytes = c.getLong(sizeIdx)
                }
            }
        }
        // Sorgu boyut vermezse AssetFileDescriptor'tan dene (content:// ve
        // file:// için çalışır).
        if (bytes < 0L) {
            runCatching {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    if (fd.declaredLength >= 0) bytes = fd.declaredLength
                }
            }
        }
        return "$name (${formatBytes(bytes)})"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "boyut bilinmiyor"
        val kb = bytes / 1024.0
        return if (kb < 1024) String.format(java.util.Locale.US, "%.0f KB", kb)
        else String.format(java.util.Locale.US, "%.1f MB", kb / 1024.0)
    }

    companion object {
        const val EXTRA_IS_SHARE = "hermes_is_share"
        const val EXTRA_SHARED_TEXT = "hermes_shared_text"
        const val EXTRA_SHARED_FILE = "hermes_shared_file"
        const val EXTRA_SHARE_TOKEN = "hermes_share_token"
    }
}
