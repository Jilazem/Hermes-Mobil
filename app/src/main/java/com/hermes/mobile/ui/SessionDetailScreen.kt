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
import androidx.compose.material3.MaterialTheme
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

/**
 * Döküm satırı (tur-4 H): konuşma ÖNE, ajan günlüğü TEK katlanır satıra.
 *
 * Boss şikâyeti: "mesaj listesi ajan günlüğü gibi; asistanın yanıtı kayboluyor".
 * Telegram'da gördüğün şey senin mesajın + asistanın bitmiş yanıtıdır; düşünme
 * ve araç turları görünmez. Bu yüzden varsayılan görünür satırlar yalnız
 * user/assistant METNİ; `Düşünme`, araç çağrıları, araç sonuçları ve sistem
 * istemleri tek bir "Ayrıntı" satırında toplanır (dokununca açılır).
 */
sealed interface TranscriptRow {
    data class Message(val message: SessionMessage) : TranscriptRow

    /** Katlanmış ajan günlüğü: reasoning + araç çağrı/sonuçları + sistem. */
    data class Detail(val entries: List<ToolEntry>) : TranscriptRow
}

/** Araç gövdesi hata izi taşıyor mu — sunucu ayrı başarı alanı göndermiyor. */
private fun looksFailed(body: String): Boolean =
    body.contains("\"error\"") || body.contains("BLOCKED:")

/**
 * Ham mesaj listesini görüntüleme satırlarına indirger. Saf fonksiyon —
 * Compose'suz JVM testi (`TranscriptFoldTest`).
 */
fun foldTranscript(
    messages: List<SessionMessage>,
    toolLabel: String = "araç",
    systemLabel: String = "Sistem",
    otherLabel: String = "Kayıt",
): List<TranscriptRow> {
    val rows = mutableListOf<TranscriptRow>()
    val pending = mutableListOf<ToolEntry>()

    fun flush() {
        if (pending.isEmpty()) return
        rows += TranscriptRow.Detail(pending.toList())
        pending.clear()
    }

    messages.forEach { m ->
        when {
            m.isUser -> {
                flush()
                rows += TranscriptRow.Message(m)
            }

            m.isAssistant -> {
                // Yanıt metni varsa ÖNE çıkar (katlanmaz).
                if (!m.content.isNullOrBlank()) {
                    rows += TranscriptRow.Message(m)
                }
                // Düşünme + araç çağrıları ayrıntı satırına katılır.
                m.reasoning?.takeIf { it.isNotBlank() }?.let {
                    pending += ToolEntry(name = "Düşünme", state = ToolEntryState.Done, detail = it)
                }
                m.toolCalls.forEach { call ->
                    pending += ToolEntry(
                        name = call.function.name?.takeIf { n -> n.isNotBlank() } ?: "araç",
                        state = ToolEntryState.Done,
                        detail = call.function.arguments,
                    )
                }
            }

            m.isTool -> {
                val body = m.content.orEmpty()
                pending += ToolEntry(
                    name = m.toolName ?: toolLabel,
                    state = if (looksFailed(body)) ToolEntryState.Failed else ToolEntryState.Done,
                    detail = body,
                )
            }

            else -> {
                // system (ve bilinmeyen roller): asla öne çıkmaz.
                m.content?.takeIf { it.isNotBlank() }?.let {
                    pending += ToolEntry(
                        name = if (m.isSystem) systemLabel else otherLabel,
                        state = ToolEntryState.Done,
                        detail = it,
                    )
                }
            }
        }
    }
    flush()
    return rows
}

/**
 * Detay üst şeridinin sayaç metni — kusur D: "0 mesaj" yazılmaz.
 *
 * Kural: sunucunun oturum sayacı (`message_count`) varsa O gösterilir — kartla
 * birebir aynı sayı (tutarlılık sözleşmesi). Sayı yoksa (canlı oturumda REST
 * kaydı bulunmuyorsa) YÜKLENEN mesaj sayısı gösterilir. İkisi de yoksa (henüz
 * yükleniyor / hata / gerçekten boş) satır hiç çizilmez.
 */
fun detailCounterText(
    sessionCount: Int,
    loading: Boolean,
    error: String?,
    loaded: Int,
    en: Boolean,
): String? {
    if (loading || error != null) return null
    val count = sessionCount.takeIf { it > 0 } ?: loaded.takeIf { it > 0 } ?: return null
    return if (en) "$count messages" else "$count mesaj"
}

@Composable
fun SessionDetailScreen(state: SessionDetailState, onBack: () -> Unit) {
    val en = S.lang == Lang.EN
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
                    contentDescription = S.t2("Geri", "Back"),
                    tint = HermesColors.Midground,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    state.session?.title ?: state.sessionId,
                    color = HermesColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Tur-4 (kusur D): sayaç YÜKLENEN konuşmayı sayar; yüklenirken
                // ya da hata varsa hiçbir sayı yazılmaz (eski kod oturum
                // kaydındaki 0'ı basıp karttaki 39 ile çelişiyordu).
                detailCounterText(
                    sessionCount = state.session?.messageCount ?: 0,
                    loading = state.loading,
                    error = state.error,
                    loaded = state.messages.size,
                    en = en,
                )?.let { counter ->
                    Text(
                        counter,
                        color = HermesColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
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
                    Text(
                        S.t2("Mesajlar alınamadı", "Could not load messages"),
                        color = HermesColors.Danger,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(state.error, color = HermesColors.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }

            state.messages.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    S.t2("Bu oturumda mesaj yok.", "No messages in this session."),
                    color = HermesColors.TextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {
                val rows = foldTranscript(
                    state.messages,
                    toolLabel = tr("araç", "tool"),
                    systemLabel = tr("Sistem", "System"),
                    otherLabel = tr("Kayıt", "Record"),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(rows) { index, row ->
                        if (index == 0) Spacer(Modifier.height(2.dp))
                        when (row) {
                            is TranscriptRow.Message -> MessageBubble(row.message, withReasoning = false)
                            is TranscriptRow.Detail -> ToolActivityRow(
                                row.entries,
                                label = if (en) DETAIL_ROW_EN else DETAIL_ROW_TR,
                            )
                        }
                    }
                    // "N mesaj yüklendi" dipnotu kaldırıldı (tur-4 D): üst şeritteki
                    // sayı zaten oturumun gerçek mesaj sayısı; iki farklı sayı
                    // göstermek güven kırığıydı.
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

