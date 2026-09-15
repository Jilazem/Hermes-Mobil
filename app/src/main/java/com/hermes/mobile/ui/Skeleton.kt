package com.hermes.mobile.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hermes.mobile.ui.theme.HermesColors

/**
 * İskelet (skeleton) yükleme yer tutucuları (KALAN-1).
 *
 * Tur-4'te boş DURUM ekranları vardı ama yüklenirken ekran bomboş kalıyordu:
 * sunucu yavaşken kullanıcı "hiç oturum yok" ile "henüz gelmedi"yi
 * ayırt edemiyordu. İskelet, içeriğin geleceğini söyler ve düzeni önceden
 * kurar (zıplama olmaz).
 *
 * Kural saf fonksiyonda: [skeletonRowCount] — yükleniyorsa ve elde hiç öğe
 * yoksa yer tutucu satır sayısı, aksi hâlde 0.
 */
fun skeletonRowCount(loading: Boolean, loadedItems: Int, placeholder: Int = 3): Int =
    if (loading && loadedItems <= 0) placeholder.coerceAtLeast(1) else 0

/** Sönüp yanan yer tutucu blok. */
@Composable
fun SkeletonBox(width: Dp?, height: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "iskelet")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "iskelet-alfa",
    )
    val base = modifier.alpha(alpha).height(height)
    androidx.compose.foundation.layout.Box(
        (if (width != null) base.width(width) else base.fillMaxWidth())
            .background(HermesColors.SurfaceDim, RoundedCornerShape(6.dp)),
    )
}

/** Oturum listesi iskeleti — kart anatomisini (nokta · başlık · saat · meta) taklit eder. */
@Composable
fun SessionListSkeleton(rows: Int = 3, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(rows) { i ->
            HermesCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(7.dp, 7.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        SkeletonBox(null, 12.dp)
                        Spacer(Modifier.height(7.dp))
                        // İlk satırın önizlemesi biraz daha kısa: göz "metin"
                        // desenini tanısın, düz çizgi gibi görünmesin.
                        SkeletonBox(if (i % 2 == 0) 220.dp else 160.dp, 10.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    SkeletonBox(34.dp, 10.dp)
                }
            }
        }
    }
}

/** Sohbet iskeleti — dönüşümlü kullanıcı/asistan balonu yer tutucusu. */
@Composable
fun ChatSkeleton(rows: Int = 4, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        repeat(rows) { i ->
            val mine = i % 2 == 1
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
            ) {
                SkeletonBox(if (mine) 190.dp else 240.dp, if (i % 3 == 0) 46.dp else 30.dp)
            }
        }
    }
}
