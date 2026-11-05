package com.hermes.mobile.ui.bots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.*
import kotlinx.coroutines.CancellationException

/** Canonical identities and durable session ids; no duplicate mobile bot is created. */
@Composable
internal fun HermesProfileBots(
    server: ServerProfile?, profiles: List<HermesProfile>,
    onRefresh: () -> Unit,
    onOpen: (String, HermesSession?) -> Unit,
) {
    var selected by remember(server?.id) { mutableStateOf<String?>(null) }
    var window by remember(server?.id) { mutableStateOf(ProfileSessionWindow()) }
    var offset by remember(server?.id) { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(server?.id, server?.token, selected, offset, revision) {
        error = null
        val name = selected ?: return@LaunchedEffect
        val connection = server ?: return@LaunchedEffect
        loading = true
        try {
            DiagLog.i("profile-bots", "Sohbet sayfası isteniyor: offset=$offset")
            val page = kotlinx.coroutines.withTimeout(35_000) { HermesClient(connection).profileSessionPage(name, offset) }
            window = appendProfileSessionPage(window, page, offset)
            DiagLog.i("profile-bots", "Sohbet sayfası alındı: ${page.sessions.size} satır")
        }
        catch (_: kotlinx.coroutines.TimeoutCancellationException) { error = "Sohbet listesi zamanında alınamadı. Yeniden deneyin." }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Sohbetler alınamadı: ${e.message}" }
        finally { loading = false }
    }
    val selectedName = selected
    if (selectedName != null) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Column {
                TextButton(onClick = { selected = null; window = ProfileSessionWindow(); offset = 0 }) { Text("‹ Botlarıma dön") }
                Text(profiles.firstOrNull { it.name == selectedName }?.displayName?.ifBlank { selectedName } ?: selectedName,
                    style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = { onOpen(selectedName, null) }) { Text("Yeni sohbet") }
                    TextButton(enabled = !loading, onClick = { window = ProfileSessionWindow(); offset = 0; revision++ }) { Text("Yenile") }
                }
                if (loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Sohbetler yükleniyor…", style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(if (window.total != null) "${window.rows.size} / ${window.total} sohbet" else "${window.rows.size} sohbet", style = MaterialTheme.typography.labelSmall)
            } }
            items(window.rows, key = { it.id }) { session ->
                OutlinedCard(onClick = { onOpen(selectedName, session) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(session.serverTitle ?: session.displayName ?: "Sohbet", maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${session.messageCount} mesaj", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (window.nextOffset != null || error != null) item {
                TextButton(enabled = !loading, onClick = { offset = window.nextOffset ?: offset; revision++ }) {
                    Text(if (error != null) "Yeniden dene" else "Diğer sohbetleri yükle")
                }
            }
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Hermes ekibim", style = MaterialTheme.typography.titleMedium)
            Text("Masaüstündeki aynı botlar ve sohbetler", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onRefresh(); revision++ }) { Text("Yenile") }
        }
        if (profiles.isEmpty()) item { Text("Bot profilleri henüz alınmadı. Dashboard bağlantısını kontrol edip yenileyin.") }
        items(profiles, key = { it.name }) { bot ->
            val name = bot.displayName.ifBlank { bot.name }
            Card(onClick = { window = ProfileSessionWindow(); offset = 0; selected = bot.name }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(listOf("🐾", "🌱", "✨", "🦊")[Math.floorMod(bot.name.hashCode(), 4)], style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Text(bot.description.ifBlank { bot.model.orEmpty() }.ifBlank { "Hermes profili" }, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (selected == bot.name) "▴" else "▾")
                }
            }
        }
    }
}
