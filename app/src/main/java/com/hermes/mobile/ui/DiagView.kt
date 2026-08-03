package com.hermes.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.DiagLog

/**
 * Tanılama kaydı görünümü.
 *
 * Amacı, uzaktaki bir telefonda ne olduğunu ekran görüntüsü göndermeden
 * anlatabilmek: kullanıcı "Paylaş"a basıp kaydı gönderiyor. Kayıt zaten
 * ayıklanmış olarak üretiliyor ([DiagLog.redact]), o yüzden paylaşmak güvenli.
 *
 * Varsayılan süzgeç **uyarı ve üstü**: normal akış kaydı (her soket açılışı,
 * her istek) listeyi doldurup asıl hatayı gizliyordu.
 */
@Composable
fun DiagView() {
    val context = LocalContext.current
    val all by DiagLog.entries.collectAsState()
    var onlyProblems by remember { mutableStateOf(true) }

    val shown = remember(all, onlyProblems) {
        if (onlyProblems) all.filter { it.level >= DiagLog.Level.WARN } else all
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Dört çip dar ekranda sığmıyor — "Temizle" kırpılıyordu. Yatay kaydırma
        // kırpmaktan iyi: hiçbir eylem erişilemez kalmıyor.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = onlyProblems,
                onClick = { onlyProblems = !onlyProblems },
                label = { Text(S.t2("Yalnız sorunlar", "Problems only")) },
            )
            AssistChip(
                onClick = { copyToClipboard(context, DiagLog.dump()) },
                label = { Text(S.t2("Kopyala", "Copy")) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.height(16.dp)) },
            )
            AssistChip(
                onClick = { shareText(context, DiagLog.dump()) },
                label = { Text(S.t2("Paylaş", "Share")) },
                leadingIcon = { Icon(Icons.Default.Share, null, Modifier.height(16.dp)) },
            )
            AssistChip(
                onClick = { DiagLog.clear() },
                label = { Text(S.t2("Temizle", "Clear")) },
                leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.height(16.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    labelColor = MaterialTheme.colorScheme.error,
                    leadingIconContentColor = MaterialTheme.colorScheme.error,
                ),
            )
        }

        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            Text(
                if (onlyProblems) {
                    S.t2(
                        "Kayıtlı sorun yok. Her şey yolunda demek — ya da " +
                            "süzgeci kapatıp normal akışa bakabilirsin.",
                        "No problems logged. That means things are fine — or " +
                            "turn off the filter to see the normal flow.",
                    )
                } else {
                    S.t2("Kayıt boş.", "Log is empty.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            S.t2("${shown.size} kayıt · en yeni üstte", "${shown.size} entries · newest first"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(shown, key = { "${it.at}-${it.tag}-${it.message.hashCode()}" }) { entry ->
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                entry.level.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorFor(entry.level),
                            )
                            Text(
                                entry.tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                entry.clock,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            entry.message,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun colorFor(level: DiagLog.Level): Color = when (level) {
    DiagLog.Level.CRASH, DiagLog.Level.ERROR -> MaterialTheme.colorScheme.error
    DiagLog.Level.WARN -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("hermes-diag", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Hermes Mobile — tanılama")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
