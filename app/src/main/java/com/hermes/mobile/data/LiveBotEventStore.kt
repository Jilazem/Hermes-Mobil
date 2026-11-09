package com.hermes.mobile.data

import com.hermes.mobile.ui.SmartSessionTitle
import com.hermes.mobile.ui.bots.BotLocalization
import com.hermes.mobile.ui.bots.BotMascotMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

enum class BotEventType {
    TASK_STARTED,
    BOT_MESSAGE,
    THINKING,
    TOOL_CALL,
    SUBAGENT_PROGRESS,
    APPROVAL_REQUEST,
    TASK_COMPLETED,
    TASK_FAILED,
    ARTIFACT_PRODUCED
}

data class LiveBotEvent(
    val id: String,
    val sessionId: String,
    val botName: String,
    val botAvatar: String = "🤖",
    val mood: BotMascotMood = BotMascotMood.IDLE,
    val type: BotEventType,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolName: String? = null,
    val toolDetail: String? = null,
    val terminalOutput: String? = null,
    val model: String? = null,
    val isStreaming: Boolean = false,
    val status: String = "working", // working, completed, failed, waiting
    val artifactUrl: String? = null,
    val artifactType: String? = null, // image, video, file, code
    val approvalKey: String? = null,
)

/**
 * Telegram benzeri gerçek zamanlı bot akış deposu (v7.7.0).
 *
 * Çoklu botların tüm faaliyetlerini kronolojik olarak biriktirir,
 * WebSocket üzerinden akan deltalari tek balonda büyütür,
 * tekilleştirir ve ekran titremesi olmadan arayüze sunar.
 */
object LiveBotEventStore {

    private val _events = MutableStateFlow<List<LiveBotEvent>>(emptyList())
    val events: StateFlow<List<LiveBotEvent>> = _events.asStateFlow()

    private val eventIndex = ConcurrentHashMap<String, Int>()
    private val activeStreams = ConcurrentHashMap<String, String>() // sessionId -> eventId
    private val seq = AtomicLong(1)
    private const val MAX_EVENTS = 1000

    @Synchronized
    fun clear() {
        _events.value = emptyList()
        eventIndex.clear()
        activeStreams.clear()
    }

    @Synchronized
    fun appendOrUpdate(event: LiveBotEvent) {
        val currentList = _events.value.toMutableList()
        val existingIdx = eventIndex[event.id]

        if (existingIdx != null && existingIdx < currentList.size && currentList[existingIdx].id == event.id) {
            // Var olan olayı güncelle (delta ekleme, durum değişikliği vb.)
            val prev = currentList[existingIdx]
            val updatedContent = if (event.isStreaming && prev.isStreaming) {
                if (event.content.startsWith(prev.content)) event.content else prev.content + event.content
            } else {
                event.content.ifBlank { prev.content }
            }
            currentList[existingIdx] = event.copy(
                content = updatedContent,
                timestamp = prev.timestamp, // İlk başlama anını koru
            )
            _events.value = currentList
        } else {
            // Yeni olay ekle
            if (currentList.size >= MAX_EVENTS) {
                currentList.removeAt(0)
                rebuildIndex(currentList)
            }
            val newIdx = currentList.size
            currentList.add(event)
            eventIndex[event.id] = newIdx
            _events.value = currentList
        }
    }

    private fun rebuildIndex(list: List<LiveBotEvent>) {
        eventIndex.clear()
        list.forEachIndexed { idx, it ->
            eventIndex[it.id] = idx
        }
    }

    /**
     * WebSocket olay akışını dinler ve gerçek zamanlı bot akışına dönüştürür.
     */
    fun attachWebSocket(eventsFlow: SharedFlow<GatewayEvent>, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            eventsFlow.collect { gw ->
                processGatewayEvent(gw)
            }
        }
    }

    fun processGatewayEvent(gw: GatewayEvent) {
        val sid = gw.sessionId.orEmpty().ifBlank { "hermes-main" }
        val payload = gw.payload ?: JsonObject(emptyMap())

        when (gw.type) {
            "message.delta" -> {
                val delta = gw.text.orEmpty()
                if (delta.isEmpty()) return
                val streamId = activeStreams.getOrPut(sid) { "msg_${sid}_${seq.incrementAndGet()}" }
                appendOrUpdate(
                    LiveBotEvent(
                        id = streamId,
                        sessionId = sid,
                        botName = extractBotName(payload, "Hermes"),
                        mood = BotMascotMood.WORKING,
                        type = BotEventType.BOT_MESSAGE,
                        title = "Mesaj üretiyor",
                        content = delta,
                        isStreaming = true,
                        status = "working",
                    )
                )
            }

            "thinking.delta" -> {
                val delta = gw.text.orEmpty()
                if (delta.isEmpty()) return
                val streamId = "think_${sid}_active"
                appendOrUpdate(
                    LiveBotEvent(
                        id = streamId,
                        sessionId = sid,
                        botName = extractBotName(payload, "Hermes"),
                        mood = BotMascotMood.THINKING,
                        type = BotEventType.THINKING,
                        title = "Akıl yürütüyor",
                        content = delta,
                        isStreaming = true,
                        status = "working",
                    )
                )
            }

            "tool.call", "tool.start" -> {
                val toolName = gw.toolName ?: payload["name"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                val toolTr = BotLocalization.toolDisplayName(toolName)
                val eventId = "tool_${sid}_${seq.incrementAndGet()}"
                appendOrUpdate(
                    LiveBotEvent(
                        id = eventId,
                        sessionId = sid,
                        botName = extractBotName(payload, "Hermes"),
                        mood = BotMascotMood.WORKING,
                        type = BotEventType.TOOL_CALL,
                        title = "Araç çalıştırılıyor: $toolTr",
                        content = gw.toolDetail ?: "",
                        toolName = toolTr,
                        terminalOutput = gw.toolDetail,
                        status = "working",
                    )
                )
            }

            "tool.end", "tool.result" -> {
                val toolName = gw.toolName ?: payload["name"]?.let { (it as? JsonPrimitive)?.content }.orEmpty()
                val toolTr = BotLocalization.toolDisplayName(toolName)
                val eventId = "tool_${sid}_done_${seq.incrementAndGet()}"
                appendOrUpdate(
                    LiveBotEvent(
                        id = eventId,
                        sessionId = sid,
                        botName = extractBotName(payload, "Hermes"),
                        mood = BotMascotMood.SUCCESS,
                        type = BotEventType.TOOL_CALL,
                        title = "Araç tamamlandı: $toolTr",
                        content = gw.toolDetail ?: "Çıktı hazır",
                        toolName = toolTr,
                        terminalOutput = gw.toolDetail,
                        status = "completed",
                    )
                )
            }

            "session.create", "task.start" -> {
                val eventId = "start_${sid}_${seq.incrementAndGet()}"
                activeStreams.remove(sid)
                appendOrUpdate(
                    LiveBotEvent(
                        id = eventId,
                        sessionId = sid,
                        botName = extractBotName(payload, "Hermes"),
                        mood = BotMascotMood.IDLE,
                        type = BotEventType.TASK_STARTED,
                        title = "Yeni görev başlatıldı",
                        content = gw.text ?: "Oturum hazırlandı.",
                        status = "working",
                    )
                )
            }

            "session.end", "task.done", "task.complete" -> {
                val eventId = "end_${sid}_${seq.incrementAndGet()}"
                activeStreams.remove(sid)
                appendOrUpdate(
                    LiveBotEvent(
                        id = eventId,
                        sessionId = sid,
                        botName = extractBotName(payload, "Hermes"),
                        mood = BotMascotMood.SUCCESS,
                        type = BotEventType.TASK_COMPLETED,
                        title = "Görev başarıyla tamamlandı",
                        content = gw.text ?: "Tüm adımlar tamamlandı.",
                        status = "completed",
                    )
                )
            }

            "task.error", "error" -> {
                val eventId = "err_${sid}_${seq.incrementAndGet()}"
                activeStreams.remove(sid)
                appendOrUpdate(
                    LiveBotEvent(
                        id = eventId,
                        sessionId = sid,
                        botName = extractBotName(payload, "Hermes"),
                        mood = BotMascotMood.ERROR,
                        type = BotEventType.TASK_FAILED,
                        title = "Görevde hata oluştu",
                        content = gw.text ?: "Beklenmeyen bir hata alındı.",
                        status = "failed",
                    )
                )
            }

            "approval.request" -> {
                val key = payload["key"]?.let { (it as? JsonPrimitive)?.content } ?: sid
                val eventId = "appr_${sid}_$key"
                appendOrUpdate(
                    LiveBotEvent(
                        id = eventId,
                        sessionId = sid,
                        botName = extractBotName(payload, "Hermes"),
                        mood = BotMascotMood.WAITING,
                        type = BotEventType.APPROVAL_REQUEST,
                        title = "Kullanıcı onayı bekleniyor",
                        content = gw.text ?: "İşlemin devamı için onayınız gerekiyor.",
                        status = "waiting",
                        approvalKey = key,
                    )
                )
            }
        }
    }

    private fun extractBotName(payload: JsonObject, defaultName: String): String {
        return payload["bot"]?.let { (it as? JsonPrimitive)?.content }
            ?: payload["bot_name"]?.let { (it as? JsonPrimitive)?.content }
            ?: payload["source"]?.let { (it as? JsonPrimitive)?.content?.replaceFirstChar(Char::uppercase) }
            ?: defaultName
    }

    /**
     * Oturumlardan başlangıç akışını oluşturur (uygulama ilk açıldığında veya çevrimdışıyken).
     */
    fun seedFromSessions(sessions: List<HermesSession>, live: List<LiveSession>) {
        if (_events.value.isNotEmpty()) return // Zaten veri varsa üzerine yazma

        val seedList = mutableListOf<LiveBotEvent>()

        // 1. Canlı çalışan oturumlar en üstte
        live.forEach { l ->
            val sid = l.dbId.ifBlank { l.id }
            val smartTitle = SmartSessionTitle.deriveTitle(
                rawTitle = l.title,
                preview = l.preview,
                isSubagent = SubagentTreeStore.parentOf(sid) != null,
                epochSeconds = maxOf(l.lastActive, l.startedAt),
                sessionId = sid,
            )
            val mood = if (l.isWorking) BotMascotMood.WORKING else if (l.isWaiting) BotMascotMood.WAITING else BotMascotMood.IDLE
            seedList.add(
                LiveBotEvent(
                    id = "seed_live_$sid",
                    sessionId = sid,
                    botName = "Hermes",
                    mood = mood,
                    type = if (l.isWorking) BotEventType.BOT_MESSAGE else BotEventType.TASK_STARTED,
                    title = smartTitle,
                    content = l.preview.ifBlank { "İşlem devam ediyor…" },
                    timestamp = (maxOf(l.lastActive, l.startedAt) * 1000).toLong().coerceAtLeast(System.currentTimeMillis() - 60000),
                    status = if (l.isWorking) "working" else if (l.isWaiting) "waiting" else "completed",
                )
            )
        }

        // 2. Geçmiş oturumların en son aktiviteleri
        sessions.take(15).forEach { s ->
            val sid = s.id
            if (seedList.any { it.sessionId == sid }) return@forEach
            val smartTitle = SmartSessionTitle.deriveTitle(
                rawTitle = s.serverTitle ?: s.displayName,
                preview = s.preview,
                isSubagent = s.effectiveParentId != null || SubagentTreeStore.parentOf(sid) != null,
                epochSeconds = s.startedAt ?: 0.0,
                sessionId = sid,
            )
            seedList.add(
                LiveBotEvent(
                    id = "seed_rest_$sid",
                    sessionId = sid,
                    botName = s.source?.replaceFirstChar(Char::uppercase) ?: "Hermes",
                    mood = BotMascotMood.SUCCESS,
                    type = BotEventType.TASK_COMPLETED,
                    title = smartTitle,
                    content = s.preview?.ifBlank { "Oturum başarıyla tamamlandı." } ?: "Oturum hazır.",
                    timestamp = ((s.startedAt ?: 0.0) * 1000).toLong().coerceAtLeast(1000L),
                    status = if (s.isActive) "working" else "completed",
                )
            )
        }

        seedList.sortBy { it.timestamp }
        seedList.forEach { appendOrUpdate(it) }
    }
}
