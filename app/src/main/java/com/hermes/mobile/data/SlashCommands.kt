package com.hermes.mobile.data

/**
 * Hermes slash komutları — Telegram bot'unda kullanılanların aynısı.
 *
 * Kaynak: `hermes_cli/commands.py` (69 komut). Buradaki liste mobilde anlamlı
 * olanlarla sınırlı: terminal/TUI'ye özgü olanlar (`redraw`, `statusbar`,
 * `indicator`, `busy`, `footer`, `timestamps`, `prompt`/$EDITOR) atlandı.
 *
 * Çalıştırma `slash.exec` RPC'siyle; çıktı sohbete düz metin olarak düşer.
 * Liste gömülü çünkü sunucuda makine-okunur bir komut listesi ucu yok —
 * `/help` insan için biçimlenmiş metin döndürüyor.
 */
data class SlashCommand(
    val name: String,
    val description: String,
    val category: SlashCategory,
    /** Argüman ister; palette bir giriş kutusu açar. */
    val takesArgument: Boolean = false,
    val argumentHint: String = "",
    /** Geri alınamaz ya da maliyetli — onay sorulur. */
    val needsConfirm: Boolean = false,
)

enum class SlashCategory(val label: String) {
    Session("Oturum"),
    Agent("Ajan"),
    Model("Model ve görünüm"),
    Tools("Araçlar ve beceriler"),
    Automation("Otomasyon"),
    Info("Bilgi"),
    System("Sistem"),
}

val SLASH_COMMANDS: List<SlashCommand> = listOf(
    // ── Oturum ───────────────────────────────────────────────────────
    SlashCommand("new", "Yeni oturum başlat", SlashCategory.Session),
    SlashCommand("clear", "Ekranı temizle, yeni oturum", SlashCategory.Session),
    SlashCommand("history", "Konuşma geçmişini göster", SlashCategory.Session),
    SlashCommand("save", "Konuşmayı kaydet", SlashCategory.Session),
    SlashCommand("retry", "Son mesajı yeniden gönder", SlashCategory.Session),
    SlashCommand("undo", "N kullanıcı turu geri al", SlashCategory.Session, true, "kaç tur (varsayılan 1)"),
    SlashCommand("title", "Oturuma başlık ver", SlashCategory.Session, true, "başlık"),
    SlashCommand("branch", "Oturumu dallandır", SlashCategory.Session),
    SlashCommand("resume", "Adlandırılmış oturumu sürdür", SlashCategory.Session, true, "oturum adı"),
    SlashCommand("sessions", "Önceki oturumlara göz at", SlashCategory.Session),
    SlashCommand("compress", "Bağlamı sıkıştır", SlashCategory.Session),

    // ── Ajan ─────────────────────────────────────────────────────────
    SlashCommand("steer", "Turu kesmeden mesaj enjekte et", SlashCategory.Agent, true, "mesaj"),
    SlashCommand("queue", "Sonraki tur için sıraya al", SlashCategory.Agent, true, "mesaj"),
    SlashCommand("background", "Arka planda çalıştır", SlashCategory.Agent, true, "görev"),
    SlashCommand("goal", "Turlar boyu sürecek hedef koy", SlashCategory.Agent, true, "hedef"),
    SlashCommand("subgoal", "Etkin hedefe ölçüt ekle", SlashCategory.Agent, true, "ölçüt"),
    SlashCommand("agents", "Etkin ajanlar ve görevler", SlashCategory.Agent),
    SlashCommand("stop", "Arka plan süreçlerini durdur", SlashCategory.Agent, needsConfirm = true),
    SlashCommand("approve", "Bekleyen komutu onayla", SlashCategory.Agent),
    SlashCommand("deny", "Bekleyen komutu reddet", SlashCategory.Agent, true, "gerekçe (isteğe bağlı)"),

    // ── Model ve görünüm ─────────────────────────────────────────────
    SlashCommand("model", "Model değiştir", SlashCategory.Model, true, "model adı"),
    SlashCommand("personality", "Karakter seç", SlashCategory.Model, true, "karakter adı"),
    SlashCommand("reasoning", "Akıl yürütme çabası", SlashCategory.Model, true, "none | low | medium | high | xhigh | max"),
    SlashCommand("skin", "Tema değiştir", SlashCategory.Model, true, "tema adı"),
    SlashCommand("verbose", "Araç ilerleme ayrıntısı", SlashCategory.Model),

    // ── Araçlar ve beceriler ─────────────────────────────────────────
    SlashCommand("tools", "Araçları yönet", SlashCategory.Tools, true, "list | enable | disable"),
    SlashCommand("toolsets", "Araç kümelerini listele", SlashCategory.Tools),
    SlashCommand("skills", "Becerileri ara / kur / yönet", SlashCategory.Tools, true, "arama"),
    SlashCommand("bundles", "Beceri paketleri", SlashCategory.Tools),
    SlashCommand("memory", "Bekleyen hafıza yazımları", SlashCategory.Tools),
    SlashCommand("browser", "Tarayıcı araçlarını bağla", SlashCategory.Tools),
    SlashCommand("reload-mcp", "MCP sunucularını yeniden yükle", SlashCategory.Tools),
    SlashCommand("reload-skills", "Becerileri yeniden tara", SlashCategory.Tools),

    // ── Otomasyon ────────────────────────────────────────────────────
    SlashCommand("cron", "Zamanlanmış görevler", SlashCategory.Automation),
    SlashCommand("suggestions", "Önerilen otomasyonlar", SlashCategory.Automation),
    SlashCommand("blueprint", "Şablondan otomasyon kur", SlashCategory.Automation),
    SlashCommand("kanban", "İşbirliği panosu", SlashCategory.Automation),
    SlashCommand("journey", "Öğrenme zaman çizelgesi", SlashCategory.Automation),

    // ── Bilgi ────────────────────────────────────────────────────────
    SlashCommand("status", "Oturum, model, token, bağlam", SlashCategory.Info),
    SlashCommand("config", "Yapılandırmayı göster", SlashCategory.Info),
    SlashCommand("profile", "Etkin profil", SlashCategory.Info),
    SlashCommand("whoami", "Komut erişim düzeyin", SlashCategory.Info),
    SlashCommand("insights", "Kullanım analizleri", SlashCategory.Info),
    SlashCommand("platforms", "Platform durumları", SlashCategory.Info),
    SlashCommand("plugins", "Kurulu eklentiler", SlashCategory.Info),
    SlashCommand("commands", "Tüm komutlara göz at", SlashCategory.Info),
    SlashCommand("help", "Yardım", SlashCategory.Info),

    // ── Sistem ───────────────────────────────────────────────────────
    SlashCommand("platform", "Platformu duraklat / sürdür", SlashCategory.System, true, "pause | resume | list"),
    SlashCommand("reload", ".env değişkenlerini yeniden yükle", SlashCategory.System),
    SlashCommand("rollback", "Dosya sistemi kontrol noktaları", SlashCategory.System),
    SlashCommand("snapshot", "Durum anlık görüntüsü", SlashCategory.System),
    SlashCommand("yolo", "YOLO kipi (onay atlama)", SlashCategory.System, needsConfirm = true),
    SlashCommand("restart", "Gateway'i yeniden başlat", SlashCategory.System, needsConfirm = true),
)
