package com.hermes.mobile.data

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Profile selection happens at session creation, never by replacing an existing agent's SOUL. */
class VoiceSpecialistSessions(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("voice-specialist", Context.MODE_PRIVATE)
    data class Selection(val runtime: String, val stored: String, val model: String?, val context: String)
    private fun records(): List<VoiceSpecialistLogic.Record> =
        Json.decodeFromString<List<VoiceSpecialistLogic.Record>>(prefs.getString("records", "[]")!!)
    private fun save(records: List<VoiceSpecialistLogic.Record>) {
        check(prefs.edit().putString("records", Json.encodeToString(records)).commit()) { "Sesli oturum kaydedilemedi" }
    }
    suspend fun select(gw: GatewayWsClient, server: ServerProfile, source: String?): Selection? = lock.withLock {
        val cacheKey = "${server.id}|${server.normalizedUrl}|${server.normalizedRemote}"
        val now = System.currentTimeMillis()
        val cached = available[cacheKey]?.takeIf { now - it.first < 60_000 }
        val expert = if (cached != null) cached.second else HermesClient(server).profiles()
            .firstOrNull { it.name == VoiceSpecialistLogic.PROFILE }.also { available[cacheKey] = now to it }
        if (expert == null) return@withLock null // Older servers keep their existing voice path.
        val all = records()
        val saved = VoiceSpecialistLogic.find(all, server.id, source)
        if (saved != null) {
            val runtime = gw.resumeSession(saved.voice, VoiceSpecialistLogic.PROFILE) // Failed restores must not silently erase context.
            return@withLock Selection(runtime, gw.storedSessionId(runtime), expert.model, saved.context)
        }
        val history = if (source == null) emptyList() else {
            val liveSource = gw.activeSessions().firstOrNull { it.dbId == source || it.id == source }
            // A valid empty live conversation has no REST row yet. Read by runtime without
            // rebinding its event transport or mistaking an empty history for a missing session.
            if (liveSource != null) gw.sessionHistory(liveSource.id)
            else HermesClient(server).sessionMessages(source)
        }
        val seed = VoiceSpecialistLogic.context(source, history)
        val runtime = gw.createSession(VoiceSpecialistLogic.PROFILE)
        val stored = gw.storedSessionId(runtime)
        save(all + VoiceSpecialistLogic.Record(server.id, source, stored, seed))
        Selection(runtime, stored, expert.model, seed)
    }
    fun profileFor(server: String, stored: String): String? =
        VoiceSpecialistLogic.PROFILE.takeIf { records().any { it.server == server && it.voice == stored } }

    suspend fun pendingContext(server: String, stored: String): String = lock.withLock {
        records().firstOrNull { it.server == server && it.voice == stored }?.context.orEmpty()
    }
    suspend fun submitted(server: String, stored: String) = lock.withLock {
        save(records().map { if (it.server == server && it.voice == stored) it.copy(context = "") else it })
    }
    suspend fun forget(server: String, stored: String?) = lock.withLock {
        save(records().filterNot { it.server == server && it.voice == stored })
    }
    companion object {
        private val lock = Mutex()
        private val available = mutableMapOf<String, Pair<Long, HermesProfile?>>()
    }
}
