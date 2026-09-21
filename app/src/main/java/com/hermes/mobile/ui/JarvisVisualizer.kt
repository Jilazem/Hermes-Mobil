package com.hermes.mobile.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hermes.mobile.data.JarvisAnim
import com.hermes.mobile.data.JarvisPhase
import com.hermes.mobile.data.JarvisVisualLogic
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tur-23 (JARVIS-1) — Jarvis temalı konuşma görselleştirici.
 *
 * Merkezde nefes alan cyan halka + etrafında İKİ ters yönlü dönen yay
 * parçası (reaktör hissi, sade). Faz → hız/genlik/ışıma eşlemesi SAF
 * [JarvisVisualLogic] tablosundadır (JVM testi sabitler); burada yalnız
 * zaman (infinite transition) ve seviye çizilir.
 *
 * DÖNÜŞ HIZI anim.arcPeriodMs'den gelir: 0 = durağan (idle ve indirimli
 * animasyon). Nefes periyodu anim.pulsePeriodMs'den gelir — aynı değer,
 * aynı mantık: 0 → salınım donar (hareket yok, renk/faz kalarak erişim
 * gereksinimi karşılanır).
 *
 * Renkler sabittir ve kullanıcı temalarından BAĞIMSIZDIR (görev maddesi 3:
 * "mevcut temayla çakışmasın"): merkez #7FD4FF, yay #2E9BD6, ışıma #3FC8FF.
 */
object JarvisColors {
    val Core = Color(0xFF7FD4FF)
    val Arc = Color(0xFF2E9BD6)
    /** Dış ışıma ve genlik barları — level ile parlar. */
    val Glow = Color(0xFF3FC8FF)
}

@Composable
fun JarvisVisualizer(
    phase: JarvisPhase,
    level: Float,
    modifier: Modifier = Modifier,
    /** Dış ölçek — 1 = normal (168 dp). Sürüş ekranı daha küçük çağırır. */
    scale: Float = 1f,
) {
    val reduced = LocalReducedMotion.current
    val base = JarvisVisualLogic.ANIMS.getValue(phase)
    val anim = if (reduced) JarvisVisualLogic.reducedOf(base) else base

    // Sonsuz zaman tabanları — periyotlar FAZ tablosundan. specMs(reduced)
    // ile süre 0'a iner; ayrıca pulseAmp=0 olduğundan çizim durağandır
    // (iki katman güvence: hareket yok + yeniden-çizim maliyeti minimal).
    val transition = rememberInfiniteTransition(label = "jarvis")
    val pulseT by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(HermesMotion.specMs(anim.pulsePeriodMs.coerceAtLeast(1), reduced)),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "jarvis-pulse",
    )
    // Yay dönüşü: arcPeriodMs = 0 ise (idle/reduced) animasyon YARATILMAZ —
    // spinning composition gereksiz yeniden-çizim üretmesin.
    val spinDeg = if (anim.arcPeriodMs > 0) {
        val s by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(HermesMotion.specMs(anim.arcPeriodMs, reduced)),
                repeatMode = RepeatMode.Restart,
            ),
            label = "jarvis-spin",
        )
        s
    } else 0f
    // İkinci yay TERSE döner ve biraz daha yavaştır (arcPeriodMs * 1.6).
    val spin2Deg = if (anim.arcPeriodMs > 0) {
        val s by transition.animateFloat(
            initialValue = 360f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(HermesMotion.specMs((anim.arcPeriodMs * 1.6f).toInt(), reduced)),
                repeatMode = RepeatMode.Restart,
            ),
            label = "jarvis-spin2",
        )
        s
    } else 0f

    val cd = remember(phase) { "jarvis-gorsellestirici-${phase.name.lowercase()}" }
    Canvas(
        modifier
            .size((168 * scale).dp)
            .semantics { contentDescription = cd },
    ) {
        drawJarvis(anim, if (reduced) 0.5f else pulseT, spinDeg, spin2Deg, level)
    }
}

/**
 * Çizim — saf geometri. Parametrelerin doğruluğu JVM testinde
 * [JarvisVisualLogic] üzerinden sabitlenir; burada yalnız piksel üretilir.
 */
private fun DrawScope.drawJarvis(
    anim: JarvisAnim,
    pulseT: Float,
    spinDeg: Float,
    spin2Deg: Float,
    level: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val c = Offset(cx, cy)
    val base = minOf(size.width, size.height) / 2f
    val stroke = (size.width * 0.035f).coerceAtLeast(3f)

    // Nefes + level büyütmesi: Speaking'te seviyeyle şişer (amplitude-driven scale).
    val breath = 1f + (pulseT * 2f - 1f) * anim.pulseAmp
    val ringR = base * 0.42f * breath * (1f + level * 0.18f)

    // Dış ışıma — level ile parlar (ses yükselince büyür).
    if (level > 0.02f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    JarvisColors.Glow.copy(alpha = 0.34f * level),
                    JarvisColors.Glow.copy(alpha = 0f),
                ),
                center = c,
                radius = base,
            ),
            radius = base,
        )
    }

    // İki ters yönlü yay parçası (reaktör hissi).
    drawArcPair(JarvisColors.Arc, base * 0.86f, spinDeg, 24f, 160f, stroke)
    drawArcPair(JarvisColors.Arc.copy(alpha = 0.70f), base * 0.72f, spin2Deg, 40f, 120f, stroke)

    // Genlik barları — yalnız anim.bars (Listening + Speaking), 12 radiyal çubuk.
    if (anim.bars) {
        drawRadialBars(c, ringR + stroke * 2f, level)
    }

    // Merkez: seviyeyle dolan saydam dolgu + ana halka.
    drawCircle(
        color = JarvisColors.Core.copy(alpha = 0.10f + level * 0.10f),
        radius = ringR,
        center = c,
    )
    drawCircle(
        color = JarvisColors.Core,
        radius = ringR,
        center = c,
        style = Stroke(width = stroke),
    )
    // Thinking/parlama merkezi (centerGlow fazdan gelir).
    if (anim.centerGlow > 0.01f) {
        drawCircle(
            color = JarvisColors.Core.copy(alpha = 0.55f * anim.centerGlow),
            radius = ringR * 0.62f,
            center = c,
        )
    }
    // Çekirdek nokta.
    drawCircle(
        color = JarvisColors.Core,
        radius = ringR * 0.22f,
        center = c,
    )
}

/** İki 180° kaynaklı yay parçası (gap'li), `angle` kadar döner. */
private fun DrawScope.drawArcPair(
    color: Color,
    radius: Float,
    angleDeg: Float,
    gapDeg: Float,
    spanDeg: Float,
    stroke: Float,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val box = Size(radius * 2f, radius * 2f)
    val topLeft = Offset(cx - radius, cy - radius)
    rotate(degrees = angleDeg, pivot = Offset(cx, cy)) {
        drawArc(
            color = color,
            startAngle = gapDeg,
            sweepAngle = spanDeg - gapDeg,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = gapDeg + 180f,
            sweepAngle = spanDeg - gapDeg,
            useCenter = false,
            topLeft = topLeft,
            size = box,
            style = Stroke(width = stroke),
        )
    }
}

/**
 * 12 radiyal çubuk — uzunluk level'ın sabit ölçeğidir. Titreşim YOK:
 * level'ın kendisi zaten 20 ms RMS karelerinden akar (canlı sinyali çift
 * frekansla modüle etme — tur-22 CPU notu).
 */
private fun DrawScope.drawRadialBars(c: Offset, r0: Float, level: Float) {
    val n = 12
    val inner = r0 + size.width * 0.025f
    val len = size.width * 0.10f * (0.15f + 0.85f * level)
    val barW = (size.width * 0.018f).coerceAtLeast(2f)
    for (i in 0 until n) {
        val a = (2f * PI.toFloat() * i / n) - PI.toFloat() / 2f
        val dx = cos(a)
        val dy = sin(a)
        drawLine(
            color = JarvisColors.Glow,
            start = Offset(c.x + dx * inner, c.y + dy * inner),
            end = Offset(c.x + dx * (inner + len), c.y + dy * (inner + len)),
            strokeWidth = barW,
            cap = StrokeCap.Round,
        )
    }
}
