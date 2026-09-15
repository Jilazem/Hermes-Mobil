package com.hermes.mobile.data

/**
 * sparkDash uç noktaları — hangi host + path denenecek ve hata nasıl anlatılacak.
 *
 * Sahada ölçülen durum (v2 logu 12:55 + Mac denetimi, 2026-09-15):
 *  - `GET /spark-api/api/sparks` → **403** (Caddy'nin `@spark_ok` kapısı
 *    `X-Hermes-Session-Token` başlığını `SPARK_GATE_TOKEN` ile karşılaştırıyor;
 *    LaunchAgent ortamında bu değişken **tanımlı değil** → hiçbir istek eşleşmez)
 *  - `GET /api/sparks` (yerel, tokenli) → **404** (Hermes panosunda böyle bir uç yok)
 *  - `GET http://192.168.1.101:5555/api/sparks` (LAN, doğrudan sparkDash) → **200** ✓
 *
 * Yani çalışan yol LAN'da doğrudan 5555; dış yolda kapı anahtarı sunucu tarafında
 * eksik. Uygulamanın işi: **doğru adayları sırayla denemek** (tek sağlıklı adres),
 * çalışanı hatırlamak ve hiçbiri çalışmıyorsa **ne yapılacağını söyleyen** bir
 * hata göstermek. Kapı anahtarı Caddy yapılandırmasıdır — canlı sunucuya
 * dokunulmaz, not olarak raporlanır.
 *
 * Saf mantık: Android'e bağlı değil, birim testte doğrudan koşar.
 */
object SparkEndpoints {

    /** sparkDash'in ev ağındaki portu (kimlik doğrulaması yok → internete kapalı). */
    const val LAN_PORT = 5555

    /** Dış erişimde Caddy'nin token kapısı altındaki yol. */
    const val EXTERNAL_PATH = "/spark-api"

    data class Attempt(val base: String, val code: Int, val detail: String = "") {
        val reachable: Boolean get() = code > 0
        val ok: Boolean get() = code in 200..299
    }

    /**
     * Denenecek taban adresler, sırayla.
     *
     * @param explicit ayarlardaki sparkDash adresi (varsa tek başına kazanır)
     * @param preferred son çalışan adres — hem profil adresi biçiminde
     *   (`https://host`) hem de türetilmiş biçimde (`https://host/spark-api`,
     *   `http://host:5555`) verilebilir; ikisi de aynı sonuca çıkar.
     */
    fun candidates(profile: ServerProfile, explicit: String = "", preferred: String? = null): List<String> {
        val explicitTrim = explicit.trim().trimEnd('/')
        if (explicitTrim.isNotBlank()) return listOf(explicitTrim)
        // Türetilmiş biçimde verilen "son çalışan"ı profil adresine geri çevir,
        // yoksa `…/spark-api/spark-api` üretirdik (ölçüldü: test kırmızısı).
        val preferredBase = preferred?.trim()?.trimEnd('/')?.let {
            if (it.endsWith(EXTERNAL_PATH)) it.removeSuffix(EXTERNAL_PATH) else it
        }
        val ordered = buildList {
            preferredBase?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(profile.candidates)
        }.distinct()
        return ordered.mapNotNull { base ->
            val url = base.trim().trimEnd('/')
            val host = hostOf(url) ?: return@mapNotNull null
            if (isPrivateHost(host)) "http://$host:$LAN_PORT"
            else url + EXTERNAL_PATH
        }.distinct()
    }

    /**
     * Başarısız denemeleri kullanıcıya ANLAŞILIR tek cümleye çevirir.
     *
     * @param t dil seçici — `ui.tr` üretimde, testte sabit lambda
     */
    fun describe(attempts: List<Attempt>, t: (String, String) -> String): String {
        if (attempts.isEmpty()) {
            return t(
                "Spark adresi tanımlı değil — Ayarlar → Spark bölümüne adres gir",
                "No Spark address configured — set one under Settings → Spark",
            )
        }
        val tried = attempts.joinToString(", ") { "${it.base} (${codeText(it.code)})" }
        val gate = attempts.firstOrNull { it.code == 403 || it.code == 401 }
        val missing = attempts.firstOrNull { it.code == 404 }
        val unreach = attempts.none { it.reachable }
        val head = when {
            gate != null -> t(
                "Spark kapısı isteği reddetti (HTTP ${gate.code}) — sunucu tarafı iş: " +
                    "dış yol için SPARK_GATE_TOKEN ayarı gerekli; şimdilik ev ağında açın",
                "The Spark gate rejected the request (HTTP ${gate.code}) — server-side work: " +
                    "the external route needs SPARK_GATE_TOKEN; open this on the home network for now",
            )
            missing != null -> t(
                "Sunucuda sparkDash ucu yok (HTTP 404) — sparkDash çalışmıyor ya da adres yanlış",
                "No sparkDash endpoint on the server (HTTP 404) — sparkDash is down or the address is wrong",
            )
            unreach -> t(
                "Spark adresine ulaşılamıyor (ev ağında :$LAN_PORT, dışarıda $EXTERNAL_PATH)",
                "Spark address unreachable (:$LAN_PORT on the home network, $EXTERNAL_PATH externally)",
            )
            else -> t("Spark panosu yüklenemedi", "Spark panel failed to load")
        }
        return "$head · $tried"
    }

    fun codeText(code: Int): String = if (code > 0) "HTTP $code" else "ulaşılamadı"

    private fun hostOf(url: String): String? {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        return uri.host?.takeIf { it.isNotBlank() }
    }
}
