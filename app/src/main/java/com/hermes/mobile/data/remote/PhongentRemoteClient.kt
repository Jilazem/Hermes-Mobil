package com.hermes.mobile.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.util.UUID

/** Phongent's approved LAN protocol, pinned in docs/integration/phongent.md. */
class PhongentRemoteClient(connection: RemoteAgentConnection) {
    private val http = RemoteAgentHttp(connection)

    suspend fun state(): PhongentState = parseState(http.request("GET", "/state"))
    suspend fun tasks(): List<RemoteAgentTask> = parseTasks(http.request("GET", "/tasks"))

    suspend fun beginPair(name: String, requestId: String = UUID.randomUUID().toString() + UUID.randomUUID().toString()): RemotePairState {
        require(PAIR_ID.matches(requestId))
        val response = http.request("POST", "/pair", buildJsonObject {
            put("requestId", requestId); put("name", name.filterNot(Char::isISOControl).take(60).ifBlank { "Hermes Mobile" })
        }, mutation = false, requireAuth = false)
        return parsePair(response, requestId)
    }

    suspend fun pollPair(requestId: String): RemotePairState {
        require(PAIR_ID.matches(requestId))
        return parsePair(http.request("GET", "/pair/$requestId", requireAuth = false), requestId)
    }

    suspend fun submit(goal: String, conversationId: String = "", requestId: String = UUID.randomUUID().toString()): RemoteAgentTask {
        require(goal.isNotBlank() && goal.length <= 8000 && COMMAND_ID.matches(requestId))
        val before = state()
        val conversation = conversationId.ifBlank { before.conversationId }
        val queued = before.taskQueueVersion == 1
        val body = buildJsonObject {
            put("id", requestId); put("action", "task"); put("text", goal.trim()); put("conversationId", conversation)
        }
        val response = try { http.request("POST", if (queued) "/tasks" else "/command", body) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (unknown: RemoteOutcomeUnknownException) {
            if (queued) {
                val receipt = try { tasks().firstOrNull { it.id == requestId } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                if (receipt != null) return receipt
            }
            throw unknown
        }
        if (response.string("id") != requestId) throw RemoteOutcomeUnknownException()
        val status = response.string("status")
        if (status !in TASK_STATES + "accepted") throw RemoteOutcomeUnknownException()
        return RemoteAgentTask(requestId, response.string("conversationId").ifBlank { conversation }, goal, status)
    }

    suspend fun stop(runId: String): RemoteCommandReceipt {
        require(runId.isNotBlank())
        val before = state()
        check(before.runId == runId) { "Cihazda açık görev değişti; durumu yenileyin." }
        val response = http.request("POST", "/command", buildJsonObject {
            put("id", UUID.randomUUID().toString()); put("action", "stop"); put("runId", runId)
        })
        val accepted = response.string("status") == "accepted"
        val after = try { state() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
        return RemoteCommandReceipt(response.string("status"), runId, before.conversationId,
            confirmedTerminal = after?.phase == "cancelled" && (after.runId == runId || after.runId.isBlank()), accepted = accepted)
    }

    suspend fun resume(runId: String): RemoteCommandReceipt {
        val before = state()
        check(before.runId == runId && before.canResume) { "Bu görev duraklatılmış değil; devam komutu gönderilmedi." }
        val response = http.request("POST", "/command", buildJsonObject {
            put("id", UUID.randomUUID().toString()); put("action", "resume"); put("runId", runId)
        })
        return RemoteCommandReceipt(response.string("status"), runId, before.conversationId, accepted = response.string("status") == "accepted")
    }

    suspend fun cancelQueued(id: String): RemoteCommandReceipt {
        require(COMMAND_ID.matches(id))
        val response = http.request("POST", "/tasks", buildJsonObject { put("action", "cancel"); put("id", id) })
        val task = parseTasks(response).firstOrNull { it.id == id }
        return RemoteCommandReceipt(task?.status ?: "unknown", id, task?.conversation,
            confirmedTerminal = task?.status == "cancelled", accepted = task?.status == "cancelled")
    }

    companion object {
        private val PAIR_ID = Regex("[A-Za-z0-9-]{64,80}")
        private val COMMAND_ID = Regex("[A-Za-z0-9-]{8,80}")
        private val TASK_STATES = setOf("queued", "dispatching", "running", "completed", "failed", "cancelled", "uncertain")

        fun parseState(value: JsonObject): PhongentState {
            val phase = value.string("phase")
            if (phase !in setOf("idle", "running", "waiting", "takeover", "confirmation", "completed", "failed", "cancelled"))
                throw RemoteProtocolException("Bu adres Phongent cihaz durumunu sunmuyor.")
            val lastExchange = (value["exchanges"] as? JsonArray)?.lastOrNull() as? JsonObject
            return PhongentState(value.string("device"), phase, value.string("detail"), value.string("runId"),
                value.string("currentConversationId").ifBlank { value.string("conversationId") },
                (value["taskQueueVersion"] as? JsonPrimitive)?.intOrNull ?: 0,
                canResume = lastExchange?.string("phase") == "paused")
        }

        fun parseTasks(value: JsonObject): List<RemoteAgentTask> {
            val rows = value["items"] as? JsonArray ?: throw RemoteProtocolException("Cihaz görev listesi alınamadı.")
            return rows.map { element ->
                val task = element as? JsonObject ?: throw RemoteProtocolException("Görev satırı geçersiz.")
                val id = task.string("id"); val state = task.string("state")
                if (!COMMAND_ID.matches(id) || state !in TASK_STATES) throw RemoteProtocolException("Görev kimliği veya durumu geçersiz.")
                RemoteAgentTask(id, task.string("conversation"), task.string("text"), state,
                    progress = task.string("error").takeIf(String::isNotBlank), answer = task.string("result").takeIf(String::isNotBlank))
            }
        }

        private fun parsePair(value: JsonObject, requestId: String): RemotePairState {
            val status = value.string("status")
            if (status !in setOf("pending", "approved", "denied", "expired")) throw RemoteProtocolException("Cihaz bağlantı onayı yanıtı geçersiz.")
            val token = value.string("token").takeIf(String::isNotBlank)
            if (status == "approved" && token == null) throw RemoteProtocolException("Cihaz erişim tokenını doğrulamadı.")
            return RemotePairState(requestId, status, token)
        }
    }
}

data class PhongentState(val device: String, val phase: String, val detail: String, val runId: String,
    val conversationId: String, val taskQueueVersion: Int, val canResume: Boolean = false)

class RemotePairState(val requestId: String, val status: String, val token: String? = null) {
    override fun toString() = "RemotePairState(status=$status, token=[hidden])"
}

private fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
