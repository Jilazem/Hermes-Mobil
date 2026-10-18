package com.hermes.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * K13c: oturum birleştirme mantığı — SAF fonksiyonlar (JVM testli).
 *
 * Sözleşme: gateway'in `POST /api/czip/merge` ucu birleştirmeyi SUNUCU
 * TARAFINDA yapar; yanıt YALNIZ üst veridir (yeni session_id, başlık, kaynak
 * özeti, sayaçlar) — birleşik ileti İÇERİĞİ hiçbir koşulda telefona inmez
 * (K6/K13c disiplini). Bu dosya ağ da içermiyor; bütün kurallar burada test
 * edilir, [HermesClient] ince kalır.
 *
 * Kaynak oturumlara sunucu dokunmaz (arşiv/pasife alma yok); birleşik oturum
 * KENDİ session_id'siyle yazılır — K12b oturum izolasyonu korunur.
 */

/** Birleşen kaynak oturumun üst verisi (içerik YOK). */
data class MergeKaynak(
    val sid: String,
    val baslik: String,
    val ilet: Int,
)

/** /api/czip/merge yanıtı — üst veri. */
data class MergeSonuc(
    val sessionId: String,
    val title: String,
    val kaynaklar: List<MergeKaynak> = emptyList(),
    val kaynakIlet: Int = 0,
    val birlesikIlet: Int = 0,
    val atilan: Int = 0,
)

/** Çekmecede birleştirme akışının durum kutusu. */
data class SessionMergeState(
    val calisiyor: Boolean = false,
    val sonuc: MergeSonuc? = null,
    val hata: String? = null,
)

/**
 * `/api/czip/merge` yanıtını çözer — bozuk alan toler edilir, eksikse null
 * döner (asla fırlatmaz). `id: null` alanını "null" metni sanmamak için her
 * üst veri alanı strOrNull'dan geçer (parseCzipCatalog kuralı).
 */
private fun JsonElement?.strOrNull(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (this is JsonNull) return null
    return p.content
}

fun parseMergeSonuc(govde: JsonElement): MergeSonuc? {
    val obj = runCatching { govde.jsonObject }.getOrNull() ?: return null
    val sessionId = obj["session_id"].strOrNull()?.takeIf { it.isNotBlank() } ?: return null
    val kaynaklar = runCatching {
        (obj["sources"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { el ->
            val k = el as? JsonObject ?: return@mapNotNull null
            val sid = k["sid"].strOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MergeKaynak(
                sid = sid,
                baslik = k["baslik"].strOrNull().orEmpty().ifBlank { sid },
                ilet = runCatching { (k["ilet"] as? JsonPrimitive)?.int ?: 0 }.getOrDefault(0),
            )
        }
    }.getOrDefault(emptyList())
    return MergeSonuc(
        sessionId = sessionId,
        title = obj["title"].strOrNull().orEmpty().ifBlank { "Birleşik oturum" },
        kaynaklar = kaynaklar,
        kaynakIlet = runCatching { (obj["source_message_count"] as? JsonPrimitive)?.int ?: 0 }.getOrDefault(0),
        birlesikIlet = runCatching { (obj["merged_message_count"] as? JsonPrimitive)?.int ?: 0 }.getOrDefault(0),
        atilan = runCatching { (obj["duplicates_dropped"] as? JsonPrimitive)?.int ?: 0 }.getOrDefault(0),
    )
}

/** Ham JSON metninden çözer (istemci şeridi). */
fun parseMergeSonuc(text: String): MergeSonuc? = runCatching {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    parseMergeSonuc(json.parseToJsonElement(text.ifBlank { "{}" }))
}.getOrNull()

/** Birleştirme en az 2 FARKLI oturumla yapılabilir (sunucu sözleşmesi). */
fun mergeSecilebilirMi(ids: Collection<String>): Boolean =
    ids.filter { it.isNotBlank() }.distinct().size >= 2

/**
 * Kullanıcının girdiği birleşik başlığı temizler: boşsa null (sunucu varsayılan
 * "<ana başlık> (+N oturum birleşik)" kullanır), doluysa 80 karaktere kırpar.
 */
fun temizBirlestirBasligi(girdi: String?): String? =
    girdi?.trim()?.take(80)?.ifBlank { null }

/** Bildirim satırı için kaynak özeti — "A, B (+2 kaynak)". */
fun kaynakOzeti(sonuc: MergeSonuc): String =
    sonuc.kaynaklar.joinToString(", ") { it.baslik }
        .ifBlank { sonuc.kaynaklar.size.toString() + " kaynak" }

/** Birleştirme isteğinin JSON gövdesi — id'ler güvenli şekilde tırnaklanır. */
fun mergeGovdesi(ids: List<String>, title: String?): String = buildString {
    append("{\"ids\":[")
    append(
        ids.filter { it.isNotBlank() }.distinct().joinToString(",") {
            JsonPrimitive(it).toString()
        },
    )
    append("]")
    temizBirlestirBasligi(title)?.let { append(",\"title\":").append(JsonPrimitive(it).toString()) }
    append("}")
}
