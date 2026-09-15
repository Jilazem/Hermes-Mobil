package com.hermes.mobile

import com.hermes.mobile.data.FullControl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Tam kontrol" çekirdeği.
 *
 * Buradaki her kural gerçek bir dokunuşa, gerçek bir yazıya ya da gerçek bir
 * ret cevabına dönüşüyor; yanlış eşleşme yanlış öğeye basmak demek. Bu yüzden
 * koruma, hedef seçimi ve boyut sınırları cihazsız test ediliyor.
 */
class FullControlTest {

    // ── Koruma ───────────────────────────────────────────────────────

    @Test
    fun `kanal kapaliyken hicbir arac calismaz`() {
        FullControl.ALL_TOOLS.forEach { tool ->
            assertEquals(
                FullControl.WHY_AGENT_OFF,
                FullControl.guardReason(false, false, true, tool),
            )
        }
    }

    @Test
    fun `tam kontrol kapaliyken reddedilir`() {
        FullControl.ALL_TOOLS.forEach { tool ->
            assertEquals(
                FullControl.WHY_FULL_CONTROL_OFF,
                FullControl.guardReason(true, false, false, tool),
            )
        }
    }

    @Test
    fun `salt okunur kipte yazma araclari reddedilir okuma araclari gecer`() {
        FullControl.READ_TOOLS.forEach { tool ->
            assertNull(tool, FullControl.guardReason(true, true, true, tool))
        }
        FullControl.WRITE_TOOLS.forEach { tool ->
            assertEquals(
                tool,
                FullControl.WHY_READ_ONLY,
                FullControl.guardReason(true, true, true, tool),
            )
        }
    }

    @Test
    fun `uc anahtar da acikken her sey serbest`() {
        FullControl.ALL_TOOLS.forEach { tool ->
            assertNull(tool, FullControl.guardReason(true, false, true, tool))
        }
    }

    @Test
    fun `tam kontrol araclari yalniz iki anahtar da acikken duyurulur`() {
        assertTrue(FullControl.advertise(false, false).isEmpty())
        assertTrue(FullControl.advertise(false, true).isEmpty())
        assertTrue(FullControl.advertise(true, false).isEmpty())
        assertEquals(FullControl.ALL_TOOLS, FullControl.advertise(true, true))
    }

    @Test
    fun `red gerekceleri kopru ile ayni metin`() {
        // Köprüdeki mevcut davranış bozulmasın: bu metinler ajanın
        // kullanıcıya "neyi açman gerekiyor" demesini sağlıyor.
        assertEquals("agent access is off", FullControl.WHY_AGENT_OFF)
        assertEquals("read-only mode is on", FullControl.WHY_READ_ONLY)
    }

    @Test
    fun `araç adlari kapsamda`() {
        listOf(
            "screen.dump", "tap", "long_press", "swipe", "type", "key",
            "global", "screenshot", "apps.list", "app.start", "app.stop",
        ).forEach { assertTrue(it, FullControl.isFullControlTool(it)) }
        assertFalse(FullControl.isFullControlTool("phone_dial"))
        assertFalse(FullControl.isFullControlTool("phone_status"))
    }

    // ── Dokunma hedefi ───────────────────────────────────────────────

    @Test
    fun `koordinat verilince arama yapilmaz`() {
        val t = FullControl.parseTap("100", "200", "Ayarlar", "com.x:id/y")
        assertTrue(t is FullControl.TapTarget.Point)
        assertEquals(100, (t as FullControl.TapTarget.Point).x)
        assertEquals(200, t.y)
    }

    @Test
    fun `id metinden once gelir`() {
        val t = FullControl.parseTap(null, null, "Ayarlar", "com.x:id/y")
        assertTrue(t is FullControl.TapTarget.ViewId)
    }

    @Test
    fun `metin tek basina gecerli hedef`() {
        val t = FullControl.parseTap(null, null, "  Ayarlar ", "")
        assertEquals(FullControl.TapTarget.Label("Ayarlar"), t)
    }

    @Test
    fun `bos args gecersiz hedef`() {
        assertEquals(FullControl.TapTarget.Invalid, FullControl.parseTap(null, null, null, null))
        assertEquals(FullControl.TapTarget.Invalid, FullControl.parseTap("", " ", "", ""))
        // Tek koordinat yetmez: yarısı tahmin edilmiş bir dokunuş olurdu.
        assertEquals(FullControl.TapTarget.Invalid, FullControl.parseTap("100", null, null, null))
    }

    // ── Düğüm eşleştirme ────────────────────────────────────────────

    private fun node(
        text: String = "",
        desc: String = "",
        id: String = "",
        bounds: FullControl.Bounds = FullControl.Bounds(0, 0, 100, 50),
        clickable: Boolean = false,
        editable: Boolean = false,
        scrollable: Boolean = false,
        focused: Boolean = false,
        children: List<FullControl.UiNode> = emptyList(),
        visible: Boolean = true,
    ) = FullControl.UiNode(
        text, desc, id, "android.widget.TextView", bounds, clickable, editable,
        scrollable, focused, visible, children,
    )

    private val screen: FullControl.UiNode = node(
        bounds = FullControl.Bounds(0, 0, 1080, 2400),
        children = listOf(
            node(
                bounds = FullControl.Bounds(0, 0, 1080, 400),
                children = listOf(node(text = "Ayarlar", bounds = FullControl.Bounds(40, 100, 300, 180))),
            ),
            node(
                text = "Ayarlar",
                bounds = FullControl.Bounds(0, 300, 1080, 480),
                clickable = true,
                children = listOf(node(text = "Ayarlar hakkında", bounds = FullControl.Bounds(60, 380, 500, 440))),
            ),
            node(
                text = "Ara",
                id = "com.android.settings:id/search_bar",
                bounds = FullControl.Bounds(0, 500, 1080, 620),
                clickable = true,
                editable = true,
                focused = true,
            ),
        ),
    )

    @Test
    fun `tam eslesme iceren eslesmeden once gelir`() {
        val m = FullControl.matchLabel(screen, "Ayarlar")
        assertNotNull(m)
        // "Ayarlar hakkında" satırı daha küçük olabilir ama tam eşleşme kazanır.
        assertEquals("Ayarlar", m!!.text)
        assertTrue(m.clickable)
    }

    @Test
    fun `esitlikte tiklanabilir ve kucuk alan tercih edilir`() {
        val m = FullControl.matchLabel(screen, "Ayarlar")
        // Başlıktaki tıklanamaz "Ayarlar" değil, listedeki tıklanabilir satır.
        assertEquals(FullControl.Bounds(0, 300, 1080, 480), m!!.bounds)
    }

    @Test
    fun `buyuk-kucuk harf duyarsiz eslesme`() {
        assertNotNull(FullControl.matchLabel(screen, "ayarlar"))
        assertNotNull(FullControl.matchLabel(screen, "AYARLAR"))
    }

    @Test
    fun `bulunamayan metin null doner - tahmin yok`() {
        assertNull(FullControl.matchLabel(screen, "Kamera"))
        assertNull(FullControl.matchLabel(screen, "   "))
    }

    @Test
    fun `id tam ve son-parca eslesmesi`() {
        assertNotNull(FullControl.matchId(screen, "com.android.settings:id/search_bar"))
        assertNotNull(FullControl.matchId(screen, "search_bar"))
        assertNull(FullControl.matchId(screen, "yok_boyle_id"))
    }

    @Test
    fun `sifir alanli dugumler hedef olmaz`() {
        val flat = node(
            text = "Görünmez",
            bounds = FullControl.Bounds(0, 0, 0, 0),
            clickable = true,
        )
        assertNull(FullControl.matchLabel(flat, "Görünmez"))
    }

    @Test
    fun `yazi alani odakli olani secer`() {
        val e = FullControl.editableTarget(screen)
        assertNotNull(e)
        assertEquals("com.android.settings:id/search_bar", e!!.id)
    }

    @Test
    fun `odakli yazi alani yoksa ilk duzenlenebilir`() {
        val two = node(
            bounds = FullControl.Bounds(0, 0, 100, 200),
            children = listOf(
                node(id = "a", editable = true, bounds = FullControl.Bounds(0, 0, 100, 40)),
                node(id = "b", editable = true, bounds = FullControl.Bounds(0, 50, 100, 90)),
            ),
        )
        assertEquals("a", FullControl.editableTarget(two)?.id)
    }

    @Test
    fun `yazi alani yoksa null`() {
        assertNull(FullControl.editableTarget(screen.copy(children = emptyList())))
    }

    // ── Jestler ──────────────────────────────────────────────────────

    @Test
    fun `yon kisayollari ekran oranindan jest uretir`() {
        val up = FullControl.directionGesture("yukarı", 1000, 2000)!!
        assertEquals(500, up.x1)
        assertEquals(1500, up.y1)
        assertEquals(500, up.x2)
        assertEquals(500, up.y2)

        val down = FullControl.directionGesture("asagi", 1000, 2000)!!
        assertEquals(500, down.y1)
        assertEquals(1500, down.y2)
        assertTrue(down.x1 == down.x2)

        val left = FullControl.directionGesture("sol", 1000, 2000)!!
        assertEquals(850, left.x1)
        assertEquals(150, left.x2)
        assertEquals(1000, left.y1)

        val right = FullControl.directionGesture("sag", 1000, 2000)!!
        assertEquals(150, right.x1)
        assertEquals(850, right.x2)
    }

    @Test
    fun `ingilizce yon adlari da calisir`() {
        listOf("up", "down", "left", "right").forEach {
            assertNotNull(it, FullControl.directionGesture(it, 1080, 2400))
        }
        assertNull(FullControl.directionGesture("capraz", 1080, 2400))
    }

    @Test
    fun `koordinatli kaydirma dogrudan gecer`() {
        val g = FullControl.parseSwipe("10", "20", "30", "40", "500", null, 1000, 2000)!!
        assertEquals(10, g.x1)
        assertEquals(20, g.y1)
        assertEquals(30, g.x2)
        assertEquals(40, g.y2)
        assertEquals(500L, g.durationMs)
    }

    @Test
    fun `sure sinirlanir`() {
        assertEquals(50L, FullControl.parseSwipe("1", "2", "3", "4", "0", null, 100, 100)!!.durationMs)
        assertEquals(5000L, FullControl.parseSwipe("1", "2", "3", "4", "99999", null, 100, 100)!!.durationMs)
    }

    @Test
    fun `ne yon ne koordinat varsa jest uretilmez`() {
        assertNull(FullControl.parseSwipe(null, null, null, null, null, null, 1080, 2400))
        assertNull(FullControl.parseSwipe("1", "2", null, null, null, "capraz", 1080, 2400))
    }

    // ── Tuşlar ───────────────────────────────────────────────────────

    @Test
    fun `tus adlari iki dilli`() {
        assertEquals(FullControl.KeyKind.ENTER, FullControl.keyKind("enter"))
        assertEquals(FullControl.KeyKind.ENTER, FullControl.keyKind("Gir"))
        assertEquals(FullControl.KeyKind.BACKSPACE, FullControl.keyKind("sil"))
        assertEquals(FullControl.KeyKind.SPACE, FullControl.keyKind("boşluk"))
        assertEquals(FullControl.KeyKind.TAB, FullControl.keyKind("Tab"))
        assertEquals(FullControl.KeyKind.ESCAPE, FullControl.keyKind("esc"))
        assertNull(FullControl.keyKind("megafon"))
    }

    @Test
    fun `desteklenmeyen tusler acikca reddedilir`() {
        // Erişilebilirlik API'si yön tuşu gönderemiyor; "yapıldı" demek
        // sessiz bir yalan olurdu.
        val kind = FullControl.keyKind("yukari")
        assertNotNull(kind)
        assertEquals(FullControl.KeyKind.DPAD_UP, kind)
        assertFalse(kind!! in FullControl.SUPPORTED_KEYS)
        listOf(
            FullControl.KeyKind.ENTER, FullControl.KeyKind.BACKSPACE,
            FullControl.KeyKind.SPACE, FullControl.KeyKind.TAB,
            FullControl.KeyKind.ESCAPE,
        ).forEach { assertTrue(it.name, it in FullControl.SUPPORTED_KEYS) }
    }

    // ── Global eylemler ──────────────────────────────────────────────

    @Test
    fun `global eylem adlari iki dilli`() {
        assertEquals(FullControl.GlobalKind.BACK, FullControl.globalKind("geri"))
        assertEquals(FullControl.GlobalKind.BACK, FullControl.globalKind("back"))
        assertEquals(FullControl.GlobalKind.HOME, FullControl.globalKind("ana ekran"))
        assertEquals(FullControl.GlobalKind.RECENTS, FullControl.globalKind("son uygulamalar"))
        assertEquals(FullControl.GlobalKind.NOTIFICATIONS, FullControl.globalKind("bildirimler"))
        assertEquals(FullControl.GlobalKind.QUICK_SETTINGS, FullControl.globalKind("hızlı ayarlar"))
        assertNull(FullControl.globalKind("uzaya cik"))
    }

    @Test
    fun `turkce katlama uzunlugu korur`() {
        val s = "İğüşöçı AYA"
        assertEquals(s.length, FullControl.foldTr(s).length)
        assertEquals("igusoci aya", FullControl.foldTr(s))
    }

    // ── Ekran dökümü ─────────────────────────────────────────────────

    @Test
    fun `dokum paket ve ozet tasir`() {
        val json = Json.parseToJsonElement(
            FullControl.dump(screen, "com.android.settings", "com.android.settings.Settings"),
        ).jsonObject
        assertEquals("com.android.settings", json["package"]!!.jsonPrimitive.content)
        assertEquals("com.android.settings.Settings", json["activity"]!!.jsonPrimitive.content)
        assertFalse(json["truncated"]!!.jsonPrimitive.content.toBoolean())
        val summary = json["summary"]!!.jsonPrimitive.content
        assertTrue(summary.contains("com.android.settings"))
        assertTrue(summary.contains("düğüm"))
        assertTrue(summary.contains("tıklanabilir"))
        assertEquals("1080x2400", json["screen"]!!.jsonPrimitive.content)
        assertNotNull(json["tree"])
        // Paket/aktivite metinde görünmeli ki ajan "hangi ekrandayım" bilsin.
        assertTrue(summary.contains("Settings"))
    }

    @Test
    fun `dugum siniri asilinca truncated isaretlenir`() {
        var root = node(text = "yaprak", bounds = FullControl.Bounds(0, 0, 10, 10))
        repeat(30) { i ->
            root = node(
                text = "katman $i",
                bounds = FullControl.Bounds(0, 0, 500, 500),
                children = listOf(root),
            )
        }
        val json = Json.parseToJsonElement(
            FullControl.dump(root, "p", "a", maxNodes = 5),
        ).jsonObject
        assertTrue(json["truncated"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("p", json["package"]!!.jsonPrimitive.content)
    }

    @Test
    fun `karakter siniri asilinca agac atilir ozet kalir`() {
        var root = node(text = "ilk", bounds = FullControl.Bounds(0, 0, 10, 10))
        repeat(40) { i ->
            root = node(
                text = "uzun metin $i " + "x".repeat(200),
                bounds = FullControl.Bounds(0, 0, 500, 500),
                children = listOf(root),
            )
        }
        val json = Json.parseToJsonElement(
            FullControl.dump(root, "pkg", "act", maxChars = 1500),
        ).jsonObject
        assertTrue(json["truncated"]!!.jsonPrimitive.content.toBoolean())
        assertNull(json["tree"])
        assertTrue(json["summary"]!!.jsonPrimitive.content.contains("pkg"))
        assertNotNull(json["note"])
    }

    @Test
    fun `bos kapsayicilar ayiklanir`() {
        val bloated = node(
            bounds = FullControl.Bounds(0, 0, 100, 100),
            children = listOf(
                node(bounds = FullControl.Bounds(0, 0, 100, 100), children = listOf(
                    node(bounds = FullControl.Bounds(0, 0, 100, 100)),
                )),
                node(text = "Gerçek", bounds = FullControl.Bounds(0, 0, 50, 20)),
            ),
        )
        val pruned = FullControl.prune(bloated)!!
        assertEquals(1, pruned.children.size)
        assertEquals("Gerçek", pruned.children[0].text)
    }

    @Test
    fun `onemli sinif bos olsa da korunur`() {
        val edit = FullControl.UiNode(
            cls = "android.widget.EditText",
            bounds = FullControl.Bounds(0, 0, 100, 40),
        )
        assertNotNull(FullControl.prune(edit))
    }

    @Test
    fun `dokum gecerli json`() {
        val root = node(
            text = "tırnak \" ve ters \\ ve yeni\nsatır",
            bounds = FullControl.Bounds(0, 0, 100, 50),
        )
        val text = FullControl.dump(root, "p", "a")
        // Kaçış olmadan JSON bozulur ve ajan dökümü hiç okuyamaz.
        val parsed = Json.parseToJsonElement(text).jsonObject
        val t = parsed["tree"]!!.jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(t.contains("\""))
        assertTrue(t.contains("\\"))
    }

    @Test
    fun `ekran disi ogeler ozete ve kaydirma farkina girmez`() {
        val listed = node(
            text = "Liste",
            bounds = FullControl.Bounds(0, 0, 1080, 2000),
            scrollable = true,
            children = listOf(
                node(text = "Görünen satır", bounds = FullControl.Bounds(0, 0, 1080, 100)),
                node(
                    text = "Ekran dışı satır",
                    bounds = FullControl.Bounds(0, 3000, 1080, 3100),
                    visible = false,
                ),
            ),
        )
        val labels = FullControl.visibleLabels(listed)
        assertTrue(labels.contains("Görünen satır"))
        assertFalse(labels.contains("Ekran dışı satır"))
        // Zıplama ölçüsü görünen etiketler: kaydırınca yalnız bunlar değişir.
        val summary = FullControl.summary(listed, "p", "a")
        assertTrue(summary.contains("Görünen satır"))
        assertFalse(summary.contains("Ekran dışı satır"))
        assertTrue(summary.contains("görünür"))
    }

    @Test
    fun `ekran disi isaretlenir dokumde`() {
        val root = node(
            text = "kök",
            bounds = FullControl.Bounds(0, 0, 100, 100),
            children = listOf(
                node(text = "aşağıda", bounds = FullControl.Bounds(0, 500, 100, 600), visible = false),
            ),
        )
        val json = Json.parseToJsonElement(FullControl.dump(root, "p", "a")).jsonObject
        val child = json["tree"]!!.jsonObject["children"]!!.jsonArray[0].jsonObject
        assertTrue(child.containsKey("offscreen"))
        assertTrue(child["offscreen"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `screen size en buyuk siniri verir`() {
        assertEquals("1080x2400", FullControl.screenSize(screen))
    }

    @Test
    fun `walk tum dugumleri gezer`() {
        assertEquals(6, FullControl.walk(screen).count())
    }

    // ── Ekran görüntüsü ──────────────────────────────────────────────

    @Test
    fun `kucuk kare icin kucultme yapilmaz`() {
        assertTrue(FullControl.shrinkSteps(100 * 1024).isEmpty())
        assertTrue(FullControl.shrinkSteps(FullControl.SCREENSHOT_TARGET_BYTES).isEmpty())
    }

    @Test
    fun `buyuk kare icin olcekler azalan sirada`() {
        val steps = FullControl.shrinkSteps(3 * 1024 * 1024)
        assertTrue(steps.isNotEmpty())
        val scales = steps.map { it.first }
        assertEquals(scales.sortedDescending(), scales)
        assertTrue(scales.all { it < 1.0f && it > 0f })
        assertTrue(steps.all { it.second in 1..100 })
    }

    @Test
    fun `base64 boyutu dogru hesaplanir`() {
        assertEquals(4, FullControl.base64Size(3))
        assertEquals(4, FullControl.base64Size(1))
        assertEquals(300 * 1024 * 4 / 3, FullControl.base64Size(300 * 1024 - 1) / 1)
    }
}
