package com.hermes.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Modül şeması: kapalı tür kümesi, WEB için http(s) zorunluluğu, aynı
 * kimlik/ad reddi ve bozuk kayıtların listeden düşmesi.
 */
class ModuleSchemaTest {

    private fun mod(
        id: String = "m1",
        name: String = "Formlar",
        kind: ModuleKind = ModuleKind.FORMS,
        url: String = "",
        icon: String = "🧩",
    ) = AppModule(id = id, name = name, icon = icon, kind = kind, url = url)

    @Test
    fun gecerliModulHatasiz(): Unit {
        assertTrue(ModuleSchema.validate(mod()).isEmpty())
    }

    @Test
    fun webModuluAdresIster(): Unit {
        assertTrue(ModuleSchema.validate(mod(kind = ModuleKind.WEB)).any { it.contains("http") })
        assertTrue(ModuleSchema.validate(mod(kind = ModuleKind.WEB, url = "https://ornek.test")).isEmpty())
    }

    @Test
    fun ayniAdReddedilir(): Unit {
        val existing = listOf(mod(id = "a", name = "Pano"))
        assertTrue(ModuleSchema.validate(mod(id = "b", name = "pano"), existing).any { it.contains("zaten var") })
    }

    @Test
    fun ayniKimlikReddedilir(): Unit {
        val existing = listOf(mod(id = "a", name = "Pano"))
        assertTrue(ModuleSchema.validate(mod(id = "a", name = "Başka"), existing).any { it.contains("Bu kimlikte") })
    }

    @Test
    fun adUzunluguVeSimgeSinirli(): Unit {
        assertTrue(ModuleSchema.validate(mod(name = "x".repeat(ModuleSchema.MAX_NAME + 1))).any { it.contains("en çok") })
        assertTrue(ModuleSchema.validate(mod(icon = "🧩🧩🧩")).any { it.contains("Simge") })
    }

    @Test
    fun bilinmeyenTurNullDoner(): Unit {
        assertNull(ModuleSchema.parse("""{"id":"x","name":"X","kind":"MAGIC"}"""))
    }

    @Test
    fun jsonGidisDonus(): Unit {
        val m = mod(id = "w", name = "Drive", kind = ModuleKind.WEB, url = "https://drive.google.com")
        assertEquals(m, ModuleSchema.parse(ModuleSchema.toJson(m)))
    }

    @Test
    fun parseListBozukKaydiAtlar(): Unit {
        val good = ModuleSchema.toJson(mod(id = "ok", name = "Tamam"))
        val result = ModuleSchema.parseList("[$good, {\"id\":\"bad\",\"name\":\"\",\"kind\":\"FORMS\"}]")
        assertEquals(1, result.modules.size)
        assertEquals("ok", result.modules.first().id)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun parseListTekrarEdenKimligiDusurur(): Unit {
        val one = ModuleSchema.toJson(mod(id = "dup", name = "Tek"))
        val result = ModuleSchema.parseList("[$one, $one]")
        assertEquals(1, result.modules.size)
        assertTrue(result.errors.any { it.contains("tekrar ediyor") })
    }

    @Test
    fun bosDosyaBosSonuc(): Unit {
        val result = ModuleSchema.parseList("")
        assertTrue(result.errors.isEmpty())
        assertTrue(result.modules.isEmpty())
    }

    @Test
    fun hazirModullerGecerli(): Unit {
        val builtIns = BuiltInModules.all()
        assertTrue(builtIns.isNotEmpty())
        builtIns.forEach { m ->
            assertTrue("${m.id}: ${ModuleSchema.validate(m)}", ModuleSchema.validate(m).isEmpty())
            assertNotNull(ModuleSchema.parse(ModuleSchema.toJson(m)))
        }
    }
}
