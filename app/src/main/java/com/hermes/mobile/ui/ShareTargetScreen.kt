package com.hermes.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.ui.theme.HermesColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Paylaşım hedefi seçim ekranı.
 *
 * WhatsApp'ın (veya başka bir uygulamanın) "Hermes'e ilet" niyeti geldiğinde
 * kullanıcıya hedefi sormak için açılır: üstte büyük "Yeni konu" butonu,
 * altında son 10 oturum (AppState.sessions → listSessions). Dokununca
 * [onPickSession] hedef oturumu döner; [onNewTopic] yeni konu açar.
 *
 * Dosya paylaşımında [fileNote] "ad (boyut)" biçiminde gösterilir — gerçek
 * yükleme HermesClient.uploadManagedFile ile seçilen hedefte yapılır
 * (POST /api/files/upload-stream; SAHTE upload yok).
 */
@Composable
fun ShareTargetScreen(
    textSnippet: String?,
    fileNote: String?,
    recentSessions: List<HermesSession>,
    onNewTopic: () -> Unit,
    onPickSession: (HermesSession) -> Unit,
    onCancel: () -> Unit,
) {
    val recent = recentSessions.filter { it.isActive }.take(10)

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                S.t2("Hermes'e iletmek için hedef seç", "Choose where to send"),
                color = HermesColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                S.t2("Vazgeç", "Cancel"),
                color = HermesColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable { onCancel() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        // Paylaşılan içeriği kısa önizleme olarak göster; kullanıcı hedefi
        // yanlış seçmesin.
        val preview = textSnippet?.trim().orEmpty()
        if (preview.isNotEmpty()) {
            SelectionContainer {
                Text(
                    preview,
                    color = HermesColors.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        // Dosya geldiğinde "ad (boyut)" notu — yükleme hedef seçildikten
        // sonra gerçek uca POST edilir.
        if (!fileNote.isNullOrBlank()) {
            Text(
                "📎 $fileNote",
                color = HermesColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))

        // ── Yeni konu ──────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable { onNewTopic() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "+ ${S.t2("Yeni konu", "New topic")}",
                color = HermesColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = HermesColors.Border)

        // ── Son 10 oturum ──────────────────────────────────────────────
        Text(
            S.t2("Son oturumlar", "Recent sessions"),
            color = HermesColors.TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 4.dp),
        )

        if (recent.isEmpty()) {
            Text(
                S.t2("Henüz aktif oturum yok.", "No active sessions yet."),
                color = HermesColors.TextFaint,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(recent, key = { it.id }) { s ->
                    ShareTargetRow(session = s, onClick = { onPickSession(s) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** Tek bir oturum satırı: başlık + model + zaman damgası. */
@Composable
private fun ShareTargetRow(session: HermesSession, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()) }
    val whenLabel = session.startedAt?.let { fmt.format(Date((it * 1000).toLong())) } ?: ""
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                session.title,
                color = HermesColors.TextPrimary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOf(session.model.orEmpty(), whenLabel).filter { it.isNotBlank() }
            if (meta.isNotEmpty()) {
                Text(
                    meta.joinToString(" · "),
                    color = HermesColors.TextFaint,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
    }
}