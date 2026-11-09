package com.hermes.mobile.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

/**
 * Form şablonları + kayıtları + taslaklar için dosya deposu.
 *
 * Kök: `filesDir/forms/`. Her yazma önce `.tmp` dosyasına, sonra `rename` ile
 * hedefe gider: yarıda kalan yazma eski dosyayı bozmaz. Tüm erişim
 * [synchronized] çünkü kayıt ekranı ile arka plan derlemesi aynı anda
 * yazabiliyor.
 */
class FormStore(context: Context) {

    private val root = File(context.filesDir, "forms")
    private val templatesFile = File(root, "templates.json")
    private val recordsDir = File(root, "records")
    private val draftsDir = File(root, "drafts")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /** Son dosya hatası — UI'da gösterilir; `null` ise sorun yok. */
    @Volatile
    var lastError: String? = null
        private set

    // ── Şablonlar ────────────────────────────────────────────────────

    fun templates(): List<FormTemplate> = synchronized(this) {
        if (!templatesFile.exists()) {
            val seeds = FormSchema.presets()
            writeAtomic(templatesFile, FormSchema.toJsonList(seeds))
            return@synchronized seeds
        }
        val raw = runCatching { templatesFile.readText() }.getOrElse {
            lastError = "Şablon dosyası okunamadı: ${it.message}"
            return@synchronized FormSchema.presets()
        }
        val result = FormSchema.parseList(raw)
        if (!result.ok) {
            lastError = result.errors.joinToString("; ")
            // Bozuk dosyayı sessizce ezmiyoruz: yanına yedek bırakıp okunabilenleri kullanıyoruz.
            runCatching {
                templatesFile.copyTo(File(root, "templates.corrupt-${System.currentTimeMillis()}.json"), overwrite = true)
            }
            if (result.templates.isEmpty()) return@synchronized FormSchema.presets()
        } else {
            lastError = null
        }
        result.templates
    }

    /** Doğrulanmış şablonu yazar. Boş liste = başarı. */
    fun saveTemplate(template: FormTemplate): List<String> = synchronized(this) {
        val errors = FormSchema.validate(template)
        if (errors.isNotEmpty()) return@synchronized errors
        val current = templates().toMutableList()
        val index = current.indexOfFirst { it.id == template.id }
        if (index >= 0) current[index] = template else current += template
        if (!writeAtomic(templatesFile, FormSchema.toJsonList(current))) {
            return@synchronized listOf(lastError ?: "Şablon yazılamadı")
        }
        lastError = null
        emptyList()
    }

    fun deleteTemplate(id: String): Boolean = synchronized(this) {
        val current = templates().filterNot { it.id == id }
        val ok = writeAtomic(templatesFile, FormSchema.toJsonList(current))
        if (ok) {
            recordsDirFor(id).deleteRecursively()
            draftFile(id).delete()
        }
        ok
    }

    // ── Kayıtlar ─────────────────────────────────────────────────────

    fun records(templateId: String): List<FormRecord> = synchronized(this) {
        val dir = recordsDirFor(templateId)
        if (!dir.isDirectory) return@synchronized emptyList()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { parseRecord(file.readText()) }.getOrElse {
                    lastError = "${file.name} okunamadı: ${it.message}"
                    null
                }
            }
            .sortedByDescending { it.createdAt }
    }

    fun saveRecord(templateId: String, template: FormTemplate, values: Map<String, String>): FormRecord? =
        synchronized(this) {
            val errors = FormSchema.validateValues(template, values)
            if (errors.isNotEmpty()) {
                lastError = errors.joinToString("; ")
                return@synchronized null
            }
            val record = FormRecord(
                id = UUID.randomUUID().toString().take(8),
                templateId = templateId,
                createdAt = System.currentTimeMillis(),
                values = values.filterValues { it.isNotBlank() },
            )
            val dir = recordsDirFor(templateId)
            if (!dir.isDirectory && !dir.mkdirs()) {
                lastError = "Kayıt klasörü oluşturulamadı"
                return@synchronized null
            }
            val target = File(dir, "rec-${record.id}.json")
            if (!writeAtomic(target, recordToJson(record))) {
                return@synchronized null
            }
            clearDraft(templateId)
            lastError = null
            record
        }

    fun deleteRecord(templateId: String, recordId: String): Boolean = synchronized(this) {
        File(recordsDirFor(templateId), "rec-$recordId.json").delete()
    }

    fun exportRecord(template: FormTemplate, record: FormRecord): String =
        FormSchema.exportText(template, record.values)

    // ── Taslaklar (otomatik) ─────────────────────────────────────────

    fun draft(templateId: String): Map<String, String> = synchronized(this) {
        val file = draftFile(templateId)
        if (!file.isFile) return@synchronized emptyMap()
        runCatching {
            val obj = json.parseToJsonElement(file.readText()).jsonObject
            (obj["values"] as? JsonObject)?.mapValues { it.value.toString().trim('"') }.orEmpty()
        }.getOrElse {
            lastError = "Taslak okunamadı: ${it.message}"
            emptyMap()
        }
    }

    fun saveDraft(templateId: String, values: Map<String, String>): Boolean = synchronized(this) {
        if (!draftsDir.isDirectory && !draftsDir.mkdirs()) return@synchronized false
        val payload = buildJsonObject {
            put("templateId", JsonPrimitive(templateId))
            put("savedAt", JsonPrimitive(System.currentTimeMillis()))
            put("values", buildJsonObject {
                values.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            })
        }.toString()
        writeAtomic(draftFile(templateId), payload)
    }

    fun clearDraft(templateId: String) {
        draftFile(templateId).delete()
    }

    // ── iç yardımcılar ───────────────────────────────────────────────

    private fun recordsDirFor(templateId: String) =
        File(recordsDir, templateId.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    private fun draftFile(templateId: String) =
        File(draftsDir, templateId.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    private fun recordToJson(record: FormRecord): String = buildJsonObject {
        put("id", JsonPrimitive(record.id))
        put("templateId", JsonPrimitive(record.templateId))
        put("createdAt", JsonPrimitive(record.createdAt))
        put("values", buildJsonObject {
            record.values.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
        })
    }.toString()

    private fun parseRecord(raw: String): FormRecord {
        val obj = json.parseToJsonElement(raw).jsonObject
        val id = obj["id"]?.toString()?.trim('"').orEmpty()
        val templateId = obj["templateId"]?.toString()?.trim('"').orEmpty()
        val createdAt = obj["createdAt"]?.toString()?.toLongOrNull() ?: 0L
        require(id.isNotBlank()) { "kayıt kimliği yok" }
        require(templateId.isNotBlank()) { "şablon kimliği yok" }
        val values = (obj["values"] as? JsonObject)?.mapValues { it.value.toString().trim('"') }.orEmpty()
        return FormRecord(id = id, templateId = templateId, createdAt = createdAt, values = values)
    }

    /** `.tmp` → `rename`. Dönüş: başarılı mı. Hata [lastError]'a yazılır. */
    private fun writeAtomic(target: File, content: String): Boolean = runCatching {
        target.parentFile?.let { if (!it.isDirectory) it.mkdirs() }
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(content)
        if (target.exists() && !target.delete()) error("eski dosya silinemedi: ${target.name}")
        if (!tmp.renameTo(target)) error("yeniden adlandırma başarısız: ${target.name}")
        true
    }.getOrElse {
        lastError = "Yazma hatası: ${it.message}"
        false
    }
}
