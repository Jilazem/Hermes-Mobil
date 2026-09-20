package com.hermes.mobile.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Yerel LLM karar katmanı — **saf** (Android yok, JVM testi koşar).
 *
 * Sözleşme (node1 :8888, caddy ardındaki vLLM — 20.09.2026 canlı ölçümü):
 *  - `GET /v1/models` → `{"data":[{"id":"Qwen/Qwen3.8-Flash-Next",...}]}`
 *  - `POST /v1/chat/completions` → OpenAI standardı (`choices[0].message.content`).
 *
 * Model kilidi kuralı (tur-21): beklenen model servis edilmiyorsa **null**
 * döner ve çağıran açık hata gösterir — sessiz başka modele/sunucuya geçiş YOK.
 */
object LocalModelLogic {

    /** Yerli varsayılan model kimliği (node1 registry — 20.09.2026 doğrulandı). */
    const val DEFAULT_MODEL = "Qwen/Qwen3.8-Flash-Next"

    /** Yerel asistan isteği sistem yönergesi — kısa ve doğal cevap ister. */
    const val SYSTEM_PROMPT =
        "Sen Hermes'sin — kişisel yapay zekâ asistanı. Türkçe konuş. Sesli " +
            "sohbette kısa ve doğal cümleler kur; uzun liste okuma. Emin " +
            "değilsen söyle."

    /**
     * /v1/models verisinden istenen modeli seçer.
     *
     * @param wanted beklenen model id'si (boşsa [DEFAULT_MODEL])
     * @param servedId servis edilen model id'si (`data[0].id`)
     * @return eşleşme varsa id; yoksa **null** — çağıran açık hata gösterir.
     */
    fun pickModel(wanted: String?, servedId: String?): String? {
        val want = wanted?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        val served = servedId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return served.takeIf { it == want }
    }

    /** /v1/models çıktısından ilk model id'sini çıkarır (saf regex — JVM testli). */
    fun parseServedId(json: String): String? =
        Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(json)?.groupValues?.get(1)

    /**
     * Model adı eşleşmediğinde kullanıcıya gösterilen satır. Kilidi kırma:
     * otomatik geçiş önerisi yok — kullanıcı Ayarlar'dan kendisi seçer.
     */
    fun modelMismatchMessage(
        wanted: String,
        served: String?,
        t: (String, String) -> String,
    ): String = if (served.isNullOrBlank()) {
        t(
            "Yerel model listesi alınamadı: $wanted bekleniyor",
            "Could not read the local model list: expected $wanted",
        )
    } else {
        t(
            "Yerel model adı farklı: beklenen \"$wanted\", servis edilen \"$served\". " +
                "Modeli Ayarlar'dan değiştir ya da node tarafını düzelt.",
            "Local model name differs: expected \"$wanted\", served \"$served\". " +
                "Change the model in Settings or fix the node.",
        )
    }

    /** HTTP hatası kullanıcı satırı — kod + kısa gövde ipucu; yutulmaz (D-04). */
    fun httpError(code: Int, bodySnippet: String?, t: (String, String) -> String): String {
        val base = when {
            code == 401 || code == 403 ->
                t("Yerel model isteği reddetti ($code)", "Local model rejected the request ($code)")
            code == 502 || code == 503 ->
                t("Yerel model hazır değil / meşgul ($code)", "Local model not ready or busy ($code)")
            code >= 500 -> t("Yerel model hatası ($code)", "Local model error ($code)")
            else -> t("Yerel istek başarısız ($code)", "Local request failed ($code)")
        }
        val snip = bodySnippet?.trim()?.take(160)
        return if (snip.isNullOrBlank()) base else "$base · $snip"
    }

    /**
     * Ağ hatası kullanıcı satırı — denenen adres + ne yapılacağı (tur-10
     * dersi: hangi adresin denendiği mutlaka görünür, sessiz Gemini'ye geçme).
     */
    fun unreachableMessage(url: String, reason: String, t: (String, String) -> String): String =
        t(
            "Yerel node'a ulaşılamadı: $url — $reason. Telefon ile node aynı " +
                "ağda olmalı; adresi Ayarlar'dan düzeltebilirsin.",
            "Local node unreachable: $url — $reason. The phone and the node must be " +
                "on the same network; you can fix the address in Settings.",
        )

    /** Sohbette/kartta gösterilen köken etiketi. */
    fun sourceLabel(t: (String, String) -> String): String =
        t("Yerel (node1)", "Local (node1)")

    /** Hata gövdesinden kısa ipucu (azami 200 karakter). */
    fun bodySnippet(body: String?): String? =
        body?.trim()?.takeIf { it.isNotEmpty() }?.take(200)

    /** JSON telifi — chat gövdesi elle üretilir (kotlinx yok; saf ve test edilebilir). */
    fun jsonEscape(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }

    /** Chat isteği gövdesi: system + kullanıcı turu (OpenAI messages). */
    fun chatBody(model: String, system: String, userText: String): String =
        "{\"model\":\"${jsonEscape(model)}\",\"stream\":false,\"messages\":[" +
            "{\"role\":\"system\",\"content\":\"${jsonEscape(system)}\"}," +
            "{\"role\":\"user\",\"content\":\"${jsonEscape(userText)}\"}]}"

    /**
     * `choices[0].message.content` — tam parser yerine hedefli regex; dönüşte
     * JSON kaçışları çözülür. Content yoksa null (çağıran hata üretir).
     */
    fun extractContent(json: String): String? {
        val m = Regex("\"choices\"\\s*:\\s*\\[\\s*\\{.*?\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", RegexOption.DOT_MATCHES_ALL)
            .find(json) ?: return null
        return unescapeJsonString(m.groupValues[1])
    }

    /** JSON telifli String → ham metin. */
    fun unescapeJsonString(s: String): String = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    '"' -> append('"')
                    '\\' -> append('\\')
                    '/' -> append('/')
                    'u' -> {
                        val hex = if (i + 6 <= s.length) s.substring(i + 2, i + 6) else null
                        val cp = hex?.toIntOrNull(16)
                        if (cp != null) { append(cp.toChar()); i += 4 } else append(n)
                    }
                    else -> append(n)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}

/**
 * Yerel LLM HTTP istemcisi — tek adres, kısa bağlantı + uzun okuma zaman
 * aşımı. Her hata kullanıcıya gösterilebilir mesajla döner (IOException
 * message'ı olduğu gibi ekrana yazılır).
 */
class LocalModelClient(
    /** Ayarlardaki taban adres — MainActivity adres değişince yenisini kurar. */
    val baseUrl: String,
    /** Cevap okuma tavanı — düşünce uzun olabilir (varsayılan 120 sn). */
    private val readTimeoutMs: Long = 120_000L,
    private val httpFactory: (connectMs: Long, readMs: Long) -> OkHttpClient = { c, r ->
        OkHttpClient.Builder()
            .connectTimeout(c, TimeUnit.MILLISECONDS)
            .readTimeout(r, TimeUnit.MILLISECONDS)
            .build()
    },
) {

    /** Adres boşsa null — çağıran "adres gir" hatası gösterir. */
    fun isConfigured(): Boolean = baseUrl.trim().isNotEmpty()

    /**
     * `GET /v1/models` — sağlık + model kilidi kontrolü.
     *
     * @return beklenen model eşleştiyse id; eşleşmezse **null** (kilidi kırma:
     *   hata metnini [LocalModelLogic.modelMismatchMessage] ile üret).
     */
    @Throws(IOException::class)
    suspend fun healthModel(wanted: String = LocalModelLogic.DEFAULT_MODEL): String? =
        withContext(Dispatchers.IO) {
            val raw = getRaw("/v1/models", connectMs = 8_000L, readMs = 8_000L)
            LocalModelLogic.pickModel(wanted, LocalModelLogic.parseServedId(raw))
        }

    /** Ham GET — hata kodları kullanıcı mesajına çevrilir. */
    @Throws(IOException::class)
    private fun getRaw(path: String, connectMs: Long, readMs: Long): String {
        val url = url(path)
        return try {
            httpFactory(connectMs, readMs).newCall(
                Request.Builder().url(url).get().build(),
            ).execute().use { res ->
                val body = res.body?.string()
                if (!res.isSuccessful) {
                    throw localized(
                        LocalModelLogic.httpError(
                            res.code,
                            LocalModelLogic.bodySnippet(body),
                            ::tr,
                        ),
                    )
                }
                body ?: throw localized("boş yanıt $path")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalModelException) {
            throw e
        } catch (e: IOException) {
            throw localized(
                LocalModelLogic.unreachableMessage(url, e.message ?: e.javaClass.simpleName, ::tr),
                e,
            )
        }
    }

    /**
     * Tek tur sohbet — yanıt metni döner. Hatalar kullanıcı formatlıdır
     * ([LocalModelException]); arayana "neden yerelde kilitli" cevabıdır.
     */
    @Throws(IOException::class)
    suspend fun chat(
        text: String,
        model: String = LocalModelLogic.DEFAULT_MODEL,
        system: String = LocalModelLogic.SYSTEM_PROMPT,
    ): String = withContext(Dispatchers.IO) {
        val url = url("/v1/chat/completions")
        val payload = LocalModelLogic.chatBody(model, system, text)
            .toRequestBody(JSON_MEDIA)
        try {
            httpFactory(8_000L, readTimeoutMs).newCall(
                Request.Builder().url(url).post(payload).build(),
            ).execute().use { res ->
                val raw = res.body?.string()
                if (!res.isSuccessful) {
                    throw localized(
                        LocalModelLogic.httpError(
                            res.code,
                            LocalModelLogic.bodySnippet(raw),
                            ::tr,
                        ),
                    )
                }
                raw ?: throw localized("boş chat yanıtı")
                LocalModelLogic.extractContent(raw)
                    ?: throw localized("Yerel yanıt çözümlenemedi (choices/content yok)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalModelException) {
            throw e
        } catch (e: IOException) {
            throw localized(
                LocalModelLogic.unreachableMessage(url, e.message ?: e.javaClass.simpleName, ::tr),
                e,
            )
        }
    }

    private fun url(path: String): String =
        baseUrl.trim().trimEnd('/') + path

    private fun localized(message: String, cause: Throwable? = null): LocalModelException =
        LocalModelException(message, cause)

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** İstemci içinden gelen mesajlar Türkçe üretilir (tr/en ikilisi). */
        fun tr(t: String, e: String): String =
            com.hermes.mobile.ui.tr(t, e)
    }
}

/** Kullanıcıya gösterilebilir yerel model hatası — mesajı olduğu gibi yazılır. */
class LocalModelException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
