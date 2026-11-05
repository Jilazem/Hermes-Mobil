package com.hermes.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Form şeması: geçersiz şablon reddedilir (kısmen kabul yok), JSON gidiş-dönüş
 * kayıpsız, bozuk dosya çökmez; değer doğrulaması zorunlu/sayı/tarih/seçenek
 * kurallarını uygular.
 */
class FormSchemaTest {

    private fun field(
        id: String = "f1",
        type: FormFieldType = FormFieldType.TEXT,
        label: String = "Alan",
        required: Boolean = false,
        options: List<String> = emptyList(),
    ) = FormField(id = id, type = type, label = label, required = required, options = options)

    private fun template(
        id: String = "t1",
        name: String = "Şablon",
        fields: List<FormField> = listOf(field()),
    ) = FormTemplate(id = id, name = name, fields = fields)

    // ── Şema doğrulama ───────────────────────────────

    @Test
    fun gecerliSablonHatasiz(): Unit {
        assertTrue(FormSchema.validate(template()).isEmpty())
    }

    @Test
    fun bosAlanListesiReddedilir(): Unit {
        val errors = FormSchema.validate(template(fields = emptyList()))
        assertTrue(errors.any { it.contains("En az bir alan") })
    }

    @Test
    fun tekrarEdenAlanKimligiReddedilir(): Unit {
        val t = template(fields = listOf(field(id = "a", label = "Bir"), field(id = "a", label = "İki")))
        assertTrue(FormSchema.validate(t).any { it.contains("kimlikleri tekrar") })
    }

    @Test
    fun tekrarEdenEtiketReddedilir(): Unit {
        val t = template(fields = listOf(field(id = "a", label = "Aynı"), field(id = "b", label = "Aynı")))
        assertTrue(FormSchema.validate(t).any { it.contains("etiketleri tekrar") })
    }

    @Test
    fun secenekliAlandaSecenekSart(): Unit {
        val t = template(fields = listOf(field(id = "c", type = FormFieldType.CHOICE, label = "Tür")))
        assertTrue(FormSchema.validate(t).any { it.contains("en az bir seçenek") })
    }

    @Test
    fun sayiAlandaSecenekOlamaz(): Unit {
        val t = template(fields = listOf(field(id = "n", type = FormFieldType.NUMBER, options = listOf("x"))))
        assertTrue(FormSchema.validate(t).any { it.contains("seçenek olamaz") })
    }

    @Test
    fun adUzunluguSinirli(): Unit {
        val t = template(name = "x".repeat(FormSchema.MAX_NAME + 1))
        assertTrue(FormSchema.validate(t).any { it.contains("en çok") })
    }

    // ── JSON ─────────────────────────────────────────

    @Test
    fun jsonGidisDonusKayipsiz(): Unit {
        val t = template(
            fields = listOf(
                field(id = "date", type = FormFieldType.DATE, label = "Tarih", required = true),
                field(id = "kind", type = FormFieldType.CHOICE, label = "Tür", options = listOf("Nem", "Çatlak")),
            ),
        )
        val parsed = FormSchema.parseOrThrow(FormSchema.toJson(t))
        assertEquals(t, parsed)
    }

    @Test
    fun bilinmeyenTurReddedilir(): Unit {
        val raw = """{"id":"x","name":"X","fields":[{"id":"a","type":"MAGIC","label":"A"}]}"""
        assertNull(FormSchema.parse(raw))
        val thrown = runCatching { FormSchema.parseOrThrow(raw) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException && thrown.message.orEmpty().contains("bilinmiyor"))
    }

    @Test
    fun bozukJsonNullDoner(): Unit {
        assertNull(FormSchema.parse("{ bu json değil"))
        assertNull(FormSchema.parse("""{"id":"x"}""")) // fields yok
    }

    @Test
    fun parseListGecerliyiAlirBozuguRaporlar(): Unit {
        val good = FormSchema.toJson(template(id = "ok"))
        val raw = "[$good, {\"id\":\"bad\",\"name\":\"\",\"fields\":[]}]"
        val result = FormSchema.parseList(raw)
        assertEquals(1, result.templates.size)
        assertEquals("ok", result.templates.first().id)
        assertTrue(result.errors.isNotEmpty())
    }

    @Test
    fun parseListTekrarEdenKimligiDusurur(): Unit {
        val one = FormSchema.toJson(template(id = "dup"))
        val result = FormSchema.parseList("[$one, $one]")
        assertEquals(1, result.templates.size)
        assertTrue(result.errors.any { it.contains("tekrar ediyor") })
    }

    @Test
    fun bosDosyaBosSonuc(): Unit {
        val result = FormSchema.parseList("")
        assertTrue(result.ok)
        assertTrue(result.templates.isEmpty())
    }

    @Test
    fun hazirSablonlarGecerli(): Unit {
        FormSchema.presets().forEach { preset ->
            assertTrue("${preset.id}: ${FormSchema.validate(preset)}", FormSchema.validate(preset).isEmpty())
            assertNotNull(FormSchema.parse(FormSchema.toJson(preset)))
        }
    }

    // ── Değer doğrulama ──────────────────────────────

    @Test
    fun zorunluAlanBosBirakilamaz(): Unit {
        val t = template(fields = listOf(field(id = "a", label = "Ad", required = true)))
        assertTrue(FormSchema.validateValues(t, emptyMap()).any { it.contains("zorunlu") })
    }

    @Test
    fun sayiAlaniSayiOlmayiReddeder(): Unit {
        val t = template(fields = listOf(field(id = "n", type = FormFieldType.NUMBER, label = "Adet")))
        assertTrue(FormSchema.validateValues(t, mapOf("n" to "abc")).any { it.contains("sayı olmalı") })
        assertTrue(FormSchema.validateValues(t, mapOf("n" to "12.5")).isEmpty())
    }

    @Test
    fun tarihBicimiDenetlenir(): Unit {
        val t = template(fields = listOf(field(id = "d", type = FormFieldType.DATE, label = "Tarih")))
        assertTrue(FormSchema.validateValues(t, mapOf("d" to "2026-10-21")).any { it.contains("GG.AA.YYYY") })
        assertTrue(FormSchema.validateValues(t, mapOf("d" to "21.10.2026")).isEmpty())
    }

    @Test
    fun secenekDisiDegerReddedilir(): Unit {
        val t = template(
            fields = listOf(field(id = "c", type = FormFieldType.CHOICE, label = "Tür", options = listOf("Nem"))),
        )
        assertTrue(FormSchema.validateValues(t, mapOf("c" to "Çatlak")).any { it.contains("seçeneklerden") })
        assertTrue(FormSchema.validateValues(t, mapOf("c" to "Nem")).isEmpty())
    }

    @Test
    fun exportTextTumAlanlariYazar(): Unit {
        val t = template(fields = listOf(field(id = "a", label = "Ad"), field(id = "b", label = "Soyad")))
        val text = FormSchema.exportText(t, mapOf("a" to "Gökhan"))
        assertTrue(text.contains("Şablon: Şablon"))
        assertTrue(text.contains("Ad: Gökhan"))
        assertTrue(text.contains("Soyad: —"))
    }
}
