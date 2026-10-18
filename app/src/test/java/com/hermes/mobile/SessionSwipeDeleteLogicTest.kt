package com.hermes.mobile

import com.hermes.mobile.ui.SwipeDeleteLogic
import com.hermes.mobile.ui.SwipeDeleteLogic.Phase
import com.hermes.mobile.ui.SwipeDeleteLogic.Target
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * K21 — sağa çek iki aşamalı sil durum makinesi testleri (saf
 * [SwipeDeleteLogic] üzerinden).
 *
 * Kabul eşlemesi:
 *  - (2) sağa ilk çek → satır yarıda TAKILIR (arm=true, Stuck).
 *  - (3) takılıyken ikinci tam çek → sil (delete=true, Settled).
 *  - (4) sıfırlama: sola geri çekim / ARM altına düşüş → reset.
 *  - (1) sola tam çek (Idle) → arşiv.
 */
class SessionSwipeDeleteLogicTest {

    // ---- (2) 1. çekim: satır yarıda takılır --------------------------------

    @Test
    fun `idle ilk sag cekim yarıda takılır silmez`() {
        val d = SwipeDeleteLogic.onRelease(Phase.Idle, positionFraction = 0.5f)
        assertTrue(d.arm)
        assertTrue(d.phase == Phase.Armed)
        assertTrue(d.target == Target.Stuck)
        assertFalse(d.delete)
        assertFalse(d.archive)
    }

    @Test
    fun `idle tam sag cekim bile silmez once takilir`() {
        // Brief: 1. çekim ~%50'ye sürüklenir ve TAKILIR — tam çekimde bile
        // silme İKİNCİ çekimdedir.
        val d = SwipeDeleteLogic.onRelease(Phase.Idle, positionFraction = 1.0f)
        assertTrue(d.arm)
        assertFalse(d.delete)
        assertTrue(d.phase == Phase.Armed)
    }

    @Test
    fun `idle kucuk sag cekim takilmaz yerinde toplanir`() {
        val d = SwipeDeleteLogic.onRelease(Phase.Idle, positionFraction = 0.1f)
        assertFalse(d.arm)
        assertFalse(d.delete)
        assertTrue(d.target == Target.Settled)
        assertTrue(d.phase == Phase.Idle)
    }

    // ---- (3) 2. çekim: sil ---------------------------------------------------

    @Test
    fun `armed ikinci tam cekim siler ve faz sifirlanir`() {
        val d = SwipeDeleteLogic.onRelease(Phase.Armed, positionFraction = 1.0f)
        assertTrue(d.delete)
        assertFalse(d.arm)
        assertTrue(d.phase == Phase.Idle)
        assertTrue(d.target == Target.Settled)
    }

    @Test
    fun `armed esik alti cekim takili kalmaya devam eder`() {
        val d = SwipeDeleteLogic.onRelease(Phase.Armed, positionFraction = 0.6f)
        assertFalse(d.delete)
        assertFalse(d.arm)
        assertTrue(d.phase == Phase.Armed)
        assertTrue(d.target == Target.Stuck)
    }

    // ---- (4) sıfırlama -------------------------------------------------------

    @Test
    fun `armed sola geri cekim sıfırlar arşivlemes`() {
        val d = SwipeDeleteLogic.onRelease(Phase.Armed, positionFraction = -0.8f)
        assertTrue(d.reset)
        assertFalse(d.archive)
        assertTrue(d.phase == Phase.Idle)
        assertTrue(d.target == Target.Settled)
    }

    @Test
    fun `armed hafif sola dusus sıfırlar`() {
        val d = SwipeDeleteLogic.onRelease(Phase.Armed, positionFraction = 0.1f)
        assertTrue(d.reset)
        assertTrue(d.phase == Phase.Idle)
    }

    // ---- (1) arşiv: yalnız Idle'daki sola tam çekim --------------------------

    @Test
    fun `idle sola tam cekim arşivler`() {
        val d = SwipeDeleteLogic.onRelease(Phase.Idle, positionFraction = -1.0f)
        assertTrue(d.archive)
        assertFalse(d.delete)
        assertTrue(d.target == Target.Settled)
    }

    @Test
    fun `idle yarim sola cekim arşivlemez`() {
        val d = SwipeDeleteLogic.onRelease(Phase.Idle, positionFraction = -0.3f)
        assertFalse(d.archive)
        assertTrue(d.target == Target.Settled)
    }

    // ---- eşik sınır değerleri ------------------------------------------------

    @Test
    fun `esik sinir degerleri kapsayici ve karin altidir`() {
        // ARM: 0.25 → arm; 0.249 → değil. DELETE: 0.75 üstü → sil; 0.75 → değil.
        assertTrue(SwipeDeleteLogic.onRelease(Phase.Idle, 0.25f).arm)
        assertFalse(SwipeDeleteLogic.onRelease(Phase.Idle, 0.24f).arm)
        assertTrue(SwipeDeleteLogic.onRelease(Phase.Armed, 0.76f).delete)
        assertFalse(SwipeDeleteLogic.onRelease(Phase.Armed, 0.75f).delete)
        // ARCHIVE: -0.5 (kapsayıcı) → arşiv; -0.49 → değil.
        assertTrue(SwipeDeleteLogic.onRelease(Phase.Idle, -0.5f).archive)
        assertFalse(SwipeDeleteLogic.onRelease(Phase.Idle, -0.49f).archive)
    }

    @Test
    fun `tam dongu birinci ikinci ve geri al sonrasi durum temiz`() {
        // 1. çekim → takılır; yarım bırakış faz bozmaz; 2. tam çekim → sil.
        val first = SwipeDeleteLogic.onRelease(Phase.Idle, 0.4f)
        assertTrue(first.arm)
        val jiggle = SwipeDeleteLogic.onRelease(first.phase, 0.5f)
        assertTrue(jiggle.target == Target.Stuck)
        assertTrue(jiggle.phase == Phase.Armed)
        val second = SwipeDeleteLogic.onRelease(jiggle.phase, 0.9f)
        assertTrue(second.delete)
        assertTrue(second.phase == Phase.Idle)
    }
}
