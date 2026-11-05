package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.ActivityNotice
import com.hermes.mobile.data.NoticeKind
import com.hermes.mobile.data.noticeVisible
import com.hermes.mobile.ui.theme.HermesColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun WorkspaceHeader(title: String, subtitle: String, onSettings: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = HermesColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HermesColors.TextMuted)
        }
        action()
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, S.t2("Ayarlar", "Settings"), tint = HermesColors.TextMuted) }
    }
}

/** V5-2: bildirim türü etiketi (tek yer — filtre çipleri ve rozet aynı metni kullanır). */
@Composable
private fun noticeKindLabel(kind: String): String = when (kind) {
    NoticeKind.CRON -> S.t2("Zamanlanmış", "Scheduled")
    NoticeKind.SYSTEM -> S.t2("Sistem", "System")
    NoticeKind.REPLY -> S.t2("Yanıt", "Reply")
    NoticeKind.FOLLOW -> S.t2("Takip", "Follow")
    NoticeKind.AGENT -> S.t2("Ajan", "Agent")
    else -> kind
}

/**
 * V5-2 madde 6 — sistem/cron bildirimleri ayrı sekmede.
 *
 * [onToggleMute] sessiz türü ayarlara yazar; sessiz tür kutuya yine düşer
 * (kayıp yok) ama durum çubuğuna çıkmaz ve rozeti şişirmez.
 * [onDelete] tek bildirimi kaldırır.
 */
@Composable
fun NotificationsScreen(
    notices: List<ActivityNotice>,
    onRead: (String) -> Unit,
    onOpen: (ActivityNotice) -> Unit,
    onReadAll: () -> Unit,
    onSettings: () -> Unit,
    mutedKinds: Set<String> = emptySet(),
    onToggleMute: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
) {
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var muteMenu by remember { mutableStateOf(false) }
    val visible = notices.filter { noticeVisible(it, kindFilter?.let { k -> setOf(k) } ?: emptySet(), unreadOnly) }
    Column(Modifier.fillMaxSize()) {
        WorkspaceHeader(S.t2("Bildirimler", "Notifications"), S.t2("Zamanlanmış görevler ve sistem bildirimleri — sohbet akışına karışmaz", "Scheduled jobs and system notices — kept out of the chat flow"), onSettings) {
            if (notices.any { !it.read }) IconButton(onClick = onReadAll) {
                Icon(Icons.Default.DoneAll, S.t2("Tümünü okundu işaretle", "Mark all read"), tint = HermesColors.TextMuted)
            }
            Box {
                IconButton(onClick = { muteMenu = true }) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        S.t2("Tür sessize al", "Mute kinds"),
                        tint = if (mutedKinds.isEmpty()) HermesColors.TextMuted else HermesColors.Midground,
                    )
                }
                DropdownMenu(expanded = muteMenu, onDismissRequest = { muteMenu = false }) {
                    Text(
                        S.t2("Sessiz türler", "Muted kinds"),
                        style = MaterialTheme.typography.labelMedium,
                        color = HermesColors.TextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    NoticeKind.ALL.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(noticeKindLabel(kind)) },
                            leadingIcon = {
                                Checkbox(checked = kind in mutedKinds, onCheckedChange = null)
                            },
                            onClick = { onToggleMute(kind) },
                        )
                    }
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 18.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = !unreadOnly, onClick = { unreadOnly = false }, label = { Text(S.t2("Tümü", "All")) })
            FilterChip(selected = unreadOnly, onClick = { unreadOnly = true }, label = { Text(S.t2("Okunmamış", "Unread")) })
            NoticeKind.ALL.forEach { kind ->
                FilterChip(
                    selected = kindFilter == kind,
                    onClick = { kindFilter = if (kindFilter == kind) null else kind },
                    label = {
                        Text(
                            if (kind in mutedKinds) "🔕 " + noticeKindLabel(kind) else noticeKindLabel(kind),
                        )
                    },
                )
            }
        }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, null, tint = HermesColors.TextFaint, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(S.t2("${if (unreadOnly) "Okunmamış bildirim" else "Henüz bildirim"} yok", if (unreadOnly) "No unread notifications" else "No notifications yet"), color = HermesColors.TextPrimary)
                    Text(S.t2("Yeni yanıtlar burada saklanır.", "New replies are saved here."), color = HermesColors.TextMuted)
                }
            }
        } else LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.id }) { notice ->
                Column(Modifier.fillMaxWidth().background(HermesColors.Surface, MaterialTheme.shapes.large).clickable {
                    onRead(notice.id)
                    if (!notice.sessionId.isNullOrBlank()) onOpen(notice)
                }.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!notice.read) { Box(Modifier.size(7.dp).background(HermesColors.Online, MaterialTheme.shapes.small)); Spacer(Modifier.width(8.dp)) }
                        Text(noticeKindLabel(notice.kind), color = HermesColors.Midground, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 8.dp))
                        Text(notice.title, color = HermesColors.TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(notice.time)), color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                        IconButton(onClick = { onDelete(notice.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, S.t2("Bildirimi sil", "Delete notification"), tint = HermesColors.TextFaint, modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(notice.text, color = HermesColors.TextSecondary, maxLines = 8, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (!notice.sessionId.isNullOrBlank()) Text(S.t2("Sohbette aç →", "Open chat →"), color = HermesColors.Midground, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

@Composable
fun FollowingScreen(rows: List<DrawerRow>, loading: Boolean, error: String?, onOpen: (DrawerRow) -> Unit, onUnfollow: (DrawerRow) -> Unit, onBrowse: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        WorkspaceHeader(S.t2("Takip", "Following"), S.t2("Seçtiğin oturumlar, tek yerde", "Your selected sessions, in one place"), onSettings) {
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, S.t2("Yenile", "Refresh"), tint = HermesColors.TextMuted) }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(S.t2("Takip ettiklerinde ara", "Search followed sessions")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
        SessionListPane(
            rows = rows,
            query = query,
            onOpen = onOpen,
            onToggleFollow = onUnfollow,
            loading = loading,
            error = error,
            compact = false,
            followedOnly = true,
            onBrowse = onBrowse,
            modifier = Modifier.weight(1f),
        )
    }
}
