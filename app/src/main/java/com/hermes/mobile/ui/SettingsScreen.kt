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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.BUILTIN_PERSONAS
import com.hermes.mobile.data.HermesAccessibilityService
import com.hermes.mobile.data.LIVE_VOICES
import com.hermes.mobile.data.VoiceStatusLogic
import com.hermes.mobile.ui.theme.BUILTIN_THEMES
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.HermesPalette
import com.hermes.mobile.ui.theme.MonoTextStyle
import com.hermes.mobile.ui.theme.toColorOrNull
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.lazy.items
import com.hermes.mobile.data.ShizukuBridge
import com.hermes.mobile.data.AssistantModeLogic
import com.hermes.mobile.data.VoiceSpeakLogic
import com.hermes.mobile.data.LocalTtsLogic
import com.hermes.mobile.data.LiveModelLogic
import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.LogResponse
import com.hermes.mobile.data.MaintenanceStatusResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    /**
     * Bakım ekranı için: aktif sunucu profili. Bakım kartı bu profil üzerinden
     * `hermes doctor` / `hermes update` çalıştırır; profil yoksa kart gizli kalır.
     */
    activeProfile: com.hermes.mobile.data.ServerProfile? = null,
    /** Ayarlar→Sunucular: kayıtlı sunucu/token ekranını tam ekran açar. */
    onOpenServers: () -> Unit = {},
    /**
     * Ayarlar→Profiller (KALAN-4): Hermes ajan profili seçimi.
     *
     * Tur-4'te `default/ac/android` çipleri sohbet üst şeridinden çıkarıldı ve
     * seçim yalnız ⋯ menüsünde kaldı — Ayarlar'da görünür bir giriş yoktu.
     * Bu satır aynı sayfayı (ProfileSheet) Ayarlar'dan da erişilebilir yapar.
     */
    onOpenProfiles: () -> Unit = {},
    /** Aktif Hermes profili adı — satırda görünür (boşsa "—"). */
    activeHermesProfile: String = "",
    /**
     * Ses hattı (voice_api) sağlık denemesi — `GET /health`.
     *
     * Askıya alınabilir (suspend) çünkü soğuk motor uyarısı olan bir kanal:
     * düğmeye basınca sonuç satırı dolar; ağ işi UI iş parçacığında koşmaz.
     *
     * Tur-12: dönüş **yapılandırılmış** ([VoiceStatusLogic.Probe]) — durum
     * satırı motor motor renklendirilir, canlı yenileme aynı yolu kullanır ve
     * "Şimdi dene" motor kapalıysa 'Isıt' ipucunu gösterir.
     */
    onVoiceProbe: suspend () -> VoiceStatusLogic.Probe = { VoiceStatusLogic.Probe() },
    /** Tur-12: `Isıt` — seçili motoru kısa sabit cümleyle ön-yükler. */
    onVoiceWarm: (VoiceSpeakLogic.Engine) -> Unit = {},
    /** Tur-12: motor seçimi değişti — eski "Hazır ✓" işareti sıfırlanır. */
    onVoiceWarmReset: (VoiceSpeakLogic.Engine) -> Unit = {},
    /** Tur-12: `Isıt` durum makinesi (MainActivity'den akış olarak gelir). */
    voiceWarmState: VoiceStatusLogic.WarmState = VoiceStatusLogic.WarmState(),
    /**
     * Tur-21: yerel TTS (Piper) indirme durum makinesi — Ayarlar kartı bu
     * akışı çizer (yoksa NotInstalled görünür, indirme/düzen yok sayılır).
     */
    localTtsState: LocalTtsLogic.State = LocalTtsLogic.State(),
    /** Tur-21: yerel modeli indir / yeniden dene. */
    onLocalTtsDownload: () -> Unit = {},
    /** Tur-21: yerel model disk durumunu yeniden tara (kart açılışında). */
    onLocalTtsRefresh: () -> Unit = {},
    /** Tur-21: yerel model dosyalarını sil (spec: Ayarlar'dan silme kapısı). */
    onLocalTtsDelete: () -> Unit = {},
    /**
     * Tur-21: sesli asistan yerel sağlık noktası — true = bağlı
     * (MainActivity [ChatViewModel.probeLocalHealth]'ten).
     */
    onLocalHealthProbe: suspend () -> LiveModelLogic.Health = { LiveModelLogic.Health.Unknown },
    /** Tur-21: son bilinen yerel sağlık (durum noktası başlangıcı). */
    localHealth: LiveModelLogic.Health = LiveModelLogic.Health.Unknown,
    /**
     * Tur-13: asistan rolü durumu — `RoleManager.getRoleHolders(ROLE_ASSISTANT)`.
     *
     * Saf katmanda hesaplanır ([AssistantModeLogic.roleStatus]); burada yalnız
     * satır çizilir. MainActivity her `onResume`da tazeler.
     */
    assistantRole: AssistantModeLogic.RoleStatus =
        AssistantModeLogic.RoleStatus(AssistantModeLogic.RoleState.None),
    /** "Hermes'i varsayılan asistan yap" — sistem rol diyaloğunu açar. */
    onMakeDefaultAssistant: () -> Unit = {},
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
            // KALAN-4: "Profiller" — profil seçimi Ayarlar'dan da erişilebilir.
            // Satırın kendisi bir kategori DEĞİL: sohbetin ⋯ menüsüyle aynı
            // ProfileSheet'i açar (tek seçim yolu, iki giriş).
            item(key = "profiles") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(HermesColors.SurfaceDim, RoundedCornerShape(11.dp))
                        .border(1.dp, HermesColors.Border, RoundedCornerShape(11.dp))
                        .clickable { onOpenProfiles() }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(S.t2("Profiller", "Profiles"), color = HermesColors.TextPrimary, fontSize = 15.sp)
                        Text(
                            S.t2(
                                "Ajan kişiliği ve araç seti — şu an: ${activeHermesProfile.ifBlank { "—" }}",
                                "Agent persona and toolset — now: ${activeHermesProfile.ifBlank { "—" }}",
                            ),
                            color = HermesColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = HermesColors.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
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

            // ── Sesli asistan beyni (tur-21 model seçici) ─────────────────
            // Segment: [Yerel (node1)] [Gemini]. Seçim kalıcı (AppSettings).
            // Yerel seçiliyken durum noktası GERÇEK health-check ile dolar;
            // koparsa hata tooltip'i net — sessiz Gemini geçişi YOK (kilit).
            item {
                var localHealthState by remember {
                    mutableStateOf(localHealth)
                }
                val localScope = rememberCoroutineScope()
                val selected = LiveModelLogic.Provider.fromId(settings.liveProvider)
                HermesCard(Modifier.fillMaxWidth()) {
                    Text(
                        S.t2("Sesli asistan beyni", "Voice assistant brain"),
                        color = HermesColors.TextPrimary,
                        fontSize = 14.sp,
                    )
                    Text(
                        S.t2(
                            "Yerel seçimde sohbet node1'den geçer; bağlantı koparsa " +
                                "hata gösterilir, sessiz Gemini'ye DÖNÜLMEZ.",
                            "When local is selected chat goes through node1; on disconnect an " +
                                "error is shown — there is NO silent switch to Gemini.",
                        ),
                        color = HermesColors.TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveModelLogic.options(::tr).forEach { (id, label) ->
                            val on = selected.id == id
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(
                                        if (on) HermesColors.Midground else HermesColors.SurfaceDim,
                                        RoundedCornerShape(9.dp),
                                    )
                                    .clickable {
                                        onUpdate { s -> s.copy(liveProvider = id) }
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (id == LiveModelLogic.Provider.YEREL.id) {
                                        Box(
                                            Modifier
                                                .size(7.dp)
                                                .background(
                                                    when (LiveModelLogic.dotColor(localHealthState)) {
                                                        "green" -> HermesColors.Online
                                                        "red" -> HermesColors.Danger
                                                        else -> HermesColors.Offline
                                                    },
                                                    CircleShape,
                                                ),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Text(
                                        label,
                                        color = if (on) HermesColors.Background else HermesColors.TextSecondary,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        LiveModelLogic.healthLabel(localHealthState, ::tr),
                        color = HermesColors.TextFaint,
                        fontSize = 10.sp,
                    )
                    // Adres satırı + sağlık taraması.
                    TextRow(
                        S.t2("Yerel LLM adresi", "Local LLM address"),
                        S.t2(
                            "node1 örneği: http://192.168.1.99:8888",
                            "node1 example: http://192.168.1.99:8888",
                        ),
                        settings.localLlmUrl,
                    ) { v ->
                        onUpdate { s -> s.copy(localLlmUrl = v.trim(), liveProvider = s.liveProvider) }
                        localHealthState = LiveModelLogic.Health.Unknown
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            S.t2("Sağlığı denetle", "Check health"),
                            color = HermesColors.Midground,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clickable {
                                    localScope.launch {
                                        localHealthState = onLocalHealthProbe()
                                    }
                                }
                                .padding(6.dp),
                        )
                    }
                }
            }

            // ── Sesli mesaj (tur-11) ──────────────────────────────────────
            // Kayıt (bas-konuş) + sesli okuma: uç `voice_api` (8174 / dış /voice-api).
            item { Header(S.t2("Sesli mesaj (uygulama içi)", "Voice messages (in-app)")) }

            item {
                ChoiceRow(
                    S.t2("Seslendirme motoru", "Speech engine"),
                    VoiceSpeakLogic.engineOptions(::tr),
                    VoiceSpeakLogic.Engine.fromId(settings.voiceEngine).id,
                ) { v ->
                    onUpdate { it.copy(voiceEngine = v) }
                    // Tur-12: motor değişti — eski motora ait "Hazır ✓" kalmasın.
                    onVoiceWarmReset(VoiceSpeakLogic.Engine.fromId(v))
                }
            }

            item {
                Text(
                    VoiceSpeakLogic.engineHint(
                        VoiceSpeakLogic.Engine.fromId(settings.voiceEngine),
                        ::tr,
                    ),
                    color = HermesColors.TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }

            item {
                SwitchRow(
                    S.t2("Metni otomatik gönder", "Send the transcript automatically"),
                    S.t2(
                        "Kapalıyken konuştuğun metin sohbet girdisine yazılır, " +
                            "göndermeden önce düzeltebilirsin.",
                        "When off, the transcript goes into the composer so you can fix it before sending.",
                    ),
                    settings.voiceAutoSend,
                ) { v -> onUpdate { it.copy(voiceAutoSend = v) } }
            }

            item {
                TextRow(
                    title = "voice_api",
                    detail = S.t2(
                        "boş bırakırsan sunucu adresinden türetilir: ev ağında :8174, " +
                            "dışarıda /voice-api",
                        "leave empty to derive it from the server address: :8174 on the " +
                            "home network, /voice-api externally",
                    ),
                    value = settings.voiceUrl,
                ) { v -> onUpdate { it.copy(voiceUrl = v.trim(), voiceLastOk = "") } }
            }

            item {
                // Tur-12: canlı durum (4,5 sn) + "Yenile" + "Şimdi dene" + "Isıt".
                // Tur-21: yerel motor seçiliyse ısıtma anlamsız — kart yalnız
                // bulut motorlarında Isıt düğmesini etkin çizer.
                VoiceStatusCard(
                    engine = VoiceSpeakLogic.Engine.fromId(settings.voiceEngine),
                    warm = voiceWarmState,
                    onProbe = onVoiceProbe,
                    onWarm = onVoiceWarm,
                ) { base -> onUpdate { it.copy(voiceLastOk = base) } }
            }

            // ── Yerel kadın sesi (tur-21) ─────────────────────────────────
            // İndirme durum kartı: ilerleme çubuğu + indirme/silme düğmeleri.
            // Yalnız yerel motor seçiliyken belirgin; her zaman erişilebilir.
            item {
                LaunchedEffect(Unit) { onLocalTtsRefresh() }
                HermesCard(Modifier.fillMaxWidth()) {
                    Text(
                        LocalTtsLogic.engineLabel(::tr),
                        color = HermesColors.TextPrimary,
                        fontSize = 14.sp,
                    )
                    Text(
                        LocalTtsLogic.statusLine(localTtsState, ::tr),
                        color = when (localTtsState.phase) {
                            LocalTtsLogic.Phase.Failed -> HermesColors.Danger
                            LocalTtsLogic.Phase.Ready -> HermesColors.Online
                            else -> HermesColors.TextMuted
                        },
                        fontSize = 12.sp,
                    )
                    if (localTtsState.phase == LocalTtsLogic.Phase.Downloading) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (LocalTtsLogic.percent(localTtsState.doneBytes) / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = HermesColors.Midground,
                            trackColor = HermesColors.SurfaceDim,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        LocalTtsLogic.engineHint(::tr),
                        color = HermesColors.TextFaint,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (localTtsState.phase != LocalTtsLogic.Phase.Ready) {
                            OutlinedButton(
                                onClick = onLocalTtsDownload,
                                enabled = localTtsState.phase != LocalTtsLogic.Phase.Downloading,
                            ) {
                                Text(
                                    if (localTtsState.phase == LocalTtsLogic.Phase.Failed) {
                                        S.t2("Yeniden dene", "Retry")
                                    } else {
                                        S.t2("İndir (~63 MB)", "Download (~63 MB)")
                                    },
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        if (localTtsState.phase == LocalTtsLogic.Phase.Ready) {
                            OutlinedButton(onClick = onLocalTtsDelete) {
                                Text(S.t2("Sil", "Delete"), fontSize = 12.sp)
                            }
                        }
                    }
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
    item { Header(S.t2("Genel", "General")) }
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

            // ── Bakım ──────────────────────────────────────────────────
        }

        if (open == SettingsCategory.General) {
    item { Header(S.t2("Bakım", "Maintenance")) }
            if (activeProfile != null) {
                item {
                    MaintenanceCard(activeProfile)
                }
            } else {
                item {
                    HermesCard(Modifier.fillMaxWidth()) {
                        Text(
                            S.t2("Bağlı sunucu profili yok", "No connected server profile"),
                            color = HermesColors.TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        if (open == SettingsCategory.Server) {
            // Sunucu/token girişi — ilk kurulum yolu. ConnectScreen'e gerçek
            // bağlantı (ölü kod değildi ama UI'dan erişilemiyordu; 2026-09-14
            // emülatör denetiminde kanıtlandı).
            item {
                HermesCard(Modifier.fillMaxWidth().clickable(onClick = onOpenServers)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                S.t2("Sunucular", "Servers"),
                                color = HermesColors.TextPrimary, fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                S.t2(
                                    if (activeProfile == null) "Sunucu adresi ve token ekle"
                                    else "${activeProfile.name} — profilleri ve tokeni yönet",
                                    if (activeProfile == null) "Add a server address and token"
                                    else "${activeProfile.name} — manage profiles and token",
                                ),
                                color = HermesColors.TextMuted, fontSize = 12.sp,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = HermesColors.TextFaint,
                        )
                    }
                }
            }
            item { Header(S.t2("Spark izleme", "Spark monitoring")) }
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
                        title = "sparkDash",
                        detail = S.t2(
                            "boş bırakırsan sunucu adresinin 5555 portu kullanılır",
                            "leave empty to use port 5555 of the server address",
                        ),
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

            if (settings.agentMayUsePhone) {
                item {
                    FullControlRow(
                        on = settings.fullControl,
                        onToggle = { v -> onUpdate { it.copy(fullControl = v) } },
                    )
                }
            }

            if (settings.agentMayUsePhone) {
                item { BridgeStatusRow() }
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

            // ── Telefon asistanı (tur-13) ────────────────────────────────
        }

        if (open == SettingsCategory.Assistant) {
            item { Header(S.t2("Telefon asistanı", "Phone assistant")) }

            item {
                AssistantRoleRow(
                    role = assistantRole,
                    onMakeDefault = onMakeDefaultAssistant,
                )
            }

            item {
                SwitchRow(
                    S.t2("Asistan yanıtını otomatik oku", "Read the assistant reply aloud"),
                    S.t2(
                        "Yalnız asistan akışında geçerli: bas-konuş ile sorduğun " +
                            "sorunun yanıtı gelince kendiliğinden okunur. Normal " +
                            "sohbette seslendirme değişmez — balondaki hoparlöre " +
                            "dokunman gerekir.",
                        "Only inside the assistant flow: the reply to a push-to-talk " +
                            "question is read out automatically. Normal chat is " +
                            "unchanged — tap the speaker on the bubble.",
                    ),
                    settings.assistantAutoRead,
                ) { v -> onUpdate { it.copy(assistantAutoRead = v) } }
            }

            item {
                HermesCard(Modifier.fillMaxWidth()) {
                    Text(
                        S.t2("Asistan hangi ses hattını kullanır", "Which voice path the assistant uses"),
                        color = HermesColors.TextPrimary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        S.t2(
                            "Yerel (Kahya) — VARSAYILAN. Ses, sunucudaki voice_api " +
                                "(:8174) üzerinden yazıya çevrilir ve yanıt yine orada " +
                                "seslendirilir; konuşma Google'a gitmez.",
                            "Local (Kahya) — DEFAULT. Audio is transcribed through the " +
                                "voice_api on your server (:8174) and the reply is " +
                                "synthesised there too; the exchange never goes to Google.",
                        ),
                        color = HermesColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        S.t2(
                            "Gemini Live — isteğe bağlı, ayrı bir özellik olarak duruyor " +
                                "(canlı ses ekranındaki \"Gemini Live\" sekmesi). Asistan " +
                                "hareketi artık oraya gitmez.",
                            "Gemini Live — optional and still available (the \"Gemini Live\" " +
                                "tab on the live voice screen). The assistant gesture no " +
                                "longer opens it.",
                        ),
                        color = HermesColors.TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            item {
                Text(
                    S.t2(
                        "Asistan modu: sohbet açılır, mikrofon öne çıkar ve kayıt " +
                            "SENDEN bekler — asistan hareketi tek başına mikrofonu açmaz.",
                        "Assistant mode: chat opens, the microphone comes forward and " +
                            "recording waits for YOU — the gesture alone never opens the mic.",
                    ),
                    color = HermesColors.TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                )
            }

            // ── Gizlilik ─────────────────────────────────────────────────
        }

        if (open == SettingsCategory.Privacy) {
    item { Header(S.t2("Gizlilik", "Privacy")) }
            item {
                SwitchRow(
                    S.t2("Biyometrik kilit", "Biometric lock"),
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
                    S.t2("Durum yenileme", "Status refresh"),
                    S.t2("${settings.pollSeconds} sn", "${settings.pollSeconds} s"),
                    settings.pollSeconds.toFloat(),
                    5f..60f,
                ) { v -> onUpdate { it.copy(pollSeconds = v.toInt()) } }
            }
            item {
                SliderRow(
                    S.t2("Canlı oturum yenileme", "Live session refresh"),
                    S.t2("${settings.livePollSeconds} sn", "${settings.livePollSeconds} s"),
                    settings.livePollSeconds.toFloat(),
                    3f..30f,
                ) { v -> onUpdate { it.copy(livePollSeconds = v.toInt()) } }
            }

            // ── Bildirimler ──────────────────────────────────────────────
        }

        if (open == SettingsCategory.Privacy) {
    item { Header(S.t2("Bildirimler", "Notifications")) }
            item {
                SwitchRow(S.t2("Cron bitince", "When a cron job ends"), null, settings.notifyCron) { v ->
                    onUpdate { it.copy(notifyCron = v) }
                }
            }
            item {
                SwitchRow(S.t2("Hata olunca", "When an error happens"), null, settings.notifyErrors) { v ->
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

/**
 * Tur-12 — "Ses ucu durumu" kartı: **canlı** durum + `Yenile` + `Şimdi dene` + `Isıt`.
 *
 * Kullanıcı bildirimi: durum sabit kalıyordu; motor kapalıyken "kapalı" yazıyor,
 * yükleme süreci / ne zaman hazır olacağı görünmüyordu. Çözüm:
 *  - Kart **ekranda görünürken** `/health` [VoiceStatusLogic.REFRESH_MS] (4,5 sn)
 *    aralıkla yenilenir; kart LazyColumn'dan düşünce (bölüm kapanınca ya da
 *    aşağı kaydırılınca) canlı döngü kendiliğinden durur — görünmeyen bölüm
 *    için boşuna soket açılmaz.
 *  - Durum satırı **yapılandırılmış ve renkli**:
 *    `Metinleştirme: açık · Kahya: kapalı · Kadın: kapalı · Chatterbox: kapalı`
 *    (açık = yeşil [HermesColors.Online], kapalı = gri [HermesColors.Offline]).
 *  - `Isıt` yalnız seçili motor KAPALIYSA görünür; kısa sabit cümleyle
 *    `/synthesize` çağırıp motoru ön-yükler (durum: `Isıt → Isıtılıyor… (~2-3 dk)
 *    → Hazır ✓`; tavan [VoiceStatusLogic.WARM_TIMEOUT_MS] = 420 sn).
 *  - Isıtma durumu **denetleyicide** yaşar ([warm]): kullanıcı bölümden çıksa da
 *    yükleme sürer, geri döndüğünde "Hazır ✓" görünür.
 */
@Composable
private fun VoiceStatusCard(
    engine: VoiceSpeakLogic.Engine,
    warm: VoiceStatusLogic.WarmState,
    onProbe: suspend () -> VoiceStatusLogic.Probe,
    onWarm: (VoiceSpeakLogic.Engine) -> Unit,
    onBase: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var probe by remember { mutableStateOf<VoiceStatusLogic.Probe?>(null) }
    /** Hangi düğme koşuyor: "" (canlı) · "yenile" · "dene". */
    var busy by remember { mutableStateOf("") }
    /** Üst üste istek olmasın (canlı döngü + elle düğme). */
    var inFlight by remember { mutableStateOf(false) }
    /** "Şimdi dene" en az bir kez basıldı mı — kapalı motor ipucu için. */
    var probed by remember { mutableStateOf(false) }
    var probes by remember { mutableStateOf(0) }

    suspend fun refresh(which: String) {
        if (inFlight) return
        inFlight = true
        busy = which
        val p = try {
            onProbe()
        } catch (e: CancellationException) {
            busy = ""; inFlight = false
            throw e
        } catch (e: Exception) {
            VoiceStatusLogic.Probe(
                error = e.message ?: tr("Ses ucuna ulaşılamadı", "Voice endpoint unreachable"),
            )
        }
        probe = p
        probes++
        if (p.base.isNotBlank()) onBase(p.base)
        if (which == "dene") probed = true
        busy = ""
        inFlight = false
    }

    // Canlı yenileme: kart ekranda olduğu sürece 4,5 sn'de bir.
    LaunchedEffect(Unit) {
        while (isActive) {
            refresh("")
            delay(VoiceStatusLogic.REFRESH_MS)
        }
    }

    // Isıtma bitti: durum satırı hemen tazelenir (motor artık "açık" görünür).
    LaunchedEffect(warm.phase, warm.engineId) {
        if (warm.phase == VoiceStatusLogic.WarmPhase.Ready) refresh("")
    }

    val onColor = HermesColors.Online
    val offColor = HermesColors.Offline
    val labelColor = HermesColors.TextMuted
    val sepColor = HermesColors.TextFaint
    val chips = probe?.health?.let { VoiceStatusLogic.chips(it, ::tr) }
    val knownClosed = probe?.health != null && !VoiceStatusLogic.engineOpen(probe?.health, engine)
    val warmReady = VoiceStatusLogic.warmReadyFor(warm, engine)

    HermesCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ses ucu durumu",
                color = HermesColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (inFlight) {
                CircularProgressIndicator(
                    Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = HermesColors.Midground,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                if (probes == 0) "yoklanıyor…"
                else "canlı · ${probes}. yoklama · 4,5 sn",
                color = HermesColors.TextFaint,
                fontSize = 10.sp,
            )
        }

        if (chips != null) {
            val line = buildAnnotatedString {
                chips.forEachIndexed { i, c ->
                    if (i > 0) withStyle(SpanStyle(color = sepColor)) { append(" · ") }
                    withStyle(SpanStyle(color = labelColor)) { append("${c.label}: ") }
                    withStyle(
                        SpanStyle(
                            color = if (c.on) onColor else offColor,
                            fontWeight = if (c.on) FontWeight.Medium else FontWeight.Normal,
                        ),
                    ) { append(VoiceStatusLogic.valueText(c, ::tr)) }
                }
            }
            Text(
                line,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else if (probe == null) {
            Text(
                "Motor durumu okunuyor…",
                color = HermesColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        probe?.base?.takeIf { it.isNotBlank() }?.let {
            Text("Çalışan adres: $it", color = HermesColors.TextMuted, fontSize = 11.sp)
        }
        probe?.error?.let {
            Text(it, color = HermesColors.Danger, fontSize = 11.sp, lineHeight = 15.sp)
        }
        // "Şimdi dene" kapalı motor gördüyse: ısıtma ipucu (görev maddesi 3).
        if (probed && knownClosed && !warmReady && !warm.busy) {
            Text(
                VoiceStatusLogic.coldHint(::tr),
                color = HermesColors.Busy,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        warm.message?.let {
            Text(
                it,
                color = if (warm.phase == VoiceStatusLogic.WarmPhase.Failed) HermesColors.Danger
                else HermesColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }

        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallButton(if (busy == "yenile") "Yenileniyor…" else S.t2("Yenile", "Refresh")) {
                scope.launch { refresh("yenile") }
            }
            Spacer(Modifier.width(8.dp))
            SmallButton(if (busy == "dene") "Deneniyor…" else S.t2("Şimdi dene", "Test now")) {
                scope.launch { refresh("dene") }
            }
            // Isıt: yalnız motor kapalıyken görünür; ısıtma sürerken spinner
            // (düğme yok → çift tıklama imkânsız), bitince yeşil "Hazır ✓".
            if (warm.busy) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(
                    Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = HermesColors.Midground,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    VoiceStatusLogic.warmLabel(warm, engine, ::tr),
                    color = HermesColors.TextSecondary,
                    fontSize = 12.sp,
                )
            } else if (warmReady) {
                Spacer(Modifier.width(10.dp))
                Text(
                    VoiceStatusLogic.warmLabel(warm, engine, ::tr),
                    color = HermesColors.Online,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else if (knownClosed || VoiceStatusLogic.warmVisible(probe, engine)) {
                Spacer(Modifier.width(8.dp))
                SmallButton(VoiceStatusLogic.warmLabel(warm, engine, ::tr)) { onWarm(engine) }
            }
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




/**
 * Tur-13: "Telefon asistanı" — rol durumu + sistem diyaloğunu açan düğme.
 *
 * Rol desteklenmiyorsa (API < 29 ya da cihaz rolü sunmuyor) düğme yerine
 * kısa elle atama adımları gösterilir; sessiz kalan bir düğme bırakılmaz.
 */
@Composable
private fun AssistantRoleRow(
    role: AssistantModeLogic.RoleStatus,
    onMakeDefault: () -> Unit,
) {
    HermesCard(Modifier.fillMaxWidth()) {
        Text(
            S.t2("Varsayılan asistan", "Default assistant"),
            color = HermesColors.TextPrimary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            AssistantModeLogic.roleLine(role, ::tr),
            color = when (role.state) {
                AssistantModeLogic.RoleState.Hermes -> HermesColors.Online
                AssistantModeLogic.RoleState.Unsupported -> HermesColors.TextMuted
                else -> HermesColors.TextSecondary
            },
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))

        val canRequest = AssistantModeLogic.canRequestRole(role)
        if (canRequest) {
            SmallButton(S.t2("Hermes'i varsayılan asistan yap", "Make Hermes the default assistant")) {
                onMakeDefault()
            }
            Spacer(Modifier.height(6.dp))
            Text(
                S.t2(
                    "Android 12 ve üstünde sistem, rolü yalnız Ayarlar'dan atatabiliyor " +
                        "— düğme diyaloğu açar, kapanırsa seni doğrudan Ayarlar → " +
                        "Varsayılan uygulamalar → Dijital asistan ekranına götürür.",
                    "On Android 12+ the system only lets the role be set from Settings — " +
                        "the button opens the dialog and, if it closes, takes you straight " +
                        "to Settings → Default apps → Digital assistant.",
                ),
                color = HermesColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        } else {
            // Rol API'si yok: kullanıcıyı adımlarla Ayarlar'a yönlendir.
            AssistantModeLogic.manualSteps(::tr).forEach { step ->
                Text(
                    "• $step",
                    color = HermesColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            S.t2(
                "Asistan hareketi (ana ekrandan yukarı kaydırma / güç tuşu) Hermes'i " +
                    "açtığında mikrofon hazır bekler — kayıt sen basınca başlar.",
                "When the assistant gesture (swipe up from a corner / power button) opens " +
                    "Hermes, the microphone waits ready — recording starts when you press.",
            ),
            color = HermesColors.TextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

/** Ayar kategorileri — panonun bölüm listesiyle aynı desen. */
enum class SettingsCategory { Appearance, Voice, Chat, General, Phone, Assistant, Server, Privacy, Developer }

@Composable
private fun catLabel(c: SettingsCategory): String = when (c) {
    SettingsCategory.Appearance -> S.t2(S.t2("Görünüm", "Appearance"), "Appearance")
    SettingsCategory.Voice -> S.t2(S.t2("Canlı ses ve görüntü", "Live voice and vision"), "Voice & camera")
    SettingsCategory.Chat -> S.t2("Sohbet", "Chat")
    SettingsCategory.General -> S.t2("Genel", "General")
    SettingsCategory.Phone -> S.t2("Telefon denetimi", "Phone control")
    SettingsCategory.Assistant -> S.t2("Telefon asistanı", "Phone assistant")
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
    SettingsCategory.Assistant -> S.t2("Varsayılan asistan, yerel ses", "Default assistant, local voice")
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
 * Bakım kartı — `hermes doctor` / `hermes update` detached olarak başlatılır.
 *
 * Eylemler sonuca kadar beklemez; "Çalışıyor" rozeti ve durum yoklaması
 * (3 sn × 20, toplam ~1 dk) bitiş kodunu getirir. "Çıktıyı gör" son 200
 * satırlık log ekranını açar.
 */
@Composable
private fun MaintenanceCard(profile: com.hermes.mobile.data.ServerProfile) {
    val client = remember(profile) { HermesClient(profile) }
    val scope = rememberCoroutineScope()
    var fixOn by remember { mutableStateOf(false) }
    var runningLabel by remember { mutableStateOf<String?>(null) }
    var lastStatus by remember { mutableStateOf<MaintenanceStatusResponse?>(null) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var logOpen by remember { mutableStateOf(false) }
    var updatingConfirmation by remember { mutableStateOf(false) }

    fun probeStatus() {
        scope.launch {
            repeat(20) {
                if (runningLabel == null) return@launch
                try {
                    val st = client.maintenanceStatus()
                    lastStatus = st
                    if (!st.running) {
                        runningLabel = null
                    }
                } catch (e: Exception) {
                    // Ulaşılamıyorsa sessizce dene; kart durumu yine gösterir.
                }
                delay(3000)
            }
        }
    }

    fun openLog() {
        scope.launch {
            val resp = runCatching { client.maintenanceLog(lines = 200) }
                .getOrElse {
                    LogResponse(file = "maintenance", lines = listOf("Log okunamadı: ${it.message}"))
                }
            logLines = resp.lines
            logOpen = true
        }
    }

    fun startDoctor() {
        scope.launch {
            val fix = fixOn
            val r = runCatching { client.maintenanceDoctor(fix = fix) }.getOrElse { e ->
                runningLabel = tr("başlatılamadı: ${e.message}", "failed to start: ${e.message}")
                return@launch
            }
            runningLabel = if (r.alreadyRunning) {
                tr("zaten çalışıyor", "already running")
            } else {
                tr("çalışıyor…", "running…")
            }
            probeStatus()
        }
    }

    fun startUpdate() {
        scope.launch {
            val r = runCatching { client.maintenanceUpdate() }.getOrElse { e ->
                runningLabel = tr("başlatılamadı: ${e.message}", "failed to start: ${e.message}")
                return@launch
            }
            runningLabel = if (r.alreadyRunning) {
                tr("zaten çalışıyor", "already running")
            } else {
                tr("çalışıyor…", "running…")
            }
            probeStatus()
        }
    }

    HermesCard(Modifier.fillMaxWidth()) {
        Text(S.t2("Bakım", "Maintenance"), color = HermesColors.TextPrimary, fontSize = 14.sp)
        Text(
            S.t2(
                "Hermes'i teşhis et veya güncelle — sunucuda ayrılmış süreç olarak koşar",
                "Diagnose or update Hermes — runs as a detached process on the server",
            ),
            color = HermesColors.TextMuted,
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton(
                if (fixOn) S.t2("Doktor + Onar", "Doctor + fix") else S.t2("Doktor çalıştır", "Run doctor"),
            ) { startDoctor() }
            SmallButton(S.t2("Hermes'i güncelle", "Update Hermes")) { updatingConfirmation = true }
            SmallButton(S.t2("Çıktıyı gör", "View output")) { openLog() }
        }

        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(S.t2("Onar (--fix)", "Repair (--fix)"), color = HermesColors.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Switch(checked = fixOn, onCheckedChange = { fixOn = it })
        }

        val status = lastStatus
        if (runningLabel != null || (status != null && status.running)) {
            Spacer(Modifier.height(7.dp))
            Text(
                runningLabel ?: S.t2("çalışıyor…", "running…"),
                color = HermesColors.Busy,
                fontSize = 12.sp,
            )
        } else if (status != null) {
            Spacer(Modifier.height(7.dp))
            Text(
                S.t2(
                    "Son: ${status.lastKind ?: "?"} · bitiş ${status.exitCode ?: "?"}",
                    "Last: ${status.lastKind ?: "?"} · exit ${status.exitCode ?: "?"}",
                ),
                color = if ((status.exitCode ?: 1) == 0) HermesColors.Online else HermesColors.Danger,
                fontSize = 12.sp,
            )
        }
    }

    if (updatingConfirmation) {
        AlertDialog(
            onDismissRequest = { updatingConfirmation = false },
            containerColor = HermesColors.Surface,
            titleContentColor = HermesColors.TextPrimary,
            textContentColor = HermesColors.TextSecondary,
            title = { Text(S.t2("Hermes güncellensin mi?", "Update Hermes?")) },
            text = {
                Text(
                    S.t2(
                        "Gateway yeniden başlayacak ve güncelleme sırasında " +
                            "aktif seanslar kesilebilir.",
                        "The gateway will restart and active sessions may be interrupted " +
                            "during the update.",
                    ),
                    color = HermesColors.TextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { updatingConfirmation = false; startUpdate() }) {
                    Text(S.t2("Güncelle", "Update"), color = HermesColors.Midground)
                }
            },
            dismissButton = {
                TextButton(onClick = { updatingConfirmation = false }) {
                    Text(S.t2("Vazgeç", "Cancel"), color = HermesColors.TextMuted)
                }
            },
        )
    }

    if (logOpen) {
        AlertDialog(
            onDismissRequest = { logOpen = false },
            containerColor = HermesColors.Surface,
            titleContentColor = HermesColors.TextPrimary,
            textContentColor = HermesColors.TextSecondary,
            title = { Text(S.t2("Bakım çıktısı", "Maintenance output")) },
            text = {
                Column(
                    Modifier
                        .height(260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (logLines.isEmpty()) {
                        Text(
                            S.t2("Henüz çıktı yok.", "No output yet."),
                            color = HermesColors.TextMuted,
                            fontSize = 12.sp,
                        )
                    } else {
                        logLines.forEach { line ->
                            Text(
                                line,
                                style = MonoTextStyle,
                                color = HermesColors.TextSecondary,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { logOpen = false }) {
                    Text(S.t2("Kapat", "Close"), color = HermesColors.Midground)
                }
            },
        )
    }
}

/**
 * "Tam kontrol" anahtarı.

 *
 * İki katmanlı bir izin: buradaki anahtar ajanın niyetini kaydediyor,
 * erişilebilirlik servisi ise gerçek yetkiyi veriyor. İkisi ayrı gösteriliyor
 * çünkü uygulama erişilebilirlik iznini programatik olarak veremez — kullanıcı
 * sistem ayar sayfasından açmak zorunda ve bunu görmezse "açtım ama
 * çalışmıyor" durumuna düşer.
 */
@Composable
private fun FullControlRow(
    on: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var serviceOn by remember { mutableStateOf(false) }
    // Ayarlar → Erişilebilirlik'ten dönüldüğünde durum tazelenmeli; her
    // yeniden başlatmada değil, ekran öne geldiğinde okuyoruz.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        serviceOn = HermesAccessibilityService.isEnabled(context)
        onPauseOrDispose { }
    }

    HermesCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    S.t2("Tam kontrol", "Full control"),
                    color = HermesColors.TextPrimary,
                    fontSize = 14.sp,
                )
                Text(
                    S.t2(
                        "Ekranı okur, dokunur, yazar, kaydırır, ekran görüntüsü alır",
                        "Reads the screen, taps, types, swipes, takes screenshots",
                    ),
                    color = HermesColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            androidx.compose.material3.Switch(checked = on, onCheckedChange = onToggle)
        }

        if (on) {
            Spacer(Modifier.height(10.dp))
            val (label, color) = if (serviceOn) {
                S.t2("erişilebilirlik izni verildi", "accessibility permission granted") to
                    HermesColors.Online
            } else {
                S.t2("erişilebilirlik izni bekliyor", "accessibility permission missing") to
                    HermesColors.Busy
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(color, RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text(label, color = color, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                if (!serviceOn) {
                    Text(
                        S.t2("İzin ver", "Grant"),
                        color = HermesColors.Midground,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
                            .clickable { HermesAccessibilityService.openPermissionSettings(context) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                S.t2(
                    "Geri alınamaz eylemler bu katmanda yok: SMS gönderilmez, arama " +
                        "başlatılmaz, uygulama kaldırılmaz, kilitli ekranda kod girilmez. " +
                        "Açıkken kalıcı bir bildirim durur ve her eylem tanı günlüğüne yazılır.",
                    "Nothing irreversible is included: no sending SMS, no placing calls, " +
                        "no uninstalling apps, no entering codes on the lock screen. While " +
                        "it is on a persistent notification stays up and every action is " +
                        "written to the diagnostics log.",
                ),
                color = HermesColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BridgeStatusRow() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by com.hermes.mobile.data.PhoneBridgeService.stateFlow
        .collectAsState()
    val (label, color) = when (state) {
        com.hermes.mobile.data.BridgeState.CONNECTED ->
            S.t2("bağlı", "connected") to HermesColors.Online
        com.hermes.mobile.data.BridgeState.TAKEN_OVER ->
            S.t2("devralındı", "taken over") to HermesColors.Busy
        com.hermes.mobile.data.BridgeState.RETRYING ->
            S.t2("yeniden bağlanıyor", "reconnecting") to HermesColors.Busy
        com.hermes.mobile.data.BridgeState.CONNECTING ->
            S.t2("bağlanıyor", "connecting") to HermesColors.Busy
        com.hermes.mobile.data.BridgeState.OFF ->
            S.t2("kapalı", "off") to HermesColors.TextMuted
    }

    HermesCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    S.t2("Köprü durumu", "Bridge status"),
                    color = HermesColors.TextPrimary,
                    fontSize = 14.sp,
                )
                Text(
                    S.t2(
                        "Telefonun sunucuya açtığı kalıcı bağlantı",
                        "The persistent connection the phone opens to the server",
                    ),
                    color = HermesColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            Box(
                Modifier
                    .size(7.dp)
                    .background(color, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, color = color, fontSize = 11.sp)
        }

        if (state == com.hermes.mobile.data.BridgeState.TAKEN_OVER) {
            Spacer(Modifier.height(10.dp))
            Text(
                S.t2(
                    "Köprü başka bir cihaz tarafından devralındı — yeniden bağlanmak " +
                        "için dokun. Kendiliğinden yeniden bağlanma durduruldu; aksi " +
                        "hâlde iki cihaz birbirini sonsuz devirir.",
                    "The bridge was taken over by another device — tap to reconnect. " +
                        "Automatic reconnection is stopped; otherwise the two devices " +
                        "would keep evicting each other forever.",
                ),
                color = HermesColors.TextMuted,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                S.t2("Yeniden bağlan", "Reconnect"),
                color = HermesColors.Midground,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
                    .clickable { com.hermes.mobile.data.PhoneBridgeService.reconnect(context) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        } else if (state != com.hermes.mobile.data.BridgeState.CONNECTED) {
            Spacer(Modifier.height(10.dp))
            Text(
                S.t2(
                    "Koptuğunda artan aralıkla yeniden denenir (2 → 5 → 15 → 30 → 60 sn). " +
                        "Hemen denemek istersen:",
                    "On drop it retries with growing backoff (2 → 5 → 15 → 30 → 60 s). " +
                        "To try right now:",
                ),
                color = HermesColors.TextMuted,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                S.t2("Yeniden bağlan", "Reconnect"),
                color = HermesColors.Midground,
                fontSize = 11.sp,
                modifier = Modifier
                    .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
                    .clickable { com.hermes.mobile.data.PhoneBridgeService.reconnect(context) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
            )
        }
    }
}

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
