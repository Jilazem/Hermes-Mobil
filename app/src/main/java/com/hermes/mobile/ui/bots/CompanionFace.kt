package com.hermes.mobile.ui.bots

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Path

/** Original little creatures, drawn with the current theme; no remote assets. */
@Composable
internal fun CompanionFace(identity: String, modifier: Modifier = Modifier) {
    val palette = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)
    val fill = palette[(identity.hashCode() and Int.MAX_VALUE) % palette.size]
    val ink = MaterialTheme.colorScheme.onPrimary
    val kind=(identity.hashCode() and Int.MAX_VALUE)%4
    val blink by rememberInfiniteTransition(label="companion").animateFloat(1f,1f,
        infiniteRepeatable(keyframes { durationMillis=5200; 1f at 0; 1f at 4600; .12f at 4720; 1f at 4840 },RepeatMode.Restart),label="blink")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        if(kind==0) {
            drawOval(fill, Offset(w*.06f,h*.02f), Size(w*.25f,h*.5f))
            drawOval(fill, Offset(w*.69f,h*.02f), Size(w*.25f,h*.5f))
        } else if(kind==1) {
            for(x in listOf(.05f,.65f)) drawCircle(fill,w*.18f,Offset(w*(x+.15f),h*.27f))
        } else if(kind==2) {
            for(x in listOf(.12f,.62f)) drawPath(Path().apply { moveTo(w*x,h*.43f); lineTo(w*(x+.09f),h*.05f); lineTo(w*(x+.27f),h*.36f); close() },fill)
        } else {
            drawOval(fill,Offset(w*.3f,h*.08f),Size(w*.15f,h*.35f))
            drawOval(fill,Offset(w*.52f,h*.04f),Size(w*.15f,h*.35f))
        }
        drawOval(fill,Offset(w*.18f,h*.81f),Size(w*.24f,h*.16f))
        drawOval(fill,Offset(w*.58f,h*.81f),Size(w*.24f,h*.16f))
        drawRoundRect(fill, Offset(w*.12f,h*.2f), Size(w*.76f,h*.72f), CornerRadius(w*.32f))
        for (x in listOf(.36f,.64f)) {
            drawOval(ink,Offset(w*(x-.06f),h*.53f-w*.065f*blink),Size(w*.12f,w*.13f*blink))
            if(blink>.5f) drawCircle(androidx.compose.ui.graphics.Color.White, w*.018f, Offset(w*(x-.015f),h*.51f))
        }
        for(x in listOf(.24f,.76f)) drawOval(androidx.compose.ui.graphics.Color(0xFFFFB2BF).copy(alpha=.65f),Offset(w*(x-.055f),h*.62f),Size(w*.11f,h*.06f))
        drawArc(ink, 0f, 180f, false, Offset(w*.40f,h*.61f), Size(w*.20f,h*.13f), style=androidx.compose.ui.graphics.drawscope.Stroke(w*.026f))
    }
}
