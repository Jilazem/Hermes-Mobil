package com.hermes.mobile

import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.AssistantModeLogic
import com.hermes.mobile.data.VoicePrefs
import com.hermes.mobile.data.VoiceRecordLogic
import com.hermes.mobile.data.VoiceSpeakLogic
import com.hermes.mobile.data.toVoicePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tur-13 "telefon asistanı" saf katman testleri.
 *
 * Kapsam: rol durumu metni, asistan akışı fazları, oto-okuma kuralı ve
 * varsayılanların normal sohbeti DEĞİŞTİRMEDİĞİ. Android'e bağlı hiçbir şey
 * çağrılmaz — `RoleManager`/`Intent` yalnız `AssistantRole` içinde.
 */
class AssistantModeTest {

    private fun t(tr: String, _en: String): String = tr

    private fun status(
        holders: List<String>,
        api: Int = 34,
        self: String = "com.hermes.mobile.v2",
        selfHolds: Boolean = false,
    ) = AssistantModeLogic.roleStatus(
        selfPackage = self,
        holders = holders,
        apiLevel = api,
        selfHolds = selfHolds,
    )

    // ── Rol durumu ────────────────────────────────────────────────────

    @Test
    fun `hermes rol sahibiyse varsayilan asistan satiri cikar`() {
        val st = status(listOf("com.hermes.mobile.v2"))
        assertEquals(AssistantModeLogic.RoleState.Hermes, st.state)
        assertEquals("Hermes: varsayılan asistan ✓", AssistantModeLogic.roleLine(st, ::t))
    }

    @Test
    fun `baska bir uygulama tutuyorsa okunabilir adi gosterilir`() {
        val st = status(listOf("com.google.android.googlequicksearchbox"))
        assertEquals(AssistantModeLogic.RoleState.Other, st.state)
        assertEquals("Google", st.holder)
        assertEquals("Şu an: Google", AssistantModeLogic.roleLine(st, ::t))
    }

    @Test
    fun `bilinmeyen paket adi uydurulmaz`() {
        val st = status(listOf("com.ornek.birsey"))
        assertEquals(AssistantModeLogic.RoleState.Other, st.state)
        assertEquals("com.ornek.birsey", st.holder)
        assertEquals("Şu an: com.ornek.birsey", AssistantModeLogic.roleLine(st, ::t))
    }

    @Test
    fun `rol atanmamissa atanmamis yazilir`() {
        val st = status(emptyList())
        assertEquals(AssistantModeLogic.RoleState.None, st.state)
        assertEquals("Şu an: atanmamış", AssistantModeLogic.roleLine(st, ::t))
    }

    @Test
    fun `bos ve kirli kayitlar atanmamis sayilir`() {
        val st = status(listOf("   ", ""))
        assertEquals(AssistantModeLogic.RoleState.None, st.state)
        assertTrue(AssistantModeLogic.canRequestRole(st))
    }

    @Test
    fun `debug paket kimligi tam eslesmeyle taninir`() {
        // Sonekli paket (…v2) kendi rolünü tanımalı; yalnız önek eşleşmesi
        // yetseydi "com.hermes.mobile.v3" gibi bir paket de Hermes sanılırdı.
        val ok = status(listOf("com.hermes.mobile.v2"), self = "com.hermes.mobile.v2")
        assertEquals(AssistantModeLogic.RoleState.Hermes, ok.state)
        val other = status(listOf("com.hermes.mobile.v3"), self = "com.hermes.mobile.v2")
        assertEquals(AssistantModeLogic.RoleState.Other, other.state)
    }

    @Test
    fun `api 29 altinda rol desteklenmez ve elle adimlar gosterilir`() {
        val st = status(listOf("com.hermes.mobile.v2"), api = 28)
        assertEquals(AssistantModeLogic.RoleState.Unsupported, st.state)
        assertFalse(AssistantModeLogic.canRequestRole(st))
        val steps = AssistantModeLogic.manualSteps(::t)
        assertEquals(2, steps.size)
        assertTrue(steps[1].contains("Hermes Asistan"))
        assertTrue(
            AssistantModeLogic.roleLine(st, ::t).contains("elle seç"),
        )
    }

    @Test
    fun `api 29 ve ustunde rol istenebilir`() {
        assertEquals(
            AssistantModeLogic.RoleState.None,
            status(emptyList(), api = 29).state,
        )
        assertTrue(AssistantModeLogic.canRequestRole(status(emptyList(), api = 29)))
    }

    @Test
    fun `isRoleHeld kesin cevap - ipuclari bos olsa da Hermes`() {
        // Android'in genel API'si BAŞKA bir uygulamanın rol sahibi olduğunu
        // söylemiyor; "biz miyiz" cevabı isRoleHeld'den gelir ve ipuçlarından
        // (Settings.Secure / resolveActivity) ÖNCE gelir.
        val st = status(emptyList(), api = 34, selfHolds = true)
        assertEquals(AssistantModeLogic.RoleState.Hermes, st.state)
        // Rol API'si olmayan cihazda isRoleHeld çağrılmaz → destek yok kalır.
        assertEquals(
            AssistantModeLogic.RoleState.Unsupported,
            status(emptyList(), api = 28, selfHolds = true).state,
        )
    }

    @Test
    fun `kayitli asistan ipucu Google ise Google yazilir`() {
        // Settings.Secure "assistant" → paket ipucu (selfHolds false).
        val st = status(listOf("com.google.android.googlequicksearchbox"), selfHolds = false)
        assertEquals(AssistantModeLogic.RoleState.Other, st.state)
        assertEquals("Google", st.holder)
    }

    // ── Akış fazları ──────────────────────────────────────────────────

    private val idleRecord = VoiceRecordLogic.State()
    private val idleSpeak = VoiceSpeakLogic.State()

    @Test
    fun `mod kapaliyken faz Off`() {
        assertEquals(
            AssistantModeLogic.Phase.Off,
            AssistantModeLogic.phase(false, idleRecord, idleSpeak, agentBusy = true),
        )
        assertEquals("", AssistantModeLogic.bannerText(AssistantModeLogic.Phase.Off, ::t))
    }

    @Test
    fun `mod acik ve sakin halde faz Ready`() {
        assertEquals(
            AssistantModeLogic.Phase.Ready,
            AssistantModeLogic.phase(true, idleRecord, idleSpeak, agentBusy = false),
        )
    }

    @Test
    fun `kayit cekimi ve ceviri her seyin onunde`() {
        val recording = VoiceRecordLogic.State(VoiceRecordLogic.Phase.Recording)
        assertEquals(
            AssistantModeLogic.Phase.Recording,
            AssistantModeLogic.phase(true, recording, idleSpeak, agentBusy = true),
        )
        val transcribing = VoiceRecordLogic.State(VoiceRecordLogic.Phase.Transcribing)
        assertEquals(
            AssistantModeLogic.Phase.Transcribing,
            AssistantModeLogic.phase(true, transcribing, idleSpeak, agentBusy = true),
        )
    }

    @Test
    fun `calan ses AwaitingReplydan once gelir`() {
        val playing = VoiceSpeakLogic.started("k", cached = true)
        assertEquals(
            AssistantModeLogic.Phase.Speaking,
            AssistantModeLogic.phase(true, idleRecord, playing, agentBusy = true),
        )
        assertEquals(
            AssistantModeLogic.Phase.AwaitingReply,
            AssistantModeLogic.phase(true, idleRecord, idleSpeak, agentBusy = true),
        )
    }

    @Test
    fun `her fazin bir ipucu satiri var ve hepsi farkli`() {
        val phases = AssistantModeLogic.Phase.entries -
            AssistantModeLogic.Phase.Off
        val lines = phases.map { AssistantModeLogic.bannerText(it, ::t) }
        assertTrue(lines.all { it.isNotBlank() })
        assertEquals(lines.size, lines.toSet().size)
    }

    // ── Oto-okuma kuralı ──────────────────────────────────────────────

    @Test
    fun `asistan modunda ayar aciksa yanit okunur`() {
        assertTrue(
            AssistantModeLogic.shouldAutoRead(
                assistantMode = true,
                settingOn = true,
                reply = "Toplantı saat 14:00'te.",
            ),
        )
    }

    @Test
    fun `normal sohbette ayar acik olsa bile okunmaz`() {
        // Tur-13 şartı: "normal sohbet varsayılanı DEĞİŞMESİN".
        assertFalse(
            AssistantModeLogic.shouldAutoRead(
                assistantMode = false,
                settingOn = true,
                reply = "Toplantı saat 14:00'te.",
            ),
        )
    }

    @Test
    fun `ayar kapaliysa asistan modunda da okunmaz`() {
        assertFalse(
            AssistantModeLogic.shouldAutoRead(
                assistantMode = true,
                settingOn = false,
                reply = "Merhaba.",
            ),
        )
    }

    @Test
    fun `okunacak metin yoksa seslendirme denenmez`() {
        assertFalse(AssistantModeLogic.shouldAutoRead(true, true, ""))
        assertFalse(AssistantModeLogic.shouldAutoRead(true, true, "   "))
        // Yalnız kod bloğu içeren yanıt sesli okunmaz (VoiceSpeakLogic süzgeci).
        assertFalse(AssistantModeLogic.shouldAutoRead(true, true, "```\nval x = 1\n```"))
        assertTrue(AssistantModeLogic.shouldAutoRead(true, true, "Sonuç **hazır**."))
    }

    // ── Metni gönderme kuralı ─────────────────────────────────────────

    @Test
    fun `asistan akisinda metin dogrudan gonderilir`() {
        assertTrue(AssistantModeLogic.autoSendTranscript(assistantMode = true, settingAutoSend = false))
    }

    @Test
    fun `normal sohbette karar kullanici ayarinda`() {
        assertFalse(AssistantModeLogic.autoSendTranscript(false, false))
        assertTrue(AssistantModeLogic.autoSendTranscript(false, true))
    }

    // ── Mikrofon izni ─────────────────────────────────────────────────

    @Test
    fun `izin yoksa istenir izin varsa istenmez`() {
        assertTrue(AssistantModeLogic.askMicOnEnter(granted = false))
        assertFalse(AssistantModeLogic.askMicOnEnter(granted = true))
    }

    // ── Varsayılanlar ─────────────────────────────────────────────────

    @Test
    fun `varsayilanlar sozlesmeye uygun`() {
        val s = AppSettings()
        assertTrue("asistan oto-okuma varsayılan AÇIK", s.assistantAutoRead)
        assertFalse("normal sohbet oto-gönder varsayılan KAPALI", s.voiceAutoSend)
        // Tur-21: motor varsayılanı YEREL (gizlilik — veri buluta çıkmaz).
        assertEquals("yerel", s.voiceEngine)
        assertTrue(VoicePrefs().assistantAutoRead)
        assertFalse(VoicePrefs().autoSend)
    }

    @Test
    fun `ayarlardan tercihe donusum oto-okumayi tasir`() {
        assertFalse(AppSettings(assistantAutoRead = false).toVoicePrefs().assistantAutoRead)
        assertTrue(AppSettings(assistantAutoRead = true).toVoicePrefs().assistantAutoRead)
        // Kapalı ayar normal sohbeti etkilemez, yalnız asistan bağlamını.
        assertNotEquals(
            AppSettings(assistantAutoRead = false).toVoicePrefs().autoSend,
            true,
        )
    }
}
