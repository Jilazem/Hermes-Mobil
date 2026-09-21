package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Hermes kartı — B4 bileşeni (TASARIM-RAPORU.md §3.3/§3.5 + tur18 FR-004):
 * Surface + radius-md (MaterialTheme.shapes.medium=10dp) + 1dp border +
 * iç boşluk 12dp (ritim sabiti) + min 48dp dokunma hedefi tabanı.
 * Kartlar arası 8dp çağıranın spacedBy'sında, ekran kenarı 16dp ekran
 * padding'indedir (HomeScreen/SettingsScreen L61/L152 — tur18'te 8'e sabitlendi).
 */
@Composable
fun HermesCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .background(HermesColors.SurfaceCard, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
            .padding(12.dp),
        content = content,
    )
}

/** Durum noktası — çevrimiçi / meşgul / çevrimdışı. */
@Composable
fun StatusDot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = HermesColors.TextMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        modifier = modifier,
    )
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return if (value >= 100 || i == 0) "${value.toInt()} ${units[i]}"
    else String.format("%.1f %s", value, units[i])
}

fun formatUptime(seconds: Double): String {
    val total = seconds.toLong()
    val days = total / 86_400
    val hours = (total % 86_400) / 3_600
    val minutes = (total % 3_600) / 60
    return when {
        days > 0 -> "${days}g ${hours}sa"
        hours > 0 -> "${hours}sa ${minutes}dk"
        else -> "${minutes}dk"
    }
}

fun formatRelative(epochSeconds: Double?): String {
    if (epochSeconds == null || epochSeconds <= 0) return "—"
    val deltaSec = (System.currentTimeMillis() / 1000.0 - epochSeconds).toLong()
    // `tr()` degil `S.t2` olmali derdik ama bu fonksiyon Compose disindan da
    // cagriliyor; `tr()` ayni secimi serviceLang uzerinden yapiyor.
    return when {
        deltaSec < 60 -> tr("az önce", "just now")
        deltaSec < 3_600 -> "${deltaSec / 60} " + tr("dk önce", "min ago")
        deltaSec < 86_400 -> "${deltaSec / 3_600} " + tr("sa önce", "h ago")
        deltaSec < 7 * 86_400 -> "${deltaSec / 86_400} " + tr("g önce", "d ago")
        // Bir haftadan eski oturumda göreli zaman bulanıklaşır; tarih daha okunur.
        else -> {
            val d = java.time.Instant.ofEpochSecond(epochSeconds.toLong())
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            "%02d.%02d".format(d.monthValue, d.dayOfMonth) +
                if (d.year != java.time.LocalDate.now().year)
                    ".${d.year}" else ""
        }
    }
}
