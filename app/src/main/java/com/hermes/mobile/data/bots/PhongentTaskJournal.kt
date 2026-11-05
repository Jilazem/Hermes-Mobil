package com.hermes.mobile.data.bots

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermes.mobile.data.remote.RemoteAgentConnection
import com.hermes.mobile.data.remote.RemoteAgentTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

/** Phongent's durable queue is actually used for outbound device commands. */
class PhongentTaskJournal internal constructor(private val prefs: SharedPreferences) {
    constructor(context: Context, connection: RemoteAgentConnection) : this(preferences(context, connection))
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutable = MutableStateFlow(emptyList<ControllerTask>())
    val tasks = mutable.asStateFlow()
    private var loadedRaw: String? = null
    private var cachedQueue: DurableControllerQueue? = null
    // Storage errors must block dispatch, but must not crash a Compose remember block.
    init { synchronized(LOCK) { runCatching { queue() } } }

    private fun queue(): DurableControllerQueue {
        val raw = prefs.getString("tasks", "[]").orEmpty()
        if (cachedQueue != null && raw == loadedRaw) return requireNotNull(cachedQueue)
        val rows = try { json.decodeFromString<List<ControllerTask>>(raw).also { saved ->
            require(saved.distinctBy { it.id }.size == saved.size)
            require(saved.all { it.id.matches(Regex("[A-Za-z0-9-]{8,80}")) && it.text.isNotBlank() &&
                it.text.length <= 8000 && it.state in DurableControllerQueue.ACTIVE + DurableControllerQueue.TERMINAL })
        } }
        catch (e: Exception) { throw IllegalStateException("Görev kaydı okunamadı; kayıt onarılmadan yeni iş gönderilmez.", e) }
        val next = DurableControllerQueue(rows) { changed ->
            val serialized = json.encodeToString(changed)
            check(prefs.edit().putString("tasks", serialized).commit()) { "Görev kaydı saklanamadı; gönderilmedi." }
            loadedRaw = serialized
            mutable.value = changed
        }
        loadedRaw = raw
        cachedQueue = next
        mutable.value = next.snapshot()
        return next
    }

    fun prepare(goal: String, conversation: String): ControllerTask = synchronized(LOCK) {
        val queue = queue()
        check(queue.snapshot().none { it.state in DurableControllerQueue.ACTIVE }) {
            "Bu cihaza önceki gönderim hâlâ izleniyor. Önce görev durumunu yenileyin."
        }
        val task = queue.enqueue(ControllerTask(UUID.randomUUID().toString(), goal, conversation))
        requireNotNull(queue.claim()).also { check(it.id == task.id) }
    }

    fun received(task: RemoteAgentTask) = synchronized(LOCK) {
        val queue = queue()
        if (task.conversation.isNotBlank()) queue.bindConversation(task.id, task.conversation)
        val state = when (task.status) {
            "completed", "done" -> "completed"
            "cancelled" -> "cancelled"
            "failed" -> "failed"
            "uncertain", "unknown" -> "uncertain"
            else -> "running"
        }
        queue.update(task.id, state, task.progress.orEmpty(), task.answer)
    }

    fun unknown(id: String) = synchronized(LOCK) {
        val queue = queue()
        if (queue.snapshot().any { it.id == id && it.state in DurableControllerQueue.ACTIVE })
            queue.update(id, "uncertain", "İstek ulaşmış olabilir; otomatik tekrar gönderilmez.")
    }

    /** A concrete rejection did not admit a remote job; it must not poison the outbox forever. */
    fun reject(id: String, reason: String) = synchronized(LOCK) {
        val queue = queue()
        if (queue.snapshot().any { it.id == id && it.state in DurableControllerQueue.ACTIVE })
            queue.update(id, "failed", reason.take(300))
    }

    fun unresolved(): List<ControllerTask> = synchronized(LOCK) {
        queue().snapshot().filter { it.state in DurableControllerQueue.ACTIVE }
    }

    /** Safe only for a persisted local queued row which was never claimed for dispatch. */
    fun discardQueued(id: String) = synchronized(LOCK) { queue().cancel(id) }

    /** Explicit user reconciliation after a successful fresh task-list read, never an automatic retry. */
    fun acknowledgeAbsent(id: String, remote: List<RemoteAgentTask>) = synchronized(LOCK) {
        val queue = queue()
        check(remote.none { it.id == id }) { "Cihaz bu görevi hâlâ listeliyor; gerçek durumu izleyin." }
        check(queue.snapshot().any { it.id == id && it.state == "uncertain" }) { "Yalnız sonucu belirsiz kayıt çözümlenebilir." }
        queue.update(id, "failed", "Kullanıcı güncel cihaz listesini kontrol etti; görev bulunamadı. Eski istek tekrar gönderilmez.")
    }

    fun reconcile(remote: List<RemoteAgentTask>) = synchronized(LOCK) {
        val queue = queue()
        queue.snapshot().filter { it.state in DurableControllerQueue.ACTIVE }.forEach { local ->
            remote.firstOrNull { it.id == local.id }?.let(::received)
        }
    }

    companion object {
        private val LOCK = Any()
        private fun preferences(context: Context, connection: RemoteAgentConnection): SharedPreferences {
            val scopeKey = MessageDigest.getInstance("SHA-256")
                .digest("${connection.baseUrl.trimEnd('/')}\n${connection.token}".toByteArray()).take(12).joinToString("") { "%02x".format(it) }
            return EncryptedSharedPreferences.create(context.applicationContext, "phongent-outbox-$scopeKey",
                MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        }
    }
}
