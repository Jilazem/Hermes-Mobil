package com.hermes.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Tur22 madde-4 — boş durum ekranı (Grok/ChatGPT/Claude kalıbı):
 * TEK cümle + ikon + (varsa) öneri aksiyonu. Uydurma veri gösterilmez.
 *
 * Ekran okuyucu: mesaj metni okunur, ikon dekoratiftir (boş etiket) — çift
 * okuma yok, aksiyon düğmesi ayrı ve erişilebilir (semantics blok YUTULMAZ).
 *
 * Kullanım yerleri (tur22):
 *  - Çekmece: oturum yok → [emptyStateNoSessions]
 *  - Çekmece arama: sonuç yok → [emptyStateNoResults] (aksiyon: aramayı temizle)
 *  - Çekmece arşiv sekmesi boş → [emptyStateArchiveEmpty]
 *  - Sohbet: bağlantı yok → [emptyStateNoConnection] (aksiyon: sunucu ekle)
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = HermesColors.TextFaint,
            modifier = Modifier.size(28.dp),
        )
        Text(
            message,
            color = HermesColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = HermesColors.Midground, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// — tur22 boş-durum metinleri (TEK cümle kuralı; TR + EN). Compose'suz `tr`:
// birim testle kilitlenir (D-11: gösterge testte, D-05: aynı veri tüm yollarda).

fun emptyStateNoSessions(): String =
    tr("Henüz oturum yok — yeni sohbet ile başla.", "No sessions yet — start a new chat.")

fun emptyStateNoResults(query: String): String =
    tr("\"$query\" için sonuç yok.", "No results for \"$query\".")

fun emptyStateArchiveEmpty(): String =
    tr("Arşiv boş — kaydırarak arşivlediğin oturumlar burada.", "Archive is empty — sessions you swipe away land here.")

fun emptyStateNoConnection(): String =
    tr("Bağlantı yok — bir sunucu ekle ya da ağın kontrol et.", "No connection — add a server or check your network.")

/** Boş-durum ikonları (madde-4'ün 4 sahnesi) — testten erişilir, liste sabit. */
object EmptyStateIcons {
    val NoSessions: ImageVector get() = Icons.Default.ChatBubbleOutline
    val NoResults: ImageVector get() = Icons.Default.SearchOff
    val ArchiveEmpty: ImageVector get() = Icons.Default.Archive
    val NoConnection: ImageVector get() = Icons.Default.CloudOff
    val AddServer: ImageVector get() = Icons.Default.Add
}
