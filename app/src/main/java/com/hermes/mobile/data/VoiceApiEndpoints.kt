package com.hermes.mobile.data

/**
 * Ses hattı (voice_api) uç noktaları — hangi adres denenecek, URL nasıl kurulur.
 *
 * Sözleşme (15.09.2026, repo kopyası `docs/ses-api-sozlesmesi.md`):
 *  - Yerel: `http://<host>:8174` · Dış: `https://<host>/voice-api`
 *  - Kimlik: `X-Hermes-Session-Token` başlığı (yanlış/eksikse 403 — fail-closed)
 *  - `GET /health` · `POST /transcribe` (multipart `audio`, <=60 sn, döner
 *    `{"text","lang"}`) · `POST /synthesize` (JSON `{text, engine}` → `audio/ogg`)
 *
 * [SparkEndpoints] ile **aynı desen**: adaylar sırayla denenir, çalışan
 * hatırlanır ([VoiceApiClient.working]), hepsi düşerse kullanıcıya ne
 * yapacağını söyleyen tek cümle üretilir. Mantık Android'e bağlı değildir —
 * birim testte doğrudan koşar (`VoiceApiEndpointsTest`).
 *
 * Aday üretimi: profil adresi **ev ağı** ise doğrudan `http://<host>:8174`
 * (ses hattı LAN'da token kapısı olmadan da dinler), dış adres ise
 * `<base>/voice-api` (Caddy'nin token kapısı altında). Açık ayar
 * ([VoiceApiEndpoints.candidates] `explicit`) verilirse tek başına o kazanır.
 */
object VoiceApiEndpoints {

    /** voice_api'nin ev ağındaki portu (sözleşme). */
    const val LAN_PORT = 8174

    /** Dış erişimde token kapısı altındaki yol. */
    const val EXTERNAL_PATH = "/voice-api"

    /** Kaydın azami uzunluğu — sözleşme: ses yükleme <= 60 sn. */
    const val MAX_RECORD_MS = 60_000L

    /**
     * Sentez okuma zaman aşımı (ms).
     *
     * Ses ucu ekibinin 15.09 ölçümü: **ilk sentez SOĞUKKEN 173-187 sn**
     * sürüyor (motorlar tembel açılıyor). Bu yüzden istemci okuma zaman aşımı
     * 300 sn'den kısa olamaz; [VoiceApiClient] bu değeri kullanır.
     */
    const val SYNTH_TIMEOUT_MS = 300_000L

    data class Attempt(val base: String, val code: Int, val detail: String = "") {
        val reachable: Boolean get() = code > 0
        val ok: Boolean get() = code in 200..299
    }

    /**
     * Denenecek taban adresler, sırayla.
     *
     * @param explicit ayarlardaki ses ucu adresi (varsa tek başına kazanır)
     * @param preferred son çalışan adres — `http://host:8174` ya da
     *   `host/voice-api` biçiminde verilebilir; ikisi de profil adresine geri
     *   çevrilir (yoksa `…/voice-api/voice-api` üretilirdi).
     */
    fun candidates(
        profile: ServerProfile,
        explicit: String = "",
        preferred: String? = null,
    ): List<String> {
        val explicitTrim = explicit.trim().trimEnd('/')
        if (explicitTrim.isNotBlank()) return listOf(explicitTrim)
        val preferredBase = preferred?.let { normalizePreferred(it) }
        val ordered = buildList {
            preferredBase?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(profile.candidates)
        }.distinct()
        return ordered.mapNotNull { base ->
            val url = base.trim().trimEnd('/')
            if (url.isBlank()) return@mapNotNull null
            val host = hostOf(url) ?: return@mapNotNull null
            if (isPrivateHost(host)) "http://$host:$LAN_PORT" else url + EXTERNAL_PATH
        }.distinct()
    }

    /**
     * Hatırlanan adresi profil biçimine döndürür.
     *
     * `192.168.1.101:8174` → `http://192.168.1.101:9150` gibi bir profil
     * adresine birebir çevrilemeyeceği için yalnız **şema ve port** kırpılır;
     * kalan host [candidates] içinde profil adaylarıyla eşleşmese de ilk sırada
     * denenir (kullanıcı o adresi elle vermiş olabilir).
     */
    fun normalizePreferred(preferred: String): String {
        var s = preferred.trim().trimEnd('/')
        if (s.endsWith(EXTERNAL_PATH)) s = s.removeSuffix(EXTERNAL_PATH)
        if (s.endsWith(":$LAN_PORT")) s = s.removeSuffix(":$LAN_PORT")
        return s
    }

    fun health(base: String): String = join(base, "/health")
    fun transcribe(base: String): String = join(base, "/transcribe")
    fun synthesize(base: String): String = join(base, "/synthesize")

    /** Taban + yol; taban zaten yol içeriyorsa tekrar eklenmez. */
    fun join(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return b + p
    }

    /**
     * Başarısız denemeleri kullanıcıya ANLAŞILIR tek cümleye çevirir.
     *
     * @param t dil seçici — `ui.tr` üretimde, testte sabit lambda
     */
    fun describe(attempts: List<Attempt>, t: (String, String) -> String): String {
        if (attempts.isEmpty()) {
            return t(
                "Ses ucu adresi tanımlı değil — Ayarlar → Ses bölümüne adres gir",
                "No voice endpoint configured — set one under Settings → Voice",
            )
        }
        val tried = attempts.joinToString(", ") { "${it.base} (${codeText(it.code)})" }
        val gate = attempts.firstOrNull { it.code == 403 || it.code == 401 }
        val missing = attempts.firstOrNull { it.code == 404 }
        val unreach = attempts.none { it.reachable }
        val head = when {
            gate != null -> t(
                "Ses ucu isteği reddetti (HTTP ${gate.code}) — oturum tokeni eksik ya da " +
                    "yanlış; Ayarlar → Sunucular'dan tokeni tazele. Ev ağında " +
                    ":$LAN_PORT doğrudan, dışarıda $EXTERNAL_PATH token ister.",
                "The voice endpoint rejected the request (HTTP ${gate.code}) — the session " +
                    "token is missing or wrong; refresh it under Settings → Servers. On the " +
                    "home network use :$LAN_PORT directly, externally $EXTERNAL_PATH needs a token.",
            )
            missing != null -> t(
                "Sunucuda ses ucu yok (HTTP 404) — voice_api çalışmıyor ya da adres yanlış",
                "No voice endpoint on the server (HTTP 404) — voice_api is down or the address is wrong",
            )
            unreach -> t(
                "Ses ucuna ulaşılamıyor (ev ağında :$LAN_PORT, dışarıda $EXTERNAL_PATH)",
                "Voice endpoint unreachable (:$LAN_PORT on the home network, $EXTERNAL_PATH externally)",
            )
            else -> t("Ses ucu yanıt veremedi", "The voice endpoint failed to respond")
        }
        return "$head · $tried"
    }

    fun codeText(code: Int): String = if (code > 0) "HTTP $code" else "ulaşılamadı"

    /** `/health` gövdesini özetler — Ayarlar'daki "şimdi dene" satırı. */
    fun healthLine(h: VoiceHealth, t: (String, String) -> String): String {
        if (!h.ok) {
            return t("motor kapalı (motorlar ilk çağrıda açılır)", "engine down (engines start on first call)")
        }
        val stt = if (h.sttOk) t("metinleştirme açık", "transcription on")
        else t("metinleştirme kapalı", "transcription off")
        val engineText = if (h.engines.isEmpty()) t("motor durumu bilinmiyor", "engine state unknown")
        else h.engines.entries.joinToString(", ") { (k, v) -> "$k=$v" }
        return "$stt · $engineText"
    }

    private fun hostOf(url: String): String? {
        val withScheme = if (url.contains("://")) url else "http://$url"
        val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
        return uri.host?.takeIf { it.isNotBlank() }
    }
}
