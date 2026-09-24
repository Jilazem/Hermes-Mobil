package com.hermes.mobile.car

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Android Auto ekran yansıtmanın SAF matematiği (testli).
 *
 * MediaProjection telefonu sanal ekrana **en-boy oranını koruyarak** sığdırır:
 * dikey telefon yatay araç ekranında ortalanır, iki yanda siyah bant kalır.
 * Araçtaki dokunuşu telefona taşırken aynı sığdırma tersine çevrilir; bantlara
 * düşen dokunuş yok sayılır (yanlış yere basmaktansa hiç basmamak).
 */
object MirrorLogic {

    data class Fit(val scale: Float, val offsetX: Float, val offsetY: Float)

    fun fit(carW: Int, carH: Int, phoneW: Int, phoneH: Int): Fit {
        val s = min(carW.toFloat() / phoneW, carH.toFloat() / phoneH)
        return Fit(s, (carW - phoneW * s) / 2f, (carH - phoneH * s) / 2f)
    }

    /** Araç noktası → telefon noktası; bant içindeyse null. */
    fun carToPhone(x: Float, y: Float, carW: Int, carH: Int, phoneW: Int, phoneH: Int): Pair<Int, Int>? {
        if (carW <= 0 || carH <= 0 || phoneW <= 0 || phoneH <= 0) return null
        val f = fit(carW, carH, phoneW, phoneH)
        val px = (x - f.offsetX) / f.scale
        val py = (y - f.offsetY) / f.scale
        if (px < 0 || py < 0 || px >= phoneW || py >= phoneH) return null
        return px.roundToInt().coerceIn(0, phoneW - 1) to py.roundToInt().coerceIn(0, phoneH - 1)
    }

    /**
     * Araçta sürükleme (onScroll birikimi, araç pikseli) → telefonda kaydırma.
     * onScroll "mesafe" verir (son − şimdiki), parmağın gidişi bunun tersi.
     * Başlangıç ekran ortası; hedef ekran içine kırpılır. Çok küçük hareket null.
     */
    fun scrollSwipe(
        distX: Float, distY: Float,
        carW: Int, carH: Int, phoneW: Int, phoneH: Int,
    ): IntArray? {
        if (phoneW <= 0 || phoneH <= 0 || carW <= 0 || carH <= 0) return null
        val s = fit(carW, carH, phoneW, phoneH).scale
        val dx = -distX / s
        val dy = -distY / s
        if (abs(dx) < 12 && abs(dy) < 12) return null
        val x1 = phoneW / 2
        val y1 = phoneH / 2
        val x2 = (x1 + dx).roundToInt().coerceIn(1, phoneW - 2)
        val y2 = (y1 + dy).roundToInt().coerceIn(1, phoneH - 2)
        return intArrayOf(x1, y1, x2, y2)
    }

    /** Hızlı savurma → ekranın %60'ı kadar kaydırma, baskın eksende. */
    fun flingSwipe(vx: Float, vy: Float, phoneW: Int, phoneH: Int): IntArray? {
        if (phoneW <= 0 || phoneH <= 0 || (abs(vx) < 200 && abs(vy) < 200)) return null
        val cx = phoneW / 2
        val cy = phoneH / 2
        return if (abs(vy) >= abs(vx)) {
            val d = (phoneH * 0.3f).roundToInt() * if (vy > 0) 1 else -1
            intArrayOf(cx, cy - d, cx, cy + d)
        } else {
            val d = (phoneW * 0.3f).roundToInt() * if (vx > 0) 1 else -1
            intArrayOf(cx - d, cy, cx + d, cy)
        }
    }

    /** 1,5 m/sn ≈ 5 km/sa üstü "sürüyor" sayılır. Hız bilinmiyorsa (null) durdurma. */
    const val MOVING_MPS = 1.5f

    fun pausedForDriving(guardOn: Boolean, speedMps: Float?): Boolean =
        guardOn && speedMps != null && speedMps > MOVING_MPS
}
