package com.hermes.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.StreamMeter
import com.hermes.mobile.ui.theme.HermesColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Tur-19 FR-003 — ekran altı tek satır eşzamanlılık istatistik şeridi.
 *
 * Sol: son ~60 sn token/s sparkline'ı (Compose Canvas). Orta: "N aktif ajan"
 * — Tur-16 durum noktalarıyla AYNI yargıyla sayılır ([activeAgentCount]).
 * Sağ: "≈Xt/s" sayısal (pencere ortalaması, [sparkRate]).
 *
 * Ölçüm dürüstlüğü (spec): örnek YOKSA grafik düz gri çizgi + "—" çizilir;
 * sıfır doldurma, interpolasyon, tahmin YOK. Örnekler yalnız gerçek
 * [StreamMeter] pencere snapshot'larından, 1 sn'de bir örnekle toplanır —
 * akış yokken listeye hiçbir şey eklenmez (pencere donar).
 *
 * Satıra dokununca açılan mini panel: oturum bazlı tek satırlar. Token/s
 * ölçümü yalnız AKAN sohbette mevcuttur — diğer oturumlar "—" gösterir.
 */
@Composable
fun SessionStatsStrip(
    rows: List<DrawerRow>,
    speed: StateFlow<StreamMeter.Snapshot?>,
    decimalSeparator: Char = '.',
) {
    // 1 sn'lik zamanlayıcı YALNIZ örnek toplar — akış boşsa liste büyümaz,
    // interpolasyon olmaz (FR-003 "veri YOKSA uydurma" yasağı).
    val samples: SnapshotStateList<Double> = remember { mutableStateListOf() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            val s = speed.value
            if (s != null && s.active && !s.finished) {
                samples.add(s.tokensPerSecond)
                while (samples.size > 60) samples.removeAt(0)
            }
        }
    }

    val active = activeAgentCount(rows)
    val rate = sparkRate(sparkWindow(samples.toList()))
    var expanded by remember { mutableStateOf(false) }
    // S.t2 @Composable — semantics lambda'sı İÇİNDE çağrılamaz (D: kompozisyon
    // sınırı); etiket önce çözülüp closure'a sabitlenir.
    val chartDesc = S.t2("Son 60 sn token hızı grafiği", "Last 60s token rate chart")

    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.Surface)
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SparkCanvas(
                samples = samples.toList(),
                modifier = Modifier
                    .size(width = 56.dp, height = 18.dp)
                    .semantics { contentDescription = chartDesc },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                S.t2("$active aktif ajan", "$active active agents"),
                color = if (active > 0) HermesColors.TextPrimary else HermesColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "≈${rateLabel(rate, decimalSeparator)}t/s",
                color = if (rate != null) HermesColors.Midground else HermesColors.TextFaint,
                fontSize = 12.sp,
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = S.t2("Oturum dökümü", "Session breakdown"),
                tint = HermesColors.TextFaint,
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            val shown = rows.filter { it.working || (it.dot.isNotEmpty() && it.dot != "grey") }
            if (shown.isEmpty()) {
                Text(
                    S.t2("Şu an aktif oturum yok.", "No active sessions."),
                    color = HermesColors.TextFaint,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            shown.forEach { row ->
                // Token/s yalnız akan sohbette ölçülür; ötesi "—" (uydurma yok).
                val label = if (row.current) rateLabel(rate, decimalSeparator) else "—"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (row.dot) {
                        "green" -> Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(HermesColors.Online))
                        "yellow" -> Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(HermesColors.Busy))
                        "grey" -> Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(HermesColors.Offline))
                        else -> Box(Modifier.size(6.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.title,
                        color = HermesColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(label, color = HermesColors.TextFaint, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Sparkline: son 60 örnek, min–max aralığına normalize poligon çizgi.
 * Örnek yoksa kesikli gri düz çizgi ("veri yok"un dürüst gösterimi).
 * Renkler tur-17 token setinden ([HermesColors]); sabit kod hex YOK.
 */
@Composable
private fun SparkCanvas(samples: List<Double>, modifier: Modifier = Modifier) {
    val line = HermesColors.Midground
    val empty = HermesColors.TextFaint
    Canvas(modifier) {
        if (samples.isEmpty()) {
            // DashedEffect API'si bu compose sürümünde yok — 3 kısa çizgiyle
            // elle kesikli "veri yok" çizgisi (aynı görsel anlam, sıfır bağımlılık).
            val y = size.height / 2f
            val seg = size.width / 5f
            var x = 0f
            while (x + seg <= size.width + 0.5f) {
                drawLine(
                    color = empty.copy(alpha = 0.7f),
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(x + seg * 0.6f, y),
                    strokeWidth = 1.dp.toPx(),
                )
                x += seg
            }
            return@Canvas
        }
        val minV = samples.min()
        val maxV = samples.max()
        val span = (maxV - minV).coerceAtLeast(1e-9)
        val pad = size.height * 0.1f
        val usable = size.height - 2 * pad
        val stepX = if (samples.size > 1) size.width / (samples.size - 1) else size.width
        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = if (samples.size > 1) (i * stepX).coerceAtMost(size.width) else size.width / 2f
            val y = pad + usable * (1f - ((v - minV) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 1.5.dp.toPx()))
    }
}
