package com.hermes.mobile.data

/**
 * Tur-21 — JEV rozetleri karar katmanı (saf, JVM testli, boş-safe).
 *
 * Masaüstündeki `jev_gate` / `jev_guard` eklentileri her turda JSON satırı
 * bırakıyor (`oturum`, `profil`, `karar`...). Mobilde oturum şeridi/kartında
 * GÖRSEL ipuç: yeşil = gate'ten geçen, sarı = log-only gözlem,
 * kırmızı = iade edilen tur.
 *
 * UYDURMA YOK: gateway şu an bu alanı GÖNDERMİYOR (kontrol: 20.09.2026 —
 * mobildeki tüm `Models.kt`/`GatewayWsClient` alanlarında `jev` yok; masaüstü
 * logları gateway üzerinden taşınmıyor). Alan gelmeyene kadar rozet ÇİZİLMEZ;
 * boş hali testlenmiştir. Alan adı ve değer kümesi için backend sözleşme
 * notu: `denetim/tur21/JEV-BACKEND-SOZLESMES.md`.
 *
 * Kabul edilen ham değerler (büyük/küçük harf duyarsız, trim):
 *  - "gec", "gecis", "ok", "pass"            → GREEN
 *  - "gozlem", "log", "log-only", "logonly"  → YELLOW
 *  - "iade", "reddedildi", "fail", "blocked" → RED
 *  - boş/bilinmeyen                          → YOK (sessizlik, uydurma yok)
 */
object JevBadgeLogic {

    /** Rozet durumları — YOK = veri yok, çizilmez (boş-safe varsayılan). */
    enum class Badge { None, Green, Yellow, Red }

    /** Ham gateway alanını çözer — tanınmayan hiçbir şey renk UYDURMAZ. */
    fun parse(raw: String?): Badge {
        val v = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return Badge.None
        return when (v) {
            "gec", "gecis", "ok", "pass", "passed" -> Badge.Green
            "gozlem", "log", "log-only", "logonly", "observe" -> Badge.Yellow
            "iade", "reddedildi", "fail", "failed", "blocked" -> Badge.Red
            else -> Badge.None
        }
    }

    /** Rozet çizilebilir mi (None → false; boş durum tasarımı bunu kullanır). */
    fun visible(b: Badge): Boolean = b != Badge.None

    /** Tooltip / içerik tanımı (erişilebilirlik + uzun basım). */
    fun tooltip(b: Badge, t: (String, String) -> String): String = when (b) {
        Badge.Green -> t("JEV: tur gate'ten geçti", "JEV: round passed the gate")
        Badge.Yellow -> t("JEV: log-only gözlem", "JEV: log-only observation")
        Badge.Red -> t("JEV: tur iade edildi", "JEV: round was returned")
        Badge.None -> ""
    }

    /**
     * İstatistik şeridi özeti — rozet sayılarından tek satır.
     *
     * Üçü de 0 ise boş satır döner (veri yoksa şerit GÖSTERİLMEZ).
     */
    fun summaryLine(
        green: Int,
        yellow: Int,
        red: Int,
        t: (String, String) -> String,
    ): String {
        if (green + yellow + red <= 0) return ""
        return t("JEV", "JEV") + " · ✓$green · ◐$yellow · ✕$red"
    }
}
