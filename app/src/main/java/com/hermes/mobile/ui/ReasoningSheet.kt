package com.hermes.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Düşünce çabası çipleri — (etiket, `/reasoning` argümanı).
 *
 * "xhigh" ve "max" aynı ailenin uçları: panel beş gösterim + Maks = xhigh
 * olarak basıyor; gateway argümanı da `xhigh` kabul ediyor.
 */
val REASONING_LEVELS: List<Pair<String, String>> = listOf(
    "Kapalı" to "none",
    "Min" to "low",
    "Orta" to "medium",
    "Yüksek" to "high",
    "Maks" to "xhigh",
)

/**
 * "Düşünce panosu" — üst çubukta Psychology ikonuyla açılan ModalBottomSheet.
 *
 * Çipler [onLevel] üzerinden `/reasoning <seviye>` yazar; seçili çip sunucudan
 * argümansız `/reasoning` okumasıyla belirlenen [selected] değerdir — panel
 * kendi yerel tahminini UYGULAMAZ (SAF: sunucu ne diyor ise onu gösterir,
 * okumadan hiçbiri seçili görünmez).
 *
 * "Düşünürken canlı göster" anahtarı [showLiveThinking] ayarına yazılır;
 * canlı çizimin kendisi bu değere bağlıdır (ChatViewModel tarafında).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasoningSheet(
    /** Sunucudan okunan aktif çaba — null ise hiçbir çip seçili görünmez. */
    selected: String?,
    onLevel: (String) -> Unit,
    showLiveThinking: Boolean,
    onShowLiveThinking: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Background,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = HermesColors.Midground,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    S.t2("Düşünce panosu", "Reasoning panel"),
                    color = HermesColors.TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                S.t2("Çaba", "Effort"),
                color = HermesColors.TextFaint,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(6.dp))
            // 3+2 ızgara: telefon genişliğinde sığar, taşma yok.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REASONING_LEVELS.subList(0, 3).forEach { (label, level) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        FilterChip(
                            selected = selected == level,
                            onClick = { onLevel(level) },
                            label = { Text(label, color = HermesColors.TextPrimary, fontSize = 12.sp) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REASONING_LEVELS.subList(3, 5).forEach { (label, level) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        FilterChip(
                            selected = selected == level,
                            onClick = { onLevel(level) },
                            label = { Text(label, color = HermesColors.TextPrimary, fontSize = 12.sp) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        S.t2("Düşünürken canlı göster", "Show thinking live"),
                        color = HermesColors.TextPrimary,
                        fontSize = 14.sp,
                    )
                    Text(
                        S.t2(
                            "Açıkken model düşünürken son satırları ekranda izlersin",
                            "When on, the last lines of the model's reasoning scroll live on screen",
                        ),
                        color = HermesColors.TextMuted,
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = showLiveThinking,
                    onCheckedChange = onShowLiveThinking,
                )
            }
        }
    }
}
