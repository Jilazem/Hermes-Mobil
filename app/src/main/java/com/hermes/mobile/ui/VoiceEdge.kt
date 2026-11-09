package com.hermes.mobile.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hermes.mobile.ui.theme.HermesColors

internal fun voiceEdgeStrength(level: Float): Float = .22f + .78f *
    (if (level.isFinite()) level.coerceIn(0f, 1f) else 0f)

/** Decorative only: no hit target or semantics, so the app remains touchable. */
@Composable
fun VoiceEdge(level: Float, active: Boolean = true, modifier: Modifier = Modifier) {
    if (!active) return
    val reduced = LocalReducedMotion.current
    val strength by animateFloatAsState(if (reduced) .35f else voiceEdgeStrength(level), tween(120), label = "voice-edge-level")
    val accent = HermesColors.Focus
    val secondary = HermesColors.Midground
    Canvas(modifier.fillMaxSize()) {
        val inset = 3.dp.toPx()
        val extent = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f))
        val brush = Brush.linearGradient(listOf(accent, secondary, accent.copy(alpha = .5f), secondary), end = Offset(size.width, size.height))
        for (layer in 4 downTo 1) {
            drawRoundRect(brush, Offset(inset, inset), extent, CornerRadius(30.dp.toPx()),
                alpha = strength * .045f, style = Stroke((layer * 3).dp.toPx()))
        }
        drawRoundRect(brush, Offset(inset, inset), extent, CornerRadius(30.dp.toPx()),
            alpha = .3f + strength * .55f, style = Stroke((1.2f + strength * 1.8f).dp.toPx()))
    }
}
