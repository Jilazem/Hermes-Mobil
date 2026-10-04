package com.hermes.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.liveThinkingTail

/**
 * TUR-29A madde 2 — yeniden kullanılabilir "Düşünüyor…" bloğu.
 *
 * ChatScreen'deki satır içi düşünme bloğunun taşınmış hâli: canlıyken açık
 * durur, son 3-4 dolu satırı canlı kuyrukla gösterir; bitince katlanır
 * (genişletme durumunu çağıran korunur). Aynı desen MessageViews'teki geçmiş
 * akıl yürütme satırında da kullanılır — tek kaynak, tek davranış.
 *
 * @param text        Ham düşünme metni (canlıyken kuyruk hesaplanır).
 * @param live        Metin hâlâ akıyor mu? true: açık + kuyruk + imleç.
 * @param stateKey    Genişletme durumunun hatırlanma anahtarı; sohbet satırı
 *                    item.key'i geçirir (mesaj başına durum), statik kullanım
 *                    için null bırakılır (yaşam döngüsü boyunca bir kez kurulur).
 */
@Composable
fun ThinkingBlock(
    text: String,
    live: Boolean,
    stateKey: Any? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(stateKey) {
        // Canlı (hâlâ akan) blok varsayılanı AÇIK: son satırları izlemek için;
        // biten blok kapanır (katlanır davranış).
        mutableStateOf(live)
    }
    // Canlı kuyruk: metin uzadıkça her yeniden çizimde son dolu satırlar
    // yeniden hesaplanır — LazyColumn kaydırması değil, içerik kendini günceller
    // (4 satır, ucu ' ▌' ile işaretli).
    val liveTail = liveThinkingTail(text)
    Column(
        modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // Canlı akışta "Düşünüyor…", tarih içeriğinde "Düşünme" — TR.
                S.t2(if (live) "Düşünüyor" else "Düşünme", "Thinking"),
                color = HermesColors.TextFaint,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = HermesColors.TextFaint,
                modifier = Modifier.width(16.dp),
            )
        }
        if (live) {
            // Canlı görünüm: açık blok + son 3-4 dolu satır + sonda imleç.
            // Kullanıcı en alttayken LazyColumn kendiliğinden izler.
            val shown = liveTail.ifEmpty { "…" }
            Text(
                buildString {
                    if (text.length > liveTail.length) {
                        append("…\n")
                    }
                    append(shown)
                    append(" ▌")
                },
                color = HermesColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            // Katlanabilir tarih: mevcut davranış aynen.
            AnimatedVisibility(expanded) {
                Text(
                    text,
                    color = HermesColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
