package com.hermes.mobile.data

import kotlinx.serialization.json.*

data class SubagentActivity(
    val id: String,
    val goal: String = "",
    val status: String = "",
    val model: String = "",
    val childSession: String = "",
    val duration: Double? = null,
    val inputTokens: Long? = null,
    val outputTokens: Long? = null,
    val toolCount: Long? = null,
    val summary: String = "",
    val output: String = "",
    val filesRead: List<String> = emptyList(),
    val filesWritten: List<String> = emptyList(),
    val role: String = "",
    val startedAtEpochSec: Double? = null,
)

/**
 * Model adını Görev Hattı rozeti için sadeleştirir; eski GLM-5.3-Flash-EXL3
 * yerel yedek kalıntısını ds4.1 olarak düzeltir.
 */
internal fun compactSubagentModel(rawModel: String): String {
    val trimmed = rawModel.trim()
    if (trimmed.isEmpty()) return ""
    val lower = trimmed.lowercase()
    if ("glm-5.3" in lower || "glm5.3" in lower) return "ds4.1"
    val afterSlash = trimmed.substringAfterLast('/').trim()
    return when (afterSlash.lowercase()) {
        "deepseek-v4.1", "deepseek-r1", "ds4.1" -> "ds4.1"
        else -> afterSlash.ifBlank { trimmed }
    }
}

/**
 * Görev özetindeki çift 'Özet: et:' ve eski GLM-5.3 fallback uyarı gürültüsünü temizler.
 */
internal fun cleanSubagentSummary(raw: String): String {
    if (raw.isBlank()) return ""
    var cleaned = raw.trim()
    val prefixes = listOf("Özet: et:", "Özet : et:", "Özet:", "et:")
    var changed = true
    while (changed) {
        changed = false
        for (p in prefixes) {
            if (cleaned.startsWith(p, ignoreCase = true)) {
                cleaned = cleaned.substring(p.length).trim()
                changed = true
            }
        }
    }
    cleaned = cleaned.replace(Regex("(?i)⚠?[ ]*Primary auth failed[^a-zA-Z0-9]*yedeğe geçiliyor:[ ]*node-local-models[ ]*/[ ]*GLM-5[.0-9]*-Flash-EXL3"), "").trim()
    return cleaned
}

/** Çalışan görev için başlangıç zamanından canlı süre veya ölçülmüş bitiş süresi (saniye). */
internal fun effectiveDurationSeconds(agent: SubagentActivity, nowEpochSec: Double? = null): Long? {
    agent.duration?.takeIf { it.isFinite() && it >= 0 }?.let { return it.toLong() }
    val start = agent.startedAtEpochSec?.takeIf { it.isFinite() && it > 0 } ?: return null
    val now = nowEpochSec?.takeIf { it.isFinite() && it >= start } ?: return null
    val running = agent.status.lowercase() in setOf("running", "working", "starting", "pending", "queued")
    return if (running) (now - start).toLong().coerceAtLeast(0L) else null
}

/**
 * Yeni Hermes WebUI (http://192.168.1.101:9120/#/) Görev Hattı ile uyumlu
 * tek satırlık şık görev özeti:
 * `[Durum] · #ID (Uzman) · [Model] · İş Adı — Plan Özeti · [Süre · Araç · Token]`
 */
internal fun formatSubagentSingleLine(agent: SubagentActivity, nowEpochSec: Double? = null): String {
    val statusLabel = when (agent.status.lowercase()) {
        "running", "working" -> "Çalışıyor"
        "completed", "done", "success", "succeeded", "pass" -> "PASS"
        "failed", "error" -> "Hata"
        "queued", "pending" -> "Sırada"
        "starting" -> "Başlıyor"
        "unconfirmed" -> "Canlı listede yok"
        "interrupted", "cancelled" -> "Durduruldu"
        else -> agent.status.ifBlank { "Çalışıyor" }
    }
    val shortId = agent.id.removePrefix("gorev-").removePrefix("subagent-").take(14)
    val idWithRole = if (agent.role.isNotBlank()) "#$shortId (${agent.role})" else "#$shortId"
    val badgeModel = compactSubagentModel(agent.model)
    val cleanGoal = cleanSubagentSummary(agent.goal).lineSequence().firstOrNull()?.trim().orEmpty()
    val cleanPlan = cleanSubagentSummary(agent.summary).lineSequence().firstOrNull()?.trim().orEmpty()
    val titleAndPlan = when {
        cleanGoal.isNotBlank() && cleanPlan.isNotBlank() && cleanPlan != cleanGoal -> "$cleanGoal — $cleanPlan"
        cleanGoal.isNotBlank() -> cleanGoal
        cleanPlan.isNotBlank() -> cleanPlan
        else -> agent.id
    }
    val durSec = effectiveDurationSeconds(agent, nowEpochSec)
    val metrics = listOfNotNull(
        durSec?.let { "${it}sn" },
        agent.toolCount?.let { "${it} araç" },
        agent.outputTokens?.let { "${it} tok" },
    ).joinToString(" · ")

    return listOfNotNull(
        statusLabel,
        idWithRole,
        badgeModel.takeIf { it.isNotBlank() }?.let { "[$it]" },
        titleAndPlan,
        metrics.takeIf { it.isNotBlank() },
    ).joinToString(" · ")
}

/** Merge only supplied values; an absent metric is never presented as measured zero. */
internal fun mergeSubagentActivity(previous: List<SubagentActivity>, type: String, payload: JsonObject?): List<SubagentActivity> {
    if (type !in setOf("subagent.spawn_requested", "subagent.start", "subagent.complete", "subagent.update", "subagent.progress", "subagent.tool")) return previous
    val p = payload ?: return previous
    fun value(key: String) = (p[key] as? JsonPrimitive)?.contentOrNull
    val id = value("subagent_id") ?: value("child_session_id")
        ?: value("delegation_id")?.let { "$it:${value("task_index") ?: "0"}" } ?: return previous
    if (id.isBlank()) return previous
    val old = previous.firstOrNull { it.id == id } ?: SubagentActivity(id)
    val parsedStart = value("started_at")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        ?: value("created_at")?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        ?: old.startedAtEpochSec
        ?: (if (type == "subagent.start") System.currentTimeMillis() / 1000.0 else null)
    val next = old.copy(
        goal = value("goal") ?: old.goal,
        status = value("status") ?: when(type) { "subagent.spawn_requested" -> "pending"; "subagent.start" -> "running"; "subagent.complete" -> "completed"; else -> old.status },
        model = value("model") ?: old.model,
        childSession = value("child_session_id") ?: old.childSession,
        duration = value("duration_seconds")?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: old.duration,
        inputTokens = value("input_tokens")?.toLongOrNull()?.takeIf { it >= 0 } ?: old.inputTokens,
        outputTokens = value("output_tokens")?.toLongOrNull()?.takeIf { it >= 0 } ?: old.outputTokens,
        toolCount = value("tool_count")?.toLongOrNull()?.takeIf { it >= 0 } ?: old.toolCount,
        summary = value("summary")?.let { cleanSubagentSummary(it).take(8000) } ?: old.summary,
        output = (p["output_tail"] as? JsonArray)?.mapNotNull { entry ->
            val row = entry as? JsonObject ?: return@mapNotNull null
            listOf("tool", "preview").mapNotNull { (row[it] as? JsonPrimitive)?.contentOrNull }.joinToString(": ")
        }?.joinToString("\n")?.takeLast(8000) ?: value("output_tail")?.takeLast(8000) ?: old.output,
        filesRead = (p["files_read"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.take(100) ?: old.filesRead,
        filesWritten = (p["files_written"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.take(100) ?: old.filesWritten,
        role = value("role") ?: value("agent_id") ?: value("profile") ?: old.role,
        startedAtEpochSec = parsedStart,
    )
    return if (previous.any { it.id == id }) previous.map { if (it.id == id) next else it } else (previous + next).takeLast(100)
}

internal fun mergeSubagentSnapshot(previous: List<SubagentActivity>, snapshot: JsonObject): List<SubagentActivity> {
    var result = previous
    val live = (snapshot["subagents"] as? JsonArray)?.filterIsInstance<JsonObject>()
    if (live != null) {
        val ids = live.mapNotNull { (it["subagent_id"] as? JsonPrimitive)?.contentOrNull }.toSet()
        result = result.map { if (it.id !in ids && it.status.lowercase() in setOf("pending", "running", "starting", "working")) it.copy(status = "unconfirmed") else it }
    }
    live?.forEach {
        result = mergeSubagentActivity(result, "subagent.update", it)
    }
    (snapshot["delegations"] as? JsonArray)?.filterIsInstance<JsonObject>()?.forEach {
        val withSummary = JsonObject(it + ("summary" to (it["error"] ?: JsonPrimitive(""))))
        result = mergeSubagentActivity(result, "subagent.complete", withSummary)
    }
    return result
}
