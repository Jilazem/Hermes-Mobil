package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Takip listesinin TEK kaynağı.
 *
 * Sol çekmecedeki "Takip" sekmesi (compact) ile tam ekran Takip ekranı aynı
 * süzme/sıralama ve aynı satır çizimini kullanır; iki kopya liste kodu
 * birbirinden ayrı düzeltilip tutarsız kalmasın diye burada toplandı
 * (plan 2026-10-21 · ortak SessionListCore).
 */

/** Takip süzmesi: yalnız takip edilenler, sorgu başlık + önizlemede. */
fun followedRows(rows: List<DrawerRow>, query: String = ""): List<DrawerRow> =
    filterSessionRows(rows, query, followedOnly = true)

/**
 * Genel süzme. [followedOnly] çekmece "Takip" sekmesi ve Takip ekranı için,
 * [archivedOnly] çekmece "Arşiv" sekmesi için kullanılır; ikisi birden
 * verilirse kesişim döner.
 */
fun filterSessionRows(
    rows: List<DrawerRow>,
    query: String = "",
    followedOnly: Boolean = false,
    archivedOnly: Boolean = false,
): List<DrawerRow> {
    val q = query.trim()
    return rows.asSequence()
        .filter { !followedOnly || it.followed }
        .filter { !archivedOnly || it.archived }
        .filter { q.isBlank() || it.title.contains(q, true) || it.preview.contains(q, true) }
        .sortedWith(compareByDescending<DrawerRow> { it.working }.thenByDescending { it.epochSeconds })
        .toList()
}

/** Satır durum etiketi — çekmece, Takip ve modül listesi aynı metni gösterir. */
@Composable
fun sessionRowStatus(row: DrawerRow): String = when {
    row.liveSession?.isWaiting == true -> S.t2("Yanıt bekliyor", "Awaiting input")
    row.liveSession?.isStarting == true -> S.t2("Başlıyor", "Starting")
    row.working -> S.t2("Çalışıyor", "Working")
    row.liveSession != null -> S.t2("Boşta", "Idle")
    row.dot == "green" || row.dot == "yellow" -> S.t2("Açık", "Open")
    else -> S.t2("Geçmiş oturum", "Past session")
}

/**
 * Ortak oturum listesi.
 *
 * [compact] = çekmece içi: dar satır, tek satır önizleme, "göz at" düğmesi yok.
 * [compact] = false: tam ekran, iki satır önizleme, boş durumda "Oturumları aç".
 */
@Composable
fun SessionListPane(
    rows: List<DrawerRow>,
    query: String,
    onOpen: (DrawerRow) -> Unit,
    onToggleFollow: (DrawerRow) -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: String? = null,
    compact: Boolean = false,
    followedOnly: Boolean = true,
    archivedOnly: Boolean = false,
    onBrowse: (() -> Unit)? = null,
    emptyActionLabel: String? = null,
) {
    val visible = filterSessionRows(rows, query, followedOnly, archivedOnly)
    Column(modifier.fillMaxSize()) {
        error?.let {
            Text(
                it,
                color = HermesColors.Danger,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (loading && visible.isEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(18.dp))
        }
        if (visible.isEmpty() && !loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(if (compact) 20.dp else 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.StarOutline, null,
                        tint = HermesColors.TextFaint,
                        modifier = Modifier.size(if (compact) 28.dp else 40.dp),
                    )
                    Text(
                        when {
                            query.isNotBlank() -> S.t2("Eşleşen oturum yok", "No matching sessions")
                            archivedOnly -> S.t2("Arşivde oturum yok", "No archived sessions")
                            else -> S.t2("Henüz takip ettiğin oturum yok", "No followed sessions yet")
                        },
                        color = HermesColors.TextPrimary,
                        modifier = Modifier.padding(top = 12.dp),
                        style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    )
                    if (!compact && query.isBlank() && !archivedOnly) {
                        Text(
                            S.t2("Oturuma uzun basıp “Takip et” seç.", "Long press a session and choose Follow."),
                            color = HermesColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    val browse = onBrowse ?: return@Column
                    TextButton(onClick = browse) {
                        Text(emptyActionLabel ?: S.t2("Oturumları aç", "Browse sessions"))
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = if (compact) 10.dp else 18.dp, vertical = if (compact) 6.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 10.dp),
            ) {
                items(visible, key = { it.key }) { row ->
                    Row(
                        Modifier.fillMaxWidth()
                            .background(HermesColors.Surface, MaterialTheme.shapes.large)
                            .clickable { onOpen(row) }
                            .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.title,
                                color = HermesColors.TextPrimary,
                                fontWeight = FontWeight.Medium,
                                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                                maxLines = if (compact) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                sessionRowStatus(row),
                                color = if (row.working) HermesColors.Online else HermesColors.TextMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (row.preview.isNotBlank()) {
                                Text(
                                    row.preview,
                                    color = HermesColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = if (compact) 1 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (!compact && row.archived) {
                                Text(S.t2("Arşivde", "Archived"), color = HermesColors.TextFaint, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { onToggleFollow(row) }) {
                            Icon(
                                Icons.Default.Star,
                                S.t2("Takibi bırak", "Unfollow"),
                                tint = HermesColors.Midground,
                                modifier = Modifier.size(if (compact) 18.dp else 24.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
