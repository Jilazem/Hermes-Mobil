package com.hermes.mobile.data

import kotlinx.serialization.json.*

data class SubagentActivity(
    val id: String, val goal: String = "", val status: String = "", val model: String = "",
    val childSession: String = "", val duration: Double? = null, val inputTokens: Long? = null,
    val outputTokens: Long? = null, val toolCount: Long? = null, val summary: String = "",
    val output: String = "", val filesRead: List<String> = emptyList(), val filesWritten: List<String> = emptyList(),
)

/** Merge only supplied values; an absent metric is never presented as measured zero. */
internal fun mergeSubagentActivity(previous: List<SubagentActivity>, type: String, payload: JsonObject?): List<SubagentActivity> {
    if (type !in setOf("subagent.spawn_requested", "subagent.start", "subagent.complete", "subagent.update", "subagent.progress", "subagent.tool")) return previous
    val p = payload ?: return previous
    fun value(key: String) = (p[key] as? JsonPrimitive)?.contentOrNull
    val id = value("subagent_id") ?: value("child_session_id")
        ?: value("delegation_id")?.let { "$it:${value("task_index") ?: "0"}" } ?: return previous
    if (id.isBlank()) return previous
    val old = previous.firstOrNull { it.id == id } ?: SubagentActivity(id)
    val next = old.copy(
        goal = value("goal") ?: old.goal,
        status = value("status") ?: when(type) { "subagent.spawn_requested" -> "pending"; "subagent.start" -> "running"; "subagent.complete" -> "completed"; else -> old.status },
        model = value("model") ?: old.model,
        childSession = value("child_session_id") ?: old.childSession,
        duration = value("duration_seconds")?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: old.duration,
        inputTokens = value("input_tokens")?.toLongOrNull()?.takeIf { it >= 0 } ?: old.inputTokens,
        outputTokens = value("output_tokens")?.toLongOrNull()?.takeIf { it >= 0 } ?: old.outputTokens,
        toolCount = value("tool_count")?.toLongOrNull()?.takeIf { it >= 0 } ?: old.toolCount,
        summary = value("summary")?.take(8000) ?: old.summary,
        output = (p["output_tail"] as? JsonArray)?.mapNotNull { entry ->
            val row = entry as? JsonObject ?: return@mapNotNull null
            listOf("tool", "preview").mapNotNull { (row[it] as? JsonPrimitive)?.contentOrNull }.joinToString(": ")
        }?.joinToString("\n")?.takeLast(8000) ?: value("output_tail")?.takeLast(8000) ?: old.output,
        filesRead = (p["files_read"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.take(100) ?: old.filesRead,
        filesWritten = (p["files_written"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.take(100) ?: old.filesWritten,
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
