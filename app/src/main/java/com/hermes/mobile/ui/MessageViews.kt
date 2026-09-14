package com.hermes.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.data.SessionMessage
import com.hermes.mobile.data.ToolCall
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

/**
 * Bir sohbet mesajını rolüne göre çizer.
 *
 * user      → sağa yaslı dolu baloncuk
 * assistant → sola yaslı düz metin (+ varsa araç çağrısı kartları, düşünme bloğu)
 * tool      → katlanabilir sonuç kartı
 * system    → katlanabilir, sönük
 */
@Composable
fun MessageBubble(
    message: SessionMessage,
    modifier: Modifier = Modifier,
    onOpenFile: (FileRef) -> Unit = {},
    /**
     * Tur-4 (H): dökümde düşünme bloğu ayrı "Ayrıntı" satırına katlandığı için
     * burada YENİDEN çizilmez (çift gösterim olurdu).
     */
    withReasoning: Boolean = true,
) {
    when {
        message.isUser -> UserBubble(message, modifier)
        message.isTool -> ToolResultCard(message, modifier)
        message.isSystem -> CollapsedBlock(S.t2("Sistem istemi", "System prompt"), message.content.orEmpty(), modifier)
        else -> AssistantBlock(message, modifier, onOpenFile, withReasoning)
    }
}

@Composable
private fun UserBubble(message: SessionMessage, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .background(HermesColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, HermesColors.BorderStrong, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                message.content.orEmpty(),
                color = HermesColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun AssistantBlock(
    message: SessionMessage,
    modifier: Modifier,
    onOpenFile: (FileRef) -> Unit = {},
    withReasoning: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (withReasoning && !message.reasoning.isNullOrBlank()) {
            CollapsedBlock(S.t2("Düşünme", "Thinking"), message.reasoning, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
        }
        if (!message.content.isNullOrBlank()) {
            MarkdownText(message.content, Modifier.fillMaxWidth())
            FileRefRow(remember(message.content) { extractFileRefs(message.content) }, onOpenFile)
        }
        message.toolCalls.forEach { call ->
            Spacer(Modifier.height(6.dp))
            ToolCallCard(call)
        }
    }
}

@Composable
private fun ToolCallCard(call: ToolCall) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
            .border(1.dp, HermesColors.Border, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("→", color = HermesColors.Busy, fontSize = 12.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                call.function.name?.takeIf { it.isNotBlank() } ?: "araç",
                style = MonoTextStyle,
                color = HermesColors.Midground,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = HermesColors.TextFaint,
                modifier = Modifier.width(16.dp),
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(
                    call.function.arguments.orEmpty().take(2_000),
                    style = MonoTextStyle,
                    color = HermesColors.TextMuted,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun ToolResultCard(message: SessionMessage, modifier: Modifier) {
    val body = message.content.orEmpty()
    val looksFailed = body.contains("\"error\"") || body.startsWith("BLOCKED", ignoreCase = true) ||
        body.contains("BLOCKED:")
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
            .border(
                1.dp,
                if (looksFailed) HermesColors.Danger.copy(alpha = 0.45f) else HermesColors.Border,
                RoundedCornerShape(8.dp),
            )
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (looksFailed) "✕" else "✓",
                color = if (looksFailed) HermesColors.Danger else HermesColors.Online,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                message.toolName ?: "sonuç",
                style = MonoTextStyle,
                color = HermesColors.TextSecondary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                S.t2("${body.length} krkt", "${body.length} chars"),
                color = HermesColors.TextFaint,
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = HermesColors.TextFaint,
                modifier = Modifier.width(16.dp),
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(
                    body.take(4_000),
                    style = MonoTextStyle,
                    color = HermesColors.TextMuted,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun CollapsedBlock(label: String, body: String, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier
            .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = HermesColors.TextFaint, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = HermesColors.TextFaint,
                modifier = Modifier.width(16.dp),
            )
        }
        AnimatedVisibility(expanded) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(body.take(4_000), color = HermesColors.TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}
