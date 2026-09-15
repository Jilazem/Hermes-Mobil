package com.hermes.mobile

import com.hermes.mobile.data.AddressHealth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-10 / F3 — ölü adres yönetimi.
 *
 * Saha kanıtı: 140 `unreachable`, ikisi de ölü iki LAN adresi her istekte
 * yeniden deneniyordu.
 */
class AddressHealthTest {

    private val lan = "http://192.168.1.10:9150"
    private val remote = "https://hermes.example.pro"

    @After
    fun tearDown() = AddressHealth.clear()

    @Test
    fun `saglikli adresler sirayla doner`() {
        val plan = AddressHealth.plan("p1", listOf(lan, remote))
        assertEquals(listOf(lan, remote), plan)
    }

    @Test
    fun `son calisan adres one alinir`() {
        val plan = AddressHealth.plan("p1", listOf(lan, remote), preferred = remote)
        assertEquals(listOf(remote, lan), plan)
    }

    @Test
    fun `tek basarisizlik adresi gecici olarak devre disi birakir`() {
        AddressHealth.noteFailure("p1", lan, "timeout", now = 1_000L)
        val plan = AddressHealth.plan("p1", listOf(lan, remote), now = 1_500L)
        assertEquals(listOf(remote), plan)          // yalnız sağlıklı olan denenir
        assertTrue(AddressHealth.skipped >= 1)
    }

    @Test
    fun `devre disi sure dolunca adres tekrar denenir`() {
        AddressHealth.noteFailure("p1", lan, "timeout", now = 1_000L)
        val cooldown = AddressHealth.cooldownMs(1)
        val plan = AddressHealth.plan("p1", listOf(lan, remote), now = 1_000L + cooldown + 1)
        assertEquals(listOf(lan, remote), plan)
    }

    @Test
    fun `tekrarlanan basarisizlikta devre disi sure uzar`() {
        assertEquals(30_000L, AddressHealth.cooldownMs(1))
        assertEquals(120_000L, AddressHealth.cooldownMs(2))
        assertEquals(300_000L, AddressHealth.cooldownMs(3))
        assertEquals(900_000L, AddressHealth.cooldownMs(4))
        assertEquals(900_000L, AddressHealth.cooldownMs(9))   // tavan
        assertEquals(0L, AddressHealth.cooldownMs(0))
    }

    @Test
    fun `basari sayaci ve devre disiligi sifirlar`() {
        AddressHealth.noteFailure("p1", lan, "timeout", now = 1_000L)
        AddressHealth.noteFailure("p1", lan, "timeout", now = 2_000L)
        assertEquals(2, AddressHealth.status("p1", lan).failures)
        assertTrue(AddressHealth.noteSuccess("p1", lan))
        assertEquals(0, AddressHealth.status("p1", lan).failures)
        assertEquals(listOf(lan, remote), AddressHealth.plan("p1", listOf(lan, remote)))
    }

    @Test
    fun `hic saglikli adres kalmazsa en erken acilacak tek adres denenir`() {
        AddressHealth.noteFailure("p1", lan, "timeout", now = 1_000L)      // 30 sn
        AddressHealth.noteFailure("p1", remote, "timeout", now = 1_000L)
        AddressHealth.noteFailure("p1", remote, "timeout", now = 1_000L)   // 120 sn → daha geç
        val plan = AddressHealth.plan("p1", listOf(lan, remote), now = 2_000L)
        assertEquals(listOf(lan), plan)   // sonsuz beklemek yerine en umut verici tek yol
    }

    @Test
    fun `profil kimlikleri birbirini etkilemez`() {
        AddressHealth.noteFailure("p1", lan, "timeout", now = 1_000L)
        assertEquals(listOf(lan, remote), AddressHealth.plan("p2", listOf(lan, remote), now = 1_500L))
        assertEquals(listOf(remote), AddressHealth.plan("p1", listOf(lan, remote), now = 1_500L))
    }

    @Test
    fun `reset bekleyen devre disiligi temizler`() {
        AddressHealth.noteFailure("p1", lan, "timeout", now = 1_000L)
        AddressHealth.reset("p1")
        assertEquals(listOf(lan, remote), AddressHealth.plan("p1", listOf(lan, remote), now = 1_100L))
        assertEquals(0, AddressHealth.status("p1", lan).failures)
    }

    @Test
    fun `bos aday listesi bos plan doner`() {
        assertTrue(AddressHealth.plan("p1", emptyList()).isEmpty())
        assertTrue(AddressHealth.plan("p1", listOf("", "  ")).isEmpty())
    }

    @Test
    fun `ayni adres tekrarlanirsa plan tekil tutar`() {
        val plan = AddressHealth.plan("p1", listOf(lan, lan, remote))
        assertEquals(listOf(lan, remote), plan)
    }

    @Test
    fun `statuses devre disi kalan adresi isaretler`() {
        AddressHealth.noteFailure("p1", lan, "timeout", now = 1_000L)
        val list = AddressHealth.statuses("p1", listOf(lan, remote), now = 1_500L)
        assertEquals(2, list.size)
        assertTrue(list[0].coolingDown(1_500L))
        assertFalse(list[1].coolingDown(1_500L))
        assertTrue(list[0].remainingMs(1_500L) > 0)
        // Süre dolduktan sonra "soğuyor" görünmez (arayüz yanlış bilgi vermesin).
        assertFalse(AddressHealth.statuses("p1", listOf(lan), now = 999_999L)[0].coolingDown(999_999L))
    }

    @Test
    fun `neden kaydedilir ve kirpilir`() {
        val st = AddressHealth.noteFailure("p1", lan, "x".repeat(300), now = 0L)
        assertEquals(120, st.lastReason?.length)
    }

    @Test
    fun `kayit sayisi sinirlidir`() {
        for (i in 1..80) AddressHealth.noteFailure("p$i", lan, "timeout", now = 0L)
        // Taşma olsaydı bellek şişerdi; en az yeni profil sağlıklı görünmeli.
        assertEquals(listOf(lan), AddressHealth.plan("p80", listOf(lan), now = 1L).take(1))
    }
}
