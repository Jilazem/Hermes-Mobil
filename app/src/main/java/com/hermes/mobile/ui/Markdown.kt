package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ui.theme.HermesColors

/**
 * Hafif Markdown görüntüleyici.
 *
 * Sohbet yanıtları için gereken alt kümeyi kapsar: başlıklar, kalın/eğik,
 * satır içi kod, çitli kod blokları (dil etiketi + kopyala), madde ve numaralı
 * listeler, alıntılar, yatay çizgi ve bağlantı metni. Harici bağımlılık yok —
 * tema tamamen Hermes paletine bağlı kalır.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = HermesColors.TextSecondary,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    val inline = rememberInlineColors()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(block)
                is MdBlock.Heading -> Text(
                    inlineMarkdown(block.text, inline),
                    color = HermesColors.TextPrimary,
                    fontSize = when (block.level) {
                        1 -> 19.sp
                        2 -> 17.sp
                        else -> 15.sp
                    },
                    fontWeight = FontWeight.Medium,
                    lineHeight = 24.sp,
                )

                is MdBlock.Paragraph -> Text(
                    inlineMarkdown(block.text, inline),
                    color = color,
                    fontSize = fontSize,
                    lineHeight = fontSize * 1.5f,
                )

                is MdBlock.Bullet -> Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width((block.indent * 14).dp))
                    Text("•", color = HermesColors.Midground, fontSize = fontSize, lineHeight = fontSize * 1.5f)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        inlineMarkdown(block.text, inline),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.5f,
                    )
                }

                is MdBlock.Numbered -> Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width((block.indent * 14).dp))
                    Text(
                        "${block.number}.",
                        color = HermesColors.Midground,
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.5f,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        inlineMarkdown(block.text, inline),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.5f,
                    )
                }

                is MdBlock.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .background(HermesColors.SurfaceDim)
                        .padding(start = 3.dp)
                ) {
                    Spacer(
                        Modifier
                            .width(2.dp)
                            .height(20.dp)
                            .background(HermesColors.BorderStrong)
                    )
                    Text(
                        inlineMarkdown(block.text, inline),
                        color = HermesColors.TextMuted,
                        fontSize = fontSize,
                        lineHeight = fontSize * 1.5f,
                        modifier = Modifier.padding(start = 9.dp, top = 4.dp, bottom = 4.dp),
                    )
                }

                MdBlock.Rule -> Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(HermesColors.Border)
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, RoundedCornerShape(8.dp))
            .border(1.dp, HermesColors.Border, RoundedCornerShape(8.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 2.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                block.language.ifBlank { "kod" },
                color = HermesColors.TextFaint,
                fontSize = 10.sp,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Kodu kopyala",
                    tint = HermesColors.TextMuted,
                    modifier = Modifier.width(15.dp),
                )
            }
        }
        Text(
            block.code,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = HermesColors.TextSecondary,
            modifier = Modifier
                .horizontalScroll(scroll)
                .padding(start = 10.dp, end = 10.dp, bottom = 9.dp),
        )
    }
}

// ── Ayrıştırma ────────────────────────────────────────────────────────

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Bullet(val text: String, val indent: Int) : MdBlock
    data class Numbered(val number: Int, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
}

private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val NUMBERED = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")

private fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = source.split("\n")
    var i = 0
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.setLength(0)
    }

    while (i < lines.size) {
        val line = lines[i]

        // Çitli kod bloğu — kapanış yoksa akış hâlâ sürüyordur, sona kadar al.
        if (line.trimStart().startsWith("```")) {
            flushParagraph()
            val language = line.trimStart().removePrefix("```").trim()
            val code = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                code.appendLine(lines[i])
                i++
            }
            i++ // kapanış çitini atla
            blocks += MdBlock.Code(language, code.toString().trimEnd())
            continue
        }

        when {
            line.isBlank() -> flushParagraph()

            line.trim().let { it == "---" || it == "***" || it == "___" } -> {
                flushParagraph()
                blocks += MdBlock.Rule
            }

            HEADING.matches(line) -> {
                flushParagraph()
                val m = HEADING.find(line)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            }

            BULLET.matches(line) -> {
                flushParagraph()
                val m = BULLET.find(line)!!
                blocks += MdBlock.Bullet(m.groupValues[2], m.groupValues[1].length / 2)
            }

            NUMBERED.matches(line) -> {
                flushParagraph()
                val m = NUMBERED.find(line)!!
                blocks += MdBlock.Numbered(
                    m.groupValues[2].toIntOrNull() ?: 1,
                    m.groupValues[3],
                    m.groupValues[1].length / 2,
                )
            }

            line.trimStart().startsWith("> ") -> {
                flushParagraph()
                blocks += MdBlock.Quote(line.trimStart().removePrefix("> "))
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

/**
 * Satır içi biçimleme: `kod`, **kalın**, *eğik*, ~~üstü çizili~~, [metin](url).
 *
 * İç içe biçimler için özyinelemeli — `**\`kod\`**` gibi bir dizide hem kalınlık
 * hem kod stili uygulanır, işaretler düz metne sızmaz. Açılış işaretinin eşleşen
 * kapanışı yoksa olduğu gibi bırakılır, böylece yarım akan yanıtlar bozulmaz.
 * Kod aralığının içi bilinçli olarak ayrıştırılmaz — kod aynen görünmeli.
 */
/**
 * Satır içi biçimlemede kullanılan renkler.
 *
 * Tema desteği gelince `HermesColors` `@Composable` okumaya dönüştü; ayrıştırıcı
 * ise düz bir fonksiyon. Renkleri çağrı yerinde toplayıp buradan geçiriyoruz —
 * ayrıştırıcıyı composable yapmak `buildAnnotatedString` ile uyumsuz olurdu.
 */
private data class InlineColors(
    val code: Color,
    val codeBackground: Color,
    val strong: Color,
    val link: Color,
)

@Composable
private fun rememberInlineColors(): InlineColors = InlineColors(
    code = HermesColors.Midground,
    codeBackground = HermesColors.SurfaceDim,
    strong = HermesColors.TextPrimary,
    link = HermesColors.Midground,
)

private fun inlineMarkdown(source: String, colors: InlineColors): AnnotatedString =
    buildAnnotatedString { appendInline(source, depth = 0, colors = colors) }

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
    source: String,
    depth: Int,
    colors: InlineColors,
) {
    if (depth > 4) {
        append(source)
        return
    }

    fun findClose(marker: String, from: Int): Int {
        val idx = source.indexOf(marker, from)
        return if (idx > from) idx else -1
    }

    var i = 0
    while (i < source.length) {
        when {
            source.startsWith("```", i) -> { append("```"); i += 3 }

            source[i] == '`' -> {
                val close = findClose("`", i + 1)
                if (close < 0) { append(source[i]); i++ } else {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = colors.code,
                            background = colors.codeBackground,
                        )
                    ) { append(source.substring(i + 1, close)) }
                    i = close + 1
                }
            }

            source.startsWith("**", i) -> {
                val close = findClose("**", i + 2)
                if (close < 0) { append("**"); i += 2 } else {
                    withStyle(
                        SpanStyle(fontWeight = FontWeight.Medium, color = colors.strong)
                    ) { appendInline(source.substring(i + 2, close), depth + 1, colors) }
                    i = close + 2
                }
            }

            source.startsWith("~~", i) -> {
                val close = findClose("~~", i + 2)
                if (close < 0) { append("~~"); i += 2 } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInline(source.substring(i + 2, close), depth + 1, colors)
                    }
                    i = close + 2
                }
            }

            source[i] == '*' -> {
                val close = findClose("*", i + 1)
                if (close < 0) { append('*'); i++ } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(source.substring(i + 1, close), depth + 1, colors)
                    }
                    i = close + 1
                }
            }

            source[i] == '[' -> {
                val closeBracket = source.indexOf(']', i)
                val openParen = if (closeBracket > 0) closeBracket + 1 else -1
                val closeParen =
                    if (openParen in source.indices && source[openParen] == '(')
                        source.indexOf(')', openParen)
                    else -1
                if (closeParen < 0) { append(source[i]); i++ } else {
                    withStyle(
                        SpanStyle(
                            color = colors.link,
                            textDecoration = TextDecoration.Underline,
                        )
                    ) { appendInline(source.substring(i + 1, closeBracket), depth + 1, colors) }
                    i = closeParen + 1
                }
            }

            else -> { append(source[i]); i++ }
        }
    }
}
