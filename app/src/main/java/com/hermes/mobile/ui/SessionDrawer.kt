package com.hermes.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.InterventionKind
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.ui.theme.HermesColors
import kotlinx.coroutines.delay

/**
 * Tur-16 — oturum çekmecesi (Claude / Grok / Gemini tarzı, tek ekran).
 *
 * Sohbet ekranı arkada sabit kalır; oturum listesi soldan kayan çekmecenin
 * içindedir. Satır seçilince çekmece kapanır ve sohbet YERİNDE değişir —
 * ayrı sayfa/rota yoktur (FR-001).
 *
 * Düzen (FR-002): sekmeler (Oturumlar · Canlı) → arama → "Yeni sohbet" →
 * zaman grupları (Sabitlenmiş / Bugün / Dün / Son 7 gün / Daha eski) →
 * altta aktif profil + Ayarlar girişi.
 *
 * Satır (FR-003): durum noktası (çalışan=yeşil, bekleyen=sarı, bitti=gri),
 * başlık (ham id ASLA — [displayLabel] filtresi), tek satır son mesaj
 * önizlemesi, göreli saat. Uzun basma alt sayfa (FR-004), sağa kaydırma
 * arşivler ("Geri al" satırı ile), aktif oturum vurguludur (FR-006).
 *
 * Canlı sekmesi (FR-007): Canlı akışın çekmece içi hâli — bağlan / müdahale
 * / dur; ayrı ekran yok.
 *
 * Tur-19 (FR-001): sekme "Tümü" — Telegram tarzı genel akış (tüm oturumların
 * SON mesajları, grup başlıksız, tam kronolojik). Tur-16 gruplu görünümü tek
 * dokunuşla geri gelir ("Gruplar" düğmesi); arama/uzun basma/kaydır-arşivle
 * her iki görünümde de aynıdır (FR-004 regresyon yok).
 * Tur-19 (FR-002): uzun basma alt sayfasına "Yanıtla" — canlı ve müdahaleye
 * açık oturumlarda satırı terk etmeden mini composer ile `session.steer`.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun SessionDrawerContent(
    rows: List<DrawerRow>,
    items: List<DrawerItem>,
    query: String,
    onQuery: (String) -> Unit,
    tab: Int,
    onTab: (Int) -> Unit,
    liveSessions: List<LiveSession>,
    liveTitleOf: (LiveSession) -> String,
    currentSessionId: String?,
    liveIntervening: LiveSession?,
    liveSending: Boolean,
    activeProfileName: String,
    onNewChat: () -> Unit,
    onPick: (DrawerRow) -> Unit,
    onTogglePin: (DrawerRow) -> Unit,
    onRename: (DrawerRow, String) -> Unit,
    onSetArchived: (DrawerRow, Boolean) -> Unit,
    onUndoArchive: (DrawerRow) -> Unit,
    onDelete: (DrawerRow) -> Unit,
    onConnectLive: (LiveSession) -> Unit,
    /** Döküm (SessionDetailScreen) — satır uzun basma menüsünden; tur-14
     *  'Döküm' erişimi korunur (FR-009 regresyon yok). */
    onOpenTranscript: (DrawerRow) -> Unit,
    onInterruptLive: (LiveSession) -> Unit,
    onOpenIntervention: (LiveSession) -> Unit,
    onCloseIntervention: () -> Unit,
    onSubmitIntervention: (LiveSession, InterventionKind, String) -> Unit,
    /** Tur-19 FR-002: hızlı yanıt — mevcut `session.steer` sözleşmesini
     *  kullanır (yeni gateway API'si YOK). Gönderim `quickLive` üzerinden
     *  izlenir: `sending` true→false düşüp notice başarılıysa sheet kapanır,
     *  hata ise notice olarak görünür (çekmece/akış YERİNDE kalır). */
    onQuickReply: (LiveSession, String) -> Unit,
    /** Mini composer'ı açar (MainActivity hızlı-yanıt hedefini set eder). */
    onOpenQuickReply: (LiveSession) -> Unit,
    /** Hızlı yanıt hedefi (MainActivity, liveViewModel.openIntervention ile
     *  aynı kanaldan set eder; null = sheet kapalı). */
    quickLive: LiveSession?,
    onClearQuickReply: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissDrawer: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    // Tur-19 FR-001: Tümü sekmesi görünümü — varsayılan akış (kronolojik,
    // başlıksız); "Gruplar" ile tur-16 zaman grupları geri gelir.
    var groupedFeed by rememberSaveable { mutableStateOf(false) }

    // Uzun basma alt sayfası / yeniden adlandır / sil — hedef satır, LazyColumn
    // kompozisyonundan bağımsız tutulur (satır yeniden kullanınca state kalmasın).
    var menuRow by remember { mutableStateOf<DrawerRow?>(null) }
    var renameTarget by remember { mutableStateOf<DrawerRow?>(null) }
    var deleteTarget by remember { mutableStateOf<DrawerRow?>(null) }

    // Geri-al satırı (kaydırarak arşiv, FR-004). Global SnackbarHost çekmeceyi
    // gölgelediğinden kendi inline satırı — 5 sn sonra kendiliğinden kalkar.
    var undoRow by remember { mutableStateOf<DrawerRow?>(null) }
    LaunchedEffect(undoRow?.key) {
        if (undoRow != null) {
            delay(5_000)
            undoRow = null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(HermesColors.Background)
            .padding(top = 14.dp),
    ) {
        // Sekmeler (FR-002): Oturumlar · Canlı
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawerTab(
                // Tur-19 FR-001: "Oturumlar" → "Tümü" — artık genel akış.
                S.t2("Tümü", "All"),
                selected = tab == 0,
                modifier = Modifier.weight(1f),
            ) { onTab(0) }
            DrawerTab(
                if (liveSessions.isNotEmpty())
                    S.t2("Canlı (${liveSessions.size})", "Live (${liveSessions.size})")
                else S.t2("Canlı", "Live"),
                selected = tab == 1,
                modifier = Modifier.weight(1f),
            ) { onTab(1) }
        }

        Spacer(Modifier.height(10.dp))

        DrawerSearchField(
            query = query,
            onChange = onQuery,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(Modifier.height(6.dp))

        // "Yeni sohbet" satırı — her iki sekmede üstte (FR-002).
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    onNewChat()
                    onDismissDrawer()
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = HermesColors.Midground,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                S.t2("Yeni sohbet", "New chat"),
                color = HermesColors.Midground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        if (undoRow != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(HermesColors.SurfaceDim, RoundedCornerShape(10.dp))
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    S.t2("Arşivlendi", "Archived"),
                    color = HermesColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        onUndoArchive(undoRow!!)
                        undoRow = null
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.Undo, null, tint = HermesColors.Midground, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(S.t2("Geri al", "Undo"), color = HermesColors.Midground, fontSize = 12.sp)
                }
            }
        }

        if (tab == 0) {
            // Tur-19 FR-001: "Tümü" = genel akış (varsayılan, grup başlıksız,
            // tam kronolojik). "Gruplar" = tur-16 zaman gruplu görünümü — tek
            // dokunuşla geri gelir; arama her ikisinde de geçerli (FR-004).
            if (!groupedFeed) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        S.t2("Genel akış", "All activity"),
                        color = HermesColors.TextFaint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { groupedFeed = true },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(S.t2("Gruplar", "Groups"), color = HermesColors.Midground, fontSize = 12.sp)
                    }
                }
            }
            val feedRows = if (groupedFeed) null else drawerFeed(rows, query)
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (feedRows != null) {
                    lazyItems(feedRows, key = { "feed-${it.key}" }) { row ->
                        DrawerSessionRow(
                            row = row,
                            onPick = {
                                onPick(row)
                                onDismissDrawer()
                            },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuRow = row
                            },
                            onArchiveSwipe = {
                                onSetArchived(row, true)
                                undoRow = row
                            },
                        )
                    }
                    val empty = feedRows.isEmpty()
                    if (empty && query.isNotBlank()) {
                        item(key = "feed-no-result") {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    S.t2("\"$query\" için sonuç yok", "No results for \"$query\""),
                                    color = HermesColors.TextSecondary,
                                    fontSize = 13.sp,
                                )
                                TextButton(
                                    onClick = { onQuery("") },
                                    contentPadding = PaddingValues(start = 0.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
                                ) {
                                    Text(
                                        S.t2("Aramayı temizle", "Clear search"),
                                        color = HermesColors.Midground,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    } else if (empty) {
                        item(key = "feed-empty") {
                            Text(
                                S.t2(
                                    "Henüz oturum yok.\nYeni sohbet ile başla.",
                                    "No sessions yet.\nStart a new chat.",
                                ),
                                color = HermesColors.TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items.forEach { entry ->
                        when (entry) {
                            is DrawerItem.Header -> item(key = entry.key) {
                                Text(
                                    S.t2(entry.labelTr, entry.labelEn),
                                    color = HermesColors.TextFaint,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 3.dp),
                                )
                            }
                            is DrawerItem.RowItem -> item(key = entry.key) {
                                DrawerSessionRow(
                                    row = entry.row,
                                    onPick = {
                                        onPick(entry.row)
                                        onDismissDrawer()
                                    },
                                    onLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuRow = entry.row
                                    },
                                    onArchiveSwipe = {
                                        onSetArchived(entry.row, true)
                                        undoRow = entry.row
                                    },
                                )
                            }
                        }
                    }
                    // Boş durum + boş arama sonucu (FR-005 boş-durum metni).
                    if (rows.isEmpty() && query.isBlank()) {
                        item(key = "empty") {
                            Text(
                                S.t2(
                                    "Henüz oturum yok.\nYeni sohbet ile başla.",
                                    "No sessions yet.\nStart a new chat.",
                                ),
                                color = HermesColors.TextMuted,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    if (query.isNotBlank() && items.none { it is DrawerItem.RowItem }) {
                        item(key = "no-result") {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    S.t2("\"$query\" için oturum yok", "No sessions for \"$query\""),
                                    color = HermesColors.TextSecondary,
                                    fontSize = 13.sp,
                                )
                                TextButton(
                                    onClick = { onQuery("") },
                                    contentPadding = PaddingValues(start = 0.dp, top = 4.dp, end = 8.dp, bottom = 4.dp),
                                ) {
                                    Text(
                                        S.t2("Aramayı temizle", "Clear search"),
                                        color = HermesColors.Midground,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "bottom-spacer") { Spacer(Modifier.height(12.dp)) }
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                lazyItems(liveSessions, key = { "lv-${it.id}" }) { session ->
                    DrawerLiveRow(
                        session = session,
                        title = displayLabel(liveTitleOf(session)),
                        current = currentSessionId != null &&
                            (currentSessionId == session.id || currentSessionId == session.dbId),
                        onConnect = {
                            onConnectLive(session)
                            onDismissDrawer()
                        },
                        onInterrupt = { onInterruptLive(session) },
                        onIntervene = { onOpenIntervention(session) },
                    )
                }
                if (liveSessions.isEmpty()) {
                    item(key = "live-empty") {
                        Text(
                            S.t2(
                                "Şu an canlı oturum yok. Telegram, cron veya CLI'dan bir ajan " +
                                    "çalışmaya başlayınca burada görünür.",
                                "No live sessions right now. Agents started from Telegram, " +
                                    "cron or the CLI show up here.",
                            ),
                            color = HermesColors.TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                item(key = "bottom-spacer") { Spacer(Modifier.height(12.dp)) }
            }
        }

        // Alt şerit: aktif profil + Ayarlar (FR-002).
        Row(
            Modifier
                .fillMaxWidth()
                .border(0.5.dp, HermesColors.Border)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = HermesColors.TextFaint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                activeProfileName.ifBlank { "—" },
                color = HermesColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    onOpenSettings()
                    onDismissDrawer()
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(Icons.Default.Settings, null, tint = HermesColors.TextMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(S.t2("Ayarlar", "Settings"), color = HermesColors.TextSecondary, fontSize = 12.sp)
            }
        }
    }

    // Uzun basma alt sayfası (FR-004) — tur-19 FR-002: "Yanıtla" yalnız
    // gateway'de canlı ve müdahaleye açık satırlarda görünür (spec: yeni
    // gateway API'si yok — mevcut session.steer yeniden kullanılıyor).
    menuRow?.let { row ->
        DrawerActionSheet(
            row = row,
            onDismiss = { menuRow = null },
            canQuickReply = canQuickReply(row),
            onQuickReply = {
                menuRow = null
                row.liveSession?.let(onOpenQuickReply)
            },
            onGoChat = {
                menuRow = null
                onPick(row)
                onDismissDrawer()
            },
            onTranscript = {
                menuRow = null
                onOpenTranscript(row)
                onDismissDrawer()
            },
            onTogglePin = {
                menuRow = null
                onTogglePin(row)
            },
            onRename = {
                menuRow = null
                renameTarget = row
            },
            onArchive = {
                menuRow = null
                onSetArchived(row, !row.archived)
            },
            onDelete = {
                menuRow = null
                deleteTarget = row
            },
        )
    }

    // Tur-19 FR-002: mini composer — gönderince çekmece/akış YERİNDE kalır.
    // Sonuç, live.sending true→false düşünce anlaşılır (MainActivity notice
    // gözlemcisi toast basar); sheet o an kapanır — hata noticesı da görünür.
    quickLive?.let { live ->
        var wasSending by remember(live.id) { mutableStateOf(liveSending) }
        LaunchedEffect(liveSending) {
            if (wasSending && !liveSending) {
                onClearQuickReply()
            }
            wasSending = liveSending
        }
        QuickReplySheet(
            title = displayLabel(liveTitleOf(live)),
            sending = liveSending,
            onDismiss = { if (!liveSending) onClearQuickReply() },
            onSend = { text -> onQuickReply(live, text) },
        )
    }

    renameTarget?.let { target ->
        var text by remember(target) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = HermesColors.Surface,
            title = {
                Text(S.t2("Yeniden adlandır", "Rename"), color = HermesColors.TextPrimary, fontSize = 14.sp)
            },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HermesColors.TextPrimary,
                        unfocusedTextColor = HermesColors.TextPrimary,
                        focusedBorderColor = HermesColors.BorderStrong,
                        unfocusedBorderColor = HermesColors.Border,
                        cursorColor = HermesColors.Midground,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(target, text)
                        renameTarget = null
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Text(S.t2("Kaydet", "Save"), color = HermesColors.Midground, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(S.t2("İptal", "Cancel"), color = HermesColors.TextMuted, fontSize = 13.sp)
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = HermesColors.Surface,
            title = {
                Text(target.title, color = HermesColors.TextPrimary, fontSize = 14.sp, maxLines = 2)
            },
            text = {
                Text(
                    S.t2("Bu oturum listeden kaldırılacak.", "This session will be removed from the list."),
                    color = HermesColors.TextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target)
                        deleteTarget = null
                    },
                ) {
                    Text(S.t2("Kaldır", "Remove"), color = HermesColors.Danger, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(S.t2("İptal", "Cancel"), color = HermesColors.TextMuted, fontSize = 13.sp)
                }
            },
        )
    }

    // Canlı sekmesi müdahale diyaloğu (Eski Canlı ekranıyla aynı dialog).
    liveIntervening?.let { target ->
        InterventionDialog(
            session = target,
            title = displayLabel(liveTitleOf(target)),
            sending = liveSending,
            onDismiss = onCloseIntervention,
            onSubmit = { kind, text -> onSubmitIntervention(target, kind, text) },
        )
    }
}

/** Sekme düğmesi — tur-14 WorkScreen `Tab` kalıbının çekmece ölçeği. */
@Composable
private fun DrawerTab(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .background(
                if (selected) HermesColors.Midground else HermesColors.SurfaceDim,
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = if (selected) HermesColors.Background else HermesColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** Arama alanı — Composer/SessionsScreen kalıbı (M3 SearchBar overlay'i Yerleşimi kırar). */
@Composable
private fun DrawerSearchField(
    query: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(S.t2("Oturum ara…", "Search sessions…"), color = HermesColors.TextFaint, fontSize = 13.sp)
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = HermesColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(
                    onClick = { onChange("") },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = S.t2("Temizle", "Clear"),
                        tint = HermesColors.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = HermesColors.TextPrimary,
            unfocusedTextColor = HermesColors.TextPrimary,
            focusedContainerColor = HermesColors.Surface,
            unfocusedContainerColor = HermesColors.Surface,
            focusedBorderColor = HermesColors.BorderStrong,
            unfocusedBorderColor = HermesColors.Border,
            cursorColor = HermesColors.Midground,
        ),
    )
}

/**
 * Çekmece oturum satırı (FR-003/FR-004): SwipeToDismissBox ile sağa kaydır =
 * arşivle; dokun = seç + çekmeceyi kapat; uzun bas = alt sayfa. Aktif oturum
 * SurfaceDim dolgusuyla vurgulanır.
 *
 * `row.archived` true olduğunda (kaydırma KENDİ arşiv kararı değil, bayrak
 * sonucu çizer) satır soldan-kaymış durur; "Geri al" bayrağı kaldırınca
 * LaunchedEffect konumu Settled'a geri çeker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DrawerSessionRow(
    row: DrawerRow,
    onPick: () -> Unit,
    onLongPress: () -> Unit,
    onArchiveSwipe: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        initialValue = if (row.archived) {
            SwipeToDismissBoxValue.StartToEnd
        } else {
            SwipeToDismissBoxValue.Settled
        },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onArchiveSwipe()
                true
            } else {
                // sola kaydırma bilinçli kapalı (kaza riski; silme alt sayfada).
                false
            }
        },
    )
    // "Geri al": bayrak kalktı → görsel konumu yerine oturt.
    LaunchedEffect(row.archived) {
        if (!row.archived && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(HermesColors.Danger.copy(alpha = 0.12f))
                    .padding(end = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = S.t2("Arşivlendi", "Archived"),
                    tint = HermesColors.Danger,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .background(
                    when {
                        row.current -> HermesColors.SurfaceDim
                        else -> HermesColors.Background
                    },
                )
                .combinedClickable(
                    onClick = onPick,
                    onLongClick = onLongPress,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Durum noktası (FR-003): boş = çizilmez (yer tutucu boşluk kalır).
            when (row.dot) {
                "green" -> DrawerDot(HermesColors.Online)
                "yellow" -> DrawerDot(HermesColors.Busy)
                "grey" -> DrawerDot(HermesColors.Offline)
                else -> Box(Modifier.size(8.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.pinned) {
                        Icon(
                            Icons.Default.PushPin,
                            contentDescription = S.t2("Sabitlenmiş", "Pinned"),
                            tint = HermesColors.Midground,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        row.title,
                        color = if (row.current) HermesColors.Midground else HermesColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (row.current) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatRelative(row.epochSeconds),
                        color = HermesColors.TextFaint,
                        fontSize = 10.sp,
                    )
                }
                if (row.preview.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        row.preview,
                        color = HermesColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerDot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}

/**
 * Canlı sekmesi satırı — dokun = bağlan + çekmeceyi kapat; çalışansa
 * "Müdahale" / "Dur". Davranış tur-14 Canlı sekmesiyle aynı, çekmece ölçeği.
 */
@Composable
private fun DrawerLiveRow(
    session: LiveSession,
    title: String,
    current: Boolean,
    onConnect: () -> Unit,
    onInterrupt: () -> Unit,
    onIntervene: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (current) HermesColors.SurfaceDim else HermesColors.Background)
            .clickable(onClick = onConnect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            session.isWorking -> DrawerDot(HermesColors.Online)
            session.isWaiting || session.isStarting -> DrawerDot(HermesColors.Busy)
            else -> DrawerDot(HermesColors.Offline)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (current) HermesColors.Midground else HermesColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = meaningfulPreview(session.preview, 60)
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    preview,
                    color = HermesColors.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(formatRelative(session.lastActive), color = HermesColors.TextFaint, fontSize = 10.sp)
        if (session.canIntervene) {
            TextButton(
                onClick = onIntervene,
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(S.t2("Müdahale", "Steer"), color = HermesColors.Midground, fontSize = 11.sp)
            }
            TextButton(
                onClick = onInterrupt,
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(S.t2("Dur", "Stop"), color = HermesColors.Danger, fontSize = 11.sp)
            }
        }
    }
}

/** Uzun basma alt sayfası (FR-004): Yanıtla / Döküm / Sabitle / Yeniden
 *  adlandır / Arşivle / Sil. Tur-19 FR-002: "Yanıtla" yalnız canlı+müdaleleye
 *  açık satırda (canQuickReply), "Sohbete git" her satırda, "Kopyala" önizleme
 *  varsa — satır-içi mini composer ve yerinde kalma akışı QuickReplySheet'te. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DrawerActionSheet(
    row: DrawerRow,
    onDismiss: () -> Unit,
    canQuickReply: Boolean,
    onQuickReply: () -> Unit,
    /** "Sohbete git" — satırı seç + çekmeceyi kapat (tur-16 dokun davranışı). */
    onGoChat: () -> Unit,
    onTranscript: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Background,
        modifier = Modifier.heightIn(max = 440.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                row.title,
                color = HermesColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            if (canQuickReply) {
                DrawerSheetRow(
                    icon = Icons.AutoMirrored.Filled.Reply,
                    label = S.t2("Yanıtla", "Reply"),
                    onClick = onQuickReply,
                )
            }
            DrawerSheetRow(
                icon = Icons.AutoMirrored.Filled.Chat,
                label = S.t2("Sohbete git", "Go to chat"),
                onClick = onGoChat,
            )
            DrawerSheetRow(
                icon = Icons.AutoMirrored.Filled.Article,
                label = S.t2("Döküm", "Transcript"),
                onClick = onTranscript,
            )
            if (row.preview.isNotBlank()) {
                DrawerSheetRow(
                    icon = Icons.Default.ContentCopy,
                    label = S.t2("Kopyala", "Copy"),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(row.preview))
                        onDismiss()
                    },
                )
            }
            DrawerSheetRow(
                icon = Icons.Default.PushPin,
                label = if (row.pinned) S.t2("Sabiti kaldır", "Unpin") else S.t2("Sabitle", "Pin"),
                onClick = onTogglePin,
            )
            DrawerSheetRow(
                icon = Icons.Default.Edit,
                label = S.t2("Yeniden adlandır", "Rename"),
                onClick = onRename,
            )
            DrawerSheetRow(
                icon = Icons.Default.Archive,
                label = if (row.archived) S.t2("Arşivden çıkar", "Unarchive") else S.t2("Arşivle", "Archive"),
                onClick = onArchive,
            )
            DrawerSheetRow(
                icon = Icons.Default.Delete,
                label = S.t2("Sil", "Delete"),
                danger = true,
                onClick = onDelete,
            )
            DrawerSheetRow(
                icon = Icons.Default.Close,
                label = S.t2("İptal", "Cancel"),
                onClick = onDismiss,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * Tur-19 FR-002 — satır-içi mini composer (1-3 satır + Gönder). Gönderim
 * mevcut `session.steer` yolundan akar (InterventionDialog'la aynı
 * sözleşme); başarıda sheet kapanır, çekmece/akış YERİNDE kalır, toast'ı
 * MainActivity basar. Gönder düğümü durum makinesiyle kilitli:
 * [quickReplySendEnabled] — boş metin ve sürmekte gönderim ENGEL.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun QuickReplySheet(
    title: String,
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf(QuickReplyPhase.Idle) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Background,
        modifier = Modifier.heightIn(max = 320.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                title,
                color = HermesColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    phase = quickReplyOnType(phase)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        S.t2("Yanıtını yaz…", "Type your reply…"),
                        color = HermesColors.TextFaint,
                        fontSize = 13.sp,
                    )
                },
                minLines = 1,
                maxLines = 3,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HermesColors.TextPrimary,
                    unfocusedTextColor = HermesColors.TextPrimary,
                    focusedContainerColor = HermesColors.Surface,
                    unfocusedContainerColor = HermesColors.Surface,
                    focusedBorderColor = HermesColors.BorderStrong,
                    unfocusedBorderColor = HermesColors.Border,
                    cursorColor = HermesColors.Midground,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !sending,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(S.t2("Vazgeç", "Cancel"), color = HermesColors.TextMuted, fontSize = 13.sp)
                }
                TextButton(
                    onClick = {
                        if (quickReplySendEnabled(phase, text)) {
                            phase = QuickReplyPhase.Sending
                            onSend(text.trim())
                        }
                    },
                    enabled = quickReplySendEnabled(phase, text),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        if (sending) S.t2("Gönderiliyor…", "Sending…") else S.t2("Gönder", "Send"),
                        color = HermesColors.Midground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val tint = if (danger) HermesColors.Danger else HermesColors.TextPrimary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = tint, fontSize = 14.sp)
    }
}
