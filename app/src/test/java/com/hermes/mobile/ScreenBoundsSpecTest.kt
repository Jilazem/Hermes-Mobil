package com.hermes.mobile

import com.hermes.mobile.data.PhoneToolSpecs
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K1 madde 2 — `screenshot` araç tanımı PNG pikseli → ekran noktası
 * dönüşüm kuralını yazar; sonuçtaki `screen_bounds` alanı bu kurala bağlı.
 */
class ScreenBoundsSpecTest {

    @Test
    fun `screenshot tanimi donusum kuralini ve screen_bounds alanimi aciklar`() {
        val spec = PhoneToolSpecs.SPECS["screenshot"] ?: error("screenshot spec yok")
        val desc = spec["description"]!!.jsonPrimitive.content
        assertTrue(desc, desc.contains("screen_bounds"))
        assertTrue(desc, desc.contains("screen_bounds[2] - screen_bounds[0]"))
        assertTrue(desc, desc.contains("/ width"))
        assertTrue(desc, desc.contains("scale"))
        // Koordinat uzayı tap ile aynı olmalı — ajan karıştırmasın.
        assertTrue(desc, desc.contains("tap"))
    }
}