package com.hermes.mobile.data

/**
 * K2 — "Doctor" kartının **saf** çekirdeği (phone-harness doctor benimseme).
 *
 * Tek dokunuşta telefon-tarafı önkoşul zincirini tarar: erişilebilirlik,
 * bildirim erişimi, Shizuku, köprü WS, sunucu erişimi, Android Auto geliştirici
 * modu. Her satır için ✓ / ✗ / ? ve kullanıcıya "şimdi yap" diyeceği TEK aksiyon
 * üretilir ([Fix]); UI bu aksiyonu derin bağlantıya çevirir.
 *
 * Bilgi sınırı (uydurma yok): Android Auto'nun **geliştirici modu** üçüncü taraf
 * uygulamadan okunamaz — Android Auto kendi tercihinde tutar. Bu satır bu yüzden
 * daima [Status.UNKNOWN] döner ve "DOĞRULANAMADI" olarak raporlanır.
 *
 * phone-harness doctor'un kendi kontrol listesi upstream main@68d883f'te (PR #138);
 * yerel kurulumda (0.3.2) yok → içerik CZİP 8pa3sv + GAP raporu kapsamından alındı.
 */
object DoctorLogic {

    enum class Status { OK, FAIL, UNKNOWN }

    /** "Şimdi yap" aksiyonları — her biri UI'da tek bir derin bağlantı. */
    enum class Fix {
        OPEN_ACCESSIBILITY,
        OPEN_NOTIFICATION_ACCESS,
        OPEN_SHIZUKU,
        RECONNECT_BRIDGE,
        OPEN_SERVERS,
        OPEN_ANDROID_AUTO,
        NONE,
    }

    /** Shizuku durumu — [ShizukuBridge.State] ile birebir, ama Android'siz. */
    enum class ShizukuStatus { READY, NEEDS_PERMISSION, UNAVAILABLE }

    /** Köprü WS durumu — [BridgeState] ile birebir, ama Android'siz. */
    enum class BridgeStatus { OFF, CONNECTING, CONNECTED, RETRYING, TAKEN_OVER }

    /** Tek tarama satırı. `key` makine kimliği, `detail` insana açıklama. */
    data class Check(
        val key: String,
        val status: Status,
        val detail: String,
        val fix: Fix,
    )

    /** Tarama satırı anahtarları — sıra rapor sırasıdır. */
    val KEYS = listOf("a11y", "notification", "shizuku", "bridge", "server", "android_auto")

    /**
     * Altı alanı tek listede toplar.
     *
     * @param serverOk `null` = sunucu profili yok ya da hiç yoklanmadı
     *   (bilinmiyor); `true`/`false` = yoklama sonucu, [serverDetail] gerekçe.
     */
    fun checks(
        a11yEnabled: Boolean,
        a11yRunning: Boolean,
        notificationAccess: Boolean,
        shizuku: ShizukuStatus,
        bridge: BridgeStatus,
        serverOk: Boolean?,
        serverDetail: String,
        androidAutoInstalled: Boolean,
    ): List<Check> = listOf(
        when {
            a11yEnabled && a11yRunning -> Check(
                "a11y", Status.OK,
                "erişilebilirlik servisi çalışıyor",
                Fix.NONE,
            )
            a11yEnabled -> Check(
                "a11y", Status.FAIL,
                "izin verilmiş ama servis kapalı (sistem henüz başlatmadı) — tam kontrol çalışmaz",
                Fix.OPEN_ACCESSIBILITY,
            )
            else -> Check(
                "a11y", Status.FAIL,
                "tam kontrol izni yok — ekran okuma/dokunma çalışmaz",
                Fix.OPEN_ACCESSIBILITY,
            )
        },
        if (notificationAccess) {
            Check("notification", Status.OK, "bildirim erişimi verilmiş", Fix.NONE)
        } else {
            Check(
                "notification", Status.FAIL,
                "bildirim erişimi yok — mesaj okuma/yanıt ve takip çalışmaz",
                Fix.OPEN_NOTIFICATION_ACCESS,
            )
        },
        when (shizuku) {
            ShizukuStatus.READY ->
                Check("shizuku", Status.OK, "Shizuku hazır — derin kontrol açık", Fix.NONE)
            ShizukuStatus.NEEDS_PERMISSION ->
                Check(
                    "shizuku", Status.FAIL,
                    "Shizuku servisi ayakta ama izin verilmemiş",
                    Fix.OPEN_SHIZUKU,
                )
            ShizukuStatus.UNAVAILABLE ->
                Check(
                    "shizuku", Status.FAIL,
                    "Shizuku kurulu değil ya da servis kapalı (yalnız derin kontrolü etkiler)",
                    Fix.OPEN_SHIZUKU,
                )
        },
        when (bridge) {
            BridgeStatus.CONNECTED ->
                Check("bridge", Status.OK, "köprü WS bağlı", Fix.NONE)
            BridgeStatus.CONNECTING ->
                Check("bridge", Status.FAIL, "köprü bağlanıyor — henüz kanal açık değil", Fix.RECONNECT_BRIDGE)
            BridgeStatus.RETRYING ->
                Check("bridge", Status.FAIL, "köprü yeniden bağlanıyor (bağlantı koptu)", Fix.RECONNECT_BRIDGE)
            BridgeStatus.TAKEN_OVER ->
                Check("bridge", Status.FAIL, "köprü başka cihaz tarafından devraldı", Fix.RECONNECT_BRIDGE)
            BridgeStatus.OFF ->
                Check(
                    "bridge", Status.FAIL,
                    "köprü kapalı — \"Ajan telefonu kullanabilsin\" kapalı ya da sunucu profili yok",
                    Fix.RECONNECT_BRIDGE,
                )
        },
        when (serverOk) {
            true -> Check("server", Status.OK, serverDetail, Fix.NONE)
            false -> Check("server", Status.FAIL, serverDetail, Fix.OPEN_SERVERS)
            null -> Check(
                "server", Status.UNKNOWN,
                serverDetail.ifBlank { "sunucu profili yok ya da hiç yoklanmadı" },
                Fix.OPEN_SERVERS,
            )
        },
        if (androidAutoInstalled) {
            Check(
                "android_auto", Status.UNKNOWN,
                "Android Auto kurulu; geliştirici modu uygulamanın kendi tercihinde tutulur ve " +
                    "üçüncü taraf uygulamadan okunamaz (DOĞRULANAMADI)",
                Fix.OPEN_ANDROID_AUTO,
            )
        } else {
            Check(
                "android_auto", Status.FAIL,
                "Android Auto kurulu değil",
                Fix.OPEN_ANDROID_AUTO,
            )
        },
    )

    /** "Doctor: 4/6 tamam · 1 eksik · 1 bilinmiyor" biçiminde özet. */
    fun summary(checks: List<Check>): String {
        val ok = checks.count { it.status == Status.OK }
        val fail = checks.count { it.status == Status.FAIL }
        val unknown = checks.count { it.status == Status.UNKNOWN }
        return "Doctor: $ok/${checks.size} tamam" +
            (if (fail > 0) " · $fail eksik" else "") +
            (if (unknown > 0) " · $unknown bilinmiyor" else "")
    }

    /**
     * DiagLog'a yazılacak satırlar — özet + her sorunlu satır.
     *
     * Sorunlu satırlar `w`, özet `i` seviyesinde: tarama geçtiyse günlük
     * yalnız tek özet satırı tutar, sorun varsa nerede olduğu da kalır.
     */
    fun diagLines(checks: List<Check>): List<String> {
        val out = mutableListOf(summary(checks))
        checks.filter { it.status != Status.OK }.forEach {
            val mark = if (it.status == Status.FAIL) "eksik" else "bilinmiyor"
            out += "$mark ${it.key}: ${it.detail}"
        }
        return out
    }

    /** Durum simgesi — UI rengiyle eşleşir. */
    fun mark(status: Status): String = when (status) {
        Status.OK -> "✓"
        Status.FAIL -> "✗"
        Status.UNKNOWN -> "?"
    }
}