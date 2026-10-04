package com.hermes.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.hermes.mobile.ui.theme.HermesColors

/**
 * TUR-29A madde 3 — akış sırasında kod bloğu renklendirme (compose-highlight
 * deseni) + kopyalama. Harici bağımlılık YOK (proje kuralı): kural tabanlı
 * mini tokenizör, AnnotatedString + SpanStyle üretir.
 *
 * Akış toleransı (TokenFlow): yarım token güvenli — kapanmamış dizge/çok satırlı
 * yorum satırı/EOF'a kadar boyanır, hata veya sonsuz döngü üretmez. Kod her
 * token'da baştan taranır ama bu YALNIZ son (açık) blokta olur; tamamlanan
 * bloklar zaten Compose skip ile yeniden hesaplanmaz (madde 1). Kod birkaç KB
 * ile sınırlı olduğundan tek geçiş taraması akış için yeterince ucuzdur.
 */

/** Kod parçası türleri — dil agnostik ortak küme. */
enum class CodeTokenKind { Keyword, String, Comment, Number, Annotation }

/** [start, end) aralığı ve türü; end dahil değil (AnnotatedString uyumlu). */
data class CodeSpan(val start: Int, val end: Int, val kind: CodeTokenKind)

private val CODE_KEYWORDS: Set<String> = setOf(
    // Kotlin / Java
    "val", "var", "fun", "if", "else", "when", "for", "while", "return", "class",
    "object", "interface", "data", "sealed", "enum", "import", "package", "const",
    "lateinit", "suspend", "override", "public", "private", "internal", "protected",
    "this", "super", "try", "catch", "finally", "throw", "in", "is", "as", "by",
    "true", "false", "null", "static", "void", "extends", "implements", "final",
    "new", "instanceof", "abstract", "boolean", "int", "long", "double", "float",
    // JS / TS
    "let", "function", "async", "await", "typeof", "undefined", "of", "default",
    "export", "require", "yield", "do",
    // Python
    "def", "self", "None", "True", "False", "elif", "from", "lambda", "with",
    "pass", "print", "not", "and", "or", "del", "global", "assert", "raise",
    // Bash / Go / Rust
    "echo", "fi", "then", "elif", "done", "func", "chan", "defer", "select",
    "match", "impl", "trait", "pub", "use", "mod", "loop",
)

/**
 * Kodu tek geçişte token'lara ayırır. `language` bilinmeyen olsa da çalışır:
 * ortak anahtar kelime kümesi + dizge/yorum/sayı kuralları c-benzeri dillerin
 * hepsinde makul sonuç verir. Yarım kodda (akış) güvenli: kapanmamış dizge
 * satır sonunda, kapanmamış blok yorum EOF'da biter.
 */
fun tokenizeCode(code: String): List<CodeSpan> {
    val spans = ArrayList<CodeSpan>()
    var i = 0
    val n = code.length
    while (i < n) {
        val c = code[i]
        when {
            // Satır yorumu: // veya #
            c == '/' && i + 1 < n && code[i + 1] == '/' || c == '#' -> {
                val end = code.indexOf('\n', i).let { if (it == -1) n else it }
                spans += CodeSpan(i, end, CodeTokenKind.Comment)
                i = end
            }
            // Blok yorum: /* ... */
            c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                val end = code.indexOf("*/", i + 2).let { if (it == -1) n else it + 2 }
                spans += CodeSpan(i, end, CodeTokenKind.Comment)
                i = end
            }
            // Dizge: " ' ` — kaçış karakterli; kapanmamışsa satır sonunda biter
            // (akış toleransı; ters tırnak tek satıra hapsolmaz, EOF'a gider).
            c == '"' || c == '\'' || c == '`' -> {
                var j = i + 1
                val lineEnd = if (c == '`') n else code.indexOf('\n', i).let { if (it == -1) n else it }
                while (j < lineEnd) {
                    if (code[j] == '\\' && j + 1 < n) {
                        j += 2
                    } else if (code[j] == c) {
                        j += 1
                        break
                    } else {
                        j += 1
                    }
                }
                val end = minOf(j, lineEnd).coerceAtMost(n)
                spans += CodeSpan(i, end, CodeTokenKind.String)
                i = end
            }
            // Sayı: 123, 1.5, 0xFF benzeri
            c.isDigit() -> {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '.' || code[j] == '_')) j += 1
                spans += CodeSpan(i, j, CodeTokenKind.Number)
                i = j
            }
            // Ek açıklama: @Annotation
            c == '@' -> {
                var j = i + 1
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j += 1
                if (j > i + 1) {
                    spans += CodeSpan(i, j, CodeTokenKind.Annotation)
                    i = j
                } else {
                    i += 1
                }
            }
            // Tanımlayıcı → anahtar kelime kümesinde mi?
            c.isLetter() || c == '_' -> {
                var j = i
                while (j < n && (code[j].isLetterOrDigit() || code[j] == '_')) j += 1
                val word = code.substring(i, j)
                if (word in CODE_KEYWORDS) spans += CodeSpan(i, j, CodeTokenKind.Keyword)
                i = j
            }
            else -> i += 1
        }
    }
    return spans
}

/**
 * Kodu renkli [AnnotatedString]'e çevirir. `cursorSuffix` akış imleci — yalnız
 * çizimde sona eklenir, kopyalanan ham koda karışmaz.
 */
@Composable
fun rememberCodeHighlight(code: String, cursorSuffix: String = ""): AnnotatedString {
    val keyword = HermesColors.Midground
    val string = HermesColors.Online
    val comment = HermesColors.TextFaint
    val number = HermesColors.Busy
    val annotation = HermesColors.Focus
    return remember(code, cursorSuffix, keyword, string, comment, number, annotation) {
        buildAnnotatedString {
            append(code + cursorSuffix)
            for (span in tokenizeCode(code)) {
                val color = when (span.kind) {
                    CodeTokenKind.Keyword -> keyword
                    CodeTokenKind.String -> string
                    CodeTokenKind.Comment -> comment
                    CodeTokenKind.Number -> number
                    CodeTokenKind.Annotation -> annotation
                }
                addStyle(SpanStyle(color = color), span.start, span.end)
            }
        }
    }
}
