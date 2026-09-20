package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    // Tur18 B3: sabit 15.sp yerine prose rolu (15 x fontScale) — sohbet govdesi
    // de artik sistem/app yazi olcegiyle olceklenir (tur3 prose noktasi sabit).
    fontSize: androidx.compose.ui.unit.TextUnit = MaterialTheme.typography.bodyLarge.fontSize,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    // Telegram: ardışık maddeler BİTİŞİK, bloklar arası boş satır. Tek sabit
    // 7dp yerine grup ayrımı — Tight içi sıfır boşluk, gruplar arası nefes.
    val groups = remember(blocks) { groupBlocks(blocks) }
    val inline = rememberInlineColors()

    // Satır aralığı ~1.45 (FR: 1.4-1.5); satırsonu yüksekliği govde fontuna bağli.
    val bodyLine = fontSize * 1.45f

    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        groups.forEach { group ->
            when (group) {
                is MdGroup.Tight -> Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    group.items.forEach { ListItemRow(it, inline, color, fontSize, bodyLine) }
                }

                is MdGroup.Solo -> when (val block = group.block) {
                    is MdBlock.Code -> CodeBlock(block)

                    // FR-001: hiyerarşi KALINLIKLA, boyutla değil — başlık gövdeyle
                    // aynı punto, yalnız daha ağır (SemiBold). Eski 19/17/15.sp
                    // boyut şişirmesi kaldırıldı.
                    is MdBlock.Heading -> Text(
                        inlineMarkdown(block.text, inline),
                        color = HermesColors.TextPrimary,
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = bodyLine,
                    )

                    // FR-005: gövde Telegram ikincil tonu (#d1d1d1 civarı), 15sp.
                    is MdBlock.Paragraph -> Text(
                        inlineMarkdown(block.text, inline),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = bodyLine,
                    )

                    is MdBlock.Quote -> Row(
                        Modifier
                            .fillMaxWidth()
                            .background(HermesColors.SurfaceDim)
                            .padding(start = 3.dp),
                    ) {
                        Spacer(
                            Modifier
                                .width(2.dp)
                                .height(20.dp)
                                .background(HermesColors.BorderStrong),
                        )
                        Text(
                            // Alıntı içi devam satırları da \n taşır; tek Text.
                            inlineMarkdown(block.text, inline),
                            color = HermesColors.TextMuted,
                            fontSize = fontSize,
                            lineHeight = bodyLine,
                            modifier = Modifier.padding(start = 9.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }

                    MdBlock.Rule -> Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(HermesColors.Border),
                    )

                    // Yalnız liste maddesi Tight'a gider; Solo'ya düşen tek madde
                    // (liste tek satırlık) burada da doğru çizilir.
                    is MdBlock.Bullet, is MdBlock.Numbered ->
                        ListItemRow(block, inline, color, fontSize, bodyLine)
                }
            }
        }
    }
}

/**
 * Bir liste maddesi: işareti sabit genişlikte bir sütunda (gutter) tutar,
 * gövde kalan alanı `weight` ile kaplar. Madde sarıldığında DEVAM satırı
 * gövde sütununun solundan — yani madde metni hizasından — başlar; sola
 * kaymaz (hanging indent, FR-002). Gövde tek `Text` olduğu için içindeki
 * "\n" satırsonları doğal olarak aynı hizada sarar.
 */
@Composable
private fun ListItemRow(
    block: MdBlock,
    inline: InlineColors,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    bodyLine: androidx.compose.ui.unit.TextUnit,
) {
    val level = block.levelIndentDp
    val mark = when (block) {
        is MdBlock.Numbered -> "${block.number}."
        else -> "•"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = level.dp),
    ) {
        // Sabit işareti sütunu: tüm maddelerde işaret aynı x'te, gövde aynı
        // x'te başlar → sarma hizası sabit kalır.
        Text(
            mark,
            color = HermesColors.Midground,
            fontSize = fontSize,
            lineHeight = bodyLine,
            modifier = Modifier.width(LIST_MARK_GUTTER_DP.dp),
        )
        Text(
            inlineMarkdown(block.textForItem(), inline),
            color = color,
            fontSize = fontSize,
            lineHeight = bodyLine,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Maddenin gövde metni (Bullet/Numbered için [text], diğerlerinde boş). */
private fun MdBlock.textForItem(): String = when (this) {
    is MdBlock.Bullet -> text
    is MdBlock.Numbered -> text
    else -> ""
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val clipboard = LocalClipboardManager.current
    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxWidth()
            // Tur18 FR-009 (tur17 finding #5 kapanisi): kod blogu dolgusu artik
            // Midground (=aksan) degil — ntr Skeleton yuzey tonu. Sol 3dp cizgi
            // Midground kalir (vurgu gorevi), dolguyu aksana cevirmek B2 ihlaliydi.
            .background(HermesColors.Skeleton, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium),
    ) {
        // Sol dikey vurgu çizgisi + içerik.
        Row(Modifier.fillMaxWidth()) {
            Spacer(
                Modifier
                    .width(3.dp)
                    .height(IntrinsicSize.Min)
                    // FR-009 (L202, tur17 finding #5): kod blogu VURGU CIZGISI
                    // artik Midground (=aksan) degil — ntr BorderStrong. Kod
                    // blogu vurgu degil icerik tasiyan ntr yuzeydir; aksani
                    // vurgu butonuna birakiriz (B2 kurali).
                    .background(HermesColors.BorderStrong),
            )
            Column(Modifier.weight(1f)) {
                // Üstte yalnız dil etiketi (satır sayısı YOK — sade).
                if (block.language.isNotBlank()) {
                    Text(
                        block.language.lowercase(),
                        color = HermesColors.TextFaint,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 10.dp, top = 6.dp, end = 10.dp),
                    )
                }
                Text(
                    block.code,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = HermesColors.TextSecondary,
                    modifier = Modifier
                        .horizontalScroll(scroll)
                        .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 10.dp),
                )
            }
        }
        // Altta ayraç + ortalanmış BÜYÜK HARF "KODU KOPYALA" (ikon + metin).
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(HermesColors.Border),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                .clickable { clipboard.setText(AnnotatedString(block.code)) }
                .padding(vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = null,
                tint = HermesColors.Midground,
                modifier = Modifier.width(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                S.t2("KODU KOPYALA", "COPY CODE"),
                color = HermesColors.Midground,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            )
        }
    }
}

// ── Ayrıştırma ────────────────────────────────────────────────────────

// internal: JVM'de Compose'suz test edilebilir render modeli (FR-002/FR-003).
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock

    /**
     * Madde. [text] birden fazla fizik satır taşıyabilir: bir madde sarıldığında
     * (devam satırı tire/numara taşımaz) devam satırları [text] içine "\n" ile
     * birleştirilir — render tarafı bunu TEK blok olarak çizip devam satırını
     * madde metni hizasından sardırır (hanging indent, FR-002). [indent] üst
     * seviye boşluk sayısı (ikili girinti = 1 kademe).
     */
    data class Bullet(val text: String, val indent: Int) : MdBlock
    data class Numbered(val number: Int, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock

    /** Liste maddesi mi? (bitişik çizim + gruplama kararı için). */
    val isListItem: Boolean get() = this is Bullet || this is Numbered

    /** İç içe kademe girintisi dp (render ve test aynı kaynaktan okur). */
    val levelIndentDp: Int
        get() = when (this) {
            is Bullet -> indent * LIST_LEVEL_INDENT_DP
            is Numbered -> indent * LIST_LEVEL_INDENT_DP
            else -> 0
        }
}

/** Liste işareti sütunu genişliği dp (Telegram ~24dp toplam kenar boşluğu). */
const val LIST_MARK_GUTTER_DP = 20
/** İç içe liste kademesi başına ek girinti. */
const val LIST_LEVEL_INDENT_DP = 14

// internal: saf gruplama (FR-003 — bitişik maddeler tek sıkı grup, blok
// kırılımları ayrı grup). Compose'suz test edilir.
internal sealed interface MdGroup {
    /** Sıkı çizilen ardışık satırlar (liste maddeleri): aralarında boşluk YOK. */
    data class Tight(val items: List<MdBlock>) : MdGroup
    /** Tek blok (paragraf/başlık/kod/alıntı/çizgi): blok arası boşluk görür. */
    data class Solo(val block: MdBlock) : MdGroup
}

/**
 * Ardışık liste maddelerini tek [MdGroup.Tight] grubunda toplar; diğer
 * bloklar solo. Telegram'da maddeler bitişik, bloklar arası boş satırla
 * ayrılır — ayrımı markdown'daki blok sınırından değil komşuluktan alıyoruz.
 */
internal fun groupBlocks(blocks: List<MdBlock>): List<MdGroup> {
    val out = mutableListOf<MdGroup>()
    var run = mutableListOf<MdBlock>()
    fun flush() {
        if (run.isEmpty()) return
        out += if (run.size == 1) MdGroup.Solo(run.first())
               else MdGroup.Tight(run.toList())
        run = mutableListOf()
    }
    for (b in blocks) {
        if (b.isListItem) run += b else { flush(); out += MdGroup.Solo(b) }
    }
    flush()
    return out
}

internal val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
internal val NUMBERED = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
internal val HEADING = Regex("""^(#{1,6})\s+(.*)$""")

private fun indentOf(leading: String): Int = leading.length / 2

/** Satır bir blok başlangıcı mı? (devam satırı tespiti için). */
private fun startsBlock(line: String): Boolean =
    line.isBlank() ||
        line.trimStart().startsWith("```") ||
        line.trim().let { it == "---" || it == "***" || it == "___" } ||
        HEADING.matches(line) ||
        BULLET.matches(line) ||
        NUMBERED.matches(line) ||
        line.trimStart().startsWith("> ")

internal fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = source.split("\n")
    var i = 0
    val paragraph = StringBuilder()
    // Açık liste maddesi: devam satırları (tire/numara olmayan, boş olmayan)
    // buraya "\n" ile eklenir → tek madde, doğru hanging indent.
    var itemLines: MutableList<String>? = null
    var itemIndent = 0
    var itemNumber = 0   // >0 ise numaralı madde
    var itemQuote = false

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.setLength(0)
    }

    fun flushItem() {
        val lines2 = itemLines ?: return
        val text = lines2.joinToString("\n")
        itemLines = null
        when {
            itemQuote -> blocks += MdBlock.Quote(text)
            itemNumber > 0 -> blocks += MdBlock.Numbered(itemNumber, text, itemIndent)
            else -> blocks += MdBlock.Bullet(text, itemIndent)
        }
        itemNumber = 0
        itemQuote = false
        itemIndent = 0
    }

    fun flushAll() { flushItem(); flushParagraph() }

    while (i < lines.size) {
        val line = lines[i]

        // Çitli kod bloğu — kapanış yoksa akış hâlâ sürüyordur, sona kadar al.
        if (line.trimStart().startsWith("```")) {
            flushAll()
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

        // Boş satır = blok kırılımı: açık madde/paragrafı kapatır.
        if (line.isBlank()) {
            flushAll()
            i++
            continue
        }

        if (line.trim().let { it == "---" || it == "***" || it == "___" }) {
            flushAll()
            blocks += MdBlock.Rule
            i++
            continue
        }

        if (HEADING.matches(line)) {
            flushAll()
            val m = HEADING.find(line)!!
            blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
            i++
            continue
        }

        if (BULLET.matches(line)) {
            flushParagraph()
            flushItem()
            val m = BULLET.find(line)!!
            itemLines = mutableListOf(m.groupValues[2].trim())
            itemIndent = indentOf(m.groupValues[1])
            itemNumber = 0
            itemQuote = false
            i++
            continue
        }

        if (NUMBERED.matches(line)) {
            flushParagraph()
            flushItem()
            val m = NUMBERED.find(line)!!
            itemLines = mutableListOf(m.groupValues[3].trim())
            itemIndent = indentOf(m.groupValues[1])
            itemNumber = m.groupValues[2].toIntOrNull() ?: 1
            itemQuote = false
            i++
            continue
        }

        if (line.trimStart().startsWith("> ")) {
            flushAll()
            itemQuote = true
            itemIndent = 0
            itemLines = mutableListOf(line.trimStart().removePrefix("> ").trim())
            i++
            continue
        }

        // Buraya kadar: boş olmayan, blok işareti taşımayan satır.
        // Açık madde varsa DEVAM satırı (hanging); yoksa paragraf satırı.
        val open = itemLines
        if (open != null && !startsBlock(line)) {
            open += line.trim()
            i++
            continue
        }

        flushItem()
        if (paragraph.isNotEmpty()) paragraph.append('\n')
        paragraph.append(line)
        i++
    }
    flushAll()
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
