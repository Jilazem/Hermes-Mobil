package com.hermes.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * K6: czip katalog mantığı — SAF fonksiyonlar (JVM testli).
 *
 * Sözleşme: gateway'in `/api/czip/packages` ucu YALNIZ üst veri döndürür
 * (kısa ID / başlık / tarih / boyut). Paket içeriği telefona hiç gelmez;
 * kullanıcı bir paket seçince promta `/czip_ex <ref>` (paket) ya da
 * `czip promt oku <kid>` (kayıtlı görev) REFERANSI yazılır ve okumayı
 * masaüstündeki Hermes ajanı (czip eklentisi) yapar. Bu dosya ağ da
 * içermiyor — [CzipClient] ince kalır, bütün kurallar burada test edilir.
 */

/** Son czip oturum paketi — üst veri (içerik YOK). */
data class CzipPackage(
    /** 6 haneli kısa kimlik (kayıtsız pakette null). */
    val id: String?,
    val title: String,
    /** "2026-10-05 19:47" biçiminde sunucu dizesi. */
    val date: String,
    val sizeBytes: Long,
    val name: String,
    /** Promta yazılacak referans: kid ya da mutlak yol. */
    val ref: String,
)

/** Kayıtlı promt/plan görevi — üst veri. */
data class CzipTask(
    val id: String,
    val type: String,
    val title: String,
    val date: String,
)

data class CzipCatalog(
    val packages: List<CzipPackage> = emptyList(),
    val tasks: List<CzipTask> = emptyList(),
)

/**
 * `/api/czip/packages` yanıtını çözer — bozuk alan toler edilir, asla fırlatmaz.
 *
 * Not: kotlinx.serialization'da `JsonNull` bir `JsonPrimitive`'dır ve
 * `.content`'i LİTERAL "null" DİZİSİ döndürür — `id: null` alanını "null"
 * string'i olarak okumamak için her üst veri alanı [strOrNull]'dan geçer.
 */
private fun JsonElement?.strOrNull(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (this is JsonNull) return null
    return p.content
}

fun parseCzipCatalog(govde: JsonElement): CzipCatalog {
    val obj = runCatching { govde.jsonObject }.getOrNull() ?: return CzipCatalog()
    val packages = runCatching {
        (obj["packages"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
            val p = el as? JsonObject ?: return@mapNotNull null
            val ref = p["ref"].strOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CzipPackage(
                id = p["id"].strOrNull()?.takeIf { it.isNotBlank() },
                title = p["title"].strOrNull().orEmpty().ifBlank { p["name"].strOrNull().orEmpty() },
                date = p["date"].strOrNull().orEmpty(),
                sizeBytes = runCatching { (p["size"] as? JsonPrimitive)?.long ?: 0L }.getOrDefault(0L),
                name = p["name"].strOrNull().orEmpty(),
                ref = ref,
            )
        }
    }.getOrDefault(emptyList())
    val tasks = runCatching {
        (obj["tasks"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
            val t = el as? JsonObject ?: return@mapNotNull null
            val id = t["id"].strOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CzipTask(
                id = id,
                type = t["type"].strOrNull() ?: "promt",
                title = t["title"].strOrNull().orEmpty(),
                date = t["date"].strOrNull().orEmpty(),
            )
        }
    }.getOrDefault(emptyList())
    return CzipCatalog(packages, tasks)
}

/** Ham JSON metninden çözer (istemci şeridi). */
fun parseCzipCatalog(text: String): CzipCatalog = runCatching {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    parseCzipCatalog(json.parseToJsonElement(text.ifBlank { "{}" }))
}.getOrDefault(CzipCatalog())

/** 1234567 → "1,2 MB" — satırda yer kaplamasın diye tek birim. */
fun formatCzipBytes(bayt: Long): String = when {
    bayt >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", bayt / 1_048_576.0)
    bayt >= 1_024 -> String.format(java.util.Locale.US, "%.0f KB", bayt / 1_024.0)
    else -> "$bayt B"
}

/** Paket seçiminin promta yazacağı referans (okuma masaüstünde yapılır). */
fun czipPackageRef(pkg: CzipPackage): String = "/czip_ex ${pkg.ref}"

/** Kayıtlı görev seçiminin promta yazacağı referans. */
fun czipTaskRef(task: CzipTask): String = "czip promt oku ${task.id}"

/**
 * Referansı taslağın SONUNA ekler — kayıtlı prompt çipiyle aynı kural:
 * dolu taslağın üstüne yazılmaz, altına satır eklenir.
 */
fun appendCzipRef(draft: String, ref: String): String =
    if (draft.isBlank()) ref else draft + "\n" + ref

/**
 * K12b: gönderilen metin TEK BAŞINA bir czip okuma referansı mı?
 * (paket: `/czip_ex <ref>`, kayıtlı görev: `czip promt oku <kid>`).
 * Yalnız çıplak referans yakalanır; referansı içeren normal mesajlar
 * ("şu paketi özetle\n/czip_ex …") ajan sohbetine girmeye devam eder.
 */
private val CZIP_OKU_REGEX = Regex(
    """\s*/czip_ex\s+\S+\s*|\s*czip\s+promt\s+oku\s+\S+\s*""",
)

fun isCzipReadRef(text: String): Boolean = CZIP_OKU_REGEX.matches(text)

/**
 * K12b: okuma çıktısının sohbete kalacak KISA özeti — K6 sözleşmesi:
 * paket/görev içeriği sohbete mesaj olarak DÖKÜLMEZ; tek satır
 * referans + kısa özet yeter. Boşluklar düzleşir, uzunluk kırpılır.
 */
fun czipReadOzet(reply: String, max: Int = 400): String {
    val duz = reply.replace(Regex("\\s+"), " ").trim()
    return when {
        duz.isBlank() -> ""
        duz.length <= max -> duz
        else -> duz.take(max - 1).trimEnd() + "…"
    }
}

/**
 * K29-a: mesaj önizlemesindeki czip referansı — paket (`/czip_ex <ref>`)
 * ya da kayıtlı görev (`czip promt oku <kid>`). Yoksa null.
 */
private val CZIP_REF_REGEX = Regex(
    """/czip_ex\s+(\S+)|czip\s+promt\s+oku\s+(\S+)""",
)

fun czipRefIn(text: String): String? =
    CZIP_REF_REGEX.find(text)?.let { it.groupValues[1].ifBlank { it.groupValues[2] } }

/**
 * K29-a: birleştirme ÖNERİSİ — AYNI czip paketini/görevini kullanan ≥2
 * farklı oturum (dbId) varsa "birleştirelim mi?" önerisi üretilir.
 * Saf kural: satırlar (dbId, önizleme) taranır, referansına göre gruplanır;
 * referansı OLMAYAN oturumlar öneriye girmez. Aynı pakete birden çok kez
 * değinen oturum tek sayılır; en kalabalık grup önerilir.
 */
data class CzipMergeOnerisi(
    val ref: String,
    /** Paket/kaynak başlığı — kartta gösterilir (katalog başlığı üstün). */
    val baslik: String,
    val dbIds: List<String>,
)

fun czipMergeOnerisi(rows: List<Pair<String, String>>): CzipMergeOnerisi? {
    // dbId → ilk bulunan referans (bir oturum tek gruba girer).
    val grup = LinkedHashMap<String, MutableSet<String>>()
    val basliklar = HashMap<String, String>()
    for ((dbId, onizleme) in rows) {
        if (dbId.isBlank()) continue
        val ref = czipRefIn(onizleme) ?: continue
        grup.getOrPut(ref) { mutableSetOf() }.add(dbId)
        basliklar.putIfAbsent(ref, onizleme.trim().take(80))
    }
    return grup.entries
        .filter { it.value.size >= 2 }
        .maxByOrNull { it.value.size }
        ?.let { (ref, ids) -> CzipMergeOnerisi(ref, basliklar[ref].orEmpty(), ids.toList()) }
}
