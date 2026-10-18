package com.hermes.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hermes.mobile.InterventionKind
import com.hermes.mobile.data.LiveSession
import com.hermes.mobile.data.SessionMergeState
import com.hermes.mobile.data.czipMergeOnerisi
import com.hermes.mobile.data.kaynakOzeti
import com.hermes.mobile.data.mergeSecilebilirMi
import com.hermes.mobile.ui.theme.HermesColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
 * önizlemesi, göreli saat. Uzun basma alt sayfa (FR-004), K21: sola tam
 * çekim arşivler, sağa çekim iki aşamalı siler ("Geri al" satırıyla),
 * aktif oturum vurguludur (FR-006).
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
    /** K21: sağa çek ikinci aşama — yerel gizleme (sunucuya gitmez). */
    onDeleteSwipe: (DrawerRow) -> Unit = {},
    /** K21: silme geri al — hidden kümesinden çıkarır (yerel). */
    onUndoDelete: (DrawerRow) -> Unit = {},
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
    /** Tur22 madde-3: oturum listesi ilk açılışta/ilk yenilemede iskelet. */
    loading: Boolean = false,
    /** K13c: birleştirme durumu — birleştirme sunucuda; buraya yalnız üst veri gelir. */
    merge: SessionMergeState = SessionMergeState(),
    onMergeConfirm: (List<String>, String?) -> Unit = { _, _ -> },
    onMergeNotifyConsumed: () -> Unit = {},
    /** K29-b: oturumu yeniye taşı (tek-kaynak birleştirme + kaynak arşiv). */
    onMoveToNew: (DrawerRow) -> Unit = {},
    onToggleFollow: (DrawerRow) -> Unit = {},
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
    // K21: geri-al türü — arşiv mi yerel gizleme mi (satır metni + geri-al yolu).
    var undoKind by remember { mutableStateOf(UndoKind.Archive) }
    LaunchedEffect(undoRow?.key) {
        if (undoRow != null) {
            delay(5_000)
            undoRow = null
        }
    }
    // K21: takılı sil satırı — "listede başka yere dokunulursa sıfırla"
    // için satır DIŞINDA tutulur (tek kaynak: key; null = takılı yok).
    var deleteArmedKey by remember { mutableStateOf<String?>(null) }

    // K13c: çoklu seçim — uzun basma menüsündeki "Birleştir" ile açılır.
    // Seçim dbId (sunucu oturum kimliği) üzerinden: birleştirme sunucuya gider.
    var secimModu by remember { mutableStateOf(false) }
    var secili by remember { mutableStateOf(setOf<String>()) }
    var mergeOnay by remember { mutableStateOf(false) }
    var mergeBaslik by remember { mutableStateOf("") }
    val secimToggle: (DrawerRow) -> Unit = { row ->
        if (row.dbId.isNotBlank()) {
            secili = if (row.dbId in secili) secili - row.dbId else secili + row.dbId
        }
    }

    // K29-a: birleştirme ÖNERİSİ — aynı czip paketini kullanan ≥2 oturum.
    val mergeOnerisi = czipMergeOnerisi(rows.map { it.dbId to it.preview })

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

        // K13c: seçim modu çubuğu — "N oturum seçildi · Birleştir · İptal".
        if (secimModu) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    S.t2(
                        secili.size.toString() + " oturum seçildi",
                        secili.size.toString() + " selected",
                    ),
                    color = HermesColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { mergeOnay = true },
                    enabled = mergeSecilebilirMi(secili) && !merge.calisiyor,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(
                        S.t2("Birleştir", "Merge"),
                        color = HermesColors.Midground,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                TextButton(
                    onClick = {
                        secimModu = false
                        secili = emptySet()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(S.t2("İptal", "Cancel"), color = HermesColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                }
            }
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
                    if (undoKind == UndoKind.Archive) S.t2("Arşivlendi", "Archived")
                    else S.t2("Silindi", "Deleted"),
                    color = HermesColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val target = undoRow
                        undoRow = null
                        if (target != null) {
                            if (undoKind == UndoKind.Archive) onUndoArchive(target) else onUndoDelete(target)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.Undo, null, tint = HermesColors.Midground, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(S.t2("Geri al", "Undo"), color = HermesColors.Midground, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // K13c: birleştirme durumu satırı — ilerleme/sonuç/hata (içerik inmez).
        when {
            merge.calisiyor -> MergeDurumSatiri(S.t2("Birleştiriliyor…", "Merging…"), null)
            merge.sonuc != null -> {
                val sonuc = merge.sonuc
                MergeDurumSatiri(
                    S.t2(
                        "Birleştirildi: " + sonuc.title + " (" + kaynakOzeti(sonuc) + ")",
                        "Merged: " + sonuc.title + " (" + kaynakOzeti(sonuc) + ")",
                    ),
                ) { onMergeNotifyConsumed() }
                LaunchedEffect(sonuc.sessionId) {
                    delay(6_000)
                    onMergeNotifyConsumed()
                }
            }
            merge.hata != null -> MergeDurumSatiri(merge.hata) { onMergeNotifyConsumed() }
        }

        // K29-a: birleştirme ÖNERİSİ kartı — aynı czip paketini kullanan
        // oturumlar için "birleştirelim mi?"; tek dokunuş seçim moduna alır.
        // Seçim modunda/katalog verisi olmadan GÖSTERİLMEZ (karışıklık olmasın).
        if (!secimModu && mergeOnerisi != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        S.t2(
                            "Bu paketi " + mergeOnerisi.dbIds.size + " oturumda kullandınız — birleştirelim mi?",
                            "You used this package in " + mergeOnerisi.dbIds.size + " sessions — merge them?",
                        ),
                        color = HermesColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        mergeOnerisi.ref,
                        color = HermesColors.TextFaint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(
                    onClick = {
                        secimModu = true
                        secili = mergeOnerisi.dbIds.toSet()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(S.t2("Seç", "Select"), color = HermesColors.Midground, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // tab 0 = Tümü, tab 2 = arşiv (aynı liste, farklı showArchived —
        // satırları MainActivity türetiyor); tab 1 = canlı.
        if (tab != 1) {
            // Tur-19 FR-001: "Tümü" = genel akış (varsayılan, grup başlıksız,
            // tam kronolojik). "Gruplar" = tur-16 zaman gruplu görünümü — tek
            // dokunuşla geri gelir; arama her ikisinde de geçerli (FR-004).
            // Arşiv sekmesi (tab 2) her zaman gruplu kalır.
            val flatFeed = tab == 0 && !groupedFeed
            // Görünüm anahtarı iki yönlü: "Gruplar" ↔ "Akış" (önceden gruplu
            // görünümden geri dönüş yoktu — rememberSaveable ile takılı kalıyordu).
            if (tab == 0) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (flatFeed) S.t2("Genel akış", "All activity")
                        else S.t2("Zaman grupları", "Grouped by time"),
                        color = HermesColors.TextFaint,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { groupedFeed = !groupedFeed },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            if (flatFeed) S.t2("Gruplar", "Groups") else S.t2("Akış", "Feed"),
                            color = HermesColors.Midground,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            val feedRows = if (flatFeed) drawerFeed(rows, query) else null
            val hasVisibleRows = feedRows?.isNotEmpty() ?: items.any { it is DrawerItem.RowItem }
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
                                if (secimModu) secimToggle(row) else {
                                    // K21: listede başka yere dokunma takılı sil
                                    // satırını sıfırlar.
                                    deleteArmedKey = null
                                    onPick(row)
                                    onDismissDrawer()
                                }
                            },
                            onLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                deleteArmedKey = null
                                if (secimModu) secimToggle(row) else menuRow = row
                            },
                            onArchiveSwipe = {
                                if (!secimModu) {
                                    onSetArchived(row, true)
                                    undoRow = row
                                    undoKind = UndoKind.Archive
                                }
                            },
                            onDeleteSwipe = {
                                deleteArmedKey = null
                                onDeleteSwipe(row)
                                undoRow = row
                                undoKind = UndoKind.Delete
                            },
                            deleteArmed = deleteArmedKey == row.key,
                            onArmDelete = { deleteArmedKey = row.key },
                            onResetDelete = { if (deleteArmedKey == row.key) deleteArmedKey = null },
                            secimModu = secimModu,
                            seciliSecim = row.dbId in secili,
                        )
                    }
                } else {
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
                                        if (secimModu) secimToggle(entry.row) else {
                                            deleteArmedKey = null
                                            onPick(entry.row)
                                            onDismissDrawer()
                                        }
                                    },
                                    onLongPress = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        deleteArmedKey = null
                                        if (secimModu) secimToggle(entry.row) else menuRow = entry.row
                                    },
                                    onArchiveSwipe = {
                                        if (secimModu) {
                                            // Seçim modunda kaydır-arşiv kapalı (kaza riski).
                                        } else if (tab == 2) {
                                            // Arşiv görünümü: kaydır = arşivden çıkar.
                                            onSetArchived(entry.row, false)
                                        } else {
                                            onSetArchived(entry.row, true)
                                            undoRow = entry.row
                                            undoKind = UndoKind.Archive
                                        }
                                    },
                                    onDeleteSwipe = {
                                        deleteArmedKey = null
                                        onDeleteSwipe(entry.row)
                                        undoRow = entry.row
                                        undoKind = UndoKind.Delete
                                    },
                                    deleteArmed = deleteArmedKey == entry.row.key,
                                    onArmDelete = { deleteArmedKey = entry.row.key },
                                    onResetDelete = { if (deleteArmedKey == entry.row.key) deleteArmedKey = null },
                                    archivedMode = tab == 2,
                                    secimModu = secimModu,
                                    seciliSecim = entry.row.dbId in secili,
                                )
                            }
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
                if (query.isNotBlank() && !hasVisibleRows) {
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
                // K2 — tek "ne çalışıyor" envanteri: sohbet oturumları + köprü
                // WS + Artemis görevleri (drawerStatusDot renk semantiği).
                item(key = "live-inventory") {
                    LiveInventoryCard(
                        liveSessions = liveSessions,
                        liveTitleOf = liveTitleOf,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
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
            onToggleFollow = { menuRow = null; onToggleFollow(row) },
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
            onMerge = if (tab != 1 && row.dbId.isNotBlank()) {
                {
                    menuRow = null
                    secimModu = true
                    secili = setOf(row.dbId)
                }
            } else {
                null
            },
            onMoveToNew = if (tab != 1 && row.dbId.isNotBlank()) {
                {
                    menuRow = null
                    onMoveToNew(row)
                }
            } else {
                null
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

    // K13c: birleştirme onayı — sunucuda tek yeni oturum; başlık isteğe bağlı.
    if (mergeOnay) {
        AlertDialog(
            onDismissRequest = { mergeOnay = false },
            containerColor = HermesColors.Surface,
            title = {
                Text(
                    S.t2("Oturumları birleştir", "Merge sessions"),
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            text = {
                Column {
                    Text(
                        S.t2(
                            secili.size.toString() + " oturum sunucuda tek yeni oturumda birleştirilecek; " +
                                "kaynak başlıkları sınırlarla etiketlenir ve kaynaklara dokunulmaz. " +
                                "İçerik telefona indirilmez.",
                            secili.size.toString() + " sessions will be merged into one new session " +
                                "server-side; sources are labeled and left untouched. " +
                                "Content never reaches the phone.",
                        ),
                        color = HermesColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = mergeBaslik,
                        onValueChange = { mergeBaslik = it },
                        label = { Text(S.t2("Başlık (isteğe bağlı)", "Title (optional)")) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMergeConfirm(secili.filter { it.isNotBlank() }.distinct(), mergeBaslik)
                        mergeOnay = false
                        secimModu = false
                        secili = emptySet()
                        mergeBaslik = ""
                    },
                ) {
                    Text(S.t2("Birleştir", "Merge"), color = HermesColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { mergeOnay = false }) {
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

/** K13c: birleştirme durum satırı — ilerleme/sonuç/hata; onKapat null = kapatma yok. */
@Composable
private fun MergeDurumSatiri(metin: String, onKapat: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            metin,
            color = HermesColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        if (onKapat != null) {
            TextButton(onClick = onKapat, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Close, null, tint = HermesColors.TextMuted, modifier = Modifier.size(14.dp))
            }
        }
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

/** K21: geri-al satırının türü — arşiv mi yerel gizleme mi. */
private enum class UndoKind { Archive, Delete }

/**
 * Çekmece oturum satırı (FR-003/FR-004): Tümü/Gruplar görünümünde K21 iki
 * aşamalı swipe ([TwoStageSwipeRow]); arşiv görünümünde tur22
 * [ArchiveSwipeRow] KORUNUR (brief madde-4: arşiv görünümü etkilenmez).
 */
@Composable
private fun DrawerSessionRow(
    row: DrawerRow,
    onPick: () -> Unit,
    onLongPress: () -> Unit,
    onArchiveSwipe: () -> Unit,
    /** Tur22 madde-4: true = arşiv görünümü — mevcut SwipeToDismissBox yolu. */
    archivedMode: Boolean = false,
    /** K13c: true = çoklu seçim modu — satır dokunuşu seçim yapar (çağıran uygular). */
    secimModu: Boolean = false,
    /** K13c: seçim modunda bu satır seçili mi (dbId üzerinden). */
    seciliSecim: Boolean = false,
    /** K21: ikinci aşama silme uygulanınca çağrılır (yerel gizleme). */
    onDeleteSwipe: () -> Unit = {},
    /** K21: bu satır takılı sil fazında mı (üst katman tek kaynağı). */
    deleteArmed: Boolean = false,
    /** K21: satır takılı konuma geçti (üst katman kaydetsin). */
    onArmDelete: () -> Unit = {},
    /** K21: bu satırın takılı fazı kendi içinde sıfırlandı. */
    onResetDelete: () -> Unit = {},
) {
    if (archivedMode) {
        ArchiveSwipeRow(
            row, onPick, onLongPress, onArchiveSwipe, archivedMode,
            secimModu, seciliSecim,
        )
    } else {
        TwoStageSwipeRow(
            row, onPick, onLongPress, onArchiveSwipe,
            onDeleteSwipe, deleteArmed, onArmDelete, onResetDelete,
            secimModu, seciliSecim,
        )
    }
}

/**
 * Arşiv görünümü satırı — tur22 madde-4 SwipeToDismissBox davranışı
 * BİREBİR korunur: sağa çek = arşivden çıkar (kaydırma KENDİ kararı değil,
 * bayrak sonucu çizer; bayrak kalkınca Settled'a toplanır).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ArchiveSwipeRow(
    row: DrawerRow,
    onPick: () -> Unit,
    onLongPress: () -> Unit,
    onArchiveSwipe: () -> Unit,
    /** Çağıran yalnız arşiv görünümünde kullanır (true); etiketler buna göre. */
    archivedMode: Boolean = true,
    /** K13c: çoklu seçim modu (arşiv görünümünde de desteklenir). */
    secimModu: Boolean = false,
    /** K13c: seçili mi. */
    seciliSecim: Boolean = false,
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
        DrawerRowSurface(row, interaction, rowScale, onPick, onLongPress, secimModu, seciliSecim) {
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

/**
 * Satır içeriği — her iki swipe uygulamasının (ArchiveSwipeRow /
 * TwoStageSwipeRow) paylaştığı ortak çizim: durum noktası, başlık, JEV
 * rozeti, saat, önizleme. Dokun = seç, uzun bas = alt sayfa; basılıyken
 * hafif ölçek (tur22 madde-2).
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DrawerRowSurface(
    row: DrawerRow,
    interaction: MutableInteractionSource,
    rowScale: Float,
    onPick: () -> Unit,
    onLongPress: () -> Unit,
    /** K13c: çoklu seçim modu — satır dokunuşu seçim yapar (çağıran uygular). */
    secimModu: Boolean = false,
    /** K13c: seçim modunda bu satır seçili mi (dbId üzerinden). */
    seciliSecim: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .background(
                if (row.current) HermesColors.SurfaceDim else HermesColors.Background,
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
        // K13c: çoklu seçim göstergesi — seçim modunda durum noktasının yerine.
        if (secimModu) {
            if (seciliSecim) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = S.t2("Seçili", "Selected"),
                    tint = HermesColors.Midground,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Box(
                    Modifier
                        .size(16.dp)
                        .border(1.5.dp, HermesColors.TextFaint, CircleShape),
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        content()
    }
}

/**
 * K21 — oturum satırı iki aşamalı swipe (Tümü/Gruplar görünümü):
 *
 *  - SAĞA 1. çekim: ARM eşiği geçilirse satır YARIDA TAKILIR — kırmızı
 *    zemin + "Silmek için tekrar çek" ipucu; bırakınca toplanmaz.
 *  - Takılıyken SAĞA 2. tam çekim (bırakma eşiği geçilirse): yerel gizleme
 *    (sunucuya gitmez) + "Silindi — Geri Al" satırı.
 *  - SOLA tam çekim: arşivle + "Arşivlendi — Geri Al".
 *  - SIFIRLAMA: takılı satır sola geri çekilirse veya üst katman
 *    (listede başka yere dokunma) armed bayrağını kaldırırsa normal duruma
 *    döner.
 *
 * Karar mantığı [SwipeDeleteLogic]'te (saf, birim testli); composable
 * yalnız eşleme yapar. Konumlar Animatable offset (px) — satır genişliği
 * onSizeChanged ile ölçülür.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TwoStageSwipeRow(
    row: DrawerRow,
    onPick: () -> Unit,
    onLongPress: () -> Unit,
    onArchiveSwipe: () -> Unit,
    onDeleteSwipe: () -> Unit,
    deleteArmed: Boolean,
    onArmDelete: () -> Unit,
    onResetDelete: () -> Unit,
    /** K13c: çoklu seçim modu. */
    secimModu: Boolean = false,
    /** K13c: seçili mi. */
    seciliSecim: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var rowWidthPx by remember(row.key) { mutableFloatStateOf(0f) }
    val offset = remember(row.key) { Animatable(0f) }
    var phase by remember(row.key) { mutableStateOf(SwipeDeleteLogic.Phase.Idle) }

    // "Listede başka yere dokunulursa" sıfırlaması: üst katman armed
    // bayrağını kaldırdığında takılı satır toplanır (tek kaynak üstte).
    LaunchedEffect(deleteArmed) {
        if (!deleteArmed && phase == SwipeDeleteLogic.Phase.Armed) {
            phase = SwipeDeleteLogic.Phase.Idle
            offset.animateTo(0f)
        }
    }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            offset.snapTo((offset.value + delta).coerceIn(-rowWidthPx, rowWidthPx))
        }
    }

    val interaction = remember(row.key) { MutableInteractionSource() }
    val pressedState by interaction.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val rowScale by animateFloatAsState(
        targetValue = if (pressedState && !reduced) 0.985f else 1f,
        animationSpec = if (reduced) tween(0) else tween(HermesMotion.FAST_MS),
        label = "cekmece-iki-asamali-olcek",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { if (it.width > 0) rowWidthPx = it.width.toFloat() }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                onDragStopped = { _ ->
                    val w = rowWidthPx
                    if (w > 0f) {
                        val d = SwipeDeleteLogic.onRelease(phase, offset.value / w)
                        phase = d.phase
                        when {
                            d.archive -> {
                                onArchiveSwipe()
                                scope.launch { offset.animateTo(0f) }
                            }
                            d.delete -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onResetDelete()
                                onDeleteSwipe()
                                scope.launch { offset.snapTo(0f) }
                            }
                            d.arm -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onArmDelete()
                                scope.launch { offset.animateTo(w * SwipeDeleteLogic.STUCK_FRACTION) }
                            }
                            d.target == SwipeDeleteLogic.Target.Stuck ->
                                scope.launch { offset.animateTo(w * SwipeDeleteLogic.STUCK_FRACTION) }
                            else ->
                                scope.launch { offset.animateTo(0f) }
                        }
                    }
                },
            ),
    ) {
        // Zemin: sağa sürüklemede soldan kırmızı sil ipucu (takılıyken
        // koyu + "tekrar çek"), sola sürüklemede sağdan arşiv — mevcut
        // arşiv görsel dili korunur.
        if (offset.value > 1f) {
            Row(
                Modifier
                    .matchParentSize()
                    .background(
                        HermesColors.Danger.copy(
                            alpha = if (deleteArmed) 0.32f else 0.16f,
                        ),
                    )
                    .padding(start = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = S.t2("Sil", "Delete"),
                    tint = HermesColors.Danger,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (deleteArmed) S.t2("Silmek için tekrar çek", "Swipe again to delete")
                    else S.t2("Sil", "Delete"),
                    color = HermesColors.Danger,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else if (offset.value < -1f) {
            Row(
                Modifier
                    .matchParentSize()
                    .background(HermesColors.Danger.copy(alpha = 0.16f))
                    .padding(end = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    S.t2("Arşivle", "Archive"),
                    color = HermesColors.Danger,
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Archive,
                    contentDescription = S.t2("Arşivle", "Archive"),
                    tint = HermesColors.Danger,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        DrawerRowSurface(
            row,
            interaction,
            rowScale,
            onPick,
            onLongPress,
            secimModu,
            seciliSecim,
            modifier = Modifier.offset { IntOffset(offset.value.roundToInt(), 0) },
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
                    // (yer tutucu yok, uydurma yok).
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
    /** K13c: null değilse "Birleştir" satırı — seçim moduna geçirir. */
    onMerge: (() -> Unit)? = null,
    /** K29-b: null değilse "Yeniye taşı" satırı — tek-kaynak birleştirme. */
    onMoveToNew: ((DrawerRow) -> Unit)? = null,
    onToggleFollow: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Background,
        modifier = Modifier.heightIn(max = 620.dp),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text(
                row.title,
                color = HermesColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
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
                icon = Icons.Default.Star,
                label = if (row.followed) S.t2("Takibi bırak", "Unfollow") else S.t2("Takip et", "Follow"),
                onClick = onToggleFollow,
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
            if (onMerge != null) {
                DrawerSheetRow(
                    icon = Icons.AutoMirrored.Filled.CallMerge,
                    label = S.t2("Birleştir…", "Merge…"),
                    onClick = onMerge,
                )
            }
            if (onMoveToNew != null) {
                DrawerSheetRow(
                    icon = Icons.AutoMirrored.Filled.Article,
                    label = S.t2("Yeniye taşı…", "Move to new…"),
                    onClick = { onMoveToNew(row) },
                )
            }
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
                style = MaterialTheme.typography.bodyMedium,
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
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                minLines = 1,
                maxLines = 3,
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
                    Text(S.t2("Vazgeç", "Cancel"), color = HermesColors.TextMuted, style = MaterialTheme.typography.bodySmall)
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
                        style = MaterialTheme.typography.bodySmall,
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
        Text(label, color = tint, style = MaterialTheme.typography.bodyMedium)
    }
}
