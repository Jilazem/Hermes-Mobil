package com.hermes.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hermes.mobile.assistant.JarvisPhase
import com.hermes.mobile.assistant.JarvisState
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** KITT kırmızısı ve tonları. */
private val KittRed = Color(0xFFFF1E1E)
private val KittDeep = Color(0xFF7A0000)
private val Glass = Color(0xF20B0F13)
private val GlassEdge = Color(0x33FFFFFF)

/**
 * Jarvis paneli: üstte KITT tarayıcı çizgisi, altta konuşma kartı. Kart dışı
 * saydam ve (session tarafında) dokunmaya kapalı — arkadaki uygulama çalışır.
 */
@Composable
fun JarvisOverlay(
    state: JarvisState,
    onMic: () -> Unit,
    onClose: () -> Unit,
    onNewTopic: () -> Unit,
    onOpenChat: () -> Unit,
    onAsk: (String) -> Unit,
    onCardBounds: (Int, Int, Int, Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        KittScannerLine(
            phase = state.phase,
            level = state.level,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(6.dp),
        )
        JarvisCard(
            state = state,
            onMic = onMic,
            onClose = onClose,
            onNewTopic = onNewTopic,
            onOpenChat = onOpenChat,
            onAsk = onAsk,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .onGloballyPositioned { c ->
                    val b = c.boundsInWindow()
                    onCardBounds(b.left.roundToInt(), b.top.roundToInt(), b.right.roundToInt(), b.bottom.roundToInt())
                },
        )
    }
}

@Composable
fun JarvisCard(
    state: JarvisState,
    onMic: () -> Unit,
    onClose: () -> Unit,
    onNewTopic: () -> Unit,
    onOpenChat: () -> Unit,
    onAsk: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val active = state.phase != JarvisPhase.Idle && state.phase != JarvisPhase.Error
    val edge = if (active) KittRed.copy(alpha = 0.55f) else GlassEdge

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Glass)
            .border(1.dp, edge, RoundedCornerShape(26.dp))
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
    ) {
        // ── KITT vizörü: siyah şerit içinde kırmızı tarayıcı ─────────────
        Box(
            Modifier
                .padding(end = 8.dp)
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF050505))
                .border(1.dp, Color(0xFF1C1C1C), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            KittScannerLine(state.phase, state.level, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        // ── Başlık: KITT ses kutusu + durum + eylemler ──────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            KittVoiceBox(state.phase, state.level, Modifier.size(width = 46.dp, height = 34.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    statusText(state),
                    color = if (state.phase == JarvisPhase.Error) Color(0xFFFF8A80) else Color(0xFFFFD6D6),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (state.heard.isNotBlank()) {
                    Text(
                        state.heard,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onNewTopic) {
                Icon(Icons.Default.Refresh, S.t2("Yeni konu", "New topic"), tint = Color(0xB3FFFFFF))
            }
            IconButton(onClick = onOpenChat) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, S.t2("Sohbette aç", "Open in chat"), tint = Color(0xB3FFFFFF))
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, S.t2("Kapat", "Close"), tint = Color(0xB3FFFFFF))
            }
        }

        // ── Yanıt ────────────────────────────────────────────────────────
        val body = state.error ?: state.answer
        AnimatedVisibility(body.isNotBlank() || state.hint != null) {
            Column(
                Modifier
                    .padding(top = 8.dp, end = 8.dp)
                    .heightIn(max = 190.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (body.isNotBlank()) {
                    Text(
                        body,
                        color = if (state.error != null) Color(0xFFFF8A80) else Color(0xE6FFFFFF),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                state.hint?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = Color(0xFFFFB4A9), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // ── Alt sıra: yazarak sor | mikrofon ─────────────────────────────
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (typing) {
                TextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(S.t2("Yaz ve gönder…", "Type and send…"), color = Color(0x80FFFFFF)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        onAsk(draft); draft = ""; typing = false
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0x1AFFFFFF),
                        unfocusedContainerColor = Color(0x1AFFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = KittRed,
                    ),
                    shape = RoundedCornerShape(18.dp),
                )
                IconButton(onClick = { onAsk(draft); draft = ""; typing = false }) {
                    Icon(Icons.AutoMirrored.Filled.Send, S.t2("Gönder", "Send"), tint = KittRed)
                }
            } else {
                IconButton(onClick = { typing = true }) {
                    Icon(Icons.Default.Keyboard, S.t2("Yazarak sor", "Type"), tint = Color(0xB3FFFFFF))
                }
                Spacer(Modifier.weight(1f))
                MicButton(state.phase, state.level, onMic)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

@Composable
private fun statusText(s: JarvisState): String = when (s.phase) {
    JarvisPhase.Listening -> S.t2("Dinliyorum…", "Listening…")
    JarvisPhase.Thinking -> s.tool?.let { S.t2("Çalışıyorum · $it", "Working · $it") }
        ?: S.t2("Düşünüyorum…", "Thinking…")
    JarvisPhase.Speaking -> S.t2("Hermes", "Hermes")
    JarvisPhase.Error -> S.t2("Bir sorun var", "Something went wrong")
    JarvisPhase.Idle -> S.t2("Konuşmak için dokun", "Tap to talk")
}

/** Büyük yuvarlak mikrofon: dinlerken kırmızı nabız, konuşurken "kes" (■). */
@Composable
private fun MicButton(phase: JarvisPhase, level: Float, onClick: () -> Unit) {
    val lvl by animateFloatAsState(level, tween(90), label = "mic")
    val listening = phase == JarvisPhase.Listening
    val busy = phase == JarvisPhase.Speaking || phase == JarvisPhase.Thinking
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(68.dp)) {
        if (listening) {
            Box(
                Modifier
                    .size((50 + 18 * lvl).dp)
                    .clip(CircleShape)
                    .background(KittRed.copy(alpha = 0.22f)),
            )
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (listening) Brush.radialGradient(listOf(KittRed, KittDeep))
                    else Brush.radialGradient(listOf(Color(0xFF3A3F45), Color(0xFF1E2226))),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (busy) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (busy) S.t2("Kes ve dinle", "Interrupt") else S.t2("Konuş", "Talk"),
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/**
 * KITT ön ışık çizgisi. Düşünürken kırmızı ışık sağa-sola süpürür (Larson
 * tarayıcı); dinlerken ve konuşurken ortadan dışa doğru ses düzeyiyle
 * açılır (KITT ses kutusu gibi); boştayken sönük nefes alır.
 */
@Composable
fun KittScannerLine(phase: JarvisPhase, level: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "kitt")
    val sweep by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "sweep",
    )
    val breath by t.animateFloat(
        0.15f, 0.35f,
        infiniteRepeatable(tween(1600), RepeatMode.Reverse),
        label = "breath",
    )
    val lvl by animateFloatAsState(level, tween(80), label = "lvl")
    Canvas(modifier) {
        val n = 36
        val gap = 2.dp.toPx()
        val segW = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val x = (i + 0.5f) / n
            val a = segmentIntensity(phase, x, sweep, lvl, breath)
            val left = i * (segW + gap)
            // Hale (glow): biraz daha geniş ve saydam.
            drawRoundRect(
                KittRed.copy(alpha = (a * 0.35f).coerceIn(0f, 1f)),
                topLeft = Offset(left - 2f, 0f),
                size = Size(segW + 4f, size.height),
                cornerRadius = CornerRadius(3f, 3f),
            )
            drawRoundRect(
                KittRed.copy(alpha = a.coerceIn(0.06f, 1f)),
                topLeft = Offset(left, size.height * 0.2f),
                size = Size(segW, size.height * 0.6f),
                cornerRadius = CornerRadius(2f, 2f),
            )
        }
    }
}

/** 0..1 parlaklık; saf fonksiyon (KittScannerTest). */
internal fun segmentIntensity(phase: JarvisPhase, x: Float, sweep: Float, level: Float, breath: Float): Float =
    when (phase) {
        JarvisPhase.Thinking -> {
            val d = (x - sweep) / 0.07f
            exp(-(d * d)).toFloat()
        }
        JarvisPhase.Listening, JarvisPhase.Speaking -> {
            val reach = 0.08f + level.coerceIn(0f, 1f) * 0.92f
            val d = abs(x - 0.5f) * 2f
            if (d <= reach) (1f - d / reach * 0.65f) else 0.08f
        }
        JarvisPhase.Error -> breath * 1.8f
        JarvisPhase.Idle -> breath
    }

/**
 * KITT ses kutusu (ünlü üç sütun): ortadaki sütun uzun, yanlar kısa; ses
 * düzeyi arttıkça çubuklar ortadan dışa yanar.
 */
@Composable
fun KittVoiceBox(phase: JarvisPhase, level: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "box")
    val wobble by t.animateFloat(0f, 6.283f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "w")
    val lvl by animateFloatAsState(level, tween(70), label = "l")
    Canvas(modifier) {
        val colW = size.width / 3.6f
        val barH = size.height / 9f
        listOf(0 to 5, 1 to 9, 2 to 5).forEach { (col, bars) ->
            val cx = size.width * (0.18f + col * 0.32f)
            val drive = when (phase) {
                JarvisPhase.Speaking -> (lvl * 0.8f + 0.2f * (0.5f + 0.5f * sin(wobble + col))).coerceIn(0f, 1f)
                JarvisPhase.Listening -> lvl
                JarvisPhase.Thinking -> 0.25f + 0.2f * (0.5f + 0.5f * sin(wobble * 2 + col * 2))
                else -> 0.12f
            }
            val lit = (drive * bars).roundToInt().coerceAtLeast(1)
            for (b in 0 until bars) {
                val fromCenter = abs(b - (bars - 1) / 2f)
                val on = fromCenter <= lit / 2f
                val y = (size.height - bars * barH) / 2f + b * barH
                drawRoundRect(
                    if (on) KittRed else KittDeep.copy(alpha = 0.35f),
                    topLeft = Offset(cx - colW / 2f, y + barH * 0.18f),
                    size = Size(colW, barH * 0.64f),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
        }
    }
}

/** Önizleme/snapshot için örnek durumlar. */
internal val jarvisPreviewStates = listOf(
    JarvisState(JarvisPhase.Listening, heard = "Yarın hava nasıl olacak", level = 0.7f),
    JarvisState(JarvisPhase.Thinking, heard = "WhatsApp'tan Ali ne yazmış", tool = "phone_messages"),
    JarvisState(
        JarvisPhase.Speaking,
        heard = "Yarın hava nasıl olacak",
        answer = "Yarın İstanbul'da parçalı bulutlu, en yüksek 22 derece efendim. Akşama doğru hafif rüzgâr bekleniyor.",
        level = 0.55f,
    ),
)
