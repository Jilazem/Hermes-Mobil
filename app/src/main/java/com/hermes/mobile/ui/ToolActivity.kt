package com.hermes.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

/**
 * Tek bir araç çağrısının görüntülenecek özeti.
 *
 * Hem canlı sohbetteki `ChatItem.Tool` hem oturum dökümündeki `role=tool`
 * mesajları buna dönüştürülür; böylece iki ekran aynı katlanmış görünümü paylaşır.
 */
data class ToolEntry(
    val name: String,
    val state: ToolEntryState,
    /** Argümanlar ya da sonuç gövdesi — genişletilince gösterilir. */
    val detail: String? = null,
)

enum class ToolEntryState { Running, Done, Failed }

/**
 * Ardışık araç çağrılarını **tek satırda** toplar; dokununca detay açılır.
 *
 * Önceki davranış her çağrıyı tam genişlikte ayrı bir karta koyuyordu — sekiz
 * `execute_code` arka arkaya gelince ekran araç kartlarından ibaret kalıyor,
 * asıl yanıt kayboluyordu. Artık satır yerinde güncelleniyor: çalışırken o anki
 * aracın adı, bitince "N araç" özeti.
 */
@Composable
fun ToolActivityRow(entries: List<ToolEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }

    val running = entries.filter { it.state == ToolEntryState.Running }
    val failed = entries.count { it.state == ToolEntryState.Failed }
    val isBusy = running.isNotEmpty()

    // Özet: çalışırken o anki araç, bittiğinde en sık kullanılanlardan kısa liste.
    val summary = when {
        isBusy -> running.last().name
        entries.size == 1 -> entries.first().name
        else -> entries
            .groupingBy { it.name }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(2)
            .joinToString(", ") { (name, count) ->
                if (count > 1) "$name ×$count" else name
            }
    }

    val accent = when {
        isBusy -> HermesColors.Busy
        failed > 0 -> HermesColors.Danger
        else -> HermesColors.Online
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
            .border(1.dp, HermesColors.Border, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = accent,
                )
            } else {
                Text(
                    if (failed > 0) "✕" else "✓",
                    color = accent,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))

            if (entries.size > 1) {
                Text(
                    "${entries.size} araç",
                    color = HermesColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(7.dp))
            }

            Text(
                summary,
                style = MonoTextStyle,
                color = HermesColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (failed > 0 && !expanded) {
                Text("$failed hata", color = HermesColors.Danger, fontSize = 10.sp)
                Spacer(Modifier.width(6.dp))
            }

            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Daralt" else "Detay",
                tint = HermesColors.TextFaint,
                modifier = Modifier.size(16.dp),
            )
        }

        AnimatedVisibility(expanded) {
            Column(
                Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                entries.forEachIndexed { index, entry ->
                    ToolEntryDetail(index + 1, entry)
                }
            }
        }
    }
}

@Composable
private fun ToolEntryDetail(order: Int, entry: ToolEntry) {
    var open by remember { mutableStateOf(false) }
    val hasDetail = !entry.detail.isNullOrBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.Background, RoundedCornerShape(6.dp))
            .then(if (hasDetail) Modifier.clickable { open = !open } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$order.",
                color = HermesColors.TextFaint,
                fontSize = 10.sp,
                modifier = Modifier.width(20.dp),
            )
            Text(
                when (entry.state) {
                    ToolEntryState.Running -> "•"
                    ToolEntryState.Done -> "✓"
                    ToolEntryState.Failed -> "✕"
                },
                color = when (entry.state) {
                    ToolEntryState.Running -> HermesColors.Busy
                    ToolEntryState.Done -> HermesColors.Online
                    ToolEntryState.Failed -> HermesColors.Danger
                },
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                entry.name,
                style = MonoTextStyle,
                color = HermesColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (hasDetail) {
                Text(
                    "${entry.detail!!.length} krkt",
                    color = HermesColors.TextFaint,
                    fontSize = 9.sp,
                )
            }
        }

        AnimatedVisibility(open && hasDetail) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.detail.orEmpty().take(4_000),
                    style = MonoTextStyle,
                    color = HermesColors.TextMuted,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
