package com.hermes.mobile.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Tur22 — merkezi animasyon yardımcıları.
 *
 * 1) Tüm süre/easing değerleri TEK yerden ([HermesMotion]) ayarlanır; ekran
 *    dosyaları hardcode `tween(120)` gibi değerler taşımaz (tek yerden ayar,
 *    tur22 plan-1).
 * 2) `prefers-reduced-motion` karşılığı: Android'de sistem animasyon ölçeği
 *    (Ayarlar → Animasyon ölçeği = 0). [prefersReducedMotionOf] bunu cihazdan
 *    okur; HermesTheme tek noktadan [LocalReducedMotion] olarak sağlar. true
 *    iken geçişler EnterTransition.None/ExitTransition.None olur, süreler
 *    sıfırlanır — içerik anında ve TAM görünür (tur22 spec 6: "animasyonsuz
 *    ama içerik tam").
 * 3) Test edilebilirlik: ölçek okuma ve süre hesabı SAF fonksiyonlarda
 *    ([prefersReducedMotionOf], [HermesMotion.specMs]) — Compose'a girmeden
 *    birim testle kilitlenir (tur22'nin tek Compose-test altyapısı YOK; bu
 *    yüzden karar mantığı saf katmanda, D-11 dersi: söz = test).
 */

/** Cihaz animasyon ölçeğinden reduced-motion kararı (saf, taklit edilebilir). */
fun prefersReducedMotionOf(scaledDurationFactor: Float): Boolean =
    scaledDurationFactor <= 0f

/**
 * Cihazın animasyon ölçeklerini okur (Ayarlar → Animasyon ölçeği üç anahtarı
 * birden yazar: window, transition, animator). ULAŞILAMAYAN anahtar 1f sayılır
 * (kısıtlı profil → animasyon normal). Ölçek 0 olan cihaz = reduced-motion.
 */
@Composable
fun rememberPrefersReducedMotion(): Boolean {
    val context = LocalContext.current
    val factor = remember(context) {
        fun read(key: String): Float = runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, key)
        }.getOrDefault(1f)
        minOf(
            read(android.provider.Settings.Global.WINDOW_ANIMATION_SCALE),
            read(android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE),
            read(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE),
        )
    }
    return prefersReducedMotionOf(factor)
}

/** HermesTheme'in doldurduğu, ekranların okuduğu reduced-motion bayrağı. */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Tur22 — animasyon token'ları (saf; kompozisyon gerektirmez). Ekranlar
 * geçişi yalnız buradan üretir ve [LocalReducedMotion] bayrağını geçirir.
 */
@Stable
object HermesMotion {

    /** Hızlı geri bildirim (mikro-etkileşim): dokunma, durum geçişi. */
    const val FAST_MS = 120

    /** Standart geçiş: crossfade, renk/durum animasyonu (spec: 150–200ms bandı). */
    const val STANDARD_MS = 180

    /** Yavaşçası: giriş animasyonları (mesaj girişi). */
    const val SLOW_MS = 240

    /** Yavaş sönüş: SpeedLine'ın akış-bitiş sönüşü (tur-13 600ms'ten taşındı). */
    const val FADE_OUT_SLOW_MS = 600

    /** İskelet nabız periyodu. */
    const val PULSE_MS = 900

    /** Shimmer (SpeedLine) devri — tur-13'ten taşındı. */
    const val SHIMMER_MS = 1100

    /** İskelet nabız alfa aralığı (tur-4 değerleri korunur). */
    const val PULSE_ALPHA_MIN = 0.35f
    const val PULSE_ALPHA_MAX = 0.75f

    /** reduced-motion'da nabız durur; sabit alfa (içerik yine "yükleniyor" okunur). */
    const val PULSE_ALPHA_STATIC = 0.55f

    /**
     * Saf süre hesabı: reduced-motion açıkken her geçiş 0 ms — öğe anında
     * hedef değerinde; içerik KAYBOLMAZ (giriş animasyonunun "0'a başla"
     * tuzağına düşülmez).
     */
    fun specMs(baseMs: Int, reduced: Boolean): Int = if (reduced) 0 else baseMs

    /** Yumuşak standart easing (Material "standard", cubic-bezier 0.2,0,0,1). */
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun tweenSpec(
        ms: Int,
        reduced: Boolean,
        easing: Easing = StandardEasing,
    ): androidx.compose.animation.core.TweenSpec<Float> =
        tween(
            durationMillis = specMs(ms, reduced),
            easing = if (specMs(ms, reduced) == 0) LinearEasing else easing,
        )

    /** Çekmece: spring — hafif sönümlemeli (ChatGPT çekmecesi gibi yumuşaksın). */
    fun <T : Any> springSpec(reduced: Boolean): AnimationSpec<T> =
        if (reduced) tween(0) else spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        )

    /**
     * Mesaj girişi: fade + hafif yukarı kayma. Yalnız GİRİŞTE çalışır
     * (AnimatedContent/enter — yer değiştirme animasyonu KAPALI), böylece
     * yeni mesaj eklenince listedeki eski satırlar topuklamaz (spec 1).
     */
    fun messageEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None
        else fadeIn(tween(SLOW_MS, easing = StandardEasing)) +
            androidx.compose.animation.slideInVertically(
                animationSpec = tween(SLOW_MS, easing = StandardEasing),
                initialOffsetY = { it / 10 },
            )

    /** Sohbet değişimi / iskelet→dolu geçişi: ölçülü fade (crossfade bileşeni). */
    fun fadeSwap(reduced: Boolean) = fadeIn(HermesMotion.tweenSpec(STANDARD_MS, reduced))

    /**
     * İskelet nabız alfası. [reduced] true iken yanıp sönmez, sabit
     * [PULSE_ALPHA_STATIC] döner — ReducedMotion açıkken ekranda hareket
     * kalmaz, içerik tam görünür kalır.
     */
    @Composable
    fun pulseAlpha(reduced: Boolean = LocalReducedMotion.current): Float {
        if (reduced || LocalInspectionMode.current) return PULSE_ALPHA_STATIC
        val transition = rememberInfiniteTransition(label = "iskelet")
        val alpha by transition.animateFloat(
            initialValue = PULSE_ALPHA_MIN,
            targetValue = PULSE_ALPHA_MAX,
            animationSpec = infiniteRepeatable(
                tween(PULSE_MS, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "iskelet-alfa",
        )
        return alpha
    }

    /**
     * Shimmer kayması (SpeedLine): 0→1 döngü; reduced-motion'da 0 (durur,
     * çizgi tek ton kalır — satır zaten donmuşta duruyordu).
     */
    @Composable
    fun shimmerShift(reduced: Boolean = LocalReducedMotion.current, frozen: Boolean = false): Float {
        if (reduced || frozen || LocalInspectionMode.current) return 0f
        val transition = rememberInfiniteTransition(label = "speed-shimmer")
        val shift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(SHIMMER_MS, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "speed-shimmer-shift",
        )
        return shift
    }
}
