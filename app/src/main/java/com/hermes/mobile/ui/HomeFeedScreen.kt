package com.hermes.mobile.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.ConnectionState
import com.hermes.mobile.ui.theme.HermesColors

/**
 * V3 ana duvar akışı — uygulama açılınca ilk görülen ekran.
 *
 * ChatGPT / Claude mobil düzeni: üstte selamlama + bağlantı durumu, altında
 * süzgeç çipleri ve konu kartları (canlı olanlar üstte), en altta her zaman
 * hazır "Hermes'e sor" kutusu. Karta dokununca o konunun sohbetine girilir;
 * sohbetten geri gelince yine burası. Karar mantığı [homeFeed] içinde (testli).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    rows: List<DrawerRow>,
    loading: Boolean,
    refreshing: Boolean,
    connection: ConnectionState,
    serverName: String,
    sourceOf: (DrawerRow) -> String?,
    messageCountOf: (DrawerRow) -> Int,
    onOpen: (DrawerRow) -> Unit,
    onNewChat: (String) -> Unit,
    onVoice: () -> Unit,
    onRefresh: () -> Unit,
    onOpenServers: () -> Unit,
    onTogglePin: (DrawerRow) -> Unit,
    onArchive: (DrawerRow) -> Unit,
    onCzip: (DrawerRow) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(HomeFilter.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val en = S.lang == Lang.EN

    val feed = remember(rows, filter, query) { homeFeed(rows, filter, query, sourceOf) }
    val liveCount = remember(rows) { rows.count { !it.archived && isLiveRow(it) } }

    Column(
        Modifier
            .fillMaxSize()
            .background(HermesColors.Background)
            .imePadding(),
    ) {
        // ── Üst şerit: marka + bağlantı + arama/sunucu ────────────────────
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    homeGreeting(java.time.LocalTime.now().hour, en),
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                ConnectionLine(connection, serverName, liveCount, onOpenServers)
            }
            IconButton(onClick = {
                searching = !searching
                if (!searching) query = ""
            }) {
                Icon(
                    if (searching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = S.t2("Ara", "Search"),
                    tint = HermesColors.TextSecondary,
                )
            }
            IconButton(onClick = onOpenServers) {
                Icon(Icons.Default.Dns, S.t2("Sunucular", "Servers"), tint = HermesColors.TextSecondary)
            }
        }

        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                placeholder = { Text(S.t2("Konularda ara…", "Search topics…")) },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HermesColors.TextPrimary,
                    unfocusedTextColor = HermesColors.TextPrimary,
                    focusedBorderColor = HermesColors.BorderStrong,
                    unfocusedBorderColor = HermesColors.Border,
                    cursorColor = HermesColors.Midground,
                ),
            )
        }

        // ── Süzgeç çipleri ─────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HomeFilter.entries.forEach { f ->
                val n = if (f == HomeFilter.All) 0 else homeFilterCount(rows, f, sourceOf)
                val label = (if (en) f.labelEn else f.labelTr) + if (n > 0) " $n" else ""
                FilterPill(label, selected = filter == f) { filter = f }
            }
        }

        // ── Konu kartları ───────────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (loading && rows.isEmpty()) {
                    item(key = "skeleton") { SessionListSkeleton(5, Modifier.padding(vertical = 4.dp)) }
                }
                items(feed, key = { "home-${it.key}" }) { row ->
                    TopicCard(
                        row = row,
                        source = sourceOf(row),
                        long = isLongSession(messageCountOf(row)),
                        messageCount = messageCountOf(row),
                        onOpen = { onOpen(row) },
                        onTogglePin = { onTogglePin(row) },
                        onArchive = { onArchive(row) },
                        onCzip = { onCzip(row) },
                    )
                }
                if (!loading && feed.isEmpty()) {
                    item(key = "empty") {
                        when {
                            connection is ConnectionState.Error && rows.isEmpty() -> EmptyState(
                                icon = EmptyStateIcons.NoConnection,
                                message = S.t2(
                                    "Sunucuya ulaşılamadı. Adres ve anahtarı kontrol et.",
                                    "Could not reach the server. Check the address and token.",
                                ),
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                actionLabel = S.t2("Sunucular", "Servers"),
                                onAction = onOpenServers,
                            )
                            query.isNotBlank() -> EmptyState(
                                icon = EmptyStateIcons.NoResults,
                                message = emptyStateNoResults(query),
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                actionLabel = S.t2("Aramayı temizle", "Clear search"),
                                onAction = { query = "" },
                            )
                            else -> EmptyState(
                                icon = EmptyStateIcons.NoSessions,
                                message = S.t2(
                                    "Burada henüz konu yok. Aşağıdan yeni bir konu başlat.",
                                    "No topics here yet. Start one below.",
                                ),
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                }
                item(key = "bottom-space") { Spacer(Modifier.height(8.dp)) }
            }
        }

        // ── Her zaman hazır soru kutusu ────────────────────────────────────
        AskBar(
            draft = draft,
            onDraft = { draft = it },
            onSend = {
                val t = draft.trim()
                if (t.isNotEmpty()) {
                    draft = ""
                    onNewChat(t)
                }
            },
            onVoice = onVoice,
        )
    }
}

@Composable
private fun ConnectionLine(
    connection: ConnectionState,
    serverName: String,
    liveCount: Int,
    onOpenServers: () -> Unit,
) {
    val (label, color) = when (connection) {
        ConnectionState.Open -> S.t2("Bağlı", "Connected") to HermesColors.Online
        ConnectionState.Connecting -> S.t2("Bağlanıyor…", "Connecting…") to HermesColors.Busy
        is ConnectionState.Error -> S.t2("Bağlantı yok", "Offline") to HermesColors.Danger
        else -> S.t2("Bekliyor", "Idle") to HermesColors.TextFaint
    }
    Row(
        Modifier.combinedClickableCompat(onOpenServers).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        val live = if (liveCount > 0) S.t2(" · $liveCount canlı ajan", " · $liveCount live agents") else ""
        Text(
            "$label · $serverName$live",
            color = HermesColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (connection is ConnectionState.Error && connection.reason.isNotBlank()) {
        Text(
            connection.reason,
            color = HermesColors.TextFaint,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.then(Modifier.clip(MaterialTheme.shapes.small)).then(
        Modifier.combinedClickableNoLong(onClick),
    )

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableNoLong(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) HermesColors.Midground else HermesColors.SurfaceDim
    val fg = if (selected) HermesColors.OnAccent else HermesColors.TextSecondary
    Text(
        label,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(bg)
            .combinedClickableNoLong(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopicCard(
    row: DrawerRow,
    source: String?,
    long: Boolean,
    messageCount: Int,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onCzip: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    val en = S.lang == Lang.EN
    val borderColor = if (row.working) HermesColors.Online.copy(alpha = 0.55f) else HermesColors.Border

    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(HermesColors.SurfaceCard)
                .border(1.dp, borderColor, MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(row)
                Text(
                    displayLabel(row.title),
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (row.pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = S.t2("Sabit", "Pinned"),
                        tint = HermesColors.TextFaint,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    formatRelative(row.epochSeconds.takeIf { it > 0 }),
                    color = HermesColors.TextFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (row.preview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    row.preview,
                    color = HermesColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceBadgeIcon(source, en = en)
                if (row.working) Badge(S.t2("çalışıyor", "working"), HermesColors.Online)
                else if (row.dot == "yellow") Badge(S.t2("yanıt bekliyor", "waiting"), HermesColors.Busy)
                if (messageCount > 0) {
                    Text(
                        S.t2("$messageCount ileti", "$messageCount msgs"),
                        color = HermesColors.TextFaint,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (long) Badge(S.t2("uzun · czip", "long · czip"), HermesColors.Midground)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (row.pinned) S.t2("Sabitlemeyi kaldır", "Unpin") else S.t2("Sabitle", "Pin")) },
                onClick = { menu = false; onTogglePin() },
            )
            DropdownMenuItem(
                text = { Text(S.t2("Arşivle", "Archive")) },
                onClick = { menu = false; onArchive() },
            )
            DropdownMenuItem(
                text = { Text(S.t2("Czip ile yeni oturumda sürdür", "Continue in new session with czip")) },
                leadingIcon = { Icon(Icons.Default.Compress, null) },
                onClick = { menu = false; onCzip() },
            )
        }
    }
}

@Composable
private fun StatusDot(row: DrawerRow) {
    val c: Color? = when (row.dot) {
        "green" -> HermesColors.Online
        "yellow" -> HermesColors.Busy
        "grey" -> HermesColors.Offline
        else -> null
    }
    if (c != null) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun AskBar(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(HermesColors.Surface)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.extraLarge)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.weight(1f),
            placeholder = { Text(S.t2("Hermes'e sor — yeni konu başlat", "Ask Hermes — start a new topic")) },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = HermesColors.TextPrimary,
                unfocusedTextColor = HermesColors.TextPrimary,
                cursorColor = HermesColors.Midground,
            ),
        )
        if (draft.isBlank()) {
            IconButton(onClick = onVoice) {
                Icon(Icons.Default.GraphicEq, S.t2("Sesli konuş", "Voice"), tint = HermesColors.TextSecondary)
            }
        } else {
            IconButton(
                onClick = onSend,
                modifier = Modifier.clip(CircleShape).background(HermesColors.Midground).size(40.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    S.t2("Gönder", "Send"),
                    tint = HermesColors.OnAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Sohbet ekranında, composer'ın üstünde: açık oturum uzunsa czip önerisi.
 * Otomatik çıkar (ayar gerekmez); tek dokunuşla paketleyip yeni oturumda
 * sürdürür, "×" ile bu oturum için kapanır.
 */
@Composable
fun CzipBanner(
    messageCount: Int,
    onCzip: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(HermesColors.Midground.copy(alpha = 0.10f))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Compress, null, tint = HermesColors.Midground, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            S.t2(
                "Uzun oturum ($messageCount ileti) — czip ile yeni oturumda sürdür",
                "Long session ($messageCount msgs) — continue lean with czip",
            ),
            color = HermesColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            S.t2("Taşı", "Move"),
            color = HermesColors.Midground,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .combinedClickableNoLong(onCzip)
                .padding(horizontal = 10.dp, vertical = 11.dp),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, S.t2("Kapat", "Dismiss"), tint = HermesColors.TextFaint, modifier = Modifier.size(16.dp))
        }
    }
}
