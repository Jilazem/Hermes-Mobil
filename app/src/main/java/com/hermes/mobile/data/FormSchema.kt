package com.hermes.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * Kullanıcı tanımlı form alanı türleri.
 *
 * Hazır form kütüphaneleri reflection/JitPack bağımlılığı getirdiği için
 * (bkz. plan 2026-10-21) şema + render katmanı kendi içimizde: tür kümesi
 * kapalı, bilinmeyen tür JSON'da reddedilir. Böylece kaydedilmiş bir şablon
 * ileride yeni bir tür eklendiğinde sessizce yanlış çizilmez.
 */
enum class FormFieldType(val labelTr: String, val labelEn: String) {
    TEXT("Metin", "Text"),
    NUMBER("Sayı", "Number"),
    DATE("Tarih", "Date"),
    CHOICE("Seçenek", "Choice"),
    PHOTO("Fotoğraf", "Photo"),
    SIGNATURE("İmza", "Signature"),
    PARSEL("Parsel", "Parcel");

    companion object {
        /** Bilinmeyen tür `null` — çağıran reddeder, tahmin etmez. */
        fun parse(raw: String): FormFieldType? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

/** Tek alan. [id] form içinde tekil; değerler bu kimlikle saklanır. */
data class FormField(
    val id: String,
    val type: FormFieldType,
    val label: String,
    val required: Boolean = false,
    /** Yalnız [FormFieldType.CHOICE] için anlamlı. */
    val options: List<String> = emptyList(),
    val hint: String = "",
)

/** Şablon: alan listesi + görünen ad. Kalıcılık `filesDir/forms/`. */
data class FormTemplate(
    val id: String,
    val name: String,
    val icon: String = "📋",
    val fields: List<FormField> = emptyList(),
    /** Fabrika şablonu — silinebilir ama yeniden üretilebilir. */
    val builtIn: Boolean = false,
)

/** Doldurulmuş form kaydı. */
data class FormRecord(
    val id: String,
    val templateId: String,
    val createdAt: Long,
    val values: Map<String, String>,
)

/**
 * Şema doğrulama + JSON (de)serileştirme.
 *
 * Tek kural: geçersiz şablon **reddedilir**, kısmen kabul edilmez. Bozuk bir
 * şablonu "yüklenebilir" saymak, doldurma ekranında sessizce eksik alan
 * göstermek demek olurdu.
 */
object FormSchema {

    const val MAX_FIELDS = 40
    const val MAX_OPTIONS = 20
    const val MAX_NAME = 60
    const val MAX_LABEL = 80

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun newId(prefix: String = "f"): String = "$prefix-" + UUID.randomUUID().toString().take(8)

    // ── Doğrulama ────────────────────────────────────────────────────

    fun validate(template: FormTemplate): List<String> {
        val errors = mutableListOf<String>()
        if (template.id.isBlank()) errors += "Şablon kimliği boş"
        if (template.name.isBlank()) errors += "Şablon adı boş"
        if (template.name.length > MAX_NAME) errors += "Şablon adı en çok $MAX_NAME karakter"
        if (template.fields.isEmpty()) errors += "En az bir alan gerekli"
        if (template.fields.size > MAX_FIELDS) errors += "En çok $MAX_FIELDS alan eklenebilir"

        val ids = template.fields.map { it.id }
        if (ids.size != ids.toSet().size) errors += "Alan kimlikleri tekrar ediyor"
        val labels = template.fields.map { it.label.trim() }.filter { it.isNotEmpty() }
        if (labels.size != labels.toSet().size) errors += "Alan etiketleri tekrar ediyor"

        template.fields.forEachIndexed { index, field ->
            val n = index + 1
            if (field.id.isBlank()) errors += "$n. alanın kimliği boş"
            if (field.label.isBlank()) errors += "$n. alanın etiketi boş"
            if (field.label.length > MAX_LABEL) errors += "$n. alan etiketi en çok $MAX_LABEL karakter"
            when (field.type) {
                FormFieldType.CHOICE -> {
                    val opts = field.options.map { it.trim() }.filter { it.isNotEmpty() }
                    if (opts.isEmpty()) errors += "\"${field.label}\": seçenekli alan için en az bir seçenek gerekli"
                    if (opts.size > MAX_OPTIONS) errors += "\"${field.label}\": en çok $MAX_OPTIONS seçenek"
                    if (opts.size != opts.toSet().size) errors += "\"${field.label}\": seçenekler tekrar ediyor"
                }
                FormFieldType.NUMBER -> if (field.options.isNotEmpty()) {
                    errors += "\"${field.label}\": sayı alanında seçenek olamaz"
                }
                else -> Unit
            }
        }
        return errors
    }

    /** Değer doğrulaması — kaydetmeden önce çağrılır. */
    fun validateValues(template: FormTemplate, values: Map<String, String>): List<String> {
        val errors = mutableListOf<String>()
        template.fields.forEach { field ->
            val raw = values[field.id].orEmpty().trim()
            if (field.required && raw.isEmpty()) {
                errors += "\"${field.label}\" zorunlu"
                return@forEach
            }
            if (raw.isEmpty()) return@forEach
            when (field.type) {
                FormFieldType.NUMBER -> if (raw.toDoubleOrNull() == null) {
                    errors += "\"${field.label}\" sayı olmalı"
                }
                FormFieldType.DATE -> if (!DATE_RE.matches(raw)) {
                    errors += "\"${field.label}\" GG.AA.YYYY biçiminde olmalı"
                }
                FormFieldType.CHOICE -> if (raw !in field.options) {
                    errors += "\"${field.label}\" seçeneklerden biri olmalı"
                }
                else -> Unit
            }
        }
        return errors
    }

    private val DATE_RE = Regex("""^\d{2}[.\-/]\d{2}[.\-/]\d{4}$""")

    // ── JSON ─────────────────────────────────────────────────────────

    fun toJson(template: FormTemplate): String = buildJsonObject {
        put("id", JsonPrimitive(template.id))
        put("name", JsonPrimitive(template.name))
        put("icon", JsonPrimitive(template.icon))
        put("builtIn", JsonPrimitive(template.builtIn))
        put("fields", buildJsonArray {
            template.fields.forEach { f ->
                add(buildJsonObject {
                    put("id", JsonPrimitive(f.id))
                    put("type", JsonPrimitive(f.type.name))
                    put("label", JsonPrimitive(f.label))
                    put("required", JsonPrimitive(f.required))
                    if (f.options.isNotEmpty()) put("options", buildJsonArray {
                        f.options.forEach { add(JsonPrimitive(it)) }
                    })
                    if (f.hint.isNotBlank()) put("hint", JsonPrimitive(f.hint))
                })
            }
        })
    }.toString()

    /** Bozuk JSON / kural ihlali → `null` (çağıran kullanıcıya sebep gösterir). */
    fun parse(raw: String): FormTemplate? = runCatching { parseOrThrow(raw) }.getOrNull()

    /** Aynı ayrıştırma ama sebep metniyle — düzenleyici kaydında kullanılır. */
    fun parseOrThrow(raw: String): FormTemplate {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { throw IllegalArgumentException("Şablon JSON değil: ${it.message}") }
        val id = root.strOrEmpty("id")
        val name = root.strOrEmpty("name")
        val icon = root.strOrEmpty("icon").ifBlank { "📋" }
        val builtIn = root["builtIn"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val fieldsJson = root["fields"] as? JsonArray
            ?: throw IllegalArgumentException("Şablonda 'fields' dizisi yok")
        val fields = fieldsJson.mapIndexed { index, element ->
            val obj = element as? JsonObject
                ?: throw IllegalArgumentException("${index + 1}. alan nesne değil")
            val typeRaw = obj.strOrEmpty("type")
            val type = FormFieldType.parse(typeRaw)
                ?: throw IllegalArgumentException("${index + 1}. alanın türü bilinmiyor: '$typeRaw'")
            val options = (obj["options"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)
            }.orEmpty()
            FormField(
                id = obj.strOrEmpty("id"),
                type = type,
                label = obj.strOrEmpty("label"),
                required = obj["required"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                options = options,
                hint = obj.strOrEmpty("hint"),
            )
        }
        val template = FormTemplate(id = id, name = name, icon = icon, fields = fields, builtIn = builtIn)
        val errors = validate(template)
        if (errors.isNotEmpty()) throw IllegalArgumentException(errors.joinToString("; "))
        return template
    }

    fun toJsonList(templates: List<FormTemplate>): String = buildJsonArray {
        templates.forEach { add(json.parseToJsonElement(toJson(it))) }
    }.toString()

    /** Liste ayrıştırma: geçerli olanlar döner, bozuklar [TemplateParseResult.errors]'a yazılır. */
    data class TemplateParseResult(val templates: List<FormTemplate>, val errors: List<String>) {
        val ok: Boolean get() = errors.isEmpty()
    }

    fun parseList(raw: String): TemplateParseResult {
        if (raw.isBlank()) return TemplateParseResult(emptyList(), emptyList())
        val array = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrElse {
            return TemplateParseResult(emptyList(), listOf("Şablon dosyası bozuk: ${it.message}"))
        }
        val good = mutableListOf<FormTemplate>()
        val errors = mutableListOf<String>()
        array.forEachIndexed { index, element ->
            runCatching { parseOrThrow(element.toString()) }
                .onSuccess { good += it }
                .onFailure { errors += "${index + 1}. şablon reddedildi: ${it.message}" }
        }
        // Aynı kimlik iki kez yüklenirse ikinci kopya düşer — sessiz çakışma yok.
        val unique = mutableListOf<FormTemplate>()
        good.forEach { t ->
            if (unique.any { it.id == t.id }) errors += "Şablon kimliği tekrar ediyor: ${t.id}"
            else unique += t
        }
        return TemplateParseResult(unique, errors)
    }

    // ── Doldurma yardımcıları ────────────────────────────────────────

    fun exportText(template: FormTemplate, values: Map<String, String>): String = buildString {
        appendLine("Şablon: ${template.name}")
        template.fields.forEach { f ->
            val v = values[f.id].orEmpty()
            appendLine("${f.label}: ${if (v.isBlank()) "—" else v}")
        }
    }

    /** Hazır şablonlar — ilk açılışta dosyaya yazılır, sonra kullanıcıya ait. */
    fun presets(): List<FormTemplate> = listOf(
        FormTemplate(
            id = "preset-kesif",
            name = "Keşif Tutanağı",
            icon = "🧭",
            builtIn = true,
            fields = listOf(
                FormField("date", FormFieldType.DATE, "Keşif tarihi", required = true),
                FormField("parcel", FormFieldType.PARSEL, "Parsel", required = true, hint = "İl/ilçe/ada/parsel"),
                FormField("address", FormFieldType.TEXT, "Adres", required = true),
                FormField("subject", FormFieldType.TEXT, "Konu", required = false),
                FormField("photo", FormFieldType.PHOTO, "Fotoğraf", required = false),
                FormField("signature", FormFieldType.SIGNATURE, "İmza", required = false),
            ),
        ),
        FormTemplate(
            id = "preset-parsel",
            name = "Parsel Sorgu",
            icon = "🗺️",
            builtIn = true,
            fields = listOf(
                FormField("il", FormFieldType.TEXT, "İl", required = true),
                FormField("ilce", FormFieldType.TEXT, "İlçe", required = true),
                FormField("ada", FormFieldType.NUMBER, "Ada", required = true),
                FormField("parsel", FormFieldType.NUMBER, "Parsel", required = true),
                FormField("note", FormFieldType.TEXT, "Not", required = false),
            ),
        ),
        FormTemplate(
            id = "preset-hasar",
            name = "Hasar Tespiti",
            icon = "🛠️",
            builtIn = true,
            fields = listOf(
                FormField("room", FormFieldType.TEXT, "Bölüm", required = true),
                FormField(
                    "kind", FormFieldType.CHOICE, "Hasar türü", required = true,
                    options = listOf("Nem", "Çatlak", "Kırık", "Deformasyon"),
                ),
                FormField("cost", FormFieldType.NUMBER, "Tahmini maliyet", required = false),
                FormField("photo", FormFieldType.PHOTO, "Fotoğraf", required = false),
                FormField("signature", FormFieldType.SIGNATURE, "İmza", required = false),
            ),
        ),
    )
}

/** JSON yardımcıları — eksik alan boş string döner (patlama yok). */
internal fun JsonObject.strOrEmpty(key: String): String =
    (this[key] as? JsonPrimitive)?.content.orEmpty()
