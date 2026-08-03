package com.hermes.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.SessionDetailState
import com.hermes.mobile.data.SessionMessage
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

private sealed interface TranscriptRow {
    data class Message(val message: SessionMessage) : TranscriptRow
    data class Tools(val entries: List<ToolEntry>) : TranscriptRow
}

/**
 * Ardışık `role=tool` mesajlarını tek gruba indirir.
 *
 * Hata tespiti gövdeye bakarak yapılır — sunucu ayrı bir başarı alanı
 * göndermiyor, sonuç JSON'unda `"error"` ya da `BLOCKED:` geçiyor.
 */
private fun foldToolMessages(messages: List<SessionMessage>): List<TranscriptRow> {
    val rows = mutableListOf<TranscriptRow>()
    var run = mutableListOf<SessionMessage>()

    fun flush() {
        if (run.isEmpty()) return
        rows += TranscriptRow.Tools(
            run.map { m ->
                val body = m.content.orEmpty()
                val failed = body.contains("\"error\"") || body.contains("BLOCKED:")
                ToolEntry(
                    name = m.toolName ?: "araç",
                    state = if (failed) ToolEntryState.Failed else ToolEntryState.Done,
                    detail = body,
                )
            }
        )
        run = mutableListOf()
    }

    messages.forEach { m ->
        if (m.isTool) run += m else { flush(); rows += TranscriptRow.Message(m) }
    }
    flush()
    return rows
}

@Composable
fun SessionDetailScreen(state: SessionDetailState, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Geri",
                    tint = HermesColors.Midground,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.session?.title ?: state.sessionId,
                    color = HermesColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.session?.let { s ->
                    Text(
                        listOfNotNull(
                            s.model,
                            "${s.messageCount} mesaj",
                            s.source,
                        ).joinToString(" · "),
                        color = HermesColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = HermesColors.Midground)
            }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Mesajlar alınamadı", color = HermesColors.Danger, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(state.error, color = HermesColors.TextMuted, fontSize = 12.sp)
                }
            }

            state.messages.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("Bu oturumda mesaj yok.", color = HermesColors.TextMuted, fontSize = 13.sp)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Ardışık araç sonuçları tek satıra katlanır — sekiz
                // `execute_code` yan yana gelince yanıt kaybolmasın.
                val rows = foldToolMessages(state.messages)
                itemsIndexed(rows) { index, row ->
                    if (index == 0) Spacer(Modifier.height(2.dp))
                    when (row) {
                        is TranscriptRow.Message -> MessageBubble(row.message)
                        is TranscriptRow.Tools -> ToolActivityRow(row.entries)
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${state.messages.size} mesaj yüklendi",
                        style = MonoTextStyle,
                        color = HermesColors.TextFaint,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    )
                }
            }
        }
    }
}
