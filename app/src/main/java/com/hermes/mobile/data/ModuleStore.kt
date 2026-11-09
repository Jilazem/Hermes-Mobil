package com.hermes.mobile.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

/**
 * Kullanıcının eklediği modüller (plan 2026-10-21 · modül ekleme).
 *
 * Modül türü kapalı küme: her tür mevcut bir ekrana bağlanır. "Serbest URL"
 * türü harici tarayıcıya gider — uygulama içi WebView açmıyoruz (token/
 * çerez sızmasın, geri tuşu davranışı bozulmasın).
 */
enum class ModuleKind(val labelTr: String, val labelEn: String) {
    FORMS("Formlar", "Forms"),
    MACROS("Makrolar", "Macros"),
    RECORDINGS("Görüşme kayıtları", "Call recordings"),
    FOLLOWING("Takip listesi", "Following list"),
    PANEL("Pano", "Dashboard"),
    WEB("Web bağlantısı", "Web link");

    companion object {
        fun parse(raw: String): ModuleKind? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

data class AppModule(
    val id: String,
    val name: String,
    val icon: String = "🧩",
    val kind: ModuleKind = ModuleKind.FORMS,
    val url: String = "",
    val builtIn: Boolean = false,
)

/**
 * Modül doğrulama + JSON. Kural: aynı kimlik/ad iki kez olamaz, WEB türü
 * http(s) adresi ister; geçersiz modül listeye girmez.
 */
object ModuleSchema {

    const val MAX_NAME = 30
    const val MAX_MODULES = 30

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun validate(module: AppModule, existing: List<AppModule> = emptyList()): List<String> {
        val errors = mutableListOf<String>()
        if (module.id.isBlank()) errors += "Modül kimliği boş"
        if (module.name.isBlank()) errors += "Modül adı boş"
        if (module.name.length > MAX_NAME) errors += "Modül adı en çok $MAX_NAME karakter"
        if (module.icon.length > 4) errors += "Simge en çok 4 karakter"
        if (module.kind == ModuleKind.WEB && !module.url.startsWith("http", true)) {
            errors += "Web modülü http(s) adresi ister"
        }
        if (existing.any { it.id == module.id }) errors += "Bu kimlikte modül var: ${module.id}"
        if (existing.any { it.name.equals(module.name.trim(), true) }) {
            errors += "\"${module.name}\" adlı modül zaten var"
        }
        if (existing.size >= MAX_MODULES) errors += "En çok $MAX_MODULES modül eklenebilir"
        return errors
    }

    fun newId(): String = "mod-" + UUID.randomUUID().toString().take(8)

    fun toJson(module: AppModule): String = buildJsonObject {
        put("id", JsonPrimitive(module.id))
        put("name", JsonPrimitive(module.name))
        put("icon", JsonPrimitive(module.icon))
        put("kind", JsonPrimitive(module.kind.name))
        put("url", JsonPrimitive(module.url))
    }.toString()

    fun toJsonList(modules: List<AppModule>): String = buildJsonArray {
        modules.forEach { add(json.parseToJsonElement(toJson(it))) }
    }.toString()

    /** Bozuk kayıt `null`; liste ayrıştırmada atlanır ve hata biriktirilir. */
    fun parse(raw: String): AppModule? = runCatching {
        val obj = json.parseToJsonElement(raw).jsonObject
        val id = obj.strOrEmpty("id")
        val name = obj.strOrEmpty("name")
        val icon = obj.strOrEmpty("icon").ifBlank { "🧩" }
        val kindRaw = obj.strOrEmpty("kind")
        val kind = ModuleKind.parse(kindRaw)
            ?: throw IllegalArgumentException("Modül türü bilinmiyor: '$kindRaw'")
        val module = AppModule(id = id, name = name, icon = icon, kind = kind, url = obj.strOrEmpty("url"))
        val errors = validate(module)
        if (errors.isNotEmpty()) throw IllegalArgumentException(errors.joinToString("; "))
        module
    }.getOrNull()

    data class ParseResult(val modules: List<AppModule>, val errors: List<String>)

    fun parseList(raw: String): ParseResult {
        if (raw.isBlank()) return ParseResult(emptyList(), emptyList())
        val array = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrElse {
            return ParseResult(emptyList(), listOf("Modül dosyası bozuk: ${it.message}"))
        }
        val modules = mutableListOf<AppModule>()
        val errors = mutableListOf<String>()
        array.forEachIndexed { index, element ->
            val module = parse(element.toString())
            if (module == null) errors += "${index + 1}. modül reddedildi"
            else if (modules.any { it.id == module.id }) errors += "Modül kimliği tekrar ediyor: ${module.id}"
            else modules += module
        }
        return ParseResult(modules, errors)
    }
}

/**
 * Fabrika modülleri: her zaman görünür, silinemez. "Formlar" modülü
 * kullanıcı form şablonlarını buradan açar.
 */
object BuiltInModules {
    val forms = AppModule(
        id = "forms",
        name = "Formlar",
        icon = "📋",
        kind = ModuleKind.FORMS,
        builtIn = true,
    )
    val macros = AppModule(id = "macros", name = "Makrolar", icon = "⚡", kind = ModuleKind.MACROS, builtIn = true)
    val recordings = AppModule(id = "call-recordings", name = "Görüşme kayıtları", icon = "🎙", kind = ModuleKind.RECORDINGS, builtIn = true)
    fun all(): List<AppModule> = listOf(forms, macros, recordings)
}

/** `filesDir/modules.json` — yalnız kullanıcı modülleri saklanır. */
class ModuleStore(context: Context) {

    private val file = File(context.filesDir, "modules.json")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Volatile
    var lastError: String? = null
        private set

    fun userModules(): List<AppModule> = synchronized(this) {
        if (!file.exists()) return@synchronized emptyList()
        val raw = runCatching { file.readText() }.getOrElse {
            lastError = "Modül dosyası okunamadı: ${it.message}"
            return@synchronized emptyList()
        }
        val result = ModuleSchema.parseList(raw)
        if (result.errors.isNotEmpty()) {
            lastError = result.errors.joinToString("; ")
            runCatching { file.copyTo(File(file.parentFile, "modules.corrupt-${System.currentTimeMillis()}.json"), overwrite = true) }
        } else lastError = null
        result.modules
    }

    /** Fabrika + kullanıcı modülleri; kimlik çakışırsa kullanıcı kaydı düşer. */
    fun modules(): List<AppModule> = BuiltInModules.all() + userModules().filterNot { m ->
        BuiltInModules.all().any { it.id == m.id }
    }

    fun add(module: AppModule): List<String> = synchronized(this) {
        val existing = modules()
        val errors = ModuleSchema.validate(module, existing)
        if (errors.isNotEmpty()) return@synchronized errors
        val next = userModules() + module
        if (!write(next)) return@synchronized listOf(lastError ?: "Modül yazılamadı")
        emptyList()
    }

    fun remove(id: String): Boolean = synchronized(this) {
        if (BuiltInModules.all().any { it.id == id }) {
            lastError = "Fabrika modülü silinemez"
            return@synchronized false
        }
        write(userModules().filterNot { it.id == id })
    }

    private fun write(modules: List<AppModule>): Boolean = runCatching {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(ModuleSchema.toJsonList(modules))
        if (file.exists() && !file.delete()) error("eski dosya silinemedi")
        if (!tmp.renameTo(file)) error("yeniden adlandırma başarısız")
        true
    }.getOrElse {
        lastError = "Yazma hatası: ${it.message}"
        false
    }
}
