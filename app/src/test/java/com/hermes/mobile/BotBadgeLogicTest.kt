package com.hermes.mobile

import com.hermes.mobile.data.BotCard
import com.hermes.mobile.ui.BotBadgeUi
import com.hermes.mobile.ui.ChatRow
import com.hermes.mobile.ui.botBadgeLabel
import com.hermes.mobile.ui.botBadgeRowKeys
import com.hermes.mobile.ui.resolveBotBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * K18B-B — bot kimliği rozeti (ad+emoji+model afişi).
 *
 * Üç kilit alanı:
 *  1) [resolveBotBadge] — roster kartı eşleme sırası, model afişi güncellemesi,
 *     kart yokken genel kimlik, kimlik bilinmiyorken rozet yok.
 *  2) [botBadgeRowKeys] — rozet yalnız bot gruplarının BAŞINDA; kullanıcı ve
 *     sistem satırlarında görünmez, sistem satırı grubu bölmez.
 *  3) WIRING — rozet gerçekten ChatScreen'e bağlı ("tanımlı ama çağrısız" uç
 *     kalmasın; JarvisUiWiringTest'teki kaynak-grep deseni, D-05 kanıt kuralı).
 */
class BotBadgeLogicTest {

    private fun card(
        id: String = "id-1",
        name: String = "Arastirmaci",
        emoji: String = "🔍",
        profile: String = "arastirma",
        model: String = "m1",
    ) = BotCard(id = id, name = name, emoji = emoji, profile = profile, model = model)

    // ── 1) resolveBotBadge — kart eşleme + afiş ──────────────────────────

    @Test
    fun `kart eslesmesi ad emoji ve model afisi gosterir`() {
        val b = resolveBotBadge(listOf(card()), profile = "arastirma", model = "m1")
        assertEquals(BotBadgeUi(emoji = "🔍", name = "Arastirmaci", modelLabel = "m1"), b)
    }

    @Test
    fun `model degisince afis guncellenir bot adi kalir`() {
        // "model değişince afiş güncellensin": kimlik (ad+emoji) karttan kalır,
        // model etiketi oturumun aktif modelini izler.
        val b = resolveBotBadge(listOf(card()), profile = "arastirma", model = "m2-yesil")
        assertEquals("Arastirmaci", b?.name)
        assertEquals("m2-yesil", b?.modelLabel)
    }

    @Test
    fun `birebir model eslesen kart tercih edilir`() {
        val cards = listOf(
            card(id = "a", name = "Genel", model = ""),
            card(id = "b", name = "Ozel", model = "m1"),
        )
        assertEquals("Ozel", resolveBotBadge(cards, "arastirma", "m1")?.name)
        // Aktif model birebir eşleşmiyorsa genel kart devralır.
        assertEquals("Genel", resolveBotBadge(cards, "arastirma", "m9")?.name)
    }

    @Test
    fun `modeli bos kart her modele eslesir`() {
        val b = resolveBotBadge(listOf(card(model = "")), profile = "arastirma", model = "m9")
        assertEquals("Arastirmaci", b?.name)
        assertEquals("m9", b?.modelLabel)
    }

    @Test
    fun `farkli profilin karti bu sohbetin rozeti olamaz`() {
        val b = resolveBotBadge(listOf(card(profile = "baska")), profile = "arastirma", model = "m1")
        // Kart eşleşmedi → genel kimlik: emoji 🤖, ad profil adı.
        assertEquals(BotBadgeUi(emoji = "🤖", name = "arastirma", modelLabel = "m1"), b)
    }

    @Test
    fun `kartsiz rozet genel kimlikle cizilir`() {
        val b = resolveBotBadge(emptyList(), profile = "arastirma", model = "m1")
        assertEquals(BotBadgeUi(emoji = "🤖", name = "arastirma", modelLabel = "m1"), b)
    }

    @Test
    fun `yolnlendirici oturumu bos profilli kartla eslesir`() {
        val b = resolveBotBadge(listOf(card(profile = "")), profile = "", model = "m1")
        assertEquals("Arastirmaci", b?.name)
    }

    @Test
    fun `kartsiz yolnlendirici oturumu adayla rozetlenir`() {
        val b = resolveBotBadge(emptyList(), profile = "", model = "m1")
        assertEquals("🤖", b?.emoji)
        assertTrue("rozet adı yönlendirici olmalı: ${b?.name}", b?.name?.isNotBlank() == true)
        assertEquals("m1", b?.modelLabel)
    }

    @Test
    fun `profil ve model bosken kimlik bilinmiyor rozet yok`() {
        assertNull(resolveBotBadge(emptyList(), profile = null, model = null))
        assertNull(resolveBotBadge(emptyList(), profile = "  ", model = ""))
    }

    @Test
    fun `oturum modeli bosken kartin modeli afis olur`() {
        val b = resolveBotBadge(listOf(card(model = "m1")), profile = "arastirma", model = null)
        assertEquals("m1", b?.modelLabel)
    }

    @Test
    fun `model afisi her yerde bosken etiket yok`() {
        val b = resolveBotBadge(listOf(card(model = "")), profile = "arastirma", model = "")
        assertEquals("", b?.modelLabel)
        assertEquals("🔍 Arastirmaci", b?.let { botBadgeLabel(it) })
    }

    @Test
    fun `rozet metni ad emoji model biciminde ve kimliksiz`() {
        val b = resolveBotBadge(listOf(card(id = "gizli-id")), profile = "arastirma", model = "m1")
        val label = botBadgeLabel(checkNotNull(b))
        assertEquals("🔍 Arastirmaci · m1", label)
        assertTrue("ham kart id'si rozete sızmamalı", !label.contains("gizli-id"))
    }

    // ── 2) botBadgeRowKeys — grup başına tek rozet ───────────────────────

    private fun user(key: String) = ChatRow.Single(ChatItem.User(key = key, text = "merhaba"))
    private fun assistant(key: String) = ChatRow.Single(ChatItem.Assistant(key = key, text = "yanit"))
    private fun thinking(key: String) = ChatRow.Single(ChatItem.Thinking(key = key, text = "dusunme"))
    private fun notice(key: String) = ChatRow.Single(ChatItem.Notice(key = key, text = "bilgi"))
    private fun tools(key: String) = ChatRow.Tools(key = key, entries = emptyList())

    @Test
    fun `bos listede rozet yok`() {
        assertEquals(emptySet<String>(), botBadgeRowKeys(emptyList()))
    }

    @Test
    fun `kullanici satiri rozet almaz`() {
        assertEquals(emptySet<String>(), botBadgeRowKeys(listOf(user("u1"))))
    }

    @Test
    fun `tek bot grubu tek rozet`() {
        assertEquals(setOf("a1"), botBadgeRowKeys(listOf(user("u1"), assistant("a1"))))
    }

    @Test
    fun `ardisik bot satirlari tek rozet alir`() {
        // Düşünme + araç dizisi + yanıt aynı tur: rozet yalnız grubun başında.
        val rows = listOf(
            user("u1"),
            thinking("t1"),
            tools("detail-t2"),
            assistant("a1"),
        )
        assertEquals(setOf("t1"), botBadgeRowKeys(rows))
    }

    @Test
    fun `iki bot grubu iki rozet alir`() {
        val rows = listOf(
            user("u1"), assistant("a1"),
            user("u2"), assistant("a2"),
        )
        assertEquals(setOf("a1", "a2"), botBadgeRowKeys(rows))
    }

    @Test
    fun `yalniz sistem satiri olan grup rozet almaz`() {
        assertEquals(emptySet<String>(), botBadgeRowKeys(listOf(notice("n1"))))
    }

    @Test
    fun `sistem satiri bot grubunu bolmez`() {
        // Tur içindeki "Talimat iletildi" satırı ayrı rozet getirmemeli.
        val rows = listOf(user("u1"), assistant("a1"), notice("n1"), assistant("a2"))
        assertEquals(setOf("a1"), botBadgeRowKeys(rows))
    }

    @Test
    fun `arac dizisiyle baslayan grup rozet alir`() {
        assertEquals(setOf("detail-t1"), botBadgeRowKeys(listOf(user("u1"), tools("detail-t1"))))
    }

    @Test
    fun `karisik akista kararli sonuc`() {
        val rows = listOf(
            notice("n0"),
            user("u1"),
            thinking("t1"),
            tools("detail-t2"),
            notice("n1"),
            assistant("a1"),
            user("u2"),
            notice("n2"),
        )
        // 1. satır n0 öncesinde kullanıcı yok ama grup BOT içermiyor → rozet yok.
        // 2. grup (t1…a1): kullanıcıyla bölünmüş tek bot turu → tek rozet t1'de.
        // 3. grup: yalnız n2 → rozet yok.
        assertEquals(setOf("t1"), botBadgeRowKeys(rows))
    }

    // ── 3) WIRING — ölü uç kalmasın (kaynak-grep, D-05) ──────────────────

    /** Gradle modülünden (app/) ya da repo kökünden çalışabilir — JarvisUiWiringTest deseni. */
    private fun source(vararg parts: String): String {
        val rel = listOf("src", "main", "java", "com", "hermes", "mobile") + parts.toList()
        val direct = File(rel.joinToString(File.separator))
        val f = if (direct.exists()) direct
        else File("app" + File.separator + rel.joinToString(File.separator))
        assertTrue("kaynak bulunamadı: ${rel.joinToString("/")}", f.exists())
        return f.readText()
    }

    @Test
    fun `wiring rozet ChatScreen gercekten ciziliyor`() {
        val cs = source("ui", "ChatScreen.kt")
        assertTrue("botBadgeRowKeys hesaplanmıyor", cs.contains("botBadgeRowKeys(rows)"))
        assertTrue("BotBadgeRow çağrılmıyor", cs.contains("BotBadgeRow(it)"))
    }

    @Test
    fun `wiring rozet kimligi roster kartindan ve aktif modelden`() {
        val cs = source("ui", "ChatScreen.kt")
        assertTrue("roster kartları yüklenmiyor", cs.contains("BotRosterStore("))
        assertTrue("rozet kimliği çözülmüyor", cs.contains("resolveBotBadge("))
        assertTrue("aktif model afişe gitmiyor", cs.contains("model = state.currentModel"))
    }
}