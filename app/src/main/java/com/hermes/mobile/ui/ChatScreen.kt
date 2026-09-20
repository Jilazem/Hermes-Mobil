package com.hermes.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ChatItem
import com.hermes.mobile.ChatState
import com.hermes.mobile.SpeedFormat
import com.hermes.mobile.StreamMeter
import com.hermes.mobile.ToolState
import com.hermes.mobile.liveThinkingTail
import com.hermes.mobile.data.ConnectionState
import com.hermes.mobile.data.HermesProfile
import com.hermes.mobile.data.PhoneIntent
import com.hermes.mobile.data.SavedPrompt
import com.hermes.mobile.data.VoiceController
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.hermes.mobile.data.DemoMask

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    state: ChatState,
    onSend: (String) -> Unit,
    onNewSession: () -> Unit,
    onStop: () -> Unit = {},
    onDictate: () -> Unit = {},
    onToggleHandsFree: () -> Unit = {},
    onPickImage: () -> Unit = {},
    onPickFile: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onOpenModelPicker: () -> Unit = {},
    onSuggestion: (String) -> Unit = {},
    onApproval: (String, Boolean) -> Unit = { _, _ -> },
    onOpenCommands: () -> Unit = {},
    onOpenFile: (FileRef) -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    /** Boş sohbet CTA'sı: sunucu + token ekranını açar (ilk kurulum yolu). */
    onOpenServers: () -> Unit = {},
    /** Üst çubuk Psychology ikonu: düşünce panosu alt sayfasını açar. */
    onOpenReasoning: () -> Unit = {},
    /**
     * ⋯ menüsünden çalışan ajana müdahale (KALAN-2): açık sohbette
     * `session.steer` / `session.redirect` — talimat metni bir diyalogla
     * sorulur, gönderim ChatViewModel'de yapılır.
     */
    onIntervene: (com.hermes.mobile.InterventionKind, String) -> Unit = { _, _ -> },
    /** Düşünce panosundaki çaba seçimi — `/reasoning <seviye>` slash komutu. */
    onReasoningLevel: (String) -> Unit = {},
    /** Gateway'in rapor ettiği aktif /reasoning seviyesi (null = bilinmiyor). */
    reasoningLevel: String? = null,
    /** "Düşünürken canlı göster" — canlı çizimi buna bağlı (aynı sayfa). */
    showLiveThinking: Boolean = true,
    onShowLiveThinking: (Boolean) -> Unit = {},
    activeProfileName: String = "",
    /**
     * Tur-16: üst çubuktaki ☰ ikonu — oturum çekmecesini açar (ModalNavigationDrawer,
     * sohbet arkada kalır; ayrı sayfa yok).
     */
    onOpenDrawer: () -> Unit = {},
    /**
     * Bot (profil) ataması — Composer üstündeki yatay çipler.
     *
     * `profiles` = `GET /api/profiles` listesi (hata/boşsa yalnız varsayılan
     * "Yönlendirici" çipi kalır). `selectedProfile` = kalıcı seçim
     * (settings); YENİ sohbette seçilen profil `createSession(profile)`
     * argümanı olur. `currentProfile` = mevcut oturumun profili; `null`
     * olmayan oturumda çipler salt-okunur (kilit) ve mevcut profil
     * (bilinmiyorsa "—") gösterilir.
     */
    profiles: List<com.hermes.mobile.data.HermesProfile> = emptyList(),
    selectedProfile: String = "",
    currentProfile: String? = null,
    onProfileChipClick: (String) -> Unit = {},
    /** Başka uygulamadan paylaşılan metin; geldiğinde taslağa eklenir. */
    sharedText: String? = null,
    onSharedTextConsumed: () -> Unit = {},
    /** Kayıtlı promptlar — boşsa boş-ekranda sabit öneriler gösterilir. */
    savedPrompts: List<SavedPrompt> = emptyList(),
    /** Sheet'e bağlanan CRUD + AI iyileştirme (AppViewModel / ChatViewModel). */
    onSavePrompt: (etiket: String, metin: String) -> Unit = { _, _ -> },
    onUpdatePrompt: (id: String, etiket: String, metin: String) -> Unit = { _, _, _ -> },
    onDeletePrompt: (id: String) -> Unit = {},
    onImprovePrompt: suspend (metin: String) -> String = { it },
    /** Yazma hızı göstergesi — ayrı StateFlow; ana `state` recomposition'ını tetiklemez. */
    speed: StateFlow<StreamMeter.Snapshot?> = MutableStateFlow(null),
    /**
     * Sesli mesaj (tur-11): kayıt fazı + seslendirme fazı.
     *
     * Ayrı bir StateFlow olarak geçirilir (`speed` deseni): kayıt sayacı her
     * 120 ms'de bir güncellenir, ana sohbet state'ini yeniden çizdirmesi
     * gerekmez.
     */
    voice: com.hermes.mobile.data.VoiceMessageController.UiState =
        com.hermes.mobile.data.VoiceMessageController.UiState(),
    /** Sesle yazılan metin — taslağa eklenir, sonra tüketilir (kapalıysa gönderilir). */
    voicePrefill: String? = null,
    onVoicePrefillConsumed: () -> Unit = {},
    onVoiceHoldStart: () -> Unit = {},
    onVoiceHoldRelease: () -> Unit = {},
    onVoiceCancel: () -> Unit = {},
    /** Asistan balonunu seslendir/durdur — (balon anahtarı, metin). */
    onSpeak: (String, String) -> Unit = { _, _ -> },
    /**
     * Tur-13: asistan modu şeridi — "yanıtı otomatik oku" anahtarının durumu
     * (`AppSettings.assistantAutoRead`).
     */
    autoReadAssistant: Boolean = true,
    /** Anahtar değişti — Ayarlar'daki aynı değere yazılır (tek kaynak). */
    onToggleAssistantAutoRead: (Boolean) -> Unit = {},
    /** Asistan modundan çık — normal sohbet davranışına dön. */
    onExitAssistantMode: () -> Unit = {},
) {
    // Sheet burada açılıyor: taslak `draft` bu kompozablda, "satıra dokun →
    // taslağı doldur" akışı (onUsePrompt) ancak burada çalışabilir.
    var promptsSheet by remember { mutableStateOf(false) }
    var reasoningSheet by remember { mutableStateOf(false) }
    // KALAN-2: ⋯ → "Müdahale" bu diyaloğu açar (yalnız ajan çalışırken).
    var interventionOpen by remember { mutableStateOf(false) }

    var draft by remember { mutableStateOf("") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(sharedText) {
        val incoming = sharedText ?: return@LaunchedEffect
        // Yazmakta olduğu bir şey varsa üstüne yazma, altına ekle.
        draft = if (draft.isBlank()) incoming else draft + "\n" + incoming
        onSharedTextConsumed()
    }

    // Sesli mesajdan gelen metin: "otomatik gönder" KAPALIYKEN buraya düşer.
    // Taslak doluysa üstüne yazmaz, altına ekler (paylaşılan metinle aynı kural).
    LaunchedEffect(voicePrefill) {
        val incoming = voicePrefill ?: return@LaunchedEffect
        draft = if (draft.isBlank()) incoming else draft + "\n" + incoming
        onVoicePrefillConsumed()
    }
    val listState = rememberLazyListState()
    var followBottom by remember { mutableStateOf(true) }

    // Tur-8 klavye düzeltmesi: klavye açılınca görünür alan kısalıyor;
    // LazyColumn konumu İLK görünür öğeye göre koruduğu için en alttaki
    // satırlar görünmez oluyordu ("metin yutulmuş"). Bu bayrak KULLANICI
    // NİYETİDİR: yalnız kullanıcı kaydırırken güncellenir, yerleşimin
    // kısalması niyeti bozmaz.
    var userPinnedBottom by remember { mutableStateOf(true) }
    // Klavye görünür mü (imePadding ile aynı inset kaynağı).
    val imeVisible = WindowInsets.isImeVisible

    // Ardışık araç çağrıları tek satıra katlanır; ham liste bozulmadan yalnız
    // görüntüleme katmanında gruplanır. RENDER EDİLEN liste budur — kaydırma
    // hedefi de bundan hesaplanır (tur-2 K1: eski kod ham items.lastIndex'i
    // katlanmış rows'a uyguluyor, taşkın indeks balonu header'ın altına
    // itip İLK SATIRI kırpıyordu).
    val rows = remember(state.items) { foldToolRuns(state.items) }

    // Kullanıcı bilinçli olarak yukarı kaydırırsa takip kapanır; alta dönünce
    // kendiliğinden açılır. (Eski kodda bayrağı false'a çeken hiçbir yol
    // yoktu — tek emniyet 'en altta değilse kaydırma' dalıydı.)
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val near = total <= 1 || lastVisible >= total - 2
            // Tur-8: kullanıcı niyeti — kaydırma sürerken "dipte" kararı
            // güncellenir; yerleşim değişimi (klavye) niyeti bozmaz.
            Triple(total, near, listState.isScrollInProgress)
        }.collect { (total, near, scrolling) ->
            followBottom = near
            if (total <= 1 || near) userPinnedBottom = true
            else if (scrolling) userPinnedBottom = false
        }
    }

    // Tur-8: görünür alan KISALDIĞINDA (klavye açıldı) kullanıcı dipteyse dibe
    // yasla — LazyColumn konumu ilk görünür öğeye göre korunduğu için en
    // alttaki satırlar aksi hâlde görünmez oluyordu ("metin yutulmuş").
    // Ölçüt IME animasyonunun kaç kare sürdüğü DEĞİL, yerleşimin kendisidir:
    // viewportSize değişimi layoutInfo'dan (yerleşim sonrası) okunur, yani
    // yeni yüksekliğe göre hesaplanır. Kullanıcı yukarıdaysa dokunulmaz.
    val currentRows by rememberUpdatedState(rows)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .distinctUntilChanged()
            .collect {
                if (!shouldPinToBottom(userPinnedBottom, listState.isScrollInProgress, currentRows.isEmpty())) return@collect
                listState.scrollToItem(currentRows.lastIndex)
            }
    }

    // Tur-2 K1: akışta izleme her mesaj türünde güncellenmeli — eski efekt
    // yalnız Thinking uzayan metni görüyordu, akan Assistant/Tool'da
    // kaydırma tetiklenmiyordu. Saf imza (test: ChatScrollTest) her
    // büyüme/eklenmede değişir.
    val streamSig = remember(state.items) { streamSignature(state.items) }
    LaunchedEffect(streamSig) {
        if (rows.isEmpty() || !followBottom) return@LaunchedEffect
        // Son BALON satırına yasla (sondaki sabit Spacer'a değil): balon üst
        // kenere oturur, ilk satırı header altında kalmaz; akışta alt satır
        // her zaman görünür.
        listState.scrollToItem(rows.lastIndex)
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        ChatHeader(
            state,
            onNewSession,
            onToggleHandsFree,
            onOpenModelPicker,
            onOpenCommands,
            onOpenProfiles,
            onOpenReasoning = { reasoningSheet = true },
            onIntervene = { interventionOpen = true },
            onStop = onStop,
            activeProfileName,
            onOpenDrawer = onOpenDrawer,
        )

        Box(Modifier.weight(1f)) {
            // KALAN-1: geçmiş yüklenirken boş ekran yerine iskelet balonlar
            // (boş-sohbet öneri ekranı ile "henüz gelmedi" karışmasın).
            val historyPlaceholder = skeletonRowCount(state.historyLoading, state.items.size, placeholder = 4)
            if (state.items.isEmpty() && historyPlaceholder > 0) {
                ChatSkeleton(
                    historyPlaceholder,
                    Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                )
            } else if (state.items.isEmpty()) {
                EmptyChatHint(
                    Modifier.align(Alignment.Center),
                    state.connection,
                    onSuggestion,
                    // Chip'ler mesaj GÖNDERMEZ, taslağa yazar — promptlar uzun,
                    // model seçimi önemli; gönderimi kullanıcı/IME yapar.
                    onUsePrompt = { prompt ->
                        draft = if (draft.isBlank()) prompt else draft + "\n" + prompt
                    },
                    savedPrompts = savedPrompts,
                    onOpenPrompts = { promptsSheet = true },
                    onOpenServers = onOpenServers,
                )
            } else {
                // rows yukarıda hesaplandı (render + kaydırma tek kaynak).
                // Tur-14: her satırın üstüne gün ayırıcı gerekir mi — önceki
                // damgalı mesajın tarihine bakılır (saf needsDaySeparator).
                val prevTsByKey = remember(rows) {
                    val m = HashMap<String, Double?>()
                    var last: Double? = null
                    rows.forEach { r ->
                        val ts = (r as? ChatRow.Single)?.item?.let { o ->
                            when (o) {
                                is ChatItem.User -> o.ts
                                is ChatItem.Assistant -> o.ts
                                else -> null
                            }
                        }
                        m[r.key] = last
                        if (ts != null) last = ts
                    }
                    m
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    itemsIndexed(rows, key = { _, r -> r.key }) { _, row ->
                        Column {
                            val prevTs = prevTsByKey[row.key]
                            val ts = (row as? ChatRow.Single)?.item?.let { o ->
                                when (o) {
                                    is ChatItem.User -> o.ts
                                    is ChatItem.Assistant -> o.ts
                                    else -> null
                                }
                            }
                            if (needsDaySeparator(prevTs, ts)) {
                                DaySeparatorRow(daySeparatorLabel(ts))
                            }
                            when (row) {
                                is ChatRow.Single -> ChatItemView(
                                    row.item,
                                    onApproval,
                                    onOpenFile,
                                    onSpeak = onSpeak,
                                    speakKey = voice.speak.key,
                                    speakBusy = voice.speak.phase ==
                                        com.hermes.mobile.data.VoiceSpeakLogic.Phase.Downloading,
                                )
                                is ChatRow.Tools -> ToolActivityRow(
                                    row.entries,
                                    label = if (S.lang == Lang.TR) DETAIL_ROW_TR else DETAIL_ROW_EN,
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(6.dp)) }
                }
            }
        }

        // Sesli kipte söylenen canlı olarak gösterilir; kullanıcı ne anlaşıldığını görür.
        if (state.voicePartial.isNotBlank()) {
            Text(
                state.voicePartial,
                color = HermesColors.Midground,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }

        state.statusLine?.let { line ->
            Text(
                line,
                style = MonoTextStyle,
                color = HermesColors.TextFaint,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 3.dp),
            )
        }

        // Bot (profil) ataması: SpeedLine'ın ÜSTÜNDE yatay çipler —
        // "Yönlendirici" (varsayılan) + profiller. Mevcut oturumda kilitli.
        // Tur-8 kararı (GİZLE): klavye açıkken bu çip satırı gizlenir. Bot
        // seçimi oturum BAŞINDA yapılan bir karar; yazarken mesaj alanına
        // ~36dp kazandırmak daha değerli (kullanıcı şikâyeti: klavye açıkken
        // içerik sıkışıyor). Klavye kapanınca satır aynı yerine döner.
        if (!imeVisible) {
            ProfileChipsRow(
                profiles = profiles,
                selectedProfile = selectedProfile,
                currentProfile = currentProfile,
                onChipClick = onProfileChipClick,
            )
        }

        // Yazma hızı: tek satır + akan shimmer. message.complete'te değerler
        // donar; satır 600 ms sönüşle kalkar. Akış AYNI sayfada toplanıyor ama
        // collect SpeedRow içinde: her pencere yalnız bu satırı yeniden derler.
        SpeedRow(speed)

        // Seslendirme satırı: indiriliyor (soğuk motor uyarısı 8 sn sonra) /
        // çalıyor / son hata. Aynı satır balonun hoparlör ikonuna da bağlı.
        com.hermes.mobile.data.VoiceSpeakLogic
            .statusLine(voice.speak, ::tr)
            ?.let { line ->
                Text(
                    line,
                    color = if (voice.speak.phase == com.hermes.mobile.data.VoiceSpeakLogic.Phase.Idle)
                        HermesColors.Danger else HermesColors.Midground,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
                )
            }

        // ── Telefon asistanı şeridi (tur-13) ─────────────────────────────
        // Asistan modunda bas-konuş ÖNE ÇIKAR: faz ipucu + "yanıtı otomatik
        // oku" anahtarı + çıkış. Mikrofonu açan tek şey kullanıcının basışı;
        // bu şerit yalnız durumu gösterir.
        if (state.assistantMode) {
            AssistantBanner(
                phase = com.hermes.mobile.data.AssistantModeLogic.phase(
                    active = true,
                    record = voice.record,
                    speak = voice.speak,
                    agentBusy = state.agentBusy,
                ),
                autoRead = autoReadAssistant,
                onToggleAutoRead = onToggleAssistantAutoRead,
                onExit = onExitAssistantMode,
            )
        }

        ChatComposer(
            draft = draft,
            // Yazmak her zaman açık; kopukken mesaj kuyruğa giriyor.
            enabled = true,
            online = state.connection is ConnectionState.Open,
            agentBusy = state.agentBusy,
            attachments = state.attachments,
            voiceMode = state.voiceMode,
            voiceRecord = voice.record,
            onVoiceHoldStart = onVoiceHoldStart,
            onVoiceHoldRelease = onVoiceHoldRelease,
            onVoiceCancel = onVoiceCancel,
            onDraftChange = { draft = it },
            onSend = {
                onSend(draft)
                draft = ""
            },
            onStop = onStop,
            onPickImage = onPickImage,
            onPickFile = onPickFile,
            onPasteUrl = {
                // Panodaki metni taslağın sonuna ekler — URL, hata çıktısı,
                // başka uygulamadan kopyalanan parça.
                clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let { text ->
                    draft = if (draft.isBlank()) text else draft + "\n" + text
                }
            },
            onOpenSnippets = onOpenCommands,
            onDictate = onDictate,
            onRemoveAttachment = onRemoveAttachment,
        )
    }

    if (promptsSheet) {
        PromptsSheet(
            prompts = savedPrompts,
            onSave = onSavePrompt,
            onUpdate = onUpdatePrompt,
            onDelete = onDeletePrompt,
            // Chip akışıyla aynı kural: draft doluysa sonuna eklenir.
            onUsePrompt = { prompt ->
                draft = if (draft.isBlank()) prompt else draft + "\n" + prompt
            },
            onImprove = onImprovePrompt,
            onDismiss = { promptsSheet = false },
        )
    }

    if (reasoningSheet) {
        ReasoningSheet(
            selected = reasoningLevel,
            onLevel = { lvl -> onReasoningLevel(lvl); reasoningSheet = false },
            showLiveThinking = showLiveThinking,
            onShowLiveThinking = onShowLiveThinking,
            onDismiss = { reasoningSheet = false },
        )
    }

    // KALAN-2: açık sohbetten çalışan ajana müdahale — Canlı sekmesindeki
    // diyaloğun aynısı (tek sözleşme: "Ekle" turu kesmez, "Yönlendir" çevirir).
    if (interventionOpen) {
        InterventionDialog(
            session = interventionSession(state),
            title = state.topic.takeIf { it.isNotBlank() } ?: S.t2("Oturum", "Session"),
            sending = false,
            onDismiss = { interventionOpen = false },
            onSubmit = { kind, text ->
                onIntervene(kind, text)
                interventionOpen = false
            },
        )
    }
}

/**
 * Müdahale diyaloğu bir `LiveSession` bekliyor; açık sohbette elimizde yalnız
 * sohbet state'i var. Köprü saf ve önemsiz görünse de tek yerde tutuluyor:
 * başlık boşsa "Sohbet" yazılır (diyalog başlığı boş çıkmasın).
 */
fun interventionSession(state: ChatState): com.hermes.mobile.data.LiveSession =
    com.hermes.mobile.data.LiveSession(
        id = state.sessionId.orEmpty(),
        sessionKey = state.sessionId.orEmpty(),
        title = state.topic,
    )

/** ⋯ menüsü etiketi — dile göre. */
@Composable
private fun chatMenuLabel(action: ChatMenuAction): String = when (action) {
    ChatMenuAction.Model -> S.t2("Model ve profiller", "Model and profiles")
    ChatMenuAction.Reasoning -> S.t2("Düşünme", "Thinking")
    ChatMenuAction.Intervene -> S.t2("Müdahale…", "Steer…")
    ChatMenuAction.Stop -> S.t2("Durdur", "Stop")
}

/** ⋯ menüsü ikonu — saf eşleme (dil bağımsız). */
private fun chatMenuIcon(action: ChatMenuAction) = when (action) {
    ChatMenuAction.Model -> Icons.Default.Tune
    ChatMenuAction.Reasoning -> Icons.Default.Psychology
    ChatMenuAction.Intervene -> Icons.Default.EditNote
    ChatMenuAction.Stop -> Icons.Default.Stop
}

@Composable
private fun ChatHeader(
    state: ChatState,
    onNewSession: () -> Unit,
    onToggleHandsFree: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenCommands: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenReasoning: () -> Unit,
    onIntervene: () -> Unit,
    onStop: () -> Unit,
    activeProfileName: String,
    onOpenDrawer: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Tur-4 (P2 #7): model etiketi ve "bağlı" rozeti üst şeritten ÇIKTI.
    // Bağlantı yalnız KOPUNCA görünür (kırmızı "bağlantı yok"); sağlıklı
    // durumda sessiz — Telegram da "bağlıyım" demez.
    val problem = when (val c = state.connection) {
        is ConnectionState.Open -> null
        is ConnectionState.Error -> c.reason
        is ConnectionState.Connecting -> null
        is ConnectionState.Closed -> S.t2("bağlantı yok", "no connection")
        ConnectionState.Idle -> S.t2("bağlantı yok", "no connection")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tur-16: ☰ — oturum çekmecesi (Claude/Grok/Gemini: menü solda).
        IconButton(onClick = onOpenDrawer) {
            Icon(
                Icons.Default.Menu,
                contentDescription = S.t2("Oturumlar", "Sessions"),
                tint = HermesColors.TextMuted,
            )
        }
        StatusDot(if (problem == null) HermesColors.Online else HermesColors.Danger)
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onOpenProfiles)
        ) {
            // Tek satır KONU (P3 #8): chrome ince, model orada durmaz.
            Text(
                state.topic.takeIf { it.isNotBlank() } ?: S.chatTitle,
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // İkinci satır yalnız gerçekten bir şey söylüyorsa.
            problem?.let {
                Text(it, color = HermesColors.Danger, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }

        // Model, düşünme ve (ajan çalışırken) müdahale/durdurma tek ⋯ menüsünde.
        // Etiketsiz ikon: iç terminoloji (model adı, profil adı) üst şeride yazılmaz.
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = S.t2("Menü", "Menu"),
                    tint = HermesColors.TextMuted,
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = HermesColors.Surface,
            ) {
                chatMenuActions(state.agentBusy).forEach { action ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                chatMenuLabel(action),
                                color = if (action == ChatMenuAction.Stop)
                                    HermesColors.Danger else HermesColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                chatMenuIcon(action),
                                contentDescription = null,
                                tint = if (action == ChatMenuAction.Stop)
                                    HermesColors.Danger else HermesColors.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            when (action) {
                                ChatMenuAction.Model -> onOpenModelPicker()
                                ChatMenuAction.Reasoning -> onOpenReasoning()
                                ChatMenuAction.Intervene -> onIntervene()
                                ChatMenuAction.Stop -> onStop()
                            }
                        },
                    )
                }
            }
        }

        // Başlıkta yalnız iki ikon: ses + yeni oturum. Komut paleti composer'ın
        // "Ek" menüsünde (onOpenSnippets = onOpenCommands), profil ⋯ içinde.
        IconButton(onClick = onToggleHandsFree) {
            Icon(
                if (state.handsFree) Icons.Default.RecordVoiceOver else Icons.Default.Headphones,
                contentDescription = if (state.handsFree)
                    S.t2("Sesli kipi kapat", "Turn voice mode off")
                else S.t2("Sesli sohbet", "Voice chat"),
                tint = if (state.handsFree) HermesColors.Online else HermesColors.TextMuted,
            )
        }

        IconButton(onClick = onNewSession) {
            Icon(
                Icons.Default.Add,
                contentDescription = S.t2("Yeni sohbet", "New chat"),
                tint = HermesColors.Midground,
            )
        }
    }

    if (state.handsFree) VoiceBanner(state.voiceMode)
}

/** Sesli kip açıkken ne yapıldığını gösteren şerit. */
@Composable
private fun VoiceBanner(mode: VoiceController.Mode) {
    val (text, tint) = when (mode) {
        VoiceController.Mode.Listening -> S.t2("Dinliyorum — konuşun", "Listening — go ahead") to HermesColors.Online
        VoiceController.Mode.Thinking -> S.t2("Düşünüyor…", "Thinking…") to HermesColors.Busy
        VoiceController.Mode.Speaking -> S.t2("Yanıtlıyor — durdurmak için dokunun", "Replying — tap to stop") to HermesColors.Midground
        VoiceController.Mode.Off -> S.t2("Sesli kip hazır", "Voice mode ready") to HermesColors.TextMuted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(tint, size = 7)
        Spacer(Modifier.width(8.dp))
        Text(text, color = tint, style = MaterialTheme.typography.bodySmall)
    }
}

/** Ajana yönelik örnekler — dile göre. */
@Composable
private fun suggestions(): List<String> =
    if (S.lang == Lang.EN) listOf(
        "Summarize gateway status and recent errors",
        "Show today's cron job results",
        "Summarize the latest report file",
        "List the connected MCP tools",
    ) else listOf(
        "Gateway durumunu ve son hataları özetle",
        "Bugünkü cron işlerinin sonucunu göster",
        "Son rapor dosyasını özetle",
        "Hangi MCP araçları bağlı, listele",
    )

/** Telefon eylemi örnekleri — niyet çözümleyici iki dili de anlıyor. */
@Composable
private fun phoneExamples(): List<String> =
    if (S.lang == Lang.EN) listOf(
        "open WhatsApp",
        "directions to Berlin",
        "battery",
        "turn on the flashlight",
    ) else PhoneIntent.EXAMPLES

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EmptyChatHint(
    modifier: Modifier,
    connection: ConnectionState,
    onSuggestion: (String) -> Unit,
    onUsePrompt: (String) -> Unit,
    savedPrompts: List<SavedPrompt>,
    onOpenPrompts: () -> Unit,
    onOpenServers: () -> Unit = {},
) {
    Column(
        modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(S.emptyTitle, color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            when (connection) {
                is ConnectionState.Open -> S.emptyHint
                is ConnectionState.Error -> connection.reason
                else -> S.t2("Gateway bağlantısı bekleniyor…", "Waiting for the gateway…")
            },
            color = HermesColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        // İlk kurulum yolu (emülatör denetimi bulgu-1, 2026-09-14): bağlantı
        // yokken kullanıcı "nereye token gireceğini" bulamıyordu — CTA doğrudan
        // Sunucular ekranına götürür (≤2 dokunuş: CTA → Sunucu ekle).
        if (connection !is ConnectionState.Open) {
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.Button(onClick = onOpenServers) {
                Text(S.t2("Sunucu ekle", "Add server"))
            }
        }
        if (connection is ConnectionState.Open) {
            Spacer(Modifier.height(18.dp))
            // Öneri çipleri: dokunma taslağı doldurur, mesaj göndermez (draft
            // doluysa üstüne yazmaz, sonuna ekler — sharedText kuralıyla aynı).
            // Kayıtlı promptlar varsa onlar gösterilir; liste boşsa sabit
            // öneriler kalır. Sondaki "+", prompt sheet'ini açar.
            val chips: List<Pair<String, String>> =
                if (savedPrompts.isNotEmpty()) savedPrompts.map { it.label to it.text }
                else suggestions().map { it to it }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { (label, body) ->
                    AssistChip(
                        onClick = { onUsePrompt(body) },
                        label = {
                            Text(
                                label,
                                color = HermesColors.TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Bolt,
                                null,
                                tint = HermesColors.Midground,
                                modifier = Modifier.size(14.dp),
                            )
                        },
                    )
                }
                AssistChip(
                    onClick = onOpenPrompts,
                    label = {
                        Text(
                            S.t2("Promptlar", "Prompts"),
                            color = HermesColors.Midground,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Add,
                            null,
                            tint = HermesColors.Midground,
                            modifier = Modifier.size(14.dp),
                        )
                    },
                )
            }

            // Telefon eylemleri cihazda çalışıyor, sunucuya hiç gitmiyor —
            // kullanıcının bunu bilmesi gerek, yoksa hiç denemiyor.
            Spacer(Modifier.height(18.dp))
            Text(
                S.phoneSectionTitle,
                color = HermesColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(7.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                phoneExamples().take(2).forEach { example ->
                    Text(
                        example,
                        color = HermesColors.Midground,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                            .clickable { onSuggestion(example) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                phoneExamples().drop(2).forEach { example ->
                    Text(
                        example,
                        color = HermesColors.Midground,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                            .clickable { onSuggestion(example) }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

/** Görüntüleme satırı — ya tek öğe ya da katlanmış araç dizisi. */
private sealed interface ChatRow {
    val key: String

    data class Single(val item: ChatItem) : ChatRow {
        override val key: String get() = item.key
    }

    data class Tools(override val key: String, val entries: List<ToolEntry>) : ChatRow
}

/** "Ayrıntı" satırının etiketi — katlanmış ajan günlüğü (tur-4 H). */
const val DETAIL_ROW_TR = "Ayrıntı"
const val DETAIL_ROW_EN = "Details"

/**
 * Ray/sohbet kaydırıcısı: yerleşim değişiminde (klavye açılması) dibe yaslama
 * kararı — tur-8. Kullanıcı NİYETİ (dipte mi) korunur; kaydırma sürüyorsa ya
 * da liste boşsa dokunulmaz. Saf — JVM testi (ChatScrollTest).
 */
internal fun shouldPinToBottom(
    userPinnedBottom: Boolean,
    scrolling: Boolean,
    empty: Boolean,
): Boolean = userPinnedBottom && !scrolling && !empty

/**
 * Akış imzası (tur-2 K1) — sohbet kaydırıcısının "yeni içerik var" sinyalini
 * her mesaj türünde üretir. Eski efekt yalnız Thinking.text uzunluğuna
 * bakıyordu; akan Assistant metni ve biten araç satırları kaydırmayı
 * tetiklemiyordu → en alttaki balonun ilk satırı header altında kalıyordu.
 *
 * Biçim: `anahtar|uzunluk|durum` öğelerinin boşluksuz birleşimi. Uzunluk
 * YAKLAŞIK olabilir (uydurma değil, ama hassas değil); önemli olan akış
 * ilerledikçe DEĞİŞMESİ ve akış durunca SABİT kalmasıdır. Saf — JVM testi.
 */
internal fun streamSignature(items: List<ChatItem>): String =
    items.joinToString(";") { item ->
        when (item) {
            is ChatItem.User -> "U:${item.key}:${item.text.length}"
            is ChatItem.Assistant -> "A:${item.key}:${item.text.length}:${item.streaming}"
            is ChatItem.Thinking -> "T:${item.key}:${item.text.length}:${item.live}"
            is ChatItem.Tool -> "F:${item.key}:${item.state}:${item.detail?.length ?: -1}"
            is ChatItem.Notice -> "N:${item.key}:${item.text.length}"
            is ChatItem.Approval -> "K:${item.key}:${item.answered ?: ""}"
        }
    }

// ── Tur-14: tarih-saat ayraçları ─────────────────────────────────────────────
// Claude/ChatGPT/Grok düzeni: mesajlar gün bölümlerinde çizilir; gün içinde
// damga balonun altında küçük yazıyla durur.

/** Ayraç başlığı: bugün/dün → isim, daha eskisi tarih, saat dilimi safe. */
internal fun daySeparatorLabel(epochSeconds: Double?, nowMs: Long = System.currentTimeMillis()): String? {
    if (epochSeconds == null || epochSeconds <= 0) return null
    val d = java.time.Instant.ofEpochSecond(epochSeconds.toLong())
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    val today = java.time.Instant.ofEpochMilli(nowMs)
        .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return when (d) {
        today -> tr("Bugün", "Today")
        today.minusDays(1) -> tr("Dün", "Yesterday")
        else -> "%02d.%02d.%04d".format(d.dayOfMonth, d.monthValue, d.year)
    }
}

/** Balon altı saat damgası — "14:32". */
internal fun clockLabel(epochSeconds: Double?): String? {
    if (epochSeconds == null || epochSeconds <= 0) return null
    val t = java.time.Instant.ofEpochSecond(epochSeconds.toLong())
        .atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}

/**
 * Bu satırın üstüne GÜN ayırıcı çizilmeli mi? Sıralı listede gün değişimini
 * bulan saf geçit — kullanıcı/askistan bağımsız çalışır, ilk öğe daima ayracı alır.
 * Saf — JVM testi (ChatDaySeparatorTest).
 */
internal fun needsDaySeparator(prevTs: Double?, ts: Double?): Boolean {
    if (ts == null || ts <= 0) return false
    if (prevTs == null || prevTs <= 0) return true
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
    val a = java.time.Instant.ofEpochSecond(prevTs.toLong()).atZone(zone).toLocalDate()
    val b = java.time.Instant.ofEpochSecond(ts.toLong()).atZone(zone).toLocalDate()
    return a != b
}

/** Satır çiftleri: (önceki ts, öğe) — ayraç kararının girdisi. */
internal data class TimedRow(val item: ChatItem, val prevTs: Double?)

/** Gün ayırıcı satırı — "Bugün" / "Dün" / "12.09.2026" ortalanmış ince etiket. */
@Composable
internal fun DaySeparatorRow(label: String?) {
    if (label.isNullOrBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = HermesColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * Ajan günlüğünü tek satıra katlar (tur-4 H): ardışık `Tool` öğeleri + bitmiş
 * `Thinking` blokları TEK "Ayrıntı" satırına iner. Canlı (akan) düşünme bloğu
 * katlanmaz — kullanıcı yazarken görür (Telegram "yazıyor…" karşılığı);
 * asistanın metni her durumda öne çıkar (hiç katlanmaz).
 */
private fun foldToolRuns(items: List<ChatItem>): List<ChatRow> {
    val rows = mutableListOf<ChatRow>()
    val run = mutableListOf<ToolEntry>()
    var runKey: String? = null

    fun flush() {
        if (run.isEmpty()) return
        rows += ChatRow.Tools(key = "detail-$runKey", entries = run.toList())
        run.clear()
        runKey = null
    }

    fun detailOf(t: ChatItem.Tool) = ToolEntry(
        name = t.name,
        state = when (t.state) {
            ToolState.Running -> ToolEntryState.Running
            ToolState.Done -> ToolEntryState.Done
            ToolState.Failed -> ToolEntryState.Failed
        },
        detail = t.detail,
    )

    items.forEach { item ->
        when {
            item is ChatItem.Tool -> {
                if (runKey == null) runKey = item.key
                run += detailOf(item)
            }
            // Bitmiş düşünme bloğu da ayrıntıya katılır; canlı blok katlanmaz.
            item is ChatItem.Thinking && !item.live -> {
                if (runKey == null) runKey = item.key
                run += ToolEntry(name = "Düşünme", state = ToolEntryState.Done, detail = item.text)
            }
            else -> {
                flush()
                rows += ChatRow.Single(item)
            }
        }
    }
    flush()
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatItemView(
    item: ChatItem,
    onApproval: (String, Boolean) -> Unit,
    onOpenFile: (FileRef) -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    activeProfileName: String = "",
    /**
     * Seslendirme (tur-11): balona **uzun basma** ya da hoparlör ikonu
     * [onSpeak]'i çağırır; [speakKey] çalan/indirilen balonun anahtarıdır
     * (aynı balona ikinci dokunuş durdurur), [speakBusy] indirme sürüyor.
     */
    onSpeak: (String, String) -> Unit = { _, _ -> },
    speakKey: String? = null,
    speakBusy: Boolean = false,
) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    // tur-17 B rol-tuketici: bubbleUser = kullanici balonu zemini
                    // (§3; varsayilan surface — piksel ayni, semantik bag).
                    .background(HermesColors.BubbleUser, MaterialTheme.shapes.medium)
                    .border(1.dp, HermesColors.BorderStrong, MaterialTheme.shapes.medium)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(item.text, color = HermesColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        is ChatItem.Assistant -> Column(
            Modifier
                .fillMaxWidth()
                // Uzun basma = seslendir/durdur (kopyalama jesti burada yok;
                // metin seçimi MarkdownText içinde kendi yolunda).
                .combinedClickable(
                    onLongClick = { if (!item.streaming) onSpeak(item.key, item.text) },
                    onClick = {},
                ),
        ) {
            MarkdownText(
                markdown = item.text + if (item.streaming) " ▌" else "",
                modifier = Modifier.fillMaxWidth(),
            )
            // Ajan dosya ürettiyse altına indirilebilir kart koy — masaüstünde
            // tıklanabilir olan bağlantının mobil karşılığı.
            if (!item.streaming) {
                FileRefRow(remember(item.text) { extractFileRefs(item.text) }, onOpenFile)
                // Sesli okuma satırı: hoparlör ikonu + motor bilgisi.
                val speaking = speakKey == item.key
                Row(
                    Modifier.padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onSpeak(item.key, item.text) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (speaking && speakBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.5.dp,
                                color = HermesColors.Busy,
                            )
                        } else {
                            Icon(
                                when {
                                    speaking -> Icons.Default.Stop
                                    else -> Icons.Default.VolumeUp
                                },
                                contentDescription = if (speaking)
                                    S.t2("Sesi durdur", "Stop the audio")
                                else S.t2("Sesli oku", "Read aloud"),
                                tint = if (speaking) HermesColors.Midground else HermesColors.TextFaint,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Text(
                        if (speaking) S.t2("Çalıyor", "Playing") else S.t2("Sesli oku", "Read aloud"),
                        color = HermesColors.TextFaint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        is ChatItem.Thinking -> {
            var expanded by remember(item.key) {
                // Canlı (hâlâ akan) blok varsayılanı AÇIK: son satırları
                // izlemek için; biten blok kapanır (katlanır davranış).
                mutableStateOf(item.live)
            }
            // Canlı kuyruk: metin uzadıkça her yeniden çizimde son dolu
            // satırlar yeniden hesaplanır — LazyColumn kaydırması değil,
            // içerik kendini günceller (4 satır, ucu ' ▌' ile işaretli).
            val liveTail = liveThinkingTail(item.text)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.t2("Düşünüyor", "Thinking"), color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = HermesColors.TextFaint,
                        modifier = Modifier.width(16.dp),
                    )
                }
                if (item.live) {
                    // Canlı görünüm: açık blok + son 3-4 dolu satır + sonda imleç.
                    // Kullanıcı en alttayken LazyColumn kendiliğinden izler.
                    val shown = liveTail.ifEmpty { "…" }
                    Text(
                        buildString {
                            if (item.text.length > liveTail.length) {
                                append("…\n")
                            }
                            append(shown)
                            append(" ▌")
                        },
                        color = HermesColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    // Katlanabilir tarih: mevcut davranış aynen.
                    AnimatedVisibility(expanded) {
                        Text(
                            item.text,
                            color = HermesColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }

        // Normalde `foldToolRuns` araçları gruplar; buraya yalnız tek başına
        // kalan bir araç düşer.
        is ChatItem.Tool -> ToolActivityRow(
            listOf(
                ToolEntry(
                    name = item.name,
                    state = when (item.state) {
                        ToolState.Running -> ToolEntryState.Running
                        ToolState.Done -> ToolEntryState.Done
                        ToolState.Failed -> ToolEntryState.Failed
                    },
                    detail = item.detail,
                )
            )
        )

        is ChatItem.Notice -> Text(
            item.text,
            color = if (item.isError) HermesColors.Danger else HermesColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )

        is ChatItem.Approval -> ApprovalCard(item, onApproval)
    }
}

/**
 * Onay kartı — Telegram'daki inline onay düğmelerinin karşılığı.
 *
 * Ajan tehlikeli bir komut çalıştırmadan önce durur; `/approve` ya da `/deny`
 * gönderilene kadar bekler.
 */
@Composable
private fun ApprovalCard(item: ChatItem.Approval, onApproval: (String, Boolean) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .border(
                1.dp,
                if (item.answered == null) HermesColors.Busy else HermesColors.Border,
                MaterialTheme.shapes.medium,
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = HermesColors.Busy,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (item.isSudo) S.t2("Yükseltilmiş izin isteniyor", "Elevated permission requested") else "Onay bekleniyor",
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.text,
            style = MonoTextStyle,
            color = HermesColors.TextSecondary,
        )
        Spacer(Modifier.height(11.dp))

        if (item.answered != null) {
            Text(item.answered, color = HermesColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .weight(1f)
                        .background(HermesColors.Midground, MaterialTheme.shapes.medium)
                        .clickable { onApproval(item.key, true) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Onayla", color = HermesColors.OnAccent, style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    Modifier
                        .weight(1f)
                        .border(1.dp, HermesColors.Danger, MaterialTheme.shapes.medium)
                        .clickable { onApproval(item.key, false) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Reddet", color = HermesColors.Danger, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * Yazma hızı satırı: giriş alanının üstünde tek satır —
 * `≈24 t/s · 1,2k token · 0:14` — altında 2dp akan shimmer çizgi.
 *
 * Ucuz tutuldu: StateFlow yalnız bu kompozablda toplanıyor, yani her 500 ms
 * pencere yalnız bu satırı yeniden derler; sohbet listesi etkilenmez. Metin
 * `Text` olarak tek defada yazılıyor (anlamsız parçalı recomposition yok).
 * Shimmer: `rememberInfiniteTransition` + `drawWithContent` yatay gradyan —
 * tek draw op, gradient brush `remember`'lanıyor.
 */
@Composable
private fun SpeedRow(speed: StateFlow<StreamMeter.Snapshot?>) {
    val snap by speed.collectAsState()
    val shown = snap?.takeIf { it.active }
    val tr = S.lang == Lang.TR
    val sep = if (tr) ',' else '.'
    val line = shown?.let {
        val rate = SpeedFormat.rate(it.tokensPerSecond, sep)
        val tokens = SpeedFormat.compactTokens(it.tokens, sep)
        val clock = SpeedFormat.clock(it.elapsedMs)
        // Faz etiketi: düşünce fazı "düşünüyor", yanıt fazı "yazıyor".
        // Aynı sayaç iki fazi de ölçüyor — sayı kesintisiz akıyor,
        // yalnız etiket geçişte değişiyor.
        val phaseLabel = when (it.phase) {
            StreamMeter.PHASE_THINKING ->
                if (tr) " · düşünüyor" else " · thinking"
            else -> if (tr) " · yazıyor" else " · writing"
        }
        S.t2(
            "≈$rate t/s · $tokens token · $clock$phaseLabel",
            "≈$rate t/s · $tokens tokens · $clock$phaseLabel"
        )
    }.orEmpty()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(shown?.finished) {
        // finished=false iken akıyor; true olduğu an satır kalkmaya başlar —
        // 600 ms'lik fadeOut (exit animasyonu) sönüşü kendisi yapar.
        visible = shown != null && shown.active && !shown.finished
    }

    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible && line.isNotBlank(),
        enter = HermesMotion.fadeSwap(reduced),
        // Akış bitişi sönüşü tur-13'den 600ms'ti — davranış korunur, yalnız
        // reduced-motion'da anlığa iner.
        exit = fadeOut(HermesMotion.tweenSpec(HermesMotion.FADE_OUT_SLOW_MS, reduced)),
    ) {
        // Donmuş (finished) anda shimmer dursun: çizgi tek tona döner.
        val frozen = snap?.finished == true
        val base = HermesColors.BorderStrong
        val hot = HermesColors.Midground
        // Tur22: shimmer kayması + reduced-motion saygısı merkezi Motion'dan.
        val shift = HermesMotion.shimmerShift(reduced = reduced, frozen = frozen)
        Column {
            Text(
                line,
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(3.dp))
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(2.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .drawWithContent {
                        // Ucuz: tek yatay gradyan, kayan parlak bant.
                        val mid = (0.3f + shift * 0.4f).coerceIn(0.05f, 0.95f)
                        drawRect(
                            brush = Brush.linearGradient(
                                0f to base,
                                mid to hot,
                                1f to base,
                                start = Offset(shift * size.width - size.width * 0.25f, 0f),
                                end = Offset(shift * size.width + size.width * 0.75f, 0f),
                            ),
                        )
                    }
            )
        }
    }
}

/**
 * Prompt sırasındaki bot (profil) ataması — SpeedLine'ın ÜSTÜNDE yatay çipler.
 *
 * Çipler: "Yönlendirici" (varsayılan, profile boş) + her profil bir çip
 * (`GET /api/profiles`). Liste boş/hatalıysa yalnız varsayılan çip kalır.
 *
 * Kilit: mevcut oturumda (`currentProfile != null`) çipler salt-okunur;
 * yalnız mevcut profil gösterilir (bilinmiyorsa "—"). YENİ sohbette
 * (`currentProfile == null`) çipler tıklanabilir; seçilen profil
 * `createSession(profile)` argümanı olur (varsayılan → null).
 */
@Composable
private fun ProfileChipsRow(
    profiles: List<HermesProfile>,
    selectedProfile: String,
    currentProfile: String?,
    onChipClick: (String) -> Unit,
) {
    val tr = S.lang == Lang.TR
    val locked = chipsLocked(currentProfile)
    // Tur-4 (kusur G): İÇ AD SIZMAZ. İnsan adı olmayan profil ("default", "ac",
    // "android") çip olarak ÇİZİLMEZ; kalan yoksa satır tamamen gizlenir.
    val visible = visibleProfileChips(profiles)

    if (locked) {
        val label = visible.firstOrNull { it.first.name == currentProfile }?.second
            ?: profiles.firstOrNull { it.name == currentProfile }
                ?.displayName?.takeIf { !isInternalProfileName(it) }
        if (label.isNullOrBlank()) return
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = {},
                label = {
                    Text(label, color = HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                },
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val routerSelected = selectedProfile.isEmpty() || selectedProfile == ROUTER_CHIP
        AssistChip(
            onClick = { onChipClick(ROUTER_CHIP) },
            label = {
                Text(
                    if (tr) ROUTER_LABEL_TR else ROUTER_LABEL_EN,
                    color = if (routerSelected) HermesColors.TextPrimary else HermesColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (routerSelected) FontWeight.Medium else FontWeight.Normal,
                )
            },
        )
        visible.forEach { (profile, label) ->
            val sel = selectedProfile == profile.name
            AssistChip(
                onClick = { onChipClick(profile.name) },
                label = {
                    Text(
                        label,
                        color = if (sel) HermesColors.TextPrimary else HermesColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

/**
 * Tur-13: telefon asistanı şeridi — bas-konuş öne çıkar, durum tek satırda.
 *
 * Şerit HİÇBİR ŞEYİ kendiliğinden başlatmaz: mikrofon yalnız kullanıcının
 * basışıyla açılır (asistan hareketi mikrofonu açmaz — yanlışlıkla kayıt
 * olmasın). "Yanıtı otomatik oku" anahtarı Ayarlar'daki aynı değeri yazar
 * (tek kaynak), böylece iki yerde ayrı doğruluk tutulmaz.
 */
@Composable
private fun AssistantBanner(
    phase: com.hermes.mobile.data.AssistantModeLogic.Phase,
    autoRead: Boolean,
    onToggleAutoRead: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.BorderStrong, MaterialTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = HermesColors.Online,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                com.hermes.mobile.data.AssistantModeLogic.bannerText(phase, ::tr),
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                S.t2("Çık", "Exit"),
                color = HermesColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clickable(onClick = onExit)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            S.t2(
                "Yerel hat · voice_api · Google'a gitmez",
                "Local path · voice_api · never goes to Google",
            ),
            color = HermesColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .background(HermesColors.Surface, MaterialTheme.shapes.large)
                .border(
                    1.dp,
                    if (autoRead) HermesColors.Midground else HermesColors.BorderStrong,
                    MaterialTheme.shapes.large,
                )
                .clickable { onToggleAutoRead(!autoRead) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.VolumeUp,
                contentDescription = null,
                tint = if (autoRead) HermesColors.Midground else HermesColors.TextFaint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (autoRead) S.t2("Yanıtı otomatik oku ✓", "Auto-read reply ✓")
                else S.t2("Yanıtı otomatik oku", "Auto-read reply"),
                color = if (autoRead) HermesColors.TextSecondary else HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
