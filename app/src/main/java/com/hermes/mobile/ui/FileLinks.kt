package com.hermes.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermes.mobile.ui.theme.HermesColors
import com.hermes.mobile.ui.theme.MonoTextStyle

/**
 * Ajan çıktısında geçen dosya yollarını bulup indirilebilir bağlantıya çevirir.
 *
 * Hermes rapor/döküman ürettiğinde yanıtın içinde mutlak yolu yazıyor
 * (`/media/user/EX/GDrive/...`). Masaüstü sürümde bu tıklanabilir; mobilde
 * düz metin olarak kalıyordu ve dosyaya ulaşmanın yolu yoktu.
 *
 * Sunucu `/api/files/download` ucunu **sorgu parametresiyle token** kabul edecek
 * şekilde açmış (`_QUERY_TOKEN_API_PATHS`) — yani bağlantı doğrudan tarayıcıya
 * ya da indiricilere verilebiliyor, özel başlık gerekmiyor.
 *
 * ⚠️ Yol tamamen kodlanmalı (`/` → `%2F`); yarım kodlanmış yol 404 dönüyor.
 */
private val FILE_PATH_REGEX = Regex(
    // Mutlak POSIX yolu; sonunda tanıdık bir belge uzantısı olanlar.
    """(/(?:[\w.\-+@ğüşıöçĞÜŞİÖÇ]+/)+[\w.\-+@ğüşıöçĞÜŞİÖÇ ]+\.""" +
        """(?:md|txt|pdf|docx?|xlsx?|csv|json|ya?ml|html?|png|jpe?g|zip|udf|pptx?))"""
)

data class FileRef(val path: String) {
    val name: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/').substringAfterLast('/')
    val extension: String get() = name.substringAfterLast('.', "").uppercase()
}

/**
 * Metindeki dosya yollarını çıkarır.
 *
 * Aynı dosya bir yanıtta birden çok geçebiliyor (önce "yazıyorum", sonra
 * "yazıldı"); tekrarlar eleniyor.
 */
fun extractFileRefs(text: String): List<FileRef> =
    FILE_PATH_REGEX.findAll(text)
        .map { FileRef(it.value.trim()) }
        .distinctBy { it.path }
        .take(8)
        .toList()

/** İndirme adresi — token sorgu parametresinde, yol tam kodlanmış. */
fun downloadUrl(baseUrl: String, token: String, path: String): String {
    val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
    val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
    return "${baseUrl.trimEnd('/')}/api/files/download?path=$encodedPath&token=$encodedToken"
}

/** Bir yanıtın altına düşen dosya kartları. */
@Composable
fun FileRefRow(refs: List<FileRef>, onOpen: (FileRef) -> Unit) {
    if (refs.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        refs.forEach { ref ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(HermesColors.SurfaceDim, RoundedCornerShape(9.dp))
                    .border(1.dp, HermesColors.BorderStrong, RoundedCornerShape(9.dp))
                    .clickable { onOpen(ref) }
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = HermesColors.Midground,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        ref.name,
                        color = HermesColors.TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            ref.extension.takeIf { it.isNotBlank() },
                            ref.folder.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MonoTextStyle,
                        color = HermesColors.TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Download,
                    contentDescription = S.t2("Aç / indir", "Open / download"),
                    tint = HermesColors.TextMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}
