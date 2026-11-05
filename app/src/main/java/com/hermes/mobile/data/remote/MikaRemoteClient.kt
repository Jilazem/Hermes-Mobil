/*
 * Mika task-state vocabulary and terminal/interaction semantics adapted from:
 * Copyright 2026 wangzan101. Apache License, Version 2.0.
 * wangzan101/Mika@ebb0e44750d80aabb6b642c69908825d6b1a8107
 * core/src/main/kotlin/com/wangzan101/mika/core/remote/RemoteTasks.kt
 * and RemoteTaskService.kt. Changes: outbound Hermes client and strict DTO parsing.
 * See third_party/mika/LICENSE and NOTICE.
 */
package com.hermes.mobile.data.remote

import kotlinx.serialization.json.*
import java.util.UUID

data class MikaCapabilities(
    val apiVersion: Int,
    val serverVersion: String,
    val capabilities: Set<String>,
    val maxWaitSeconds: Int,
    val maxGoal: Int,
    val maxAnswer: Int,
    val maxSteer: Int,
)

object MikaTaskStatus {
    const val STARTING = "starting"
    const val RUNNING = "running"
    const val QUEUED = "queued"
    const val PAUSED = "paused"
    const val WAITING_FOR_ANSWER = "waiting_for_answer"
    const val WAITING_ON_PHONE = "waiting_on_phone"
    const val DONE = "done"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
    val values = setOf(STARTING, RUNNING, QUEUED, PAUSED, WAITING_FOR_ANSWER, WAITING_ON_PHONE, DONE, FAILED, CANCELLED)
    fun finished(status: String) = status == DONE || status == FAILED || status == CANCELLED
}

class MikaRemoteClient(connection: RemoteAgentConnection) {
    private val http = RemoteAgentHttp(connection)
    private var limits: MikaCapabilities? = null

    suspend fun capabilities(): MikaCapabilities {
        val result = http.request("GET", "/api/v1")
        val version = result.int("api_version")
        val capabilities = result.strings("capabilities").toSet()
        val sizes = result["limits"] as? JsonObject ?: invalid()
        if (version != 1 || "tasks" !in capabilities) throw RemoteProtocolException("Bu servis Mika görev API v1 protokolünü sunmuyor.")
        val response = MikaCapabilities(version, result.string("server_version"), capabilities,
            sizes.positive("max_wait_s"), sizes.positive("max_goal"), sizes.positive("max_answer"), sizes.positive("max_steer"))
        limits = response
        return response
    }

    suspend fun list(limit: Int = 50): List<RemoteAgentTask> {
        require(limit in 1..50) { "Görev listesi sınırı 1–50 arasında olmalı." }
        val result = http.request("GET", "/api/v1/tasks?limit=$limit")
        val tasks = result["tasks"] as? JsonArray ?: invalid()
        return tasks.map { parseTask(it as? JsonObject ?: invalid()) }
    }

    suspend fun task(id: String): RemoteAgentTask = parseTask(http.request("GET", "/api/v1/tasks/${id(id)}"))

    suspend fun start(goal: String, conversation: String? = null): RemoteAgentTask {
        val clean = text(goal, limits?.maxGoal ?: 4000)
        val thread = conversation?.trim()?.takeIf(String::isNotEmpty)
        require(thread == null || validConversation(thread)) { "Devam etmek için Mika'nın döndürdüğü konuşma kimliğini kullanın." }
        val result = http.request("POST", "/api/v1/tasks", buildJsonObject {
            put("goal", clean); thread?.let { put("conversation", it) }
        })
        return mutationResult { parseTask(result) }
    }

    /** This bounded wait is read-only. Timing out never cancels or replays a task. */
    suspend fun wait(id: String, timeoutSeconds: Int = 30): RemoteAgentTask {
        require(timeoutSeconds in 1..minOf(limits?.maxWaitSeconds ?: 50, 50)) { "Bekleme süresi 1–50 saniye arasında olmalı." }
        return parseTask(http.request("POST", "/api/v1/tasks/${id(id)}/wait",
            buildJsonObject { put("timeout_s", timeoutSeconds) }, mutation = false))
    }

    suspend fun answer(id: String, text: String): RemoteCommandReceipt {
        val target = id(id)
        val clean = text(text, limits?.maxAnswer ?: 2000)
        val result = http.request("POST", "/api/v1/tasks/$target/answer", buildJsonObject { put("text", clean) })
        return mutationResult {
            requireReceipt(result, target)
            RemoteCommandReceipt("answer_accepted", target, accepted = true)
        }
    }

    suspend fun steer(id: String, text: String): RemoteCommandReceipt {
        val target = id(id)
        val clean = text(text, limits?.maxSteer ?: 4000)
        val result = http.request("POST", "/api/v1/tasks/$target/steer", buildJsonObject { put("text", clean) })
        return mutationResult {
            requireReceipt(result, target)
            RemoteCommandReceipt("steer_accepted", target, accepted = true, messageId = result.string("message_id"))
        }
    }

    suspend fun cancel(id: String): RemoteCommandReceipt {
        val target = id(id)
        val result = http.request("POST", "/api/v1/tasks/$target/cancel", buildJsonObject {})
        return mutationResult {
            if (result.string("task_id") != target) invalid()
            val accepted = result.boolean("accepted")
            val status = result.optional("status")
            if (status != null && status !in MikaTaskStatus.values) invalid()
            RemoteCommandReceipt(status ?: "cancellation_requested", target,
                confirmedTerminal = status == MikaTaskStatus.CANCELLED, accepted = accepted)
        }
    }

    private fun requireReceipt(result: JsonObject, target: String) {
        if (result.string("task_id") != target || !result.boolean("accepted")) invalid()
    }

    private fun parseTask(result: JsonObject): RemoteAgentTask {
        val taskId = id(result.string("task_id"))
        val status = result.string("status")
        val conversation = result.string("conversation")
        if (status !in MikaTaskStatus.values || !validConversation(conversation)) invalid()
        val answerable = result.boolean("answerable")
        if (answerable && status != MikaTaskStatus.WAITING_FOR_ANSWER) invalid()
        return RemoteAgentTask(taskId, conversation, result.string("goal"), status,
            progress = result.optional("progress") ?: result.optional("note") ?: result.optional("reason"),
            answer = result.optional("answer"), question = result.optional("question"),
            options = if (result["options"] == null) emptyList() else result.strings("options"),
            step = result["step"]?.let { (it as? JsonPrimitive)?.intOrNull ?: invalid() },
            answerable = answerable,
            verified = result["verified"]?.let { (it as? JsonPrimitive)?.booleanOrNull ?: invalid() })
    }

    private inline fun <T> mutationResult(parse: () -> T): T = try { parse() }
    catch (error: Exception) { throw RemoteOutcomeUnknownException(error) }

    private fun text(raw: String, max: Int): String = raw.trim().also {
        require(it.isNotEmpty() && it.length <= max) { "Metin 1–$max karakter arasında olmalı." }
    }

    private fun id(raw: String): String = raw.also { require(ID.matches(it)) { "Geçersiz uzak görev kimliği." } }
    private fun validConversation(value: String): Boolean = value.startsWith("mcp-") &&
        runCatching { UUID.fromString(value.removePrefix("mcp-")).toString() == value.removePrefix("mcp-") }.getOrDefault(false)

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank) ?: invalid()
    private fun JsonObject.optional(key: String): String? = when (val value = this[key]) {
        null, JsonNull -> null
        is JsonPrimitive -> value.takeIf { it.isString }?.content ?: invalid()
        else -> invalid()
    }
    private fun JsonObject.int(key: String): Int = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: invalid()
    private fun JsonObject.positive(key: String): Int = int(key).also { if (it <= 0) invalid() }
    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull ?: invalid()
    private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray)?.map {
        (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: invalid()
    } ?: invalid()

    private fun invalid(): Nothing = throw RemoteProtocolException("Mika yanıtı beklenen API v1 sözleşmesini karşılamıyor.")
    private companion object { val ID = Regex("[A-Za-z0-9_-]{1,160}") }
}
