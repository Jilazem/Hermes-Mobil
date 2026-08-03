package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.HermesProfile
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

/**
 * Profil seçici.
 *
 * Hermes profilleri yalnız bir ad değil: her biri kendi
 * `~/.hermes/profiles/<ad>` evinde yaşıyor ve kendi **SOUL.md'si (sistem
 * promptu)**, kendi beceri kümesi, kendi `.env`i var. Yani profil değiştirmek
 * ajanın kişiliğini, uzmanlığını ve araçlarını birlikte değiştiriyor —
 * uygulamada ayrı bir "prompt kaydı" tutmaya gerek yok.
 *
 * Seçim iki yere birden uygulanır:
 *   · `POST /api/profiles/active` — yönetim yüzeyi (listeler, ayarlar)
 *   · `session.create { profile }` — yeni sohbet o profilin evinde açılır
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileSheet(
    profiles: List<HermesProfile>,
    activeName: String,
    loading: Boolean,
    onSelect: (HermesProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HermesColors.Background,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).heightIn(max = 580.dp)) {
            Text(
                "Profil",
                color = HermesColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Her profilin kendi promptu, becerileri ve ayarları var",
                color = HermesColors.TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))

            if (loading) {
                Text("Yükleniyor…", color = HermesColors.TextMuted, fontSize = 13.sp)
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(profiles, key = { it.name }) { profile ->
                    val selected = profile.name == activeName
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                if (selected) HermesColors.Surface else HermesColors.SurfaceDim,
                                RoundedCornerShape(10.dp),
                            )
                            .border(
                                if (selected) 2.dp else 1.dp,
                                if (selected) HermesColors.Midground else HermesColors.Border,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelect(profile) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    profile.name,
                                    color = if (selected) HermesColors.Midground
                                    else HermesColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (profile.isDefault) {
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        "varsayılan",
                                        color = HermesColors.TextFaint,
                                        fontSize = 9.sp,
                                    )
                                }
                                if (profile.gatewayRunning) {
                                    Spacer(Modifier.width(7.dp))
                                    StatusDot(HermesColors.Online, size = 6)
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                profile.description.ifBlank {
                                    "${profile.skillCount} beceri" +
                                        (profile.model?.let { " · $it" } ?: "")
                                },
                                style = MonoTextStyle,
                                color = HermesColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = HermesColors.Online,
                                modifier = Modifier.width(17.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
        }
    }
}
