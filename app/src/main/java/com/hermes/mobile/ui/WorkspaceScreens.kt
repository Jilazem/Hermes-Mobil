package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

@Composable
fun NotificationsScreen(notices: List<ActivityNotice>, onRead: (String) -> Unit, onOpen: (ActivityNotice) -> Unit, onReadAll: () -> Unit, onSettings: () -> Unit) {
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    val visible = notices.filter { !unreadOnly || !it.read }
    Column(Modifier.fillMaxSize()) {
        WorkspaceHeader(S.t2("Bildirimler", "Notifications"), S.t2("Hermes yanıtları ve takip güncellemeleri", "Hermes replies and followed session updates"), onSettings) {
            if (notices.any { !it.read }) IconButton(onClick = onReadAll) {
                Icon(Icons.Default.DoneAll, S.t2("Tümünü okundu işaretle", "Mark all read"), tint = HermesColors.TextMuted)
            }
        }
        Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !unreadOnly, onClick = { unreadOnly = false }, label = { Text(S.t2("Tümü", "All")) })
            FilterChip(selected = unreadOnly, onClick = { unreadOnly = true }, label = { Text(S.t2("Okunmamış", "Unread")) })
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
                        Text(notice.title, color = HermesColors.TextPrimary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(notice.time)), color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(notice.text, color = HermesColors.TextSecondary, maxLines = 8, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    if (!notice.sessionId.isNullOrBlank()) Text(S.t2("Sohbette aç →", "Open chat →"), color = HermesColors.Midground, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

/** Takip edilen arşivler dahil; gizlenenler mapper tarafından zaten elenir. */
fun followedRows(rows: List<DrawerRow>, query: String = ""): List<DrawerRow> {
    val q = query.trim()
    return rows.filter { it.followed && (q.isBlank() || it.title.contains(q, true) || it.preview.contains(q, true)) }
        .sortedWith(compareByDescending<DrawerRow> { it.working }.thenByDescending { it.epochSeconds })
}

@Composable
fun FollowingScreen(rows: List<DrawerRow>, loading: Boolean, error: String?, onOpen: (DrawerRow) -> Unit, onUnfollow: (DrawerRow) -> Unit, onBrowse: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = followedRows(rows, query)
    Column(Modifier.fillMaxSize()) {
        WorkspaceHeader(S.t2("Takip", "Following"), S.t2("Seçtiğin oturumlar, tek yerde", "Your selected sessions, in one place"), onSettings) {
            IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, S.t2("Yenile", "Refresh"), tint = HermesColors.TextMuted) }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text(S.t2("Takip ettiklerinde ara", "Search followed sessions")) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
        error?.let { Text(it, color = HermesColors.Danger, modifier = Modifier.padding(18.dp), style = MaterialTheme.typography.bodySmall) }
        if (loading && visible.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth().padding(18.dp))
        if (visible.isEmpty() && !loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.StarOutline, null, tint = HermesColors.TextFaint, modifier = Modifier.size(40.dp))
                    Text(S.t2(if (query.isBlank()) "Henüz takip ettiğin oturum yok" else "Eşleşen oturum yok", if (query.isBlank()) "No followed sessions yet" else "No matching sessions"), color = HermesColors.TextPrimary, modifier = Modifier.padding(top = 12.dp))
                    Text(S.t2("Oturuma uzun basıp “Takip et” seç.", "Long press a session and choose Follow."), color = HermesColors.TextMuted)
                    TextButton(onClick = onBrowse) { Text(S.t2("Oturumları aç", "Browse sessions")) }
                }
            }
        } else LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible, key = { it.key }) { row ->
                Row(Modifier.fillMaxWidth().background(HermesColors.Surface, MaterialTheme.shapes.large).clickable { onOpen(row) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.title, color = HermesColors.TextPrimary, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(when {
                            row.liveSession?.isWaiting == true -> S.t2("Yanıt bekliyor", "Awaiting input")
                            row.liveSession?.isStarting == true -> S.t2("Başlıyor", "Starting")
                            row.working -> S.t2("Çalışıyor", "Working")
                            row.liveSession != null -> S.t2("Boşta", "Idle")
                            row.dot == "green" || row.dot == "yellow" -> S.t2("Açık", "Open")
                            else -> S.t2("Geçmiş oturum", "Past session")
                        }, color = if (row.working) HermesColors.Online else HermesColors.TextMuted, style = MaterialTheme.typography.labelSmall)
                        if (row.preview.isNotBlank()) Text(row.preview, color = HermesColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (row.archived) Text(S.t2("Arşivde", "Archived"), color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { onUnfollow(row) }) { Icon(Icons.Default.Star, S.t2("Takibi bırak", "Unfollow"), tint = HermesColors.Midground) }
                }
            }
        }
    }
}
