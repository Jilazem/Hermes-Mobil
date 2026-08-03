package com.hermes.mobile.data

/**
 * Ekran görüntüsü / tanıtım kipi — sunucudan gelen adları görüntüde maskeler.
 *
 * Neden var: uygulamanın ekranlarının çoğu sunucudan gelen **gerçek** metni
 * gösteriyor — oturum başlıkları, cron iş adları, beceri açıklamaları, MCP
 * sunucu adları, dosya adları, makine adları. Yayınlanacak bir ekran
 * görüntüsü ölçüldüğünde 14 ekranın 9'u kullanıcının mesleğini, makine
 * adlarını ve özel ağ adreslerini sızdırıyordu. Görüntü üzerine karalama
 * yapmak kırılgan (bir satırı kaçırmak yeter ve kimse fark etmez); doğrusu
 * metni hiç göstermemek.
 *
 * Tasarım:
 * - **Kararlı takma ad.** Aynı gerçek ad her zaman aynı maskeyi alır, yoksa
 *   liste her yenilemede karışır ve ekran görüntüsü tutarsız görünür.
 * - **Yalnız görüntü katmanı.** Ağa giden istekler gerçek adı kullanmaya
 *   devam eder; maskelenmiş bir kimlikle istek atmak işlevi bozardı.
 * - **Varsayılan kapalı**, Ayarlar → Geliştirici'den açılıyor. Kullanıcı
 *   kendi telefonunda kendi verisini görmek istiyor.
 */
object DemoMask {

    /** Kapalıyken tüm işlevler kimlik dönüşü yapar — çağrı yerleri dallanmıyor. */
    @Volatile
    var enabled: Boolean = false

    /** Maskelenecek metin türü; her tür kendi sayaç dizisini kullanıyor. */
    enum class Kind(internal val prefix: String) {
        SESSION("Session"),
        CRON("scheduled-job"),
        SKILL("skill"),
        MCP("mcp-server"),
        FILE("file"),
        NODE("node"),
        HOST("server"),
        MODEL("model"),
    }

    private val assigned = HashMap<String, String>()
    private val counters = HashMap<Kind, Int>()
    private val lock = Any()

    /**
     * [real] için kararlı bir takma ad. Boş metin olduğu gibi döner —
     * "Session 3" yazan boş bir başlık kafa karıştırırdı.
     */
    fun name(kind: Kind, real: String): String {
        if (!enabled || real.isBlank()) return real
        val key = "${kind.name}:$real"
        synchronized(lock) {
            assigned[key]?.let { return it }
            val n = (counters[kind] ?: 0) + 1
            counters[kind] = n
            val alias = if (kind == Kind.SESSION) "${kind.prefix} $n" else "${kind.prefix}-$n"
            assigned[key] = alias
            return alias
        }
    }

    /** Serbest metin — açıklamalar, günlük satırları, ana makine adları. */
    fun text(real: String): String {
        if (!enabled || real.isBlank()) return real
        var s = real
        // Adresler: IPv4 ve alan adları. Günlük satırlarında ikisi de geçiyor.
        s = IPV4.replace(s, "10.0.0.x")
        s = HOSTNAME.replace(s) { m ->
            // Yalnız gerçekten alan adı gibi görünenleri: "1.2" veya "a.kt"
            // gibi parçaları maskelemek günlükleri okunmaz hale getirirdi.
            if (m.value.count { it == '.' } >= 2) "server.example" else m.value
        }
        return s
    }

    /**
     * Dosya yolu — yalnız kullanıcı adı geçen bölümü değiştirir, yapı kalır.
     * Yolun tamamını gizlemek dosya gezgini ekran görüntüsünü anlamsız
     * kılardı; asıl sızan şey `/home/<kullanıcı>` parçası.
     */
    fun path(real: String?): String? {
        if (!enabled || real.isNullOrBlank()) return real
        return HOME.replace(real, "/home/user")
    }

    /**
     * Model adı. Herkesin bildiği aileleri (gemini, claude, gpt, deepseek…)
     * olduğu gibi bırakıyoruz — model seçici ekran görüntüsünün değeri tam
     * olarak gerçek adları göstermesinde. Yalnız kullanıcının kendi koyduğu
     * takma adlar maskeleniyor; bunlar kişiye özgü ve tanıtıcı.
     */
    fun model(real: String): String {
        if (!enabled || real.isBlank()) return real
        val lower = real.lowercase()
        if (PUBLIC_FAMILIES.any { it in lower }) return real
        return name(Kind.MODEL, real)
    }

    /**
     * Uzun açıklama metni — beceri/MCP açıklamaları. İçeriği tamamen
     * değiştiriyoruz: kısmi maskeleme burada işe yaramıyor, çünkü asıl bilgi
     * cümlenin kendisinde.
     */
    fun description(real: String): String =
        if (!enabled || real.isBlank()) real
        else "Example description for a demo entry."

    /**
     * Sunucu günlüğü. İçerik serbest metin — ajanın gerçek çalışması, konu
     * adları, dosya yolları. Burada kısmi maskeleme işe yaramıyor: hangi
     * kelimenin tanıtıcı olduğunu önceden bilemeyiz. O yüzden zaman damgası ve
     * seviye korunup **gövde** değiştiriliyor; düzen gerçekçi kalıyor,
     * içerik sızmıyor.
     */
    fun logs(real: String): String {
        if (!enabled || real.isBlank()) return real
        return real.lineSequence().mapIndexed { i, line ->
            if (line.isBlank()) return@mapIndexed line
            val m = LOG_PREFIX.find(line)
            val body = SAMPLE[i % SAMPLE.size]
            if (m != null) m.value + body else body
        }.joinToString("\n")
    }

    /** "2026-07-30 14:02:11 INFO " gibi bir önek — varsa korunuyor. */
    private val LOG_PREFIX = Regex(
        """^\[?[\d\-/: .,TZ]{8,}\]?\s*(?:\[?(?:DEBUG|INFO|WARN|WARNING|ERROR|TRACE)\]?\s*)?""",
        RegexOption.IGNORE_CASE,
    )

    private val SAMPLE = listOf(
        "gateway ready, 3 platforms connected",
        "session started (channel=cli)",
        "tool call: read_file -> ok",
        "scheduled job finished in 4.2s",
        "model response streamed, 812 tokens",
        "websocket client attached",
        "cache warm, 128 entries",
        "health check ok",
    )

    /** `/home/<kullanici>` -- yolun geri kalani korunuyor. */
    private val HOME = Regex("""/(?:home|media|mnt|Users)/[A-Za-z0-9._-]+""")

    /**
     * Genel model aileleri. Liste tam olmak zorunda degil: kacan bir ad
     * maskelenir (guvenli taraf), fazladan eslesen bir ad ise yalnizca
     * gercek adiyla gorunur.
     */
    private val PUBLIC_FAMILIES = listOf(
        "gemini", "claude", "gpt", "o1", "o3", "deepseek", "qwen", "llama",
        "mistral", "mixtral", "phi", "gemma", "grok", "kimi", "glm",
        "command-r", "nova", "codestral", "yi-", "minimax",
    )

    private val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")
    private val HOSTNAME = Regex("""\b[a-z0-9][a-z0-9-]*(?:\.[a-z0-9][a-z0-9-]*){2,}\b""")
}
