package com.hermes.mobile

import com.hermes.mobile.ui.HermesMotion
import com.hermes.mobile.ui.prefersReducedMotionOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur22 madde-1/6 — merkezi animasyon token'ları ve reduced-motion kararı.
 *
 * Compose-test altyapısı yok (tur22 kararı: saf katman testlenir; görsel
 * kanıt emülatör ekran görüntüsüyle verilir — RAPOR eşleme tablosu).
 */
class MotionTur22Test {

    @Test fun `animasyon ölçeği sıfır ise reduced-motion açık`() {
        assertTrue(prefersReducedMotionOf(0f))
        assertTrue(prefersReducedMotionOf(-1f))
    }

    @Test fun `ölçek 1 veya aralıksa reduced-motion kapalı`() {
        assertFalse(prefersReducedMotionOf(1f))
        assertFalse(prefersReducedMotionOf(0.5f))
    }

    @Test fun `reduced-motion kapalıyken süreler token değerinde kalır`() {
        assertEquals(HermesMotion.FAST_MS, HermesMotion.specMs(HermesMotion.FAST_MS, reduced = false))
        assertEquals(HermesMotion.STANDARD_MS, HermesMotion.specMs(HermesMotion.STANDARD_MS, reduced = false))
        assertEquals(HermesMotion.SLOW_MS, HermesMotion.specMs(HermesMotion.SLOW_MS, reduced = false))
    }

    @Test fun `reduced-motion açıkken her süre sıfıra iner — içerik anında görünür`() {
        assertEquals(0, HermesMotion.specMs(HermesMotion.FAST_MS, reduced = true))
        assertEquals(0, HermesMotion.specMs(HermesMotion.SLOW_MS, reduced = true))
        assertEquals(0, HermesMotion.specMs(HermesMotion.FADE_OUT_SLOW_MS, reduced = true))
    }

    @Test fun `crossfade süresi spec bandında 150-200ms`() {
        assertTrue(
            "STANDARD_MS ${HermesMotion.STANDARD_MS} 150-200 bandında kalmalı",
            HermesMotion.STANDARD_MS in 150..200,
        )
    }

    @Test fun `tweenSpec reduced ile 0ms ve LineerEasing döndürür (patlamaz)`() {
        // Kilitlenen davranış: 0ms tween üretilebilir olmalı (Compose 0ms tween'e izin verir).
        val spec0 = HermesMotion.tweenSpec(HermesMotion.STANDARD_MS, reduced = true)
        val spec1 = HermesMotion.tweenSpec(HermesMotion.STANDARD_MS, reduced = false)
        assertEquals(HermesMotion.STANDARD_MS, spec1.durationMillis)
        assertEquals(0, (spec0 as androidx.compose.animation.core.TweenSpec<Float>).durationMillis)
    }

    @Test fun `springSpec reduced ile tween(0) olur`() {
        val spec = HermesMotion.springSpec<Float>(reduced = true)
        assertTrue(spec is androidx.compose.animation.core.TweenSpec<*>)
        assertEquals(0, (spec as androidx.compose.animation.core.TweenSpec<Float>).durationMillis)
    }

    @Test fun `nabız sabit alfası aralık içinde — reduced'ta statik değer tanımlı`() {
        assertTrue(HermesMotion.PULSE_ALPHA_STATIC in
            HermesMotion.PULSE_ALPHA_MIN..HermesMotion.PULSE_ALPHA_MAX)
    }
}
