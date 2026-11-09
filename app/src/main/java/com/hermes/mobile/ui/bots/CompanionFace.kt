package com.hermes.mobile.ui.bots

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlinx.coroutines.delay
import kotlin.math.sin

enum class BotMascotMood {
    IDLE,
    THINKING,
    WORKING,
    WAITING,
    SUCCESS,
    ERROR,
    SPEAKING
}

enum class AnimationIntensity {
    OFF,
    LIGHT,
    NORMAL
}

/**
 * Haribo / marshmallow esintili sevimli, yumuşak ve doğal mikro-hareketli bot karakteri (v7.7.1).
 *
 * Mikro hareketler:
 *  - Ayak hareketleri: Ağırlık değiştirme (weight shift), hafif ayak vurma (foot tap).
 *  - Kulak hareketleri: Merakta dikleşme, sıkılınca düşme, asimetrik tek kulak oynatması.
 *  - Göz hareketleri: Sağa-sola bakış (saccade), yukarı düşünme bakışı, esneme, uykulu/sıkılmış göz kapağı.
 *  - Karakter davranışları: Baş eğme, hafif sağa-sola sallanma, sevinç zıplaması, dokunulduğunda şaşırma.
 *  - Kontrollü rastlantısallık: Her bot kendi kimliğine göre farklı fazda hareket eder.
 *
 * Pil ve performans:
 *  - Kapalı modunda tamamen sıfır animasyon GPU/CPU yükü.
 *  - Hafif modda sadece durum değişim tepkileri.
 */
@Composable
fun CompanionFace(
    identity: String,
    modifier: Modifier = Modifier,
    mood: BotMascotMood = BotMascotMood.IDLE,
    intensity: AnimationIntensity = AnimationIntensity.NORMAL,
) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFF6C5CE7), // Mor marshmallow
        Color(0xFF00B894), // Nane marshmallow
        Color(0xFF0984E3), // Mavi marshmallow
        Color(0xFFE17055), // Mercan marshmallow
        Color(0xFFFD79A8), // Pembe marshmallow
    )
    val botSeed = (identity.hashCode() and 0x7FFFFFFF)
    val fill = palette[botSeed % palette.size]
    val ink = MaterialTheme.colorScheme.onPrimary
    val kind = botSeed % 4

    if (intensity == AnimationIntensity.OFF) {
        // Tamamen statik mod (sıfır recomposition / transition)
        StaticCompanionFace(fill, ink, kind, mood, modifier)
        return
    }

    // Kullanıcı dokunma tepkisi (küçük sevimli şaşırma / dikkat kesilme)
    var isTapped by remember { mutableStateOf(false) }
    LaunchedEffect(isTapped) {
        if (isTapped) {
            delay(850)
            isTapped = false
        }
    }

    // Animasyon döngüsü (bot kimliğine göre faz kaydırmalı)
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_anim_$identity")

    // 1. Doğal Göz Kırpma (4.6 sn aralıkla)
    val blink by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4600
                1f at 0
                1f at 3900
                0.08f at 4050
                1f at 4200
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )

    // 2. Bakış Yönü (sağa-sola-odaklanma mikro hareketleri)
    val eyeShiftX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5600
                0f at 0
                0.03f at 1600
                0.03f at 2400 // kısa süre sağa odaklanma
                0f at 3400
                -0.03f at 4400
                -0.03f at 5000 // kısa süre sola odaklanma
                0f at 5600
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "eye_x",
    )

    // 3. Nefes ve Hafif Gövde Sallanması
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (intensity == AnimationIntensity.LIGHT) 1.012f else 1.026f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )

    // 4. Baş Eğme / Tatlı Sallanma (Head Tilt)
    val headTiltAngle by infiniteTransition.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tilt",
    )

    // 5. Ayak Hareketi: Ağırlık değiştirme ve Ayak Vurma
    val weightShiftX by infiniteTransition.animateFloat(
        initialValue = -0.015f,
        targetValue = 0.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "weight_shift",
    )

    // Ayak vurma (özellikle beklerken veya sabırsızken)
    val footTapY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3200
                0f at 0
                0f at 2200
                -0.05f at 2350 // ayağı kaldır
                0f at 2450 // yere vur
                -0.04f at 2600 // ikinci küçük ayak vurma
                0f at 2700
                0f at 3200
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "foot_tap",
    )

    // 6. Kulak Mikro Hareketi (asimetrik tek kulak oynaması ve dikleşme)
    val earAsymAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4200
                0f at 0
                4f at 1400
                -6f at 2000 // tek kulak hafif yana düşer
                0f at 2800
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "ear_asym",
    )

    // 7. Esneme / Sıkılma döngüsü
    val yawnScale by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 11000
                0f at 0
                0f at 8200
                1f at 9200 // esneme ağız açılması
                0.8f at 9900
                0f at 10500
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "yawn",
    )

    // 8. Sevinç Zıplaması / Başarı Hareketi
    val successBounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (mood == BotMascotMood.SUCCESS || isTapped) -0.09f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 340, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )

    // 9. Konuşma Ağız Hareketi
    val mouthAnim by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 260, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mouth",
    )

    Canvas(
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
        ) {
            isTapped = true
        },
    ) {
        val w = size.width
        val h = size.height

        // Duruma göre etkili değerler
        val isLight = intensity == AnimationIntensity.LIGHT
        val effectiveBlink = when {
            isTapped -> 1f // dokununca gözler fal taşı gibi açık
            mood == BotMascotMood.SUCCESS -> 0.15f
            mood == BotMascotMood.WAITING -> if (blink < 0.6f) 0.1f else 0.55f // uykulu / sıkılmış göz
            else -> blink
        }

        val effectiveShiftX = when {
            isTapped -> 0f
            mood == BotMascotMood.THINKING -> 0.02f
            else -> eyeShiftX
        }
        val effectiveShiftY = when {
            isTapped -> -0.03f
            mood == BotMascotMood.THINKING -> -0.045f // yukarı bakıp düşünme
            mood == BotMascotMood.WORKING -> 0.02f // odaklanmış aşağı bakış
            else -> 0f
        }

        val effectiveTilt = if (isLight) 0f else if (isTapped) 3f else headTiltAngle
        val effectiveBounce = successBounceY
        val effectiveSway = if (isLight) 0f else weightShiftX
        val effectiveFootTap = if (mood == BotMascotMood.WAITING || (!isLight && mood == BotMascotMood.IDLE)) footTapY else 0f

        val bodyYOffset = effectiveBounce * h
        val bodyXOffset = effectiveSway * w

        rotate(degrees = effectiveTilt, pivot = Offset(w * 0.5f, h * 0.55f)) {
            // ── AYAKLAR (Marshmallow ayaklar) ──────────────────────────
            // İki sevimli basık marshmallow oval ayak; ağırlık aktarımı ve ayak vurma destekli
            val footLeftY = bodyYOffset + (if (effectiveSway < 0) h * 0.008f else 0f)
            val footRightY = bodyYOffset + (if (effectiveSway > 0) h * 0.008f else 0f) + (effectiveFootTap * h)

            // Sol ayak
            drawOval(
                color = fill.copy(alpha = 0.95f),
                topLeft = Offset(w * 0.20f + bodyXOffset, h * 0.81f + footLeftY),
                size = Size(w * 0.25f, h * 0.17f),
            )
            // Sağ ayak (ayak vurabilen ayak)
            drawOval(
                color = fill.copy(alpha = 0.95f),
                topLeft = Offset(w * 0.55f + bodyXOffset, h * 0.81f + footRightY),
                size = Size(w * 0.25f, h * 0.17f),
            )

            // ── KULAKLAR / BOYNUZLAR (Dinamik dikleşme ve asimetrik hareket) ─
            val earLift = when {
                isTapped || mood == BotMascotMood.SUCCESS -> -0.035f // heyecanla dikleşen kulaklar
                mood == BotMascotMood.THINKING -> -0.02f
                mood == BotMascotMood.WAITING -> 0.025f // sıkılınca düşen kulaklar
                else -> 0f
            }
            val leftEarAngle = if (isLight) 0f else earAsymAngle * 0.5f
            val rightEarAngle = if (isLight) 0f else earAsymAngle * 1.2f

            when (kind) {
                0 -> {
                    // Yuvarlak kulaklar (Ayı / Marshmallow tavşan)
                    rotate(degrees = leftEarAngle, pivot = Offset(w * 0.18f, h * 0.25f)) {
                        drawOval(fill, Offset(w * 0.06f + bodyXOffset, h * (0.02f + earLift) + bodyYOffset), Size(w * 0.26f, h * 0.50f))
                    }
                    rotate(degrees = rightEarAngle, pivot = Offset(w * 0.82f, h * 0.25f)) {
                        drawOval(fill, Offset(w * 0.68f + bodyXOffset, h * (0.02f + earLift) + bodyYOffset), Size(w * 0.26f, h * 0.50f))
                    }
                }
                1 -> {
                    // Dairesel yumuşak kedi kulakları
                    for (x in listOf(0.05f, 0.65f)) {
                        val earX = w * (x + 0.15f) + bodyXOffset
                        val earY = h * (0.27f + earLift) + bodyYOffset
                        drawCircle(fill, w * 0.19f, Offset(earX, earY))
                    }
                }
                2 -> {
                    // Üçgen tilki kulakları
                    for (x in listOf(0.12f, 0.62f)) {
                        val earPath = Path().apply {
                            moveTo(w * x + bodyXOffset, h * 0.43f + bodyYOffset)
                            lineTo(w * (x + 0.09f) + bodyXOffset, h * (0.04f + earLift) + bodyYOffset)
                            lineTo(w * (x + 0.27f) + bodyXOffset, h * 0.36f + bodyYOffset)
                            close()
                        }
                        drawPath(earPath, fill)
                    }
                }
                else -> {
                    // Sevimli yumuşak boynuzlar
                    drawOval(fill, Offset(w * 0.30f + bodyXOffset, h * (0.07f + earLift) + bodyYOffset), Size(w * 0.16f, h * 0.36f))
                    drawOval(fill, Offset(w * 0.54f + bodyXOffset, h * (0.03f + earLift) + bodyYOffset), Size(w * 0.16f, h * 0.36f))
                }
            }

            // ── GÖVDE (Nefes alan, yumuşak yuvarlak hatlı marshmallow) ──
            val bodyW = w * 0.76f * breathScale
            val bodyH = h * 0.70f * breathScale
            val bodyLeft = (w - bodyW) * 0.5f + bodyXOffset
            val bodyTop = h * 0.21f + bodyYOffset
            drawRoundRect(
                color = fill,
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(w * 0.32f),
            )

            // ── GÖZLER (Saccade, odaklanma, kırpma, uykulu kapak) ──────
            val eyeW = w * 0.125f * (if (isTapped) 1.25f else 1f)
            val eyeH = w * 0.135f * effectiveBlink * (if (isTapped) 1.25f else 1f)
            val pupilRadius = w * 0.020f

            for (xBase in listOf(0.36f, 0.64f)) {
                val eyeCenterX = w * (xBase + effectiveShiftX) + bodyXOffset
                val eyeCenterY = h * (0.47f + effectiveShiftY) + bodyYOffset

                if (effectiveBlink <= 0.25f) {
                    // Tebessüm eden göz yayı (^ ^)
                    drawArc(
                        color = ink,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(eyeCenterX - eyeW * 0.5f, eyeCenterY - eyeW * 0.3f),
                        size = Size(eyeW, eyeW * 0.6f),
                        style = Stroke(w * 0.026f),
                    )
                } else {
                    // Normal / Meraklı göz oveli
                    drawOval(
                        color = ink,
                        topLeft = Offset(eyeCenterX - eyeW * 0.5f, eyeCenterY - eyeH * 0.5f),
                        size = Size(eyeW, eyeH),
                    )
                    if (effectiveBlink > 0.45f) {
                        // Sevimli parlak göz ışıltısı
                        drawCircle(
                            Color.White,
                            pupilRadius,
                            Offset(eyeCenterX - w * 0.016f, eyeCenterY - w * 0.02f),
                        )
                    }
                }
            }

            // ── YANAKLAR (Tatlı pembe allık) ──────────────────────────
            for (x in listOf(0.24f, 0.76f)) {
                drawOval(
                    color = Color(0xFFFFB2BF).copy(alpha = 0.65f),
                    topLeft = Offset(w * (x - 0.055f) + bodyXOffset, h * 0.62f + bodyYOffset),
                    size = Size(w * 0.11f, h * 0.06f),
                )
            }

            // ── AĞIZ VE YÜZ İFADELERİ ─────────────────────────────────
            when {
                mood == BotMascotMood.SPEAKING -> {
                    // Konuşurken açılıp kapanan ağız
                    val mouthH = h * 0.09f * mouthAnim
                    drawOval(
                        color = ink,
                        topLeft = Offset(w * 0.44f + bodyXOffset, h * 0.64f + bodyYOffset),
                        size = Size(w * 0.12f, mouthH),
                    )
                }
                mood == BotMascotMood.ERROR -> {
                    // Şaşkın 'o' ağız
                    drawCircle(
                        color = ink,
                        radius = w * 0.045f,
                        center = Offset(w * 0.50f + bodyXOffset, h * 0.66f + bodyYOffset),
                        style = Stroke(w * 0.024f),
                    )
                    // Mavi ter damlası rozeti
                    drawCircle(
                        color = Color(0xFF74B9FF),
                        radius = w * 0.035f,
                        center = Offset(w * 0.82f + bodyXOffset, h * 0.35f + bodyYOffset),
                    )
                }
                mood == BotMascotMood.THINKING -> {
                    // Düşünürken meraklı küçük düz çizgi
                    drawLine(
                        color = ink,
                        start = Offset(w * 0.44f + bodyXOffset, h * 0.66f + bodyYOffset),
                        end = Offset(w * 0.56f + bodyXOffset, h * 0.66f + bodyYOffset),
                        strokeWidth = w * 0.024f,
                    )
                }
                mood == BotMascotMood.WAITING -> {
                    if (yawnScale > 0.3f) {
                        // Sıkılınca tatlı esneme
                        drawOval(
                            color = ink,
                            topLeft = Offset(w * 0.44f + bodyXOffset, h * 0.64f + bodyYOffset),
                            size = Size(w * 0.12f, h * 0.08f * yawnScale),
                        )
                    } else {
                        // Hafif düz bekleyen dudak
                        drawLine(
                            color = ink,
                            start = Offset(w * 0.44f + bodyXOffset, h * 0.66f + bodyYOffset),
                            end = Offset(w * 0.54f + bodyXOffset, h * 0.66f + bodyYOffset),
                            strokeWidth = w * 0.022f,
                        )
                    }
                }
                isTapped -> {
                    // Dokununca şaşkın küçük açık ağız
                    drawOval(
                        color = ink,
                        topLeft = Offset(w * 0.45f + bodyXOffset, h * 0.64f + bodyYOffset),
                        size = Size(w * 0.10f, h * 0.07f),
                    )
                }
                else -> {
                    // Mutlu tebessüm yayı
                    drawArc(
                        color = ink,
                        startAngle = 0f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(w * 0.40f + bodyXOffset, h * 0.61f + bodyYOffset),
                        size = Size(w * 0.20f, h * 0.13f),
                        style = Stroke(w * 0.026f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StaticCompanionFace(
    fill: Color,
    ink: Color,
    kind: Int,
    mood: BotMascotMood,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        when (kind) {
            0 -> {
                drawOval(fill, Offset(w * 0.06f, h * 0.02f), Size(w * 0.25f, h * 0.5f))
                drawOval(fill, Offset(w * 0.69f, h * 0.02f), Size(w * 0.25f, h * 0.5f))
            }
            1 -> {
                for (x in listOf(0.05f, 0.65f)) drawCircle(fill, w * 0.18f, Offset(w * (x + 0.15f), h * 0.27f))
            }
            2 -> {
                for (x in listOf(0.12f, 0.62f)) {
                    drawPath(Path().apply {
                        moveTo(w * x, h * 0.43f); lineTo(w * (x + 0.09f), h * 0.05f); lineTo(w * (x + 0.27f), h * 0.36f); close()
                    }, fill)
                }
            }
            else -> {
                drawOval(fill, Offset(w * 0.3f, h * 0.08f), Size(w * 0.15f, h * 0.35f))
                drawOval(fill, Offset(w * 0.52f, h * 0.04f), Size(w * 0.15f, h * 0.35f))
            }
        }
        drawOval(fill, Offset(w * 0.18f, h * 0.81f), Size(w * 0.24f, h * 0.16f))
        drawOval(fill, Offset(w * 0.58f, h * 0.81f), Size(w * 0.24f, h * 0.16f))
        drawRoundRect(fill, Offset(w * 0.12f, h * 0.2f), Size(w * 0.76f, h * 0.72f), CornerRadius(w * 0.32f))

        for (x in listOf(0.36f, 0.64f)) {
            drawOval(ink, Offset(w * (x - 0.06f), h * 0.465f), Size(w * 0.12f, w * 0.13f))
            drawCircle(Color.White, w * 0.018f, Offset(w * (x - 0.015f), h * 0.51f))
        }
        for (x in listOf(0.24f, 0.76f)) {
            drawOval(Color(0xFFFFB2BF).copy(alpha = 0.65f), Offset(w * (x - 0.055f), h * 0.62f), Size(w * 0.11f, h * 0.06f))
        }
        drawArc(ink, 0f, 180f, false, Offset(w * 0.40f, h * 0.61f), Size(w * 0.20f, h * 0.13f), style = Stroke(w * 0.026f))
    }
}
