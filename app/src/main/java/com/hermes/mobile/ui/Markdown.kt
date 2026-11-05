package com.hermes.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.hermes.mobile.data.MediaKind
import com.hermes.mobile.data.mediaKindOf
import com.hermes.mobile.data.safeExternalUrl
import com.hermes.mobile.data.isServerMediaPath
import com.hermes.mobile.data.serverMediaUrl
import com.hermes.mobile.data.conversationMediaKind
import com.hermes.mobile.data.isInlineConversationImage
import com.hermes.mobile.ui.theme.HermesColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hafif Markdown görüntüleyici.
 *
 * Sohbet yanıtları için gereken alt kümeyi kapsar: başlıklar, kalın/eğik,
 * satır içi kod, çitli kod blokları (dil etiketi + kopyala), madde ve numaralı
 * listeler, alıntılar, yatay çizgi ve bağlantı metni. Harici bağımlılık yok —
 * tema tamamen Hermes paletine bağlı kalır.
 */
/**
 * Akış imleci soneki — TUR-29A (TokenFlow deseni).
 *
 * İmleç " ▌" artık ham markdown'a EKLENİP yeniden ayrıştırılmaz; yalnız SON
 * bloğun çizim metnine render sırasında eklenir. Böylece (1) akan kod bloğunun
 * kopyalanan içeriği imleçle bozulmaz, (2) tamamlanan blokların çizim girdileri
 * token'dan token'a değişmez — Compose bunları atlar (skip), yerleşim yalnız
 * değişen son blokta yeniden yapılır.
 */
internal fun streamCursorSuffix(streaming: Boolean, isLastBlock: Boolean): String =
    if (streaming && isLastBlock) " ▌" else ""

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = HermesColors.TextSecondary,
    // Tur18 B3: sabit 15.sp yerine prose rolu (15 x fontScale) — sohbet govdesi
    // de artik sistem/app yazi olcegiyle olceklenir (tur3 prose noktasi sabit).
    fontSize: androidx.compose.ui.unit.TextUnit = MaterialTheme.typography.bodyLarge.fontSize,
    /**
     * TUR-29A: metin hâlâ akıyor mu? true ise imleç yalnız son bloğa çizilir;
     * ayrıştırıcı ham metni olduğu gibi görür (imleç içerik karışmaz).
     * Tamamlanan balonlar için varsayılan false — davranış değişmez.
     */
    streaming: Boolean = false,
    /**
     * V5-2: bağlantıya dokunma. Tıklanabilir bağlantılar bu geri çağrıyı
     * GÜVENLİ URL ile çağırır; açma onayı çağıranda ([ChatScreen]).
     */
    onOpenLink: (String) -> Unit = rememberExternalLinkOpener(),
    selectable: Boolean = true,
) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    // Telegram: ardışık maddeler BİTİŞİK, bloklar arası boş satır. Tek sabit
    // 7dp yerine grup ayrımı — Tight içi sıfır boşluk, gruplar arası nefes.
    val groups = remember(blocks) { groupBlocks(blocks) }
    val inline = rememberInlineColors()

    // Satır aralığı ~1.45 (FR: 1.4-1.5); satırsonu yüksekliği govde fontuna bağli.
    val bodyLine = fontSize * 1.45f

    MarkdownSelection(enabled = selectable && !streaming) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        groups.forEachIndexed { groupIndex, group ->
            // TUR-29A: imleç YALNIZ son grupta çizilir; tamamlanan grupların
            // çizim girdileri token'dan token'a aynı kalır → çocuklar skip.
            val lastGroup = streaming && groupIndex == groups.lastIndex
            when (group) {
                is MdGroup.Tight -> Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    group.items.forEachIndexed { itemIndex, item ->
                        val suffix = streamCursorSuffix(lastGroup, itemIndex == group.items.lastIndex)
                        ListItemRow(item, inline, color, fontSize, bodyLine, cursorSuffix = suffix, onOpenLink = onOpenLink)
                    }
                }

                is MdGroup.Solo -> when (val block = group.block) {
                    is MdBlock.Code -> Column {
                        if (!streaming && block.language.lowercase() in setOf("html", "htm"))
                            HtmlDocumentPreview(block.code, onOpenLink)
                        CodeBlock(block, cursorSuffix = streamCursorSuffix(lastGroup, true))
                    }

                    // V5-2: medya blokları — bağlantı onayı olmadan görüntülenir
                    // (sunucu URL'si değil, http/https görsel/Video); dokunma
                    // tam ekran açar, javascript: vb. [safeExternalUrl]'de ölür.
                    is MdBlock.Image -> ConversationMediaBlock(block.url, MediaKind.IMAGE, block.alt, onOpenLink)
                    is MdBlock.Video -> ConversationMediaBlock(block.url, MediaKind.VIDEO, block.label, onOpenLink)
                    is MdBlock.Audio -> ConversationMediaBlock(block.url, MediaKind.AUDIO, block.label, onOpenLink)
                    is MdBlock.Html -> ConversationMediaBlock(block.url, MediaKind.HTML, block.label, onOpenLink)

                    // FR-001: hiyerarşi KALINLIKLA, boyutla değil — başlık gövdeyle
                    // aynı punto, yalnız daha ağır (SemiBold). Eski 19/17/15.sp
                    // boyut şişirmesi kaldırıldı.
                    is MdBlock.Heading -> LinkedText(
                        inlineMarkdown(block.text, inline) + AnnotatedString(streamCursorSuffix(lastGroup, true)),
                        color = HermesColors.TextPrimary,
                        fontSize = fontSize,
                        bodyLine = bodyLine,
                        onOpenLink = onOpenLink,
                        fontWeight = FontWeight.SemiBold,
                    )

                    // FR-005: gövde Telegram ikincil tonu (#d1d1d1 civarı), 15sp.
                    is MdBlock.Paragraph -> LinkedText(
                        inlineMarkdown(block.text, inline) + AnnotatedString(streamCursorSuffix(lastGroup, true)),
                        color = color,
                        fontSize = fontSize,
                        bodyLine = bodyLine,
                        onOpenLink = onOpenLink,
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
                        LinkedText(
                            // Alıntı içi devam satırları da \n taşır; tek Text.
                            // V5-2: alıntı içinde bağlantı tıklanabilir kalsın.
                            inlineMarkdown(block.text, inline) + AnnotatedString(streamCursorSuffix(lastGroup, true)),
                            color = HermesColors.TextMuted,
                            fontSize = fontSize,
                            bodyLine = bodyLine,
                            onOpenLink = onOpenLink,
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
                        ListItemRow(block, inline, color, fontSize, bodyLine, onOpenLink = onOpenLink)
                }
            }
        }
    }
    }
}

@Composable
private fun MarkdownSelection(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) SelectionContainer(content = content) else content()
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
    // TUR-29A: akış imleci — yalnız çağıranın belirlediği son blokta boş değil.
    cursorSuffix: String = "",
    onOpenLink: (String) -> Unit = {},
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
        LinkedText(
            inlineMarkdown(block.textForItem(), inline) + AnnotatedString(cursorSuffix),
            color = color,
            fontSize = fontSize,
            bodyLine = bodyLine,
            onOpenLink = onOpenLink,
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
private fun CodeBlock(
    block: MdBlock.Code,
    // TUR-29A: akış imleci yalnız çizimde; kopyalanan içerik (block.code) ham kalır.
    cursorSuffix: String = "",
) {
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
                    // TUR-29A: akış sırasında da renklendirilir; kopyalanan
                    // içerik hâlâ ham block.code (imleçsiz).
                    rememberCodeHighlight(block.code, cursorSuffix),
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
        DisableSelection { Row(
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
        } }
    }
}

// ── V5-2: tıklanabilir bağlantı + medya ────────────────────────────────

/** Bağlantı etiketi — [LinkedText] dokunuşta bu etiketi arar. */
internal const val LINK_TAG = "URL"

/** Every Markdown surface, including bot threads, opens links by default. */
@Composable
internal fun rememberExternalLinkOpener(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { raw ->
            safeExternalUrl(raw)?.let { url ->
                runCatching {
                    context.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url),
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure {
                    android.widget.Toast.makeText(context, "Bağlantıyı açacak uygulama bulunamadı", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/** Native links supply TalkBack actions and don't compete with bubble gestures. */
internal fun interactiveLinks(text: AnnotatedString, onOpenLink: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(text)
        text.getStringAnnotations(LINK_TAG, 0, text.length).forEach { link ->
            val url = safeExternalUrl(link.item) ?: return@forEach
            if (link.start < link.end) addLink(
                LinkAnnotation.Url(url, linkInteractionListener = LinkInteractionListener { onOpenLink(url) }),
                link.start, link.end,
            )
        }
    }

/**
 * Bağlantı etiketli metin — dokunulan bağlantı [onOpenLink]'e güvenli URL ile
 * iletilir. Açma onayı ÇAĞIRANDA: bu katman yalnız URL'yi doğrular ve iletir.
 */
@Composable
private fun LinkedText(
    text: AnnotatedString,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    bodyLine: androidx.compose.ui.unit.TextUnit,
    onOpenLink: (String) -> Unit,
    fontWeight: FontWeight? = null,
    modifier: Modifier = Modifier,
) {
    val linked = remember(text, onOpenLink) { interactiveLinks(text, onOpenLink) }
    Text(
        linked,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = bodyLine,
        modifier = modifier,
    )
}

/**
 * Uzaktan görsel — OkHttp + BitmapFactory, harici kütüphane yok.
 *
 * En uzun kenar 1600px'e örneklenir (telefon ekranına fazlasıyla yeter);
 * başarısız indirmede görsel yerine alt metin kalır, balon çökmez.
 */
@Composable
private fun ConversationMediaBlock(url: String, kind: MediaKind, label: String, onOpenLink: (String) -> Unit) {
    val server = LocalConversationServer.current
    if (isServerMediaPath(url) || (server != null && serverMediaUrl(url, server) != null)) {
        ServerConversationMedia(url, kind, label, onOpenLink)
    } else when(kind) {
        MediaKind.IMAGE -> MarkdownImage(label, url)
        MediaKind.VIDEO -> MarkdownVideo(url, label)
        MediaKind.AUDIO -> ConversationAudio(url, label)
        MediaKind.HTML -> androidx.compose.material3.TextButton(onClick = { onOpenLink(url) }) { Text("Web sayfasını aç · $label") }
        else -> Unit
    }
}

@Composable
private fun MarkdownImage(alt: String, url: String) {
    val safe = safeExternalUrl(url) ?: url.takeIf(::isInlineConversationImage) ?: return
    var bitmap by remember(safe) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(safe) { mutableStateOf(false) }
    var fullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(safe) {
        val bmp = withContext(Dispatchers.IO) { runCatching { downloadBitmap(safe) }.getOrNull() }
        if (bmp == null) failed = true else bitmap = bmp.asImageBitmap()
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(HermesColors.SurfaceDim)
            .clickable(enabled = bitmap != null) { fullScreen = true },
    ) {
        val img = bitmap
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = alt.ifBlank { null },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(
                if (failed) S.t2("Görsel yüklenemedi", "Image failed to load")
                else alt.ifBlank { S.t2("Görsel yükleniyor…", "Loading image…") },
                color = HermesColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    if (fullScreen && bitmap != null) {
        Dialog(onDismissRequest = { fullScreen = false }) {
            Image(
                bitmap = bitmap!!,
                contentDescription = alt.ifBlank { null },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
        }
    }
}

/**
 * Video kartı — dokununca yerleşik [android.widget.VideoView] ile oynatılır.
 *
 * Med3/ExoPlayer bağımlılığı eklenmedi: WebView'den farkı, burada HTML/JS
 * katmanı hiç çalışmaz. Yine yalnız güvenli http/https kaynak oynatılır.
 */
@Composable
private fun MarkdownVideo(url: String, label: String) {
    val safe = safeExternalUrl(url) ?: return
    var playing by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .background(HermesColors.SurfaceDim, MaterialTheme.shapes.medium)
            .border(1.dp, HermesColors.Border, MaterialTheme.shapes.medium)
            .clickable { playing = true }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = S.t2("Videoyu oynat", "Play video"),
            tint = HermesColors.Midground,
            modifier = Modifier.width(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label.ifBlank { S.t2("Video", "Video") },
            color = HermesColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }

    if (playing) {
        Dialog(onDismissRequest = { playing = false }) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setMediaController(android.widget.MediaController(ctx).also { it.setAnchorView(this) })
                        setVideoURI(android.net.Uri.parse(safe))
                        setOnPreparedListener { mp -> mp.isLooping = false; start() }
                    }
                },
                onRelease = { it.stopPlayback() },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.medium),
            )
        }
    }
}

/** Görseli indirip örnekler; hata/null dönerse çağıran alt metni gösterir. */
private fun downloadBitmap(url: String): android.graphics.Bitmap? {
    if (isInlineConversationImage(url)) return decodeConversationBitmap(java.util.Base64.getDecoder().decode(url.substringAfter(',')))
    val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    val request = okhttp3.Request.Builder().url(url).build()
    client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val body = resp.body ?: return null
        if (body.contentType()?.type != "image") return null
        val limit = 8L * 1024 * 1024
        if (body.contentLength() > limit) return null
        val source = body.source()
        val buffer = okio.Buffer()
        while (buffer.size <= limit) {
            if (source.read(buffer, minOf(8192L, limit + 1 - buffer.size)) == -1L) break
        }
        if (buffer.size > limit) return null
        val bytes = buffer.readByteArray()
        return decodeConversationBitmap(bytes)
    }
}

private fun decodeConversationBitmap(bytes: ByteArray): android.graphics.Bitmap? {
        if (bytes.size > 8 * 1024 * 1024) return null
        // Sınırı önce ölç, sonra uygun örnekle indirilen baytları çöz.
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth > 32_768 || bounds.outHeight > 32_768 ||
            bounds.outWidth.toLong() * bounds.outHeight > 100_000_000L) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
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

    /**
     * V5-2: görsel bloğu — `![alt](url)` ya da tek satır görsel URL'si.
     * Yalnız güvenli URL taşır ([safeExternalUrl] ayrıştırmada doğrular).
     */
    data class Image(val alt: String, val url: String) : MdBlock

    /** V5-2: video bloğu — tek satır video URL'si. */
    data class Video(val url: String, val label: String) : MdBlock
    data class Audio(val url: String, val label: String) : MdBlock
    data class Html(val url: String, val label: String) : MdBlock

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

/** V5-2: `![alt](url)` görsel bloğu. */
internal val IMAGE_BLOCK = Regex("""^!\[([^\]]*)\]\(([^)\s]+)\)$""")

/** V5-2: tek başına http(s) URL satırı (medya adayı). */
internal val BARE_URL_LINE = Regex("""^https?://\S+$""")

/** V5-2: satır içi otomatik bağlantı — boşluk/parantezle biter. */
internal val BARE_URL = Regex("""https?://[^\s<>\[\]"']+""", RegexOption.IGNORE_CASE)

private fun indentOf(leading: String): Int = leading.length / 2

/** Satır bir blok başlangıcı mı? (devam satırı tespiti için). */
private fun startsBlock(line: String): Boolean =
    line.isBlank() ||
        line.trimStart().startsWith("```") ||
        line.trim().let { it == "---" || it == "***" || it == "___" } ||
        HEADING.matches(line) ||
        BULLET.matches(line) ||
        NUMBERED.matches(line) ||
        line.trimStart().startsWith("> ") ||
        // V5-2: görsel/medya satırı, açık maddenin devamına karışmaz.
        IMAGE_BLOCK.matches(line.trim()) ||
        (BARE_URL_LINE.matches(line.trim()) && mediaKindOf(line.trim()) != MediaKind.NONE)

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

        // V5-2: `![alt](url)` görsel bloğu ve tek satır medya URL'si.
        // Yalnız güvenli URL bloğa girer; güvenli değilse satır düz metin kalır.
        val trimmed = line.trim()
        val imageMatch = IMAGE_BLOCK.matchEntire(trimmed)
        if (imageMatch != null && (safeExternalUrl(imageMatch.groupValues[2]) != null || isServerMediaPath(imageMatch.groupValues[2]) || isInlineConversationImage(imageMatch.groupValues[2]))) {
            flushAll()
            blocks += MdBlock.Image(imageMatch.groupValues[1], imageMatch.groupValues[2])
            i++
            continue
        }
        if ((BARE_URL_LINE.matches(trimmed) && safeExternalUrl(trimmed) != null) || isServerMediaPath(trimmed)) {
            when (conversationMediaKind(trimmed)) {
                MediaKind.IMAGE -> {
                    flushAll()
                    blocks += MdBlock.Image(trimmed.substringAfterLast('/'), trimmed)
                    i++
                    continue
                }
                MediaKind.VIDEO -> {
                    flushAll()
                    blocks += MdBlock.Video(trimmed, trimmed.substringAfterLast('/'))
                    i++
                    continue
                }
                MediaKind.AUDIO, MediaKind.HTML -> {
                    flushAll()
                    val label = trimmed.substringBefore('?').substringBefore('#').substringAfterLast('/')
                    blocks += if (conversationMediaKind(trimmed) == MediaKind.AUDIO) MdBlock.Audio(trimmed, label) else MdBlock.Html(trimmed, label)
                    i++
                    continue
                }
                MediaKind.NONE -> Unit
            }
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
 *
 * TUR-29A: `@Immutable` — akış sırasında ([MarkdownText] `streaming`) üst
 * kompozabl her tokenda yeniden koşar; bu sınıf değişmez işaretli olmadan
 * çocuk kompozabllar (ListItemRow, CodeBlock) atlanamaz ve TÜM bloklar her
 * tokenda yeniden ölçülürdü (TokenFlow deseninin önlediği relayout).
 */
@Immutable
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
                val destination = markdownLinkDestination(source, openParen)
                val closeParen = destination?.first ?: -1
                if (closeParen < 0) { append(source[i]); i++ } else {
                    // V5-2: [metin](url) artık TIKLANABİLİR — URL, LINK_TAG
                    // etiketi olarak yazının altına gömülür (LinkedText okur).
                    val target = destination!!.second
                    val labelStart = length
                    withStyle(
                        SpanStyle(
                            color = colors.link,
                            textDecoration = TextDecoration.Underline,
                        )
                    ) { appendInline(source.substring(i + 1, closeBracket), depth + 1, colors) }
                    if (safeExternalUrl(target) != null) {
                        addStringAnnotation(LINK_TAG, target, labelStart, length)
                    }
                    i = closeParen + 1
                }
            }

            // V5-2: çıplak http(s) URL'ler de otomatik bağlantı olur; güvenli
            // değilse (javascript: vb. bu kalıba zaten girmez) düz metin kalır.
            source.startsWith("http://", i, ignoreCase = true) || source.startsWith("https://", i, ignoreCase = true) -> {
                val match = BARE_URL.find(source, i)
                if (match == null || match.range.first != i) {
                    append(source[i]); i++
                } else {
                    val raw = match.value
                    val candidate = trimBareLink(raw)
                    val safe = safeExternalUrl(candidate)
                    if (safe == null) {
                        append(raw); i += raw.length
                    } else {
                        val start = length
                        withStyle(
                            SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)
                        ) { append(safe) }
                        addStringAnnotation(LINK_TAG, safe, start, length)
                        // Kesilen noktalama düz metin olarak geri yazılır.
                        append(raw.substring(candidate.length))
                        i += raw.length
                    }
                }
            }

            else -> { append(source[i]); i++ }
        }
    }
}
