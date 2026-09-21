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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
    onOpenSettings: () -> Unit,
    onDismissDrawer: () -> Unit,
    /** Tur22 madde-3: oturum listesi ilk açılışta/ilk yenilemede iskelet. */
    loading: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current

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
        // Sekmeler (FR-002): Oturumlar · Canlı · Arşiv (tur22 madde-4: 3.
        // sekme — boş arşiv boş-durumu artık erişilebilir; tab 2 = arşiv).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawerTab(
                S.t2("Oturumlar", "Sessions"),
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
            DrawerTab(
                S.t2("Arşiv", "Archive"),
                selected = tab == 2,
                modifier = Modifier.weight(1f),
            ) { onTab(2) }
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
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }

        if (undoRow != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    S.t2("Arşivlendi", "Archived"),
                    color = HermesColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
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
                    Text(S.t2("Geri al", "Undo"), color = HermesColors.Midground, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // tab 0 = oturumlar, tab 2 = arşiv (aynı liste, farklı showArchived —
        // satırları MainActivity türetiyor); tab 1 = canlı.
        if (tab != 1) {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                items.forEach { entry ->
                    when (entry) {
                        is DrawerItem.Header -> item(key = entry.key) {
                            Text(
                                S.t2(entry.labelTr, entry.labelEn),
                                color = HermesColors.TextFaint,
                                style = MaterialTheme.typography.labelSmall,
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
                                    if (tab == 2) {
                                        // Arşiv görünümü: kaydır = arşivden çıkar.
                                        onSetArchived(entry.row, false)
                                    } else {
                                        onSetArchived(entry.row, true)
                                        undoRow = entry.row
                                    }
                                },
                                archivedMode = tab == 2,
                            )
                        }
                    }
                }
                // Boş durum + boş arama sonucu (FR-005 boş-durum metni).
                // Tur22 madde-3: yükleniyor VE liste boşsa "yok" YAZMA —
                // iskelet göster (skeletonRowCount kuralı: yok/henüz-yok ayrımı).
                if (skeletonRowCount(loading, rows.size, placeholder = 4) > 0 && query.isBlank()) {
                    item(key = "skeleton") {
                        SessionListSkeleton(
                            skeletonRowCount(loading, rows.size, placeholder = 4),
                            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                // Tur22 madde-4: boş durumlar TEK BİLEŞENDEN — ikon + tek
                // cümle + öneri aksiyonu (uydurma veri yok).
                if (rows.isEmpty() && query.isBlank() && !loading) {
                    item(key = "empty") {
                        if (tab == 2) {
                            EmptyState(
                                icon = EmptyStateIcons.ArchiveEmpty,
                                message = emptyStateArchiveEmpty(),
                                modifier = Modifier.padding(20.dp),
                            )
                        } else {
                            EmptyState(
                                icon = EmptyStateIcons.NoSessions,
                                message = emptyStateNoSessions(),
                                modifier = Modifier.padding(20.dp),
                                actionLabel = S.t2("Yeni sohbet", "New chat"),
                                onAction = onNewChat,
                            )
                        }
                    }
                }
                if (query.isNotBlank() && items.none { it is DrawerItem.RowItem }) {
                    item(key = "no-result") {
                        EmptyState(
                            icon = EmptyStateIcons.NoResults,
                            message = emptyStateNoResults(query),
                            modifier = Modifier.padding(20.dp),
                            actionLabel = S.t2("Aramayı temizle", "Clear search"),
                            onAction = { onQuery("") },
                        )
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
                            style = MaterialTheme.typography.bodySmall,
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
                style = MaterialTheme.typography.bodySmall,
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
                Text(S.t2("Ayarlar", "Settings"), color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    // Uzun basma alt sayfası (FR-004).
    menuRow?.let { row ->
        DrawerActionSheet(
            row = row,
            onDismiss = { menuRow = null },
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

    renameTarget?.let { target ->
        var text by remember(target) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = HermesColors.Surface,
            title = {
                Text(S.t2("Yeniden adlandır", "Rename"), color = HermesColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
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
                    Text(S.t2("Kaydet", "Save"), color = HermesColors.Midground, style = MaterialTheme.typography.bodyMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(S.t2("İptal", "Cancel"), color = HermesColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = HermesColors.Surface,
            title = {
                Text(target.title, color = HermesColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            },
            text = {
                Text(
                    S.t2("Bu oturum listeden kaldırılacak.", "This session will be removed from the list."),
                    color = HermesColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target)
                        deleteTarget = null
                    },
                ) {
                    Text(S.t2("Kaldır", "Remove"), color = HermesColors.Danger, style = MaterialTheme.typography.bodyMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(S.t2("İptal", "Cancel"), color = HermesColors.TextMuted, style = MaterialTheme.typography.bodyMedium)
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
                MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = if (selected) HermesColors.Background else HermesColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
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
            Text(S.t2("Oturum ara…", "Search sessions…"), color = HermesColors.TextFaint, style = MaterialTheme.typography.bodyMedium)
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
        shape = MaterialTheme.shapes.medium,
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
    /** Tur22 madde-4: true = arşiv görünümü — satır Settled başlar (bayraklı
     * olmak burada normal); swipe ARŞİVDEN ÇIKARIR (çağıran tersini uygular). */
    archivedMode: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        initialValue = if (row.archived && !archivedMode) {
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

    // Tur22 madde-2: uzun basma/bası basılıyken hafif ölçek (0.985) — haptic
    // zaten confirm/onLongClick'te var; görsel eşlikçisi burada (reduced→1f).
    val interaction = androidx.compose.runtime.remember(row.key) {
        androidx.compose.foundation.interaction.MutableInteractionSource()
    }
    val pressedState by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val rowScale by animateFloatAsState(
        targetValue = if (pressedState && !reduced) 0.985f else 1f,
        animationSpec = if (reduced) tween(0) else tween(HermesMotion.FAST_MS),
        label = "cekmece-satir-olcek",
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            // Tur22 madde-2: arkadan görünen renk+ikon+etiket — sürüklerken
            // "ne olacak" net okunsun (Claude/ChatGPT arşiv jesti).
            // archivedMode'da jest TERS çalışır: kaydır = arşivden çıkar.
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        if (archivedMode) HermesColors.Online.copy(alpha = 0.16f)
                        else HermesColors.Danger.copy(alpha = 0.16f),
                    )
                    .padding(end = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (archivedMode) S.t2("Arşivden çıkar", "Unarchive")
                    else S.t2("Arşivle", "Archive"),
                    color = if (archivedMode) HermesColors.Online else HermesColors.Danger,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (archivedMode) Icons.Default.Undo else Icons.Default.Archive,
                    contentDescription = S.t2("Arşivlendi", "Archived"),
                    tint = if (archivedMode) HermesColors.Online else HermesColors.Danger,
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
                    interactionSource = interaction,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onPick,
                    onLongClick = onLongPress,
                )
                .graphicsLayer { scaleX = rowScale; scaleY = rowScale }
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
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (row.current) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    // Tur-21: JEV rozeti — gateway alanı gelmiyorsa HİÇ çizilmez
                    // (yer tutucu yok, uydurma yok). Renkler paletten:
                    // Online/Yedek2/BadgeWarn tur-17 palet eşlemesiyle sabit.
                    when (val badge = com.hermes.mobile.data.JevBadgeLogic.parse(row.jev)) {
                        com.hermes.mobile.data.JevBadgeLogic.Badge.None -> Unit
                        else -> {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(
                                        when (badge) {
                                            com.hermes.mobile.data.JevBadgeLogic.Badge.Green -> HermesColors.Online
                                            com.hermes.mobile.data.JevBadgeLogic.Badge.Yellow -> HermesColors.Busy
                                            else -> HermesColors.Danger
                                        },
                                        CircleShape,
                                    )
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                    }
                    Text(
                        formatRelative(row.epochSeconds),
                        color = HermesColors.TextFaint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (row.preview.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        row.preview,
                        color = HermesColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
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
            .clip(CircleShape)
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
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = meaningfulPreview(session.preview, 60)
            if (preview.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    preview,
                    color = HermesColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(formatRelative(session.lastActive), color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
        if (session.canIntervene) {
            TextButton(
                onClick = onIntervene,
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(S.t2("Müdahale", "Steer"), color = HermesColors.Midground, style = MaterialTheme.typography.labelSmall)
            }
            TextButton(
                onClick = onInterrupt,
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text(S.t2("Dur", "Stop"), color = HermesColors.Danger, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Uzun basma alt sayfası (FR-004): Sabitle / Yeniden adlandır / Arşivle / Sil. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DrawerActionSheet(
    row: DrawerRow,
    onDismiss: () -> Unit,
    onTranscript: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            DrawerSheetRow(
                icon = Icons.AutoMirrored.Filled.Article,
                label = S.t2("Döküm", "Transcript"),
                onClick = onTranscript,
            )
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
        Text(label, color = tint, style = MaterialTheme.typography.bodyMedium)
    }
}
