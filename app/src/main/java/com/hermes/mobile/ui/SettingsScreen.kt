package com.hermes.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.BUILTIN_PERSONAS
import com.hermes.mobile.data.LIVE_VOICES
import com.hermes.mobile.ui.theme.BUILTIN_THEMES
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.HermesPalette
import com.hermes.mobile.ui.theme.MonoTextStyle
import com.hermes.mobile.ui.theme.toColorOrNull
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.items
import com.hermes.mobile.data.ShizukuBridge

/**
 * Ayarlar — görünüm, canlı ses, sohbet, gizlilik, bağlantı, bildirim, geliştirici.
 *
 * Tek bir kaydırılabilir liste; bölümler başlıklarla ayrılıyor. Alt ekranlara
 * bölmek yerine tek sayfada tutuldu çünkü ayar sayısı bir telefonda gezinmeyi
 * zorlaştıracak kadar çok değil ve arama yapmak yerine kaydırmak daha hızlı.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    customThemes: List<HermesPalette>,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onSaveTheme: (HermesPalette) -> Unit,
    onDeleteTheme: (String) -> Unit,
    onImportTheme: (String) -> String?,
    onExportTheme: (HermesPalette) -> String,
    /** Shizuku durumu ve izin isteği — derin telefon denetimi için. */
    shizukuState: ShizukuBridge.State = ShizukuBridge.State.Unavailable,
    onRequestShizuku: () -> Unit = {},
) {
    var themeEditor by remember { mutableStateOf<HermesPalette?>(null) }
    var importOpen by remember { mutableStateOf(false) }

    // Açık kategori; null ise kategori listesi gösteriliyor.
    var open by remember { mutableStateOf<SettingsCategory?>(null) }
    BackHandler(enabled = open != null) { open = null }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (open != null) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = S.back,
                        tint = HermesColors.TextSecondary,
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { open = null }
                            .padding(end = 10.dp),
                    )
                }
                Text(
                    open?.let { catLabel(it) } ?: S.tabSettings,
                    color = HermesColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (open == null) {
            items(SettingsCategory.entries.toList(), key = { it.name }) { cat ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(HermesColors.SurfaceDim, RoundedCornerShape(11.dp))
                        .border(1.dp, HermesColors.Border, RoundedCornerShape(11.dp))
                        .clickable { open = cat }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(catLabel(cat), color = HermesColors.TextPrimary, fontSize = 15.sp)
                        Text(catHint(cat), color = HermesColors.TextMuted, fontSize = 11.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        // ── Görünüm ──────────────────────────────────────────────────
        if (open == SettingsCategory.Appearance) {
    item { Header(S.t2("Görünüm", "Appearance")) }

            item {
                ThemeRow(
                    themes = BUILTIN_THEMES + customThemes,
                    selectedId = settings.themeId,
                    onSelect = { id -> onUpdate { it.copy(themeId = id) } },
                    onEdit = { themeEditor = it },
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Yeni tema") {
                        themeEditor = (BUILTIN_THEMES.first { it.id == settings.themeId }
                            .takeIf { true } ?: BUILTIN_THEMES.first())
                            .copy(id = "ozel-${System.currentTimeMillis() % 100000}", label = "Kendi temam")
                    }
                    SmallButton(S.t2("İçe aktar", "Import")) { importOpen = true }
                }
            }

            item {
                SwitchRow(
                    S.t2("Sunucu temasını takip et", "Follow the server theme"),
                    S.t2("Hermes bir skin uyguladığında telefon da değişsin", "When Hermes applies a skin, the phone follows"),
                    settings.followServerSkin,
                ) { v -> onUpdate { it.copy(followServerSkin = v) } }
            }

            item {
                SliderRow(
                    S.t2("Yazı boyutu", "Text size"),
                    "%${(settings.fontScale * 100).toInt()}",
                    settings.fontScale,
                    0.85f..1.4f,
                ) { v -> onUpdate { it.copy(fontScale = v) } }
            }

            // ── Canlı ses ────────────────────────────────────────────────
        }

        if (open == SettingsCategory.Voice) {
    item { Header(S.t2("Canlı ses ve görüntü", "Live voice and vision")) }

            item {
                ChoiceRow(
                    S.t2("Kişilik", "Personality"),
                    BUILTIN_PERSONAS.map { it.id to it.label },
                    settings.personaId,
                ) { v -> onUpdate { it.copy(personaId = v, customPersona = "") } }
            }

            item {
                TextRow(
                    S.t2("Kendi yönergen", "Your own instruction"),
                    S.t2("Boş bırakırsan yukarıdaki kişilik kullanılır", "Leave empty to use the personality above"),
                    settings.customPersona,
                    minLines = 3,
                ) { v -> onUpdate { it.copy(customPersona = v) } }
            }

            item {
                ChoiceRow(
                    "Ses karakteri",
                    LIVE_VOICES.map { it to it },
                    settings.liveVoice,
                ) { v -> onUpdate { it.copy(liveVoice = v) } }
            }

            item {
                // Anahtarla erişilebilen bidi (canlı ses) modelleri —
                // 2026-07-30'da models.list ile doğrulandı.
                LiveModelRow(settings.liveModel) { v ->
                    onUpdate { it.copy(liveModel = v) }
                }
            }

            // ── Sohbet ───────────────────────────────────────────────────
        }

        if (open == SettingsCategory.Chat) {
    item { Header(S.t2("Sohbet", "Chat")) }
            item {
                SwitchRow(S.t2("Markdown biçimle", "Render markdown"), S.t2("Kapatırsan ham metin gösterilir", "Turn off to see raw text"), settings.renderMarkdown) { v ->
                    onUpdate { it.copy(renderMarkdown = v) }
                }
            }
            item {
                SwitchRow(S.t2("Düşünme bloğu açık gelsin", "Expand thinking blocks by default"), null, settings.expandThinking) { v ->
                    onUpdate { it.copy(expandThinking = v) }
                }
            }
            item {
                SwitchRow(S.t2("Araç kartları açık gelsin", "Expand tool cards by default"), null, settings.expandTools) { v ->
                    onUpdate { it.copy(expandTools = v) }
                }
            }
            item {
                SliderRow(
                    S.t2("Geçmişte yüklenecek mesaj", "Messages to load from history"),
                    "${settings.historyLimit}",
                    settings.historyLimit.toFloat(),
                    50f..500f,
                ) { v -> onUpdate { it.copy(historyLimit = v.toInt()) } }
            }

            // ── Genel ────────────────────────────────────────────────────
        }

        if (open == SettingsCategory.General) {
    item { Header("Genel") }
            item {
                // Arayüz dili: cihaz dilini geçersiz kılmak isteyenler için.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("" to S.t2("Cihaz dili", "Device"), "tr" to "Türkçe", "en" to "English")
                        .forEach { (code, label) ->
                            val on = settings.uiLang == code
                            Text(
                                label,
                                color = if (on) HermesColors.Background else HermesColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (on) HermesColors.Midground else HermesColors.SurfaceDim,
                                        RoundedCornerShape(9.dp),
                                    )
                                    .clickable { onUpdate { it.copy(uiLang = code) } }
                                    .padding(vertical = 11.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                }
            }

            item {
                SwitchRow(
                    S.t2("Çıkarken onay iste", "Confirm before exiting"),
                    "Geri tuşuyla çıkarken \"Çıkılsın mı?\" diye sorulur",
                    settings.confirmExit,
                ) { v -> onUpdate { it.copy(confirmExit = v) } }
            }

            // ── sparkDash ────────────────────────────────────────────────
        }

        if (open == SettingsCategory.Server) {
    item { Header("Spark izleme") }
            item {
                SwitchRow(
                    S.t2("DGX Spark panelini göster", "Show the DGX Spark panel"),
                    "node1 ve node2'nin GPU/CPU/bellek durumu Pano'da görünür",
                    settings.sparkEnabled,
                ) { v -> onUpdate { it.copy(sparkEnabled = v) } }
            }
            if (settings.sparkEnabled) {
                item {
                    TextRow(
                        title = "sparkDash adresi",
                        detail = "boş bırakırsan sunucu adresinin 5555 portu kullanılır",
                        value = settings.sparkUrl,
                    ) { v -> onUpdate { it.copy(sparkUrl = v.trim()) } }
                }
            }

            // ── Telefon denetimi ─────────────────────────────────────────
        }

        if (open == SettingsCategory.Phone) {
    item { Header(S.t2("Telefon denetimi", "Phone control")) }
            item {
                SwitchRow(
                    S.t2("Sesli asistan telefonu kullanabilsin",
                        "Let the voice assistant use the phone"),
                    S.t2(
                        "Canlı seste model kendiliğinden uygulama açabilir, yol tarifi " +
                            "başlatabilir. Yazarak verdiğin komutlar bu ayardan bağımsız " +
                            "çalışır — yazmak zaten açık istektir.",
                        "In live voice the model can open apps and start navigation on " +
                            "its own. Typed commands work regardless of this setting — " +
                            "typing is already an explicit request.",
                    ),
                    settings.phoneTools,
                ) { v -> onUpdate { it.copy(phoneTools = v) } }
            }

            item {
                SwitchRow(
                    S.t2("Ajan telefonu kullanabilsin", "Let the agent use the phone"),
                    S.t2(
                        "Telefon sunucuya giden bir bağlantı açar; cron'dan, " +
                            "Telegram'dan ya da terminalden çalışan ajan da telefon " +
                            "araçlarını çağırabilir. \"Sabah 8'de bildirimlerimi oku\" " +
                            "gibi şeyler ancak bununla olur. Açıkken kalıcı bir " +
                            "bildirim durur — bu kanal gizli çalışmamalı.",
                        "The phone opens an outbound connection, so an agent running " +
                            "from cron, Telegram or the terminal can call phone tools " +
                            "too. \"Read me my notifications at 8am\" only works with " +
                            "this on. A persistent notification stays up while it is — " +
                            "this channel should never run hidden.",
                    ),
                    settings.agentMayUsePhone,
                ) { v -> onUpdate { it.copy(agentMayUsePhone = v) } }
            }
            if (settings.agentMayUsePhone) {
                item {
                    SwitchRow(
                        S.t2("Yalnız okuma", "Read-only"),
                        S.t2(
                            "Ajan telefonu görebilir ama değiştiremez — bildirimler, " +
                                "takvim, konum, rehber, pil. Kapatırsan uygulama açma, " +
                                "yol tarifi, sayaç, bildirim gösterme gibi eylemler de " +
                                "açılır. Geri alınamaz hiçbir eylem yok: arama " +
                                "çeviriciyi açar, SMS taslak kalır.",
                            "The agent can see the phone but not change it — " +
                                "notifications, calendar, location, contacts, battery. " +
                                "Turn it off to also allow opening apps, navigation, " +
                                "timers and notifications. Nothing irreversible is " +
                                "included: dialling opens the dialer, SMS stays a draft.",
                        ),
                        settings.agentReadOnly,
                    ) { v -> onUpdate { it.copy(agentReadOnly = v) } }
                }
            }

            item { Spacer(Modifier.height(10.dp)) }
            item { ReadAccessRows() }
            item { Spacer(Modifier.height(10.dp)) }

            item {
                ShizukuRow(
                    enabled = settings.shizukuEnabled,
                    state = shizukuState,
                    onToggle = { v -> onUpdate { it.copy(shizukuEnabled = v) } },
                    onRequestPermission = onRequestShizuku,
                )
            }

            // ── Gizlilik ─────────────────────────────────────────────────
        }

        if (open == SettingsCategory.Privacy) {
    item { Header("Gizlilik") }
            item {
                SwitchRow(
                    "Biyometrik kilit",
                    S.t2("Uygulama açılışında parmak izi / yüz sorulsun", "Ask for fingerprint / face on launch"),
                    settings.biometricLock,
                ) { v -> onUpdate { it.copy(biometricLock = v) } }
            }
            item {
                SwitchRow(S.t2("Tokeni gizli göster", "Mask the token"), null, settings.maskToken) { v ->
                    onUpdate { it.copy(maskToken = v) }
                }
            }

            // ── Bağlantı ─────────────────────────────────────────────────
        }

        if (open == SettingsCategory.Server) {
    item { Header(S.t2("Bağlantı", "Connection")) }
            item {
                SliderRow(
                    "Durum yenileme",
                    "${settings.pollSeconds} sn",
                    settings.pollSeconds.toFloat(),
                    5f..60f,
                ) { v -> onUpdate { it.copy(pollSeconds = v.toInt()) } }
            }
            item {
                SliderRow(
                    S.t2("Canlı oturum yenileme", "Live session refresh"),
                    "${settings.livePollSeconds} sn",
                    settings.livePollSeconds.toFloat(),
                    3f..30f,
                ) { v -> onUpdate { it.copy(livePollSeconds = v.toInt()) } }
            }

            // ── Bildirimler ──────────────────────────────────────────────
        }

        if (open == SettingsCategory.Privacy) {
    item { Header("Bildirimler") }
            item {
                SwitchRow("Cron bitince", null, settings.notifyCron) { v ->
                    onUpdate { it.copy(notifyCron = v) }
                }
            }
            item {
                SwitchRow("Hata olunca", null, settings.notifyErrors) { v ->
                    onUpdate { it.copy(notifyErrors = v) }
                }
            }
            item {
                SwitchRow(S.t2("Onay istendiğinde", "When approval is requested"), null, settings.notifyApprovals) { v ->
                    onUpdate { it.copy(notifyApprovals = v) }
                }
            }

            // ── Geliştirici ──────────────────────────────────────────────
        }

        if (open == SettingsCategory.Developer) {
    item { Header(S.t2("Geliştirici", "Developer")) }
            item {
                SwitchRow(S.t2("Ham WS olaylarını göster", "Show raw WS events"), null, settings.showRawEvents) { v ->
                    onUpdate { it.copy(showRawEvents = v) }
                }
            }
            item {
                SwitchRow(
                    S.t2("Tanıtım kipi (adları maskele)", "Demo mode (mask names)"),
                    S.t2(
                        "Oturum başlıkları, cron işleri, beceriler, MCP sunucuları, " +
                            "dosya ve makine adları görüntüde takma adla gösterilir. " +
                            "Ekran görüntüsü ya da video paylaşacaksan aç — ölçtük, " +
                            "14 ekranın 9'u gerçek veri gösteriyor. Yalnız görüntüyü " +
                            "değiştirir, sunucuya giden istekler etkilenmez.",
                        "Session titles, cron jobs, skills, MCP servers, file and " +
                            "machine names are shown under aliases. Turn this on " +
                            "before sharing screenshots or video — measured, 9 of 14 " +
                            "screens show real data. Display only; requests to the " +
                            "server are unaffected.",
                    ),
                    settings.demoMask,
                ) { v -> onUpdate { it.copy(demoMask = v) } }
            }

            item { Spacer(Modifier.height(28.dp)) }
        }

        
    }

    themeEditor?.let { palette ->
        ThemeEditorDialog(
            palette = palette,
            exportText = onExportTheme(palette),
            onDismiss = { themeEditor = null },
            onSave = { onSaveTheme(it); onUpdate { s -> s.copy(themeId = it.id) }; themeEditor = null },
            onDelete = if (BUILTIN_THEMES.none { it.id == palette.id }) {
                { onDeleteTheme(palette.id); themeEditor = null }
            } else null,
        )
    }

    if (importOpen) {
        ImportThemeDialog(
            onDismiss = { importOpen = false },
            onImport = { text ->
                val err = onImportTheme(text)
                if (err == null) importOpen = false
                err
            },
        )
    }
}

// ── Parçalar ─────────────────────────────────────────────────────────

@Composable
private fun Header(text: String) {
    Text(
        text,
        color = HermesColors.Midground,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, detail: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    HermesCard(Modifier.fillMaxWidth().clickable { onChange(!value) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = HermesColors.TextPrimary, fontSize = 14.sp)
                detail?.let {
                    Text(it, color = HermesColors.TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
            Switch(checked = value, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    HermesCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = HermesColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, color = HermesColors.Midground, fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun TextRow(
    title: String,
    detail: String?,
    value: String,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    HermesCard(Modifier.fillMaxWidth()) {
        Text(title, color = HermesColors.TextPrimary, fontSize = 14.sp)
        detail?.let { Text(it, color = HermesColors.TextMuted, fontSize = 11.sp) }
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = HermesColors.TextSecondary),
        )
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    HermesCard(Modifier.fillMaxWidth()) {
        Text(title, color = HermesColors.TextPrimary, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            options.forEach { (id, label) ->
                val on = id == selected
                Row(
                    Modifier
                        .background(
                            if (on) HermesColors.Midground else HermesColors.SurfaceDim,
                            RoundedCornerShape(18.dp),
                        )
                        .clickable { onSelect(id) }
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                ) {
                    Text(
                        label,
                        color = if (on) HermesColors.Background else HermesColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .background(HermesColors.SurfaceDim, RoundedCornerShape(9.dp))
            .border(1.dp, HermesColors.BorderStrong, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, color = HermesColors.TextSecondary, fontSize = 12.sp)
    }
}

/** Tema şeridi — her tema kendi renkleriyle önizlenir. */
@Composable
private fun ThemeRow(
    themes: List<HermesPalette>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onEdit: (HermesPalette) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        themes.forEach { theme ->
            val on = theme.id == selectedId
            Column(
                Modifier
                    .width(96.dp)
                    .background(
                        theme.background.toColorOrNull() ?: HermesColors.Surface,
                        RoundedCornerShape(11.dp),
                    )
                    .border(
                        if (on) 2.dp else 1.dp,
                        if (on) HermesColors.Midground else HermesColors.Border,
                        RoundedCornerShape(11.dp),
                    )
                    .clickable { onSelect(theme.id) }
                    .padding(9.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(theme.accent, theme.textSecondary, theme.border).forEach { hex ->
                        Box(
                            Modifier
                                .size(13.dp)
                                .background(hex.toColorOrNull() ?: HermesColors.Border, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    theme.label,
                    color = theme.textPrimary.toColorOrNull() ?: HermesColors.TextPrimary,
                    fontSize = 11.sp,
                    maxLines = 2,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    S.t2("düzenle", "edit"),
                    color = theme.textMuted.toColorOrNull() ?: HermesColors.TextMuted,
                    fontSize = 9.sp,
                    modifier = Modifier.clickable { onEdit(theme) },
                )
            }
        }
    }
}

/** Tema düzenleyici — her renk hex olarak girilir, canlı önizlenir. */
@Composable
private fun ThemeEditorDialog(
    palette: HermesPalette,
    exportText: String,
    onDismiss: () -> Unit,
    onSave: (HermesPalette) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var draft by remember(palette.id) { mutableStateOf(palette) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HermesColors.Surface,
        titleContentColor = HermesColors.TextPrimary,
        textContentColor = HermesColors.TextSecondary,
        title = { Text(S.t2("Tema düzenle", "Edit theme")) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = { draft = draft.copy(label = it) },
                    label = { Text("Ad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))

                // Üçlü: kararlı anahtar · görünen etiket · o anki değer.
                // Anahtar şart — bu alanlar önceden **görünen metne** göre
                // dallanıyordu (`when (label)`), yani etiketi çevirmek
                // düzenleyiciyi sessizce bozuyordu: İngilizce arayüzde hiçbir
                // dal eşleşmeyip her düzenleme kenarlığa yazılıyordu.
                val fields = listOf(
                    Triple("background", S.t2("Arka plan", "Background"), draft.background),
                    Triple("surface", S.t2("Yüzey", "Surface"), draft.surface),
                    Triple("accent", S.t2("Vurgu", "Accent"), draft.accent),
                    Triple("textPrimary", S.t2("Ana metin", "Primary text"), draft.textPrimary),
                    Triple("textSecondary", S.t2("İkincil metin", "Secondary text"), draft.textSecondary),
                    Triple("border", S.t2("Kenarlık", "Border"), draft.border),
                )
                Column(
                    Modifier.height(210.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    fields.forEach { (key, label, value) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .background(
                                        value.toColorOrNull() ?: HermesColors.Border,
                                        RoundedCornerShape(4.dp),
                                    )
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = value,
                                onValueChange = { v ->
                                    draft = when (key) {
                                        "background" -> draft.copy(background = v, surfaceDim = v)
                                        "surface" -> draft.copy(surface = v)
                                        "accent" -> draft.copy(accent = v)
                                        "textPrimary" -> draft.copy(textPrimary = v)
                                        "textSecondary" ->
                                            draft.copy(textSecondary = v, textMuted = v)
                                        else -> draft.copy(border = v, borderStrong = v)
                                    }
                                },
                                label = { Text(label, fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = MonoTextStyle,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .clickable { clipboard.setText(AnnotatedString(exportText)) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = HermesColors.TextMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(S.t2("Temayı panoya kopyala", "Copy theme to clipboard"), color = HermesColors.TextMuted, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text("Kaydet", color = HermesColors.Midground)
            }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) { Text("Sil", color = HermesColors.Danger) }
                }
                TextButton(onClick = onDismiss) { Text("Kapat") }
            }
        },
    )
}

@Composable
private fun ImportThemeDialog(onDismiss: () -> Unit, onImport: (String) -> String?) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HermesColors.Surface,
        titleContentColor = HermesColors.TextPrimary,
        textContentColor = HermesColors.TextSecondary,
        title = { Text(S.t2("Tema içe aktar", "Import theme")) },
        text = {
            Column {
                Text(
                    S.t2("Tema JSON'unu yapıştır.", "Paste the theme JSON."),
                    color = HermesColors.TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 8,
                    textStyle = MonoTextStyle,
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = HermesColors.Danger, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { error = onImport(text) },
            ) { Text("Aktar", color = HermesColors.Midground) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.t2("Vazgeç", "Cancel")) } },
    )
}




/** Ayar kategorileri — panonun bölüm listesiyle aynı desen. */
enum class SettingsCategory { Appearance, Voice, Chat, General, Phone, Server, Privacy, Developer }

@Composable
private fun catLabel(c: SettingsCategory): String = when (c) {
    SettingsCategory.Appearance -> S.t2(S.t2("Görünüm", "Appearance"), "Appearance")
    SettingsCategory.Voice -> S.t2(S.t2("Canlı ses ve görüntü", "Live voice and vision"), "Voice & camera")
    SettingsCategory.Chat -> S.t2("Sohbet", "Chat")
    SettingsCategory.General -> S.t2("Genel", "General")
    SettingsCategory.Phone -> S.t2("Telefon denetimi", "Phone control")
    SettingsCategory.Server -> S.t2("Sunucu", "Server")
    SettingsCategory.Privacy -> S.t2("Gizlilik ve bildirim", "Privacy & notifications")
    SettingsCategory.Developer -> S.t2(S.t2("Geliştirici", "Developer"), "Developer")
}

@Composable
private fun catHint(c: SettingsCategory): String = when (c) {
    SettingsCategory.Appearance -> S.t2("Tema, yazı boyutu", "Theme, text size")
    SettingsCategory.Voice -> S.t2("Model, ses, kişilik, dil", "Model, voice, persona, language")
    SettingsCategory.Chat -> S.t2("Düşünme, araçlar, geçmiş", "Thinking, tools, history")
    SettingsCategory.General -> S.t2("Arayüz dili, çıkış onayı", "Interface language, exit confirmation")
    SettingsCategory.Phone -> S.t2("Sesli asistanın telefonu kullanması", "Letting voice use the phone")
    SettingsCategory.Server -> S.t2("Spark izleme, yenileme aralıkları", "Spark monitoring, refresh intervals")
    SettingsCategory.Privacy -> S.t2("Kilit, token, bildirimler", "Lock, token, notifications")
    SettingsCategory.Developer -> S.t2("Ham olaylar", "Raw events")
}


/** Canlı ses modeli seçimi — elle yazmak yerine doğrulanmış liste. */
@Composable
private fun LiveModelRow(current: String, onPick: (String) -> Unit) {
    val options = listOf(
        "gemini-2.5-flash-native-audio-latest" to
            S.t2("Native ses · güncel (önerilen)", "Native audio · latest (recommended)"),
        "gemini-2.5-flash-native-audio-preview-12-2025" to
            S.t2("Native ses · 12-2025", "Native audio · 12-2025"),
        "gemini-3.1-flash-live-preview" to
            S.t2("Gemini 3.1 Flash Live", "Gemini 3.1 Flash Live"),
        "gemini-3.5-live-translate-preview" to
            S.t2("Canlı çeviri", "Live translate"),
    )
    HermesCard(Modifier.fillMaxWidth()) {
        Text(
            S.t2("Canlı ses modeli", "Live voice model"),
            color = HermesColors.TextPrimary,
            fontSize = 14.sp,
        )
        Text(
            S.t2("Röleye iletilir", "Passed to the relay"),
            color = HermesColors.TextMuted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(8.dp))
        options.forEach { (id, label) ->
            val on = current == id
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (on) HermesColors.Midground else HermesColors.SurfaceDim,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable { onPick(id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Column {
                    Text(
                        label,
                        color = if (on) HermesColors.Background else HermesColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                    Text(
                        id,
                        style = MonoTextStyle,
                        color = if (on) HermesColors.Background else HermesColors.TextFaint,
                        fontSize = 9.sp,
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
        }
    }
}


/**
 * Shizuku satırı — durum + aç/kapa + izin.
 *
 * Durumu göstermek şart: Shizuku her yeniden başlatmada elle başlatılmak
 * zorunda ve kullanıcı neden çalışmadığını bilmeli.
 */
@Composable
private fun ShizukuRow(
    enabled: Boolean,
    state: ShizukuBridge.State,
    onToggle: (Boolean) -> Unit,
    onRequestPermission: () -> Unit,
) {
    HermesCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    S.t2("Shizuku ile derin denetim", "Deep control via Shizuku"),
                    color = HermesColors.TextPrimary,
                    fontSize = 14.sp,
                )
                Text(
                    S.t2(
                        "Wi-Fi/Bluetooth'u gerçekten aç-kapa, rahatsız etme, kabuk komutu",
                        "Actually toggle Wi-Fi/Bluetooth, Do Not Disturb, shell commands",
                    ),
                    color = HermesColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            androidx.compose.material3.Switch(
                checked = enabled,
                onCheckedChange = onToggle,
            )
        }

        if (enabled) {
            Spacer(Modifier.height(10.dp))
            val (label, color) = when (state) {
                ShizukuBridge.State.Ready -> S.t2("hazır", "ready") to HermesColors.Online
                ShizukuBridge.State.NeedsPermission -> S.t2("izin gerekiyor", "permission needed") to HermesColors.Busy
                ShizukuBridge.State.Unavailable -> S.t2("Shizuku çalışmıyor", "Shizuku not running") to HermesColors.Danger
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(color, RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(label, color = color, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                if (state == ShizukuBridge.State.NeedsPermission) {
                    Text(
                        S.t2("İzin ver", "Grant"),
                        color = HermesColors.Midground,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
                            .clickable(onClick = onRequestPermission)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
            if (state == ShizukuBridge.State.Unavailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    S.t2(
                        "Shizuku uygulamasını kur, kablosuz hata ayıklamayla başlat. " +
                            "Her yeniden başlatmada tekrar başlatmak gerekiyor — bu adımı " +
                            "Tasker ile otomatikleştirebilirsin.",
                        "Install the Shizuku app and start it with wireless debugging. " +
                            "It must be restarted after every reboot — you can automate " +
                            "that step with Tasker.",
                    ),
                    color = HermesColors.TextFaint,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}
