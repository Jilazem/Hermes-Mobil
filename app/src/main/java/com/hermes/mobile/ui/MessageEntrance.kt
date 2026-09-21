package com.hermes.mobile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Tur22 madde-1 — mesaj giriş animasyonu (fade + hafif yukarı kayma).
 *
 * Neden AnimatedVisibility DEĞİL: AnimatedVisibility görünmezken yüksekliği 0
 * olur; LazyColumn'da satır sonradan açılınca YÜKSEKLİK değişir ve
 * "ilk görünür öğeye göre konum" kuralı yüzünden liste yukarı zıplar (tur-14
 * kaydırma notu, D-05 ailesi: aynı hatanın kardeş yolu). Burada içerik hep
 * bestelenir; yalnız alpha/translationY (graphicsLayer) animasyonlanır —
 * layout'a dokunmayan giriş, topuklama yok (spec 1).
 *
 * Yalnız GİRİŞTE çalışır: anahtar stable (row.key) olduğu için mevcut satırlar
 * yeniden animasyon YAPMAZ; yeni eklenen satır bir kere açılır.
 *
 * reduced-motion: süreler 0 — satır ilk karede tam görünür (spec 6).
 */
@Composable
fun Modifier.messageEntrance(stableKey: Any, reduced: Boolean = LocalReducedMotion.current): Modifier {
    var shown by remember(stableKey) { mutableStateOf(false) }
    LaunchedEffect(stableKey) { shown = true }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (shown) {
            HermesMotion.tweenSpec(HermesMotion.SLOW_MS, reduced)
        } else {
            tween(0)
        },
        label = "mesaj-giris-alfa",
    )
    val slidePx = with(LocalDensity.current) { 20.dp.toPx() }
    val dy by animateFloatAsState(
        targetValue = if (shown) 0f else slidePx,
        animationSpec = if (shown) {
            HermesMotion.tweenSpec(HermesMotion.SLOW_MS, reduced, HermesMotion.StandardEasing)
        } else {
            tween(0)
        },
        label = "mesaj-giris-kayma",
    )
    return alpha(alpha).graphicsLayer { translationY = dy }
}

// Not (tur22 madde-1, 3. halka — sohbet değişimi): oturum değişince her
// satırın key'i değiştiği için messageEntrance TÜM satırlarda aynı anda
// tetiklenir → liste bütün olarak yumuşak girer (ayrı container crossfade
// gerekmez; çifte alfa sönümü yerine tek kaynak).
