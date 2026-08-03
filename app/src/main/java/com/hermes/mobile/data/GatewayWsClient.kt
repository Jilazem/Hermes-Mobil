package com.hermes.mobile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * `/api/ws` — Hermes tui_gateway JSON-RPC istemcisi.
 *
 * Dashboard'ın `GatewayClient`'ıyla aynı lehçe (bkz.
 * hermes-agent/apps/shared/src/json-rpc-gateway.ts):
 *
 *   istek : {"jsonrpc":"2.0","id":"a1","method":"session.create","params":{}}
 *   yanıt : {"id":"a1","result":{...}} | {"id":"a1","error":{"message":"..."}}
 *   olay  : {"method":"event","params":{"type":"message.delta","payload":{…}}}
 *
 * Token modunda kimlik `?token=` sorgu parametresiyle geçilir — gated modda
 * bunun yerine tek kullanımlık ticket gerekir (Faz 5).
 */
class GatewayWsClient(private val profile: ServerProfile) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nextId = AtomicLong(0)
    private val pending = mutableMapOf<String, CompletableDeferred<JsonElement?>>()
    private val pendingLock = Any()

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private var closedByUser = false
    private var reconnectAttempt = 0

    /**
     * Soket **yeniden** açıldığında çağrılır (ilk bağlantıda değil).
     *
     * Gateway her WS istemcisini ayrı sayıyor: soket yenilendiğinde sunucu için
     * bu yeni bir istemci ve eski oturumla hiçbir bağı yok. Yeniden bağlanmayı
     * fark etmeden `prompt.submit` göndermek "oturum kopuyor" şikâyetinin asıl
     * sebebiydi — burada oturumu yeniden bağlama şansı veriyoruz.
     */
    var onReconnected: (() -> Unit)? = null

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<GatewayEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<GatewayEvent> = _events.asSharedFlow()

    fun connect() {
        if (_connection.value == ConnectionState.Open || _connection.value == ConnectionState.Connecting) return
        closedByUser = false
        openSocket()
    }

    private fun openSocket() {
        _connection.value = ConnectionState.Connecting
        val url = "${profile.wsBase}/api/ws?token=${profile.token}"
        val request = Request.Builder().url(url).build()
        socket = http.newWebSocket(request, Listener())
    }

    fun close() {
        closedByUser = true
        socket?.close(1000, "client closed")
        socket = null
        _connection.value = ConnectionState.Closed
        failAllPending("bağlantı kapatıldı")
        scope.cancel()
    }

    /** Bir JSON-RPC çağrısı yapar ve sonucu bekler. */
    suspend fun request(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
        timeoutMs: Long = 30_000,
    ): JsonElement? {
        val sock = socket ?: throw IllegalStateException("gateway bağlı değil")
        val id = "a${nextId.incrementAndGet()}"
        val deferred = CompletableDeferred<JsonElement?>()
        synchronized(pendingLock) { pending[id] = deferred }

        val frame = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", JsonPrimitive(id))
            put("method", JsonPrimitive(method))
            put("params", params)
        }

        if (!sock.send(frame.toString())) {
            synchronized(pendingLock) { pending.remove(id) }
            throw IllegalStateException("çerçeve gönderilemedi: $method")
        }

        return withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: run {
                synchronized(pendingLock) { pending.remove(id) }
                throw IllegalStateException("$method — ${timeoutMs / 1000}s içinde yanıt yok")
            }
    }

    /**
     * Yeni bir ajan oturumu açar, session_id döner.
     *
     * [profile] verilirse oturum o profilin evinde açılır: profilin kendi
     * SOUL.md'si (sistem promptu), becerileri ve .env'i geçerli olur.
     */
    suspend fun createSession(profile: String? = null): String {
        val params = buildJsonObject {
            profile?.takeIf { it.isNotBlank() }?.let { put("profile", JsonPrimitive(it)) }
        }
        val result = request("session.create", params)
        return result?.jsonObject?.get("session_id")?.jsonPrimitive?.content
            ?: throw IllegalStateException("session.create session_id döndürmedi")
    }

    /** Kullanıcı mesajını gönderir; yanıt `message.delta` olaylarıyla akar. */
    suspend fun submitPrompt(sessionId: String, text: String) {
        request(
            "prompt.submit",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("text", JsonPrimitive(text))
            },
        )
    }

    /**
     * Slash komutu çalıştırır — Telegram bot'undaki `/model`, `/cron`, `/skills`…
     * komutlarının aynısı. Çıktı düz metin döner.
     */
    suspend fun slashExec(sessionId: String, command: String): String {
        val result = request(
            "slash.exec",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("command", JsonPrimitive(command))
            },
            timeoutMs = 60_000,
        )
        return result?.jsonObject?.get("output")?.jsonPrimitive?.contentOrNullSafe().orEmpty()
    }

    /** Süren üretimi keser (ChatGPT'deki "durdur" düğmesinin karşılığı). */
    suspend fun interrupt(sessionId: String) {
        request(
            "session.interrupt",
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
            timeoutMs = 10_000,
        )
    }

    /** Geçmiş bir oturuma bağlanıp kaldığı yerden devam eder. */
    suspend fun resumeSession(sessionId: String) {
        request(
            "session.resume",
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    // ── Canlı oturum müdahalesi ───────────────────────────────────────
    //
    // Bu dört çağrı Hermes'in en ayırt edici yeteneği: başka bir kanalda
    // (Telegram, cron, CLI, TUI) çalışan bir ajana telefondan müdahale etmek.

    /** Gateway'de şu an canlı olan oturumlar (tarihsel DB değil). */
    suspend fun activeSessions(currentSessionId: String? = null): List<LiveSession> {
        val result = request(
            "session.active_list",
            buildJsonObject {
                currentSessionId?.let { put("current_session_id", JsonPrimitive(it)) }
            },
            timeoutMs = 15_000,
        ) ?: return emptyList()
        return runCatching {
            Json { ignoreUnknownKeys = true }
                .decodeFromJsonElement(ActiveSessionsResponse.serializer(), result)
                .sessions
        }.getOrDefault(emptyList())
    }

    /**
     * Oturumun mesaj geçmişi — **canlı oturumlar için tek doğru kaynak**.
     *
     * REST `/api/sessions/{id}/messages` yalnız veritabanına yazılmış mesajları
     * görür; canlı bir oturumun geçmişi ise gateway'in belleğinde durur ve
     * kapanana kadar DB'ye düşmeyebilir (361 mesajlık canlı oturum REST'te 0
     * dönüyordu). Bu RPC önce DB'yi dener, yoksa bellekteki geçmişi verir.
     */
    suspend fun sessionHistory(sessionId: String): List<SessionMessage> {
        val result = request(
            "session.history",
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
            timeoutMs = 30_000,
        ) ?: return emptyList()
        return runCatching {
            Json { ignoreUnknownKeys = true; isLenient = true }
                .decodeFromJsonElement(SessionHistoryResponse.serializer(), result)
                .messages
                .map { it.toSessionMessage() }
        }.getOrDefault(emptyList())
    }

    /** Canlı bir oturuma bağlanır — öncekini kapatmaz. */
    suspend fun activateSession(sessionId: String) {
        request(
            "session.activate",
            buildJsonObject { put("session_id", JsonPrimitive(sessionId)) },
        )
    }

    /**
     * Çalışan tura KESMEDEN metin enjekte eder.
     *
     * Metin, sonraki araç yığınının son sonucuna iliştirilir; model bir sonraki
     * iterasyonunda görür. Tur bölünmez, rol sırası bozulmaz.
     * Dönen `status`: "queued" | "rejected".
     */
    suspend fun steer(sessionId: String, text: String): String {
        val result = request(
            "session.steer",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("text", JsonPrimitive(text))
            },
            timeoutMs = 15_000,
        )
        return result?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNullSafe() ?: "unknown"
    }

    /**
     * Süren model turunu yönlendirir, geçerli işi koruyarak.
     *
     * Ajan aktif-tur yönlendirmeyi desteklemiyorsa 4010 hatası döner —
     * bu durumda çağıran `steer`e düşmeli.
     */
    suspend fun redirect(sessionId: String, text: String): String {
        val result = request(
            "session.redirect",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("text", JsonPrimitive(text))
            },
            timeoutMs = 15_000,
        )
        return result?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNullSafe() ?: "unknown"
    }

    private fun failAllPending(reason: String) {
        val snapshot = synchronized(pendingLock) {
            val copy = pending.values.toList()
            pending.clear()
            copy
        }
        snapshot.forEach { it.completeExceptionally(IllegalStateException(reason)) }
    }

    private fun handleFrame(text: String) {
        val frame = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return

        val id = frame["id"]?.jsonPrimitive?.contentOrNullSafe()
        if (id != null) {
            val call = synchronized(pendingLock) { pending.remove(id) } ?: return
            val error = frame["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNullSafe()
            if (error != null) call.completeExceptionally(IllegalStateException(error))
            else call.complete(frame["result"])
            return
        }

        if (frame["method"]?.jsonPrimitive?.contentOrNullSafe() == "event") {
            val params = frame["params"]?.jsonObject ?: return
            val type = params["type"]?.jsonPrimitive?.contentOrNullSafe() ?: return
            // ÖNEMLİ: `scope.launch { emit }` kullanma — her çerçeve ayrı bir
            // coroutine'e giderdi ve `message.delta` parçaları sırasını kaybederdi
            // (metin karışık çıkar). `tryEmit` OkHttp'nin tek okuma iş parçacığında
            // eşzamanlı çalışır, böylece sıra korunur.
            _events.tryEmit(
                GatewayEvent(
                    type = type,
                    sessionId = params["session_id"]?.jsonPrimitive?.contentOrNullSafe(),
                    payload = params["payload"]?.jsonObject,
                )
            )
        }
    }

    /** Kesintide üssel geri çekilmeyle yeniden bağlanır (en fazla 30 sn). */
    private fun scheduleReconnect() {
        if (closedByUser) return
        reconnectAttempt++
        // İlk deneme hemen: kopmaların çoğu anlık (NAT, hücre-Wi-Fi geçişi) ve
        // 2 saniye beklemek kullanıcıya "koptu" gibi görünüyor.
        val backoffMs =
            if (reconnectAttempt == 1) 300L
            else minOf(30_000L, 1_000L * (1L shl minOf(reconnectAttempt, 5)))
        DiagLog.d("ws", "reconnect #$reconnectAttempt in ${backoffMs}ms")
        scope.launch {
            delay(backoffMs)
            if (!closedByUser) openSocket()
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val wasReconnect = reconnectAttempt > 0
            reconnectAttempt = 0
            _connection.value = ConnectionState.Open
            DiagLog.i("ws", if (wasReconnect) "reconnected" else "connected")
            if (wasReconnect) onReconnected?.invoke()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Normalde bir kare = bir JSON nesnesi (dashboard da böyle ayrıştırıyor).
            // Satırlara bölmek, çok satırlı JSON'u parçalayıp veri düşürürdü; bu yüzden
            // önce bütünü dene, yalnız başarısız olursa satır-ayrılmış akışa düş.
            if (runCatching { json.parseToJsonElement(text) }.isSuccess) {
                handleFrame(text)
            } else {
                text.lineSequence().forEach { line ->
                    if (line.isNotBlank()) handleFrame(line)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connection.value = ConnectionState.Closed
            // Kapanış kodu tek teşhis ipucu: 1000 düzgün kapanış, 1006 ağ
            // kayboldu, 1011 sunucu hatası. Bekleyen çağrı sayısı da önemli —
            // sıfır değilse kullanıcı "Düşünüyor"da takılı kalıyor demektir.
            DiagLog.w("ws", "closed code=$code reason=${reason.ifBlank { "-" }}")
            failAllPending("bağlantı kapandı ($code)")
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connection.value = ConnectionState.Error(
                when (response?.code) {
                    401, 403 -> "Token reddedildi"
                    else -> t.message ?: "bağlantı hatası"
                }
            )
            DiagLog.e("ws", "failed http=${response?.code ?: "-"}", t)
            failAllPending(t.message ?: "bağlantı hatası")
            scheduleReconnect()
        }
    }
}

/** Bir JsonPrimitive'in içeriğini güvenle okur (JSON null → Kotlin null). */
private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

data class GatewayEvent(
    val type: String,
    val sessionId: String? = null,
    val payload: JsonObject? = null,
) {
    /** message.delta / thinking.delta gibi olaylarda akan metin parçası. */
    val text: String?
        get() = payload?.get("text")?.let { (it as? JsonPrimitive)?.content }

    /** tool.* olaylarında araç adı. */
    val toolName: String?
        get() = payload?.get("name")?.let { (it as? JsonPrimitive)?.content }
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Open : ConnectionState
    data object Closed : ConnectionState
    data class Error(val reason: String) : ConnectionState
}
