package com.hermes.mobile.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.PinnedChatMessage
import com.hermes.mobile.ui.theme.HermesColors

internal fun shareChatText(context: Context, text: String) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Paylaş").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "Paylaşım uygulaması bulunamadı", Toast.LENGTH_LONG).show() }
}

/** Message gestures belong to text selection; tools remain explicit, accessible buttons. */
@Composable
fun MessageTextActions(
    text: String,
    onInspect: () -> Unit,
    pinned: Boolean = false,
    onPin: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        IconButton(onClick = onInspect, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.SelectAll, S.t2("Metni seç ve bağlantıları göster", "Select text and inspect links"),
                tint = HermesColors.TextMuted, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.ContentCopy, S.t2("Mesajı kopyala", "Copy message"), tint = HermesColors.TextMuted, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = { shareChatText(context, text) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Share, S.t2("Mesajı paylaş", "Share message"), tint = HermesColors.TextMuted, modifier = Modifier.size(18.dp))
        }
        onPin?.let {
            IconButton(onClick = it, modifier = Modifier.size(40.dp)) {
                Icon(if (pinned) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    if (pinned) S.t2("Sabitlemeyi kaldır", "Unpin message") else S.t2("Mesajı sabitle", "Pin message"),
                    tint = if (pinned) HermesColors.Midground else HermesColors.TextMuted, modifier = Modifier.size(18.dp))
            }
        }
        onMore?.let { IconButton(onClick = it, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.MoreHoriz, S.t2("Mesaj işlemleri", "Message actions"), tint = HermesColors.TextMuted)
        } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectableMessageSheet(entry: ChatTextEntry, pinned: Boolean, onPin: (() -> Unit)? = null, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val opener = rememberExternalLinkOpener()
    val links = remember(entry.text) { messageLinks(entry.text) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HermesColors.Background) {
        Column(Modifier.fillMaxWidth().heightIn(max = 740.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(S.t2("Metin ve bağlantılar", "Text and links"), style = MaterialTheme.typography.titleLarge)
            Text(S.t2("Seçmek için bir sözcüğe basılı tutup tutamaçları sürükle.", "Hold a word, then drag the handles to select text."),
                style = MaterialTheme.typography.bodySmall, color = HermesColors.TextMuted, modifier = Modifier.padding(top = 6.dp))
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(entry.text)) }) { Text(S.t2("Tümünü kopyala", "Copy all")) }
                TextButton(onClick = { shareChatText(context, entry.text) }) { Text(S.t2("Paylaş", "Share")) }
                onPin?.let { TextButton(onClick = it) { Text(if (pinned) S.t2("Sabitlemeyi kaldır", "Unpin") else S.t2("Sabitle", "Pin")) } }
            }
            Surface(color = HermesColors.Surface, shape = MaterialTheme.shapes.large) {
                SelectionContainer {
                    Text(entry.text, color = HermesColors.TextPrimary, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
            if (links.isNotEmpty()) {
                Text(S.t2("Bağlantılar · ${links.size}", "Links · ${links.size}"), style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                links.forEach { link ->
                    OutlinedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(link.label, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            SelectionContainer { Text(link.url, style = MaterialTheme.typography.bodySmall, color = HermesColors.TextMuted) }
                            Row {
                                TextButton(onClick = { opener(link.url) }) { Text(S.t2("Aç", "Open")) }
                                TextButton(onClick = { clipboard.setText(AnnotatedString(link.url)) }) { Text(S.t2("Kopyala", "Copy")) }
                                TextButton(onClick = { shareChatText(context, link.url) }) { Text(S.t2("Paylaş", "Share")) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationToolsSheet(
    title: String,
    entries: List<ChatTextEntry>,
    pinned: List<PinnedChatMessage>,
    onInspect: (ChatTextEntry) -> Unit,
    onUnpin: (PinnedChatMessage) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var showPinned by remember { mutableStateOf(false) }
    val results = remember(entries, query) { searchChatText(entries, query) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HermesColors.Background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(S.t2("Sohbet araçları", "Conversation tools"), style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showPinned, onClick = { showPinned = false }, label = { Text(S.t2("Ara", "Search")) })
                FilterChip(selected = showPinned, onClick = { showPinned = true }, label = { Text(S.t2("Sabitlenen · ${pinned.size}", "Pinned · ${pinned.size}")) })
            }
            Row {
                TextButton(enabled = entries.isNotEmpty(), onClick = { shareChatText(context, exportChatMarkdown(title, entries)) }) {
                    Text(S.t2("Sohbeti paylaş", "Share conversation"))
                }
                TextButton(enabled = entries.isNotEmpty(), onClick = onExport) { Text(S.t2("Markdown kaydet", "Save Markdown")) }
            }
            if (!showPinned) OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), label = { Text(S.t2("Bu sohbette ara", "Search this conversation")) },
                leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showPinned) {
                    if (pinned.isEmpty()) item { Text(S.t2("Henüz sabitlenmiş mesaj yok.", "No pinned messages yet."), color = HermesColors.TextMuted) }
                    items(pinned, key = { it.key }) { message ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().clickable { onInspect(ChatTextEntry(message.key, message.role, message.text)) }.padding(12.dp)) {
                                Text(message.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                TextButton(onClick = { onUnpin(message) }) { Text(S.t2("Sabitlemeyi kaldır", "Unpin")) }
                            }
                        }
                    }
                } else {
                    item { Text(S.t2("${results.size} mesaj", "${results.size} messages"), color = HermesColors.TextMuted, style = MaterialTheme.typography.labelMedium) }
                    items(results.take(100), key = { it.key }) { entry ->
                        OutlinedCard(Modifier.fillMaxWidth().clickable { onInspect(entry) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(if (entry.role == "user") S.t2("Sen", "You") else "Hermes", color = HermesColors.Midground, style = MaterialTheme.typography.labelMedium)
                                Text(entry.text, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(26.dp)) }
            }
        }
    }
}
