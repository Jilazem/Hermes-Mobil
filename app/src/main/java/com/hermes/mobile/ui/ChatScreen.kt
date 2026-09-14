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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.size
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
    /** Düşünce panosundaki çaba seçimi — `/reasoning <seviye>` slash komutu. */
    onReasoningLevel: (String) -> Unit = {},
    /** Gateway'in rapor ettiği aktif /reasoning seviyesi (null = bilinmiyor). */
    reasoningLevel: String? = null,
    /** "Düşünürken canlı göster" — canlı çizimi buna bağlı (aynı sayfa). */
    showLiveThinking: Boolean = true,
    onShowLiveThinking: (Boolean) -> Unit = {},
    activeProfileName: String = "",
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
) {
    // Sheet burada açılıyor: taslak `draft` bu kompozablda, "satıra dokun →
    // taslağı doldur" akışı (onUsePrompt) ancak burada çalışabilir.
    var promptsSheet by remember { mutableStateOf(false) }
    var reasoningSheet by remember { mutableStateOf(false) }

    var draft by remember { mutableStateOf("") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(sharedText) {
        val incoming = sharedText ?: return@LaunchedEffect
        // Yazmakta olduğu bir şey varsa üstüne yazma, altına ekle.
        draft = if (draft.isBlank()) incoming else draft + "\n" + incoming
        onSharedTextConsumed()
    }
    val listState = rememberLazyListState()
    var followBottom by remember { mutableStateOf(true) }

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
            total <= 1 || lastVisible >= total - 2
        }.collect { near -> followBottom = near }
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
            activeProfileName,
        )

        Box(Modifier.weight(1f)) {
            if (state.items.isEmpty()) {
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is ChatRow.Single -> ChatItemView(row.item, onApproval, onOpenFile)
                            is ChatRow.Tools -> ToolActivityRow(
                                row.entries,
                                label = if (S.lang == Lang.TR) DETAIL_ROW_TR else DETAIL_ROW_EN,
                            )
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
                fontSize = 13.sp,
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
        ProfileChipsRow(
            profiles = profiles,
            selectedProfile = selectedProfile,
            currentProfile = currentProfile,
            onChipClick = onProfileChipClick,
        )

        // Yazma hızı: tek satır + akan shimmer. message.complete'te değerler
        // donar; satır 600 ms sönüşle kalkar. Akış AYNI sayfada toplanıyor ama
        // collect SpeedRow içinde: her pencere yalnız bu satırı yeniden derler.
        SpeedRow(speed)

        ChatComposer(
            draft = draft,
            // Yazmak her zaman açık; kopukken mesaj kuyruğa giriyor.
            enabled = true,
            online = state.connection is ConnectionState.Open,
            agentBusy = state.agentBusy,
            attachments = state.attachments,
            voiceMode = state.voiceMode,
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
    activeProfileName: String,
) {
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
        StatusDot(if (problem == null) HermesColors.Online else HermesColors.Danger)
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenProfiles)
        ) {
            // Tek satır KONU (P3 #8): chrome ince, model orada durmaz.
            Text(
                state.topic.takeIf { it.isNotBlank() } ?: S.chatTitle,
                color = HermesColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // İkinci satır yalnız gerçekten bir şey söylüyorsa.
            problem?.let {
                Text(it, color = HermesColors.Danger, fontSize = 11.sp, maxLines = 1)
            }
        }

        // Model seçimi burada yaşıyor ama ETİKETSİZ: ⋯ ikonu (iç terminoloji
        // üst şeride yazılmaz).
        IconButton(onClick = onOpenModelPicker) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = S.t2("Model ve profiller", "Model and profiles"),
                tint = HermesColors.TextMuted,
            )
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
            .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(tint, size = 7)
        Spacer(Modifier.width(8.dp))
        Text(text, color = tint, fontSize = 12.sp)
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
        Text(S.emptyTitle, color = HermesColors.TextSecondary, fontSize = 16.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            when (connection) {
                is ConnectionState.Open -> S.emptyHint
                is ConnectionState.Error -> connection.reason
                else -> S.t2("Gateway bağlantısı bekleniyor…", "Waiting for the gateway…")
            },
            color = HermesColors.TextMuted,
            fontSize = 12.sp,
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
                                fontSize = 12.sp,
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
                            fontSize = 12.sp,
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
                fontSize = 11.sp,
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
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .background(HermesColors.SurfaceDim, RoundedCornerShape(9.dp))
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
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .background(HermesColors.SurfaceDim, RoundedCornerShape(9.dp))
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

@Composable
private fun ChatItemView(
    item: ChatItem,
    onApproval: (String, Boolean) -> Unit,
    onOpenFile: (FileRef) -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    activeProfileName: String = "",
) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .widthIn(max = 300.dp)
                    .background(HermesColors.Surface, RoundedCornerShape(12.dp))
                    .border(1.dp, HermesColors.BorderStrong, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(item.text, color = HermesColors.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }

        is ChatItem.Assistant -> Column(Modifier.fillMaxWidth()) {
            MarkdownText(
                markdown = item.text + if (item.streaming) " ▌" else "",
                modifier = Modifier.fillMaxWidth(),
            )
            // Ajan dosya ürettiyse altına indirilebilir kart koy — masaüstünde
            // tıklanabilir olan bağlantının mobil karşılığı.
            if (!item.streaming) {
                FileRefRow(remember(item.text) { extractFileRefs(item.text) }, onOpenFile)
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
                    .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.t2("Düşünüyor", "Thinking"), color = HermesColors.TextFaint, fontSize = 11.sp)
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
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    // Katlanabilir tarih: mevcut davranış aynen.
                    AnimatedVisibility(expanded) {
                        Text(
                            item.text,
                            color = HermesColors.TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
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
            fontSize = 12.sp,
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
            .background(HermesColors.SurfaceDim, RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (item.answered == null) HermesColors.Busy else HermesColors.Border,
                RoundedCornerShape(10.dp),
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
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.text,
            style = MonoTextStyle,
            color = HermesColors.TextSecondary,
            lineHeight = 17.sp,
        )
        Spacer(Modifier.height(11.dp))

        if (item.answered != null) {
            Text(item.answered, color = HermesColors.TextMuted, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .weight(1f)
                        .background(HermesColors.Midground, RoundedCornerShape(8.dp))
                        .clickable { onApproval(item.key, true) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Onayla", color = HermesColors.Background, fontSize = 13.sp)
                }
                Row(
                    Modifier
                        .weight(1f)
                        .border(1.dp, HermesColors.Danger, RoundedCornerShape(8.dp))
                        .clickable { onApproval(item.key, false) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("Reddet", color = HermesColors.Danger, fontSize = 13.sp)
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

    AnimatedVisibility(
        visible = visible && line.isNotBlank(),
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(600)),
    ) {
        // Donmuş (finished) anda shimmer dursun: çizgi tek tona döner.
        val frozen = snap?.finished == true
        val base = HermesColors.BorderStrong
        val hot = HermesColors.Midground
        val transition = rememberInfiniteTransition(label = "speed-shimmer")
        val shift by transition.animateFloat(
            initialValue = 0f,
            targetValue = if (frozen) 0f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "speed-shimmer-shift",
        )
        Column {
            Text(
                line,
                color = HermesColors.TextFaint,
                fontSize = 11.sp,
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
                    .clip(RoundedCornerShape(1.dp))
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
                    Text(label, color = HermesColors.TextMuted, fontSize = 11.sp)
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
                    fontSize = 11.sp,
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
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                    )
                },
            )
        }
    }
}
