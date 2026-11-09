package com.hermes.mobile.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.UUID

@Serializable
data class MacroAction(val kind: String, val value: String = "")
@Serializable
data class MacroRule(
    val id: String = UUID.randomUUID().toString(), val name: String,
    val trigger: String = "manual", val actions: List<MacroAction>,
    val enabled: Boolean = false, val cooldown: Int = 30, val source: String = "Hermes",
    val blockers: List<String> = emptyList(), val original: String = "",
)

object MacroRules {
    fun read(input: java.io.InputStream): String {
        val buffer = ByteArray(1_048_577)
        var total = 0
        while (total < buffer.size) {
            val count = input.read(buffer, total, buffer.size - total)
            if (count < 0) break
            if (count == 0) error("Dosya okunamadı")
            total += count
        }
        require(total <= 1_048_576) { "Dosya en çok 1 MB olabilir" }
        return buffer.copyOf(total).toString(Charsets.UTF_8)
    }
    val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    val triggers = linkedMapOf("manual" to "Elle çalıştır", "charging" to "Şarja bağlanınca", "unplugged" to "Şarjdan ayrılınca", "headset" to "Kablolu kulaklık takılınca", "unlocked" to "Ekran kilidi açılınca")
    val actions = linkedMapOf("notify" to "Yerel bildirim", "vibrate" to "Kısa titreşim", "volume" to "Medya sesi (%)", "torch" to "Fener (aç/kapat)")
    fun validate(rule: MacroRule): List<String> = buildList {
        if (rule.id.isBlank() || rule.id.length > 80) add("Geçersiz kimlik")
        if (rule.name.isBlank() || rule.name.length > 100) add("Ad 1–100 karakter olmalı")
        if (rule.trigger !in triggers) add("Desteklenmeyen tetikleyici: ${rule.trigger}")
        if (rule.cooldown !in 1..3600) add("Bekleme süresi 1–3600 saniye olmalı")
        if (rule.actions.size !in 1..20) add("1–20 eylem gerekli")
        rule.actions.forEach { action ->
            if (action.kind !in actions) add("Desteklenmeyen eylem: ${action.kind}")
            if (action.value.length > 500) add("Eylem değeri çok uzun")
            if (action.kind == "volume" && action.value.toIntOrNull() !in 0..100) add("Ses düzeyi 0–100 olmalı")
            if (action.kind == "torch" && action.value !in listOf("on", "off")) add("Fener değeri on/off olmalı")
            if (action.kind == "notify" && action.value.isBlank()) add("Bildirim metni gerekli")
        }
        addAll(rule.blockers)
    }.distinct()

    fun export(rules: List<MacroRule>) = buildJsonObject {
        put("format", "hermes-macros-v1")
        put("rules", json.encodeToJsonElement(rules.map { it.copy(enabled = false) }))
    }.toString()

    /** Foreign imports never acquire execution authority. Unknown semantics block execution. */
    fun import(raw: String): List<MacroRule> {
        require(raw.toByteArray().size <= 1_048_576) { "Dosya en çok 1 MB olabilir" }
        val root = json.parseToJsonElement(raw).jsonObject
        fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (root.text("format") == "hermes-macros-v1") {
            val rules = json.decodeFromJsonElement<List<MacroRule>>(root.getValue("rules"))
            require(rules.size in 1..100) { "1–100 makro gerekli" }
            return rules.map { it.copy(id = UUID.randomUUID().toString(), enabled = false) }
        }
        if (root["macro"] is JsonObject || root["macroList"] is JsonArray) {
            val macros = (root["macroList"] as? JsonArray)?.map { it.jsonObject } ?: listOf(root.getValue("macro").jsonObject)
            require(macros.size in 1..100)
            return macros.map { obj ->
                val kinds = listOf("m_triggerList", "m_actionList", "m_constraintList").flatMap { key ->
                    (obj[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.text("m_classType") }
                }.distinct()
                MacroRule(name = obj.text("m_name").take(100).ifBlank { "MacroDroid makrosu" }, actions = emptyList(), source = "MacroDroid",
                    blockers = listOf("Özgün MacroDroid koşulları yürütücüye taşınmadı; otomatik çalıştırılmaz.") + kinds.take(40),
                    original = if (macros.size == 1) raw else buildJsonObject {
                        root.filterKeys { it != "macroList" && it != "macro" }.forEach { (key, value) -> put(key, value) }
                        put("macro", obj)
                    }.toString())
            }
        }
        if (root["schemaVersion"] != null && root["tasks"] is JsonArray) {
            val tasks = root.getValue("tasks").jsonArray
            require(tasks.size in 1..100)
            return tasks.map { element ->
                val task = element.jsonObject
                val kinds = (task["actions"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.text("type") }.distinct()
                MacroRule(name = task.text("name").take(100).ifBlank { "OpenTasker görevi" }, actions = emptyList(), source = "OpenTasker",
                    blockers = listOf("OpenTasker görev/koşul grafiği inceleme kaydıdır; yerel bir tarife uyarlanmadan çalışmaz.") + kinds.take(40),
                    original = if (tasks.size == 1) raw else buildJsonObject {
                        root.filterKeys { it != "tasks" }.forEach { (key, value) -> put(key, value) }
                        put("tasks", JsonArray(listOf(task)))
                    }.toString())
            }
        }
        error("Hermes JSON, MacroDroid .macro/.mdr veya OpenTasker bundle gerekli")
    }
    fun recipes(): List<MacroRule> = listOf(
        MacroRule(name = "Şarj arkadaşı", trigger = "charging", actions = listOf(MacroAction("notify", "Telefon şarja bağlandı."))),
        MacroRule(name = "Odak zamanı", actions = listOf(MacroAction("volume", "0"), MacroAction("vibrate"))),
        MacroRule(name = "Medya sesi %35", actions = listOf(MacroAction("volume", "35")), source = "MacroDroid FastMediaVol yaklaşımı · yerel uyarlama"),
        MacroRule(name = "Feneri aç", actions = listOf(MacroAction("torch", "on")), source = "MacroDroid ShakeFlashlight · elle çalışan uyarlama"),
        MacroRule(name = "Feneri kapat", actions = listOf(MacroAction("torch", "off"))),
    )
}
