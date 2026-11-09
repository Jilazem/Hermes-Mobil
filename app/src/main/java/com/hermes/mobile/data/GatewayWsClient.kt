package com.hermes.mobile.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.flow.first
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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
 * Kimlik `?token=` sorgusunda taşınır (WS upgrade'inde sunucunun tek kabul
 * ettiği yol) ve ayrıca `Authorization` başlığında gönderilir (iş #1021).
 * Gated modda tek kullanımlık ticket gerekir (Faz 5).
 */
class GatewayWsClient(
    private val profile: ServerProfile,
    /** gateway.ready bekleme bütçesi; birim testte kısaltılır. */
    private val readyTimeoutMs: Long = GatewayReady.TIMEOUT_MS,
    /** v6.4 test dikişi: üretimde NULL → IO kapsamı. */
    private val scopeOverride: CoroutineScope? = null,
    /**
     * v6.4 test dikişi: üretimde NULL — gerçek OkHttp kullanılır. Birim
     * testleri sahte `WebSocketListener` takarak iptal edilmiş eski soketlerin
     * **gecikmeli** `onFailure`'ını senkron taklit eder (bkz. GatewayReconnectTest).
     */
    private val webSocketFactory: ((Request, WebSocketListener) -> WebSocket)? = null,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * v6.4 madde C: backoff (1→2→4→8→15 sn) yalnız bu süre kadar KARARLI
     * açık kalan soketten sonra sıfırlanır. `onOpen`'da sıfırlamak backoff'u
     * hiç devreye sokmuyordu (teşhis madde 4).
     */
    private val stableOpenResetMs = 30_000L
    // Tur-2 K3(a): SupervisorJob kardeşleri korusa da launch içinde yakalanmayan
    // hata buradan sessizce düşerdi (telefon 'sik sik kapaniyor' izsiz). Handler
    // izi DiagLog'a bırakır — scope ölmese de ne olduğunu görüyoruz.
    /** v6.4: test dikişi ile değiştirilebilir; üretimde boş bırakılır. */
    private val scope = scopeOverride
        ?: CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler)
    private val nextId = AtomicLong(0)
    private val pending = mutableMapOf<String, CompletableDeferred<JsonElement?>>()
    private val pendingLock = Any()
    private val sessionBindings = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val executionGenerations = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** Only server-issued generations may fence Stop; never guess or advance a rejected fence. */
    private fun rememberExecutionGeneration(value: JsonObject?, fallbackSessionId: String? = null) {
        val fields = value ?: return
        val generation = (fields["execution_generation"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?.takeIf { it >= 0 } ?: return
        val ref = fields["ref"] as? JsonObject
        val sid = (fields["session_id"] as? JsonPrimitive)?.content
            ?: (ref?.get("session_id") as? JsonPrimitive)?.content
            ?: fallbackSessionId
        if (sid.isNullOrBlank()) return
        // A delayed resume/submit reply must not overwrite a newer generation already received in an event.
        executionGenerations.merge(sid, generation) { previous, incoming -> maxOf(previous, incoming) }
    }

    fun storedSessionId(runtimeId: String): String = sessionBindings[runtimeId] ?: runtimeId

    private fun bindSession(result: JsonElement?, requestedId: String): String {
        val binding = SessionBinding.parse(result, requestedId)
        sessionBindings[binding.runtimeId] = binding.storedId
        // M1c: bağlandıktan sonra bu soket artık bu oturumun — yeniden bağlanma
        // `fresh=1` istememeli, aynı oturumu `resume=<stored>` ile istemeli.
        freshWanted.set(false)
        resumeWanted = binding.storedId.takeIf { it.isNotBlank() }
        return binding.runtimeId
    }

    /**
     * M1c: yeni sohbet için GERÇEK gateway oturumu. `session.create` çağrılır;
     * runtime (canlı) ve stored (veritabanı) kimlikler birlikte döner.
     * [closeOnDisconnect] true → soket kapanınca sunucu oturumu kapatır (test
     * düğmesi bunu kullanır, böylece geride boş oturum birikmez).
     */
    suspend fun createSessionBinding(
        profile: String? = null,
        title: String? = null,
        closeOnDisconnect: Boolean = false,
    ): SessionBinding {
        val params = buildJsonObject {
            profile?.takeIf { it.isNotBlank() }?.let { put("profile", JsonPrimitive(it)) }
            title?.takeIf { it.isNotBlank() }?.let { put("title", JsonPrimitive(it)) }
            if (closeOnDisconnect) put("close_on_disconnect", JsonPrimitive(true))
        }
        val result = request("session.create", params)
        val binding = SessionBinding.parse(result, "")
        sessionBindings[binding.runtimeId] = binding.storedId
        freshWanted.set(false)
        resumeWanted = binding.storedId.takeIf { it.isNotBlank() }
        return binding
    }

    suspend fun attachSession(runtimeId: String, storedId: String = storedSessionId(runtimeId), agentProfile: String? = null): String {
        return restoreConversation(runtimeId, storedId, ::activateSession) { durable ->
            // A new WebSocket has no runtime binding. Locate the existing live conversation across
            // profiles before asking the default profile's database to restore a durable key.
            val live = activeSessions().firstOrNull { it.dbId == durable || it.id == durable }
            if (live != null) activateSession(live.id) else resumeSession(durable, agentProfile)
        }
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(SocketTuning.GATEWAY_CONNECT_SECONDS, TimeUnit.SECONDS)
        // Aynı zamanda "pong bütçesi": OkHttp, pong pingInterval içinde gelmezse
        // bağlantıyı ölü sayar. Tur-10'da 20→15 sn: algılama gecikmesi ~5 sn
        // kısaldı, yine de normal tur süresinin ~10 katı (yanlış kopma üretmez).
        .pingInterval(SocketTuning.GATEWAY_PING_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private var closedByUser = false
    private val openLock = Any()

    /**
     * v6.4 (WS kendini iptal döngüsü onarımı): soket nesili. Her yeni
     * açılışta artar; eski neslin callback'leri — özellikle iptal edilmiş
     * soketin GECİKMELİ `onFailure`'ı — yok sayılır. Böylece eski soketin
     * hatası yeni soketi kapatıp kendini besleyen ~1 sn'lik döngüyü kuramaz.
     */
    private var socketGeneration = 0

    /** v6.4 madde D: tek yeniden bağlanma işi — çift plan döngüyü besler. */
    private var reconnectJob: Job? = null

    /** v6.4 madde C: 30 sn kararlı açık kalma eşiği işi. */
    private var stabilityJob: Job? = null

    /** v6.4 test gözlemi: kaç kez yeniden bağlanma planlandı. */
    @Volatile internal var reconnectScheduleCount = 0

    /** v6.4 test gözlemi: güncel deneme sayısı (onOpen sıfırlamamalı). */
    internal val reconnectAttempts: Int get() = reconnectAttempt

    /** v6.4 tanılama: son açılıştan bu yana geçen süre (lifetimeMs hesabı). */
    private val openedAtMs = java.util.concurrent.atomic.AtomicLong(0L)
    private var reconnectAttempt = 0

    /**
     * B3 (v6.3): uygulama düzeyi keepalive — 25 sn'de bir `ping` RPC'si.
     * **2 ardışık** yanıtsız ping soketi ölü sayar (tek kayıp toleranslı).
     * Taşıma düzeyinde OkHttp `pingInterval` (15 sn) ayrıca yedek: pong
     * gelmezse bağlantıyı kendisi düşürür.
     */
    private val keepalive = KeepalivePolicy()
    private var keepaliveJob: Job? = null

    /**
     * M1c — YENİ sohbet / DEVAM niyeti (`/api/ws?token=…&fresh=1`, `…&resume=<id>`).
     *
     * Canlı denetim (22.10.2026, yerel dashboard :9120): `/api/ws` bu iki sorgu
     * parametresini YOK SAYAR; `fresh`/`resume` yalnız `/api/pty` rotasında
     * anlamlıdır (hermes_cli/web_routers/chat_ws.py). Bu yüzden parametreler yine
     * de gönderilir (ileri sürüm/vekil için zararsız) ama GERÇEK oturum RPC ile
     * kurulur: `session.create` → runtime + stored kimlik, `session.resume` →
     * saklı kimliğe dönüş. "fresh=1 tek başına oturum açar" iddiası YOK; katman
     * ayrımı docs/G1-GATEWAY-MOBIL-API.md'de yazılı.
     */
    private val freshWanted = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var resumeWanted: String? = null

    /**
     * İş #1021 KÖK NEDEN 3: el sıkışmanın 101 dönmesi bağlantının HAZIR olduğu
     * anlamına gelmez. Gateway ilk çerçeve olarak `gateway.ready` olayı yazar
     * (tui_gateway/ws.py). Bu olay gelmeden `Open` demek, hazır olmayan sokete
     * `prompt.submit` göndermek — kullanıcı "Sunucu bağlı değil"/sessiz düşüş
     * görüyordu. Bu yüzden `Open` yalnız ready olayından sonra ilan edilir;
     * gelmezse somut hata ekrana yazılır (sessiz kalma yasak).
     */
    private val readySeen = java.util.concurrent.atomic.AtomicBoolean(false)
    private var readyTimer: Job? = null

    /** Son BAŞARILI açılışın zamanı — sarsıntı (flap) tespiti için. */
    private var lastOpenAt = 0L

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

    /**
     * M1c: [fresh] → YENİ gateway oturumu niyeti (`?fresh=1`), [resumeId] →
     * saklı kimliğe dönüş (`?resume=<id>`). Açık/bağlanmakta olan soket varsa ve
     * ikisi de boşsa eski davranış korunur (hiçbir şey yapılmaz); niyet varsa
     * soket bilerek yeniden açılır — bayat bağlantıya yeni oturum yazılmasın.
     */
    fun connect(fresh: Boolean = false, resumeId: String? = null) {
        freshWanted.set(fresh)
        resumeWanted = resumeId?.takeIf { it.isNotBlank() }
        if (!fresh && resumeWanted == null &&
            (_connection.value == ConnectionState.Open || _connection.value == ConnectionState.Connecting)
        ) return
        closedByUser = false
        // v6.4 madde E: deneme sayacını yalnız gerçekten YENİ açılış sıfırlar.
        // Çağrı başına sıfırlamak backoff'u (1→2→4→8→15) hiç devreye
        // sokmuyordu (teşhis madde 5).
        if (fresh) reconnectAttempt = 0
        openSocket()
    }

    /**
     * M1c: soketin `Open` (gateway.ready) ya da `Error` olmasını bekler.
     * Test düğmesi ve "yeni sohbet" akışı bu kapıdan geçer; süre aşımı `false`.
     */
    suspend fun awaitOpen(timeoutMs: Long = 15_000): Boolean =
        withTimeoutOrNull(timeoutMs) {
            val state = connection.first {
                it is ConnectionState.Open || it is ConnectionState.Error || it is ConnectionState.Closed
            }
            state is ConnectionState.Open
        } ?: false

    /**
     * `/api/ws` açılış isteği.
     *
     * İş #1021 KÖK NEDEN: B2'de kimlik yalnız `Authorization` başlığına
     * taşınmıştı; oysa dashboard sunucusu WS upgrade'inde tokeni YALNIZCA
     * `?token=` sorgusundan okur (hermes_cli/web_server_chat.py:
     * `_ws_auth_ok` → `query_params.get("token")`; başlık yok sayılır,
     * auth_middleware WS isteklerini olduğu gibi geçirir). Sonuç: el sıkışma
     * 403 ile reddediliyor, soket hiç `Open` olmuyor — Ayarlar HTTP ping'i
     * "çevrimiçi" derken sohbet "Sunucu bağlı değil" diyordu.
     *
     * Bu yüzden token HEM sorguda (sunucunun WS için tek kabul ettiği yol)
     * HEM de başlıkta gönderilir; başlık HTTP/REST kanalları ve ileride
     * sorguyu bırakacak sunucu sürümleri için korunur.
     */
    fun openRequest(): Request {
        // B3 (v6.3): adres kararı TEK yerde — ServerProfile.apiWsUrl
        // (EndpointNormalizer). OkHttp `HttpUrl` yalnız http/https kabul eder;
        // ws/wss → http/https çevrilir. `/api/ws` yolu burada EKLENMEZ, çünkü
        // normalizer zaten ekliyor (çift `/api/ws/api/ws` 404'ü kapandı).
        val httpUrl = profile.apiWsUrl
            .replaceFirst("wss://", "https://", ignoreCase = true)
            .replaceFirst("ws://", "http://", ignoreCase = true)
        val base = httpUrl.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("geçersiz ws adresi: ${profile.apiWsUrl}")
        val url = base.newBuilder()
            .addQueryParameter("token", profile.token)
            .apply {
                // M1c: yeni sohbet / devam niyeti. `/api/ws` bunları yok sayar
                // (gerçek bağ RPC'de) ama `/api/pty` ve ileri sürümler okur;
                // göndermek zararsız ve niyeti kayda geçirir.
                if (freshWanted.get()) addQueryParameter("fresh", "1")
                resumeWanted?.let { addQueryParameter("resume", it) }
            }
            .build()
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${profile.token}")
            .build()
    }

    private fun openSocket() {
        synchronized(openLock) {
            // v6.4: eski soketi geçersiz kıl — GECİKMELİ callback'leri (iptal
            // edilen soketin onFailure'ı) bu nesle ait OLAMAZ. Nesil artışı
            // iptalden ÖNCE: böylece iptal tetikli onFailure da stale kalır.
            val oldGen = socketGeneration
            socketGeneration++
            val gen = socketGeneration
            executionGenerations.clear()
            // v6.4 madde D: bekleyen tek yeniden bağlanma işi yenisini
            // beklemesin; çift plan döngüyü besliyordu.
            reconnectJob?.cancel()
            reconnectJob = null
            stabilityJob?.cancel()
            stabilityJob = null
            // P5: aynı anda iki açılış (connect + reconnect) eski soketi sızdırmasın;
            // eskisini iptal et, yenisini aç.
            if (socket != null) wsDiag("WS gen=$oldGen eski soket iptal ediliyor (lifetimeMs=${System.currentTimeMillis() - openedAtMs.get()})")
            socket?.cancel()
            _connection.value = ConnectionState.Connecting
            // Bozuk adres (kullanıcı 'httphttp://…' gibi girerse) eskiden
            // Request.Builder.url ile FATAL EXCEPTION üretiyordu (2026-09-14
            // emülatör denetimi kanıtı: httpws:// şeması). Artık çökme YOK —
            // görünen hata durumu.
            val request = runCatching { openRequest() }.getOrElse {
                DiagLog.w("ws", "gecersiz ws adresi: ${profile.wsBase}")
                _connection.value = ConnectionState.Error(
                    com.hermes.mobile.ui.tr(
                        "Sunucu adresi geçersiz — Sunucular ekranından düzeltin",
                        "Invalid server address — fix it on the Servers screen",
                    )
                )
                return
            }
            val listener = Listener(gen)
            socket = webSocketFactory?.invoke(request, listener)
                ?: http.newWebSocket(request, listener)
            wsDiag("WS gen=$gen açılıyor (attempt=$reconnectAttempt)")
        }
    }

    fun close() {
        closedByUser = true
        readySeen.set(false)
        readyTimer?.cancel()
        readyTimer = null
        stopKeepalive()
        // v6.4: kullanıcının kapanışı — tüm nesil callback'leri pasif kalır,
        // bekleyen yeniden bağlanma/stabilite işleri iptal edilir.
        socketGeneration++
        executionGenerations.clear()
        reconnectJob?.cancel()
        reconnectJob = null
        stabilityJob?.cancel()
        stabilityJob = null
        wsDiag("WS gen=${socketGeneration - 1} kullanıcı kapattı (lifetimeMs=${System.currentTimeMillis() - openedAtMs.get()})")
        socket?.cancel()
        socket = null
        _connection.value = ConnectionState.Closed
        // P1: scope.cancel() burada kalıcıydı — connect() geri çağrılsa bile
        // reconnect coroutine'leri ölü sokette kalırdı. Scope canlı kalsın;
        // bekleyen istekleri failAllPending ile bırak.
        failAllPending("bağlantı kapatıldı")
    }

    /** Bir JSON-RPC çağrısı yapar ve sonucu bekler. */
    suspend fun request(
        method: String,
        params: JsonObject = JsonObject(emptyMap()),
        timeoutMs: Long = SocketTuning.GATEWAY_WS_CALL_TIMEOUT_MS,
    ): JsonElement? {
        val sock = socket ?: throw IllegalStateException("gateway bağlı değil")
        val requestSocketGeneration = socketGeneration
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

        val result = withTimeoutOrNull(timeoutMs) { deferred.await() }
            ?: run {
                synchronized(pendingLock) { pending.remove(id) }
                throw IllegalStateException("$method — ${timeoutMs / 1000}s içinde yanıt yok")
            }
        if (requestSocketGeneration == socketGeneration) {
            rememberExecutionGeneration(result as? JsonObject, (params["session_id"] as? JsonPrimitive)?.content)
        }
        return result
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
        return bindSession(result, "")
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
     * V5-1B — görseli gateway'in KENDİ sözleşmesiyle (`image.attach_bytes`)
     * oturuma iliştirir.
     *
     * Eski yol `/api/files/upload-stream` + `/image uploads/x.png` idi: yükleme
     * göreli bir yol dönüyor, gateway ise görseli `HERMES_HOME/images/` altında
     * arıyor → `image not found` (canlı kanıt: 4016). Uzaktaki istemci için
     * doğru uç `image.attach_bytes`; yol çözümü sunucuda yapılır.
     */
    suspend fun attachImageBytes(
        sessionId: String,
        contentBase64: String,
        filename: String,
    ): String? {
        val result = request(
            "image.attach_bytes",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("content_base64", JsonPrimitive(contentBase64))
                put("filename", JsonPrimitive(filename))
            },
            timeoutMs = 60_000,
        )
        val obj = result?.jsonObject ?: return null
        if (obj["attached"]?.jsonPrimitive?.contentOrNullSafe() == "false") return null
        return obj["path"]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() }
    }

    /**
     * V5-1B — görsel olmayan dosyayı `file.attach` ile oturuma iliştirir.
     * Dönen `ref_text` (`@file:…`) modelin gerçekten açabildiği yoldur; eski
     * `Ek dosya: uploads/x.pdf` metni gateway'in dosya köküne göre çözülmüyordu.
     */
    suspend fun attachFileDataUrl(
        sessionId: String,
        dataUrl: String,
        name: String,
    ): FileAttachRef? {
        val result = request(
            "file.attach",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("data_url", JsonPrimitive(dataUrl))
                put("name", JsonPrimitive(name))
            },
            timeoutMs = 120_000,
        )
        val obj = result?.jsonObject ?: return null
        val path = obj["path"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
        val refText = obj["ref_text"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
        if (path.isBlank() && refText.isBlank()) return null
        return FileAttachRef(name = name, path = path, refText = refText.ifBlank { "@file:$path" })
    }

    /**
     * V5-1B — SAKLI (Mac'te açılmış, o an canlı olmayan) bir oturumu bütün
     * geçmişiyle çatallar (`session.branch_stored`). `session.branch` yalnız
     * süreç içi canlı oturumda çalışır; pasif Mac oturumunda "Çatalla"
     * düğmesi bu yüzden sessizce başarısız oluyordu.
     */
    suspend fun forkSession(
        sessionId: String,
        name: String? = null,
        idempotencyKey: String? = null,
    ): ForkedSession? {
        val result = request(
            "session.branch",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                if (!name.isNullOrBlank()) put("name", JsonPrimitive(name))
                if (idempotencyKey != null) put("idempotency_key", JsonPrimitive(idempotencyKey))
            },
        )
        return parseForkResponse(result)
    }

    companion object {
        fun parseForkResponse(result: JsonElement?): ForkedSession? {
            val obj = result?.jsonObject ?: return null
            val sid = obj["session_id"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
            return ForkedSession(
                sessionId = sid,
                storedSessionId = obj["stored_session_id"]?.jsonPrimitive?.contentOrNullSafe().orEmpty(),
                title = obj["title"]?.jsonPrimitive?.contentOrNullSafe().orEmpty(),
                parent = obj["parent"]?.jsonPrimitive?.contentOrNullSafe().orEmpty(),
                messageCount = (obj["message_count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
            )
        }
    }

    suspend fun forkStoredSession(
        parentStoredId: String,
        name: String? = null,
        idempotencyKey: String? = null,
    ): ForkedSession? {
        val result = request(
            "session.branch_stored",
            buildJsonObject {
                put("parent_session_id", JsonPrimitive(parentStoredId))
                if (!name.isNullOrBlank()) put("name", JsonPrimitive(name))
                if (idempotencyKey != null) put("idempotency_key", JsonPrimitive(idempotencyKey))
            },
            timeoutMs = 120_000,
        )
        return parseForkResponse(result)
    }

    /**
     * V5-1B — pasif oturuma DEVAM: saklı kimliği canlıya çevirip mesajı
     * gönderir. Çekmecedeki Mac oturumuna "ekle" derken `session.steer`
     * çağırmak `rejected` dönüyordu (steer yalnız süren turda anlamlı);
     * boştaki oturumda doğru yol resume + prompt.submit.
     */
    suspend fun continueStoredSession(
        storedSessionId: String,
        text: String,
        agentProfile: String? = null,
    ): String {
        val runtime = resumeSession(storedSessionId, agentProfile)
        submitPrompt(runtime, text)
        return runtime
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
        // Canonical authority requires the exact observed turn. Legacy gateways expose no generation
        // and retain the original payload. A stale_generation refusal is surfaced, never retried
        // against a newer turn which the user did not ask to stop.
        val generation = executionGenerations[sessionId]
        request(
            "session.interrupt",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                if (generation != null) put("execution_generation", JsonPrimitive(generation))
            },
            timeoutMs = 10_000,
        )
    }

    /** Geçmiş bir oturuma bağlanıp kaldığı yerden devam eder. */
    suspend fun resumeSession(sessionId: String): String = resumeSession(sessionId, null)

    suspend fun resumeSession(sessionId: String, agentProfile: String?): String {
        val result = request(
            "session.resume",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                agentProfile?.takeIf { it.isNotBlank() }?.let { put("profile", JsonPrimitive(it)) }
                put("omit_messages", JsonPrimitive(true))
            },
            timeoutMs = 60_000,
        )
        return bindSession(result, sessionId)
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
        ) ?: error("Canlı oturum listesi alınamadı")
        return runCatching {
            require(result.jsonObject["sessions"] is kotlinx.serialization.json.JsonArray) { "Canlı oturum listesi geçersiz" }
            Json { ignoreUnknownKeys = true }
                .decodeFromJsonElement(ActiveSessionsResponse.serializer(), result)
                .sessions
        }.onFailure {
            // FR-002: çözüm hatası BOŞ liste diye yutulmasın — iz bırak;
            // aksi halde "canlı oturum yok" ile "bozuk yanıt" ayırt edilemiyor.
            DiagLog.w("ws", "session.active_list cozulemedi: ${it.message}")
        }.getOrThrow()
    }

    /**
     * Oturumun mesaj geçmişi — **canlı oturumlar için tek doğru kaynak**.
     *
     * REST `/api/sessions/{id}/messages` yalnız veritabanına yazılmış mesajları
     * görür; canlı bir oturumun geçmişi ise gateway'in belleğinde durur ve
     * kapanana kadar DB'ye düşmeyebilir (361 mesajlık canlı oturum REST'te 0
     * dönüyordu). Bu RPC önce DB'yi dener, yoksa bellekteki geçmişi verir.
     */
    suspend fun subagentList(sessionId: String): JsonObject? = request(
        "subagent.list", buildJsonObject { put("session_id", JsonPrimitive(sessionId)) }, timeoutMs = 8_000,
    ) as? JsonObject

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
        }.onFailure {
            DiagLog.w("ws", "session.history cozulemedi (sid=$sessionId): ${it.message}")
        }.getOrDefault(emptyList())
    }

    /** Canlı bir oturuma bağlanır — öncekini kapatmaz. */
    suspend fun activateSession(sessionId: String): String {
        val result = request(
            "session.activate",
            buildJsonObject {
                put("session_id", JsonPrimitive(sessionId))
                put("omit_messages", JsonPrimitive(true))
            },
        )
        return bindSession(result, storedSessionId(sessionId))
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
            rememberExecutionGeneration(params)
            if (GatewayReady.isReady(type)) {
                // Bilgi karesi: yalnız "ready gördüm" işaretlenir. Bağlantı
                // 101'de zaten Open; burada durumu geri almıyoruz.
                readyTimer?.cancel()
                readyTimer = null
                readySeen.set(true)
                DiagLog.i("ws", "gateway.ready alındı (bilgi karesi)")
                return
            }
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

    /**
     * B3 (v6.3) keepalive döngüsü: 25 sn'de bir `ping` RPC'si.
     *
     * Yanıt gelmezse (8 sn bütçe) kayıp sayılır; **2 ardışık** kayıpta soket
     * kapatılır ve yeniden bağlanma başlar. JSON-RPC hata karesi de "yanıt"tır:
     * eski sunucu `ping` metodunu tanımasa bile çerçeve döndüğü için bağlantı
     * canlı sayılır (yanlış kopma üretmez).
     */
    private fun startKeepalive() {
        stopKeepalive()
        keepalive.reset()
        keepaliveJob = scope.launch {
            while (keepaliveJob?.isActive == true) {
                delay(keepalive.intervalMillis)
                if (_connection.value !is ConnectionState.Open) continue
                val answered = try {
                    request("ping", timeoutMs = KeepalivePolicy.PONG_TIMEOUT_MS)
                    true
                } catch (e: java.util.concurrent.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Zaman aşımı dışındaki her sonuç çerçeve aldığımızı gösterir.
                    !(e is IllegalStateException && e.message?.contains("yanıt yok") == true)
                }
                if (answered) {
                    keepalive.onAnswered()
                } else if (keepalive.onMissed()) {
                    DiagLog.w(
                        "ws",
                        "keepalive: ${keepalive.consecutiveMisses} ardışık ping yanıtsız → soket kapatılıyor",
                    )
                    // v6.4 madde G: güncel soketi geçersiz kıl (nesil artışı
                    // iptalden ÖNCE) + TEK scheduleReconnect. Eski kod yalnız
                    // cancel ediyordu; iptal tetikli onFailure da ayrıca
                    // scheduleReconnect çağırıyordu → çift plan döngüsü.
                    val oldGen = socketGeneration
                    socketGeneration++
                    stabilityJob?.cancel()
                    stabilityJob = null
                    if (socket != null) wsDiag("WS gen=$oldGen keepalive kaybı → yeniden bağlanma")
                    socket?.cancel()
                    socket = null
                    scheduleReconnect()
                    break
                }
            }
        }
    }

    private fun stopKeepalive() {
        keepaliveJob?.cancel()
        keepaliveJob = null
    }

    /**
     * v6.4 tanılama (madde H): her aç/kapa logcat'e de yazılır — kanıt için
     * `adb logcat -s HermesWs` yeterlidir. DiagLog'a da gider.
     */
    private fun wsDiag(msg: String) {
        DiagLog.d("ws", msg)
        runCatching { android.util.Log.d("HermesWs", msg) }
    }

    /**
     * B3 (v6.3): kesintide yeniden bağlanma — 1 → 2 → 4 → 8 → 15 sn, tavan 15 sn,
     * deneme sayısı sınırsız. Kullanıcıya "Yeniden bağlanıyor (n. deneme, 4 sn)"
     * durumu yayınlanır. (Eski ±%25 jitter kaldırıldı: istenen dizi kesin.)
     *
     * v6.4 madde D: TEK iş — aktifken yenisini açmaz. Eski kod her onFailure'da
     * yeni bir `scope.launch` açıyordu; çift plan → çift açılış → çift iptal →
     * yeni onFailure… kendini besleyen döngü (teşhis madde 3).
     */
    private fun scheduleReconnect() {
        if (closedByUser) return
        if (reconnectJob?.isActive == true) {
            wsDiag("WS reconnect zaten planlı → ikinci plan yok (attempt=$reconnectAttempt)")
            return
        }
        // v6.4: yalnız GÜNCEL nesilden planla. Job tetiklendiğinde nesil
        // değiştiyse (araya başka bir açılış girdiyse) açılış iptal edilir.
        val gen = socketGeneration
        reconnectAttempt++
        val backoffMs = WsBackoff.delayMs(reconnectAttempt)
        _connection.value = ConnectionState.Reconnecting(reconnectAttempt, backoffMs)
        wsDiag("WS reconnect #$reconnectAttempt in ${backoffMs}ms (gen=$gen)")
        reconnectScheduleCount++
        reconnectJob = scope.launch {
            delay(backoffMs)
            if (closedByUser) return@launch
            if (gen != socketGeneration) {
                wsDiag("WS reconnect iptal edildi (gen=$gen güncel=$socketGeneration)")
                return@launch
            }
            openSocket()
        }
    }

    /**
     * v6.4: her dinleyici kendi SOKET NESLİNİ taşır (`gen`). İptal edilmiş bir
     * soketin gecikmeli `onFailure`/`onClosed` callback'i güncel nesle ait
     * olmadığı için YOK SAYILIR — döngünün beslendiği yer tam burasıydı.
     */
    private inner class Listener(private val gen: Int) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // v6.4 madde A: eski neslin geç açılışı yeni durumu bozmasın.
            if (gen != socketGeneration) {
                wsDiag("WS gen=$gen eski open yok sayıldı (güncel=$socketGeneration)")
                webSocket.cancel()
                return
            }
            val wasReconnect = reconnectAttempt > 0
            // v6.4 madde C: burada reconnectAttempt SIFIRLANMAZ — sıfırlamak
            // backoff'u (1→2→4→8→15) hiç devreye sokmuyordu (teşhis madde 4).
            // Sıfırlama yalnız 30 sn kararlı açık kaldıktan sonra (stabilityJob).
            openedAtMs.set(System.currentTimeMillis())
            lastOpenAt = System.currentTimeMillis()
            readySeen.set(false)
            startKeepalive()
            // J1 KÖK NEDEN: 101 = taşıma hazır. `gateway.ready` yalnız bir BİLGİ
            // karesidir (skin/heartbeat). Dashboard /api/status taraması event
            // loop'u 14-16 sn GIL'e kilitleyebiliyor (bkz. web_server_gateway.py
            // _TOPOLOGY_CACHE notu) ve eski sunucular bu olayı hiç göndermiyor.
            // Bu yüzden 12 sn'lik katı kapı regresyondu: soket açıkken uygulama
            // Error'a düşüyor, sohbet/pano hiç açılmıyordu. Artık Open hemen.
            _connection.value = ConnectionState.Open
            wsDiag("WS gen=$gen open attempt=$reconnectAttempt")
            DiagLog.i("ws", "ws el sıkışması 101 · bağlantı açık (gateway.ready bilgi karesi)")
            if (wasReconnect) onReconnected?.invoke()
            readyTimer?.cancel()
            readyTimer = scope.launch {
                delay(readyTimeoutMs)
                if (gen != socketGeneration) return@launch
                if (!readySeen.get()) {
                    val msg = GatewayReady.missedMessage(readyTimeoutMs)
                    // Error DEĞİL: yalnız teşhis kaydı; bağlantı açık kalır.
                    DiagLog.w("ws", msg)
                    ConnectionJournal.record("ws", "ready yok: $msg", msg)
                }
            }
            // v6.4 madde C: 30 sn kararlı açık kalırsa deneme sayacını sıfırla —
            // backoff yalnız burada sıfırlanır.
            stabilityJob?.cancel()
            stabilityJob = scope.launch {
                delay(stableOpenResetMs)
                if (gen == socketGeneration && _connection.value == ConnectionState.Open) {
                    reconnectAttempt = 0
                    wsDiag("WS gen=$gen ${stableOpenResetMs / 1000} sn kararlı → backoff sıfırlandı")
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // v6.4 madde A: eski neslin kareleri yok sayılır.
            if (gen != socketGeneration) return
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
            if (gen != socketGeneration) return
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // v6.4 madde F: eski neslin gecikmeli kapanışı ne durum yazar ne
            // reconnect planlar — döngünün diğer besleme kolu.
            if (gen != socketGeneration) {
                wsDiag("WS gen=$gen eski close yok sayıldı (güncel=$socketGeneration)")
                return
            }
            wsDiag("WS gen=$gen close code=$code lifetimeMs=${System.currentTimeMillis() - openedAtMs.get()}")
            readySeen.set(false)
            readyTimer?.cancel()
            readyTimer = null
            _connection.value = ConnectionState.Closed
            // Kapanış kodu tek teşhis ipucu: 1000 düzgün kapanış, 1006 ağ
            // kayboldu, 1011 sunucu hatası. Bekleyen çağrı sayısı da önemli —
            // sıfır değilse kullanıcı "Düşünüyor"da takılı kalıyor demektir.
            DiagLog.w("ws", "closed code=$code reason=${reason.ifBlank { "-" }}")
            journal("closed code=$code reason=${reason.ifBlank { "-" }}")
            // B3 İSTENEN-5: kapanış kodunun Türkçesi kullanıcıya görünür
            // ("Ağ koptu (1006)" gibi); ham kod yalnız teşhis kaydında kalır.
            failAllPending(WsCloseCodes.describe(code))
            if (WsCloseCodes.isFatalForRetry(code)) {
                // 4401/1008: aynı token'la yeniden denemek boşuna — kullanıcı
                // Ayarlar'dan tokenı düzeltmeli (bkz. WsCloseCodes.isFatalForRetry).
                _connection.value = ConnectionState.Error(WsCloseCodes.describe(code))
            } else {
                _connection.value = ConnectionState.Closed
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // v6.4 madde F + teşhis madde 2: İPTAL EDİLMİŞ soketin GECİKMELİ
            // onFailure'ı buraya düşüyordu ve reconnect tetikliyordu. Artık
            // nesil uyuşmuyorsa ne durum yazar ne reconnect planlar.
            if (gen != socketGeneration) {
                wsDiag("WS gen=$gen eski failure yok sayıldı (${t.javaClass.simpleName}, güncel=$socketGeneration)")
                return
            }
            wsDiag("WS gen=$gen failure lifetimeMs=${System.currentTimeMillis() - openedAtMs.get()} t=${t.javaClass.simpleName}")
            // FR-002: HTTP yükseltme hatası (ör. 502) "bağlantı hatası" diye
            // yutulmaz — gateway'e ulaşılıyor ama gateway/proxy hata veriyor;
            // kullanıcı ve DiagLog gerçeği (HTTP kodunu) görür.
            val reason = when (response?.code) {
                401, 403 -> "Token reddedildi (${response.code})"
                null -> ConnectionErrors.describe(t).let { base ->
                    // B3 İSTENEN-4: zaman aşımında hedef adres şema+port ile görünür.
                    if (ConnectionErrors.code(t) == "TIMEOUT") {
                        "$base — hedef: ${profile.endpoint?.displayWs() ?: profile.wsBase}"
                    } else {
                        base
                    }
                }
                else -> "Sunucu WS el sıkışmasını reddetti (HTTP ${response.code}) — " +
                    (t.message?.take(120) ?: "gateway geçici olarak meşgul olabilir")
            }
            readySeen.set(false)
            readyTimer?.cancel()
            readyTimer = null
            _connection.value = ConnectionState.Error(reason)
            DiagLog.e("ws", "failed http=${response?.code ?: "-"} kod=${ConnectionErrors.code(t)}", t)
            journal(reason, response?.code)
            failAllPending(reason)
            if (response?.code == 401 || response?.code == 403) {
                // Kimlik reddi (yanlış token): aynı bilgiyle yeniden denemek
                // boşuna — 4401/1008 kapanış kodlarıyla aynı sınıf (bkz.
                // WsCloseCodes.isFatalForRetry). Durum Error olarak kalır,
                // kullanıcı Ayarlar'dan tokenı düzeltir.
                DiagLog.w("ws", "kimlik reddi http=${response.code}; otomatik yeniden deneme yok")
            } else {
                scheduleReconnect()
            }
        }

        /**
         * Tur-10 (F2): kopmayı sınıflandırıp **son kopma nedenleri özetini**
         * tanı kaydına yazar. Dağınık `closed/failed` satırları yerine tek
         * bakışta "ne sıklıkta, hangi kanal, hangi tür" görünür.
         */
        private fun journal(reason: String, httpCode: Int? = null) {
            val r = if (httpCode != null) "HTTP $httpCode $reason" else reason
            ConnectionJournal.record("ws", r, reason)
            DiagLog.w("conn", ConnectionJournal.summary("ws"))
        }
    }
}

/** Bir JsonPrimitive'in içeriğini güvenle okur (JSON null → Kotlin null). */
private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

/**
 * gateway.ready el sıkışması sözleşmesi (saf mantık — birim test edilir).
 *
 * Sunucu (`tui_gateway/ws.py`) WS kabulünden hemen sonra tek bir olay yazar:
 *   {"jsonrpc":"2.0","method":"event",
 *    "params":{"type":"gateway.ready","payload":{"skin":…,"heartbeat":true,…}}}
 * Bu olay gelmeden bağlantı "hazır" değildir; istek göndermek sessiz düşüş
 * üretir. [GatewayWsClient] bu yüzden `Open`'ı yalnız bu olaydan sonra ilan eder.
 */
object GatewayReady {
    /** Normalde ilk karede gelir; 12 sn cömert bir üst sınır. */
    const val TIMEOUT_MS = 12_000L
    const val EVENT_TYPE = "gateway.ready"

    fun isReady(type: String?): Boolean = type == EVENT_TYPE

    /**
     * J1: bu olay gelmese de bağlantı AÇIK kalır — mesaj artık hata değil,
     * teşhis uyarısıdır (eski sunucu ya da ağır yükte geciken dashboard).
     */
    fun missedMessage(timeoutMs: Long = TIMEOUT_MS): String {
        val sn = (timeoutMs / 1000).coerceAtLeast(1)
        return com.hermes.mobile.ui.tr(
            "Bağlantı açık; ${sn} sn içinde gateway.ready bilgi karesi gelmedi " +
                "(eski Hermes sürümü olabilir) — sohbet çalışmaya devam eder.",
            "Connection is open; the gateway.ready info frame did not arrive within ${sn}s " +
                "(possibly an older Hermes build) — chat keeps working.",
        )
    }
}

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

    val toolDetail: String?
        get() = listOf("text", "arguments", "args", "output", "result", "error")
            .mapNotNull { key -> payload?.get(key)?.takeUnless { it is kotlinx.serialization.json.JsonNull }
                ?.let { if (it is JsonPrimitive) it.content else it.toString() } }
            .filter { it.isNotBlank() }.distinct().joinToString("\n\n").ifBlank { null }
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Open : ConnectionState
    data object Closed : ConnectionState

    /**
     * B3 (v6.3) — yeniden bağlanma bekleniyor. [attempt] 1'den başlar,
     * [backoffMs] bu denemeden önceki beklemedir (bkz. [WsBackoff]).
     */
    data class Reconnecting(val attempt: Int, val backoffMs: Long) : ConnectionState

    data class Error(val reason: String) : ConnectionState
}
