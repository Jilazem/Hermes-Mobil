package com.hermes.mobile.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Sunucu dosyası indirme kanalı (iş #1021 B2).
 *
 * Ajanın ürettiği dosyalar `/api/files/download` ucundan iner. Kimlik
 * `Authorization: *** başlığında taşınır — token URL'ye, istek
 * satırına, harici tarayıcıya ve sunucu erişim kayıtlarına yazılmaz.
 * Sunucu geriye uyum için `?token=` sorgusunu da kabul eder
 * (`_QUERY_TOKEN_API_PATHS`); istemci tarafı bilinçli olarak yalnız
 * başlığı kullanır.
 */
object ServerFileDownload {

    /** İndirme isteği: token başlıkta, URL'de yalnız tam kodlanmış yol. */
    fun request(baseUrl: String, token: String, path: String): Request =
        Request.Builder()
            .url(
                baseUrl.trimEnd('/') +
                    "/api/files/download?path=" +
                    java.net.URLEncoder.encode(path, "UTF-8")
            )
            .header("Authorization", "Bearer $token")
            .build()

    /**
     * Dosyayı [target]'a indirir; HTTP 2xx değilse [okhttp3.HttpException]
     * benzeri [IllegalStateException] fırlatır. Ağ/disk işi IO'da.
     */
    suspend fun downloadToFile(
        client: OkHttpClient,
        baseUrl: String,
        token: String,
        path: String,
        target: File,
    ): File = withContext(Dispatchers.IO) {
        client.newCall(request(baseUrl, token, path)).execute().use { resp ->
            check(resp.isSuccessful) { "indirme başarısız: HTTP ${resp.code}" }
            target.parentFile?.mkdirs()
            target.outputStream().use { out ->
                resp.body?.byteStream()?.copyTo(out) ?: error("boş yanıt gövdesi")
            }
        }
        target
    }

    /** Açılış niyeti için MIME türü — bilinmeyenler ikili akış. */
    fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "txt", "md" -> "text/plain"
            "html", "htm" -> "text/html"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "yaml", "yml" -> "application/yaml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "zip" -> "application/zip"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "udf" -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }
}
