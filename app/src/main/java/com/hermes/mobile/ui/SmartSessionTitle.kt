package com.hermes.mobile.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Oturum içerik ve bağlamından akıllı, açıklayıcı Türkçe başlık üreten ve
 * mükerrer "Sohbet" sorununu ortadan kaldıran saf iş mantığı (v7.7.0).
 */
object SmartSessionTitle {

    private val GENERIC_TITLES = setOf(
        "sohbet", "chat", "?", "", "yeni sohbet", "new chat",
        "oturum", "session", "hermes", "hermes agent", "hermes mobil",
        "örnek kullanıcı", "ornek kullanici", "user", "kullanıcı", "default",
        "—", "-"
    )

    fun isGeneric(title: String?): Boolean {
        val t = title?.trim()?.lowercase().orEmpty()
        if (t.isEmpty() || t in GENERIC_TITLES) return true
        if (t.none { it.isLetter() }) return true
        if (t.startsWith("sohbet ·") || t.startsWith("chat ·")) {
            // "Sohbet · 09.11 09:30" gibi damgalı ama hala jenerik olanlar
            return true
        }
        return false
    }

    /**
     * Oturumun önizlemesi, ilk mesajı, bot adı veya zaman damgasından
     * anlamlı, profesyonel bir Türkçe başlık türetir.
     */
    fun deriveTitle(
        rawTitle: String?,
        preview: String?,
        isSubagent: Boolean = false,
        subagentTarget: String? = null,
        botName: String? = null,
        epochSeconds: Double = 0.0,
        sessionId: String = "",
    ): String {
        // Kullanıcı özel isim verdiyse ve jenerik değilse asla dokunma
        if (!rawTitle.isNullOrBlank() && !isGeneric(rawTitle)) {
            return rawTitle.trim()
        }

        // 1. İçerikten eylem ve konu tespiti
        val text = preview?.trim().orEmpty()
        val derivedFromContent = extractTopicFromText(text)
        if (derivedFromContent != null) {
            return if (isSubagent) "Alt Görev — $derivedFromContent" else derivedFromContent
        }

        // 2. Alt ajan bağlamı
        if (isSubagent) {
            if (!subagentTarget.isNullOrBlank() && !isGeneric(subagentTarget)) {
                return "Alt Görev — $subagentTarget"
            }
            val shortId = sessionId.takeLast(6).ifBlank { "İşlem" }
            return "Alt Görev · $shortId"
        }

        // 3. Bot adı bağlamı
        if (!botName.isNullOrBlank() && !isGeneric(botName)) {
            return "$botName Görevi"
        }

        // 4. Zaman damgalı açıklayıcı başlık
        if (epochSeconds > 0) {
            val date = Date((epochSeconds * 1000).toLong())
            val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
            return "Oturum · ${sdf.format(date)}"
        }

        val shortId = sessionId.takeLast(6).ifBlank { "Yeni" }
        return "Görev · #$shortId"
    }

    /**
     * Önizleme veya mesaj metnindeki belirgin eylemleri ve hedefleri yakalar.
     */
    private fun extractTopicFromText(raw: String): String? {
        if (raw.isBlank() || isMachineNoise(raw)) return null

        val clean = raw.lines()
            .firstOrNull { it.isNotBlank() && !it.startsWith("{") && !it.startsWith("[") }
            ?.replace(Regex("""^[#*\->`\s]+"""), "")
            ?.trim() ?: return null

        val lower = clean.lowercase()

        // Belirli yüksek öncelikli görev kalıpları
        if (lower.contains("raporpro") && (lower.contains("denetim") || lower.contains("incele"))) {
            return "RaporPro Kod Denetimi"
        }
        if (lower.contains("video studio") || (lower.contains("video") && lower.contains("render"))) {
            return "Video Studio Render Görevi"
        }
        if (lower.contains("hermes mobil") && (lower.contains("derle") || lower.contains("build") || lower.contains("apk"))) {
            return "Hermes Mobil Android Derleme"
        }
        if (lower.contains("bot") && (lower.contains("oluştur") || lower.contains("yarat") || lower.contains("ekle"))) {
            return "Bot Oluşturma"
        }
        if (lower.contains("arayüz") && (lower.contains("test") || lower.contains("kontrol"))) {
            return "Alt Görev — Arayüz Testi"
        }

        // Genel eylem kategorileri
        if (lower.contains("apk") || lower.contains("derle") || lower.contains("assemble")) {
            return "Android APK Derleme"
        }
        if (lower.contains("test") || lower.contains("doğrula") || lower.contains("lint")) {
            return "Kod Testi ve Doğrulama"
        }
        if (lower.contains("denetim") || lower.contains("incele") || lower.contains("audit")) {
            return "Teknik Kod Denetimi"
        }
        if (lower.contains("render") || lower.contains("video") || lower.contains("animasyon")) {
            return "Medya ve Render İşlemi"
        }
        if (lower.contains("veritabanı") || lower.contains("sqlite") || lower.contains("sql")) {
            return "Veritabanı İncelemesi"
        }
        if (lower.contains("websocket") || lower.contains("bağlantı") || lower.contains("gateway")) {
            return "Ağ ve Bağlantı Yapılandırması"
        }
        if (lower.contains("rapor") || lower.contains("özet") || lower.contains("doküman")) {
            return "Teknik Rapor Düzenleme"
        }

        // Anlamlı ilk cümleden temiz kısa başlık
        if (clean.length in 4..60 && !clean.contains("http://") && !clean.contains("https://")) {
            // Nokta veya soru işaretine kadar ilk cümle
            val firstSentence = clean.split(Regex("""[.!?\n]""")).firstOrNull()?.trim() ?: clean
            if (firstSentence.length in 4..36) {
                return firstSentence.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
            return cutAtWord(firstSentence, 32).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }

        return null
    }

    /**
     * Durum koduna göre Türkçe durum etiketi ("Tamamlandı", "Çalışıyor", vb.)
     */
    fun statusLabel(working: Boolean, dot: String): String = when {
        working || dot == "green" -> "Çalışıyor"
        dot == "yellow" -> "Bekliyor"
        dot == "red" -> "Hata"
        else -> "Tamamlandı"
    }
}
