package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ui.theme.HermesColors

/** Hermes kartı — dolgun yüzey, ince kenarlık, 12dp köşe. */
@Composable
fun HermesCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(HermesColors.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, HermesColors.Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
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
        fontSize = 11.sp,
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
        deltaSec < 3_600 -> "${deltaSec / 60} " + tr("dk", "min")
        deltaSec < 86_400 -> "${deltaSec / 3_600} " + tr("sa", "h")
        else -> "${deltaSec / 86_400} " + tr("g", "d")
    }
}
