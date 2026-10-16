package com.hermes.mobile.data

import com.hermes.mobile.ui.tr

/**
 * DiagLog ERROR/CRASH girişleri → eşleşen **kullanıcı adımı**.
 *
 * Neden: hata kaydı teknik ayrıntıyı söylüyor ama kullanıcı "şimdi ne
 * yapayım" sorusunun cevabını bulamıyordu (phone-harness doctor deseni:
 * her başarısızlık için atılacak TEK adım). Sınıflandırma saf koddur —
 * cihazdan bağımsız birim testlenebilir.
 *
 * Eşleşmeyen hata için genel adım döner: tanı kaydını paylaş. Uydurma
 * öneri yok; yalnız bu listedeki desenler bilinen bir aksiyona bağlanır.
 */
object DiagUserStep {

    enum class Kind {
        /** Kanal kapalı — ajan telefonu kullanamıyor. */
        AGENT_ACCESS,

        /** Tam kontrol kapalı. */
        FULL_CONTROL,

        /** Salt-okunur kip yazma araçlarını reddediyor. */
        READ_ONLY,

        /** Erişilebilirlik servisi kapalı/kopuk. */
        ACCESSIBILITY,

        /** Sunucu profili/token/adres eksik. */
        SERVER_SETUP,

        /** Yetkilendirme reddi — token bayat. */
        TOKEN,

        /** Sunucu tarafı hata (5xx). */
        SERVER_ERROR,

        /** Bağlantı koptu / yeniden deneniyor. */
        NETWORK,

        /** Eşleşme yok — tanılama kaydını paylaş. */
        SHARE_LOG,
    }

    /**
     * Mesaj deseninden adım sınıfı.
     *
     * Sıra önemli: `denied … (read-only mode is on)` gibi mesajlar hem
     * "denied" hem "token" kelimesini içerebilir; önce en özel desenlere
     * bakılır.
     */
    fun kindFor(tag: String, message: String): Kind {
        val m = message.lowercase()
        return when {
            FullControl.WHY_AGENT_OFF.lowercase() in m -> Kind.AGENT_ACCESS
            FullControl.WHY_FULL_CONTROL_OFF.lowercase() in m -> Kind.FULL_CONTROL
            FullControl.WHY_READ_ONLY.lowercase() in m -> Kind.READ_ONLY
            "erişilebilirlik" in m || "accessibility" in m -> Kind.ACCESSIBILITY
            "no server profile" in m || "no token" in m || "bridge url" in m -> Kind.SERVER_SETUP
            "http=401" in m || "http=403" in m || "http=407" in m -> Kind.TOKEN
            "token" in m -> Kind.TOKEN
            "http=5" in m -> Kind.SERVER_ERROR
            tag in setOf("bridge", "ws", "conn", "relay") &&
                ("failed" in m || "closed" in m || "koptu" in m || "timeout" in m) -> Kind.NETWORK
            else -> Kind.SHARE_LOG
        }
    }

    /** Kullanıcının atacağı tek adım — arayüz diliyle. */
    fun text(kind: Kind): String = when (kind) {
        Kind.AGENT_ACCESS -> tr(
            "Ayarlar → Telefon denetimi'nden «Ajan telefonu kullanabilsin»i aç.",
            "Turn on «Agent may use the phone» in Settings → Phone control.",
        )
        Kind.FULL_CONTROL -> tr(
            "Ayarlar → Telefon denetimi'nden «Tam kontrol»ü aç.",
            "Turn on «Full control» in Settings → Phone control.",
        )
        Kind.READ_ONLY -> tr(
            "Ayarlar → Telefon denetimi'nden «Yalnız okunur»u kapat.",
            "Turn off «Read-only» in Settings → Phone control.",
        )
        Kind.ACCESSIBILITY -> tr(
            "Ayarlar → Erişilebilirlik'ten «Hermes tam kontrol» servisini aç/yeniden aç.",
            "Enable (or re-enable) the «Hermes full control» service in Settings → Accessibility.",
        )
        Kind.SERVER_SETUP -> tr(
            "Ayarlar → Sunucu ekle'den adres ve token gir.",
            "Enter the server address and token under Settings → Add server.",
        )
        Kind.TOKEN -> tr(
            "Sunucu token'ını yenile (Ayarlar → Sunucu).",
            "Refresh the server token (Settings → Server).",
        )
        Kind.SERVER_ERROR -> tr(
            "Sunucu tarafında hata var — sunucuyu kontrol et; köprü otomatik yeniden deneyecek.",
            "The server is failing — check the server; the bridge will keep retrying.",
        )
        Kind.NETWORK -> tr(
            "Ağ bağlantısını kontrol et; köprü otomatik yeniden bağlanacak.",
            "Check the network connection; the bridge will reconnect automatically.",
        )
        Kind.SHARE_LOG -> tr(
            "Sorun sürerse bu kaydı «Paylaş» ile gönder.",
            "If it persists, share this log via the «Share» button.",
        )
    }

    /** Tek satır adım — eşleşen sınıf + arayüz dili. */
    fun step(tag: String, message: String): String = text(kindFor(tag, message))
}