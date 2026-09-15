package com.hermes.mobile.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Gemini Live — gerçek zamanlı sesli/görüntülü sohbet.
 *
 * Telefon doğrudan Google'a değil, **sunucudaki röleye** bağlanır. İki yol var
 * ve ikisi de `ServerProfile.effectiveRelayUrl` tarafından türetiliyor:
 * ev ağında `ws://192.168.1.10:9170/live?token=…` (doğrudan), dışarıdan
 * `wss://<alan-adi>/live-relay/live?token=…` (Hermes'in ters vekilinde bir
 * yol; ayrı port yönlendirmesi gerekmiyor). Google API anahtarı
 * sunucuda kalır; telefonda yalnızca zaten kayıtlı olan Hermes tokeni bulunur.
 * Kullanıcı isterse kendi anahtarını `apiKeyOverride` ile geçebilir.
 *
 * Ses biçimi (Live API sözleşmesi, canlı doğrulandı):
 *   giriş  → 16 kHz, mono, 16-bit little-endian PCM
 *   çıkış  → 24 kHz, mono, 16-bit PCM  (`audio/pcm;rate=24000`)
 *
 * Sözünü kesme (barge-in): model konuşurken kullanıcı konuşmaya başlarsa sunucu
 * `interrupted` gönderir; o an kuyruktaki ses atılır, yoksa asistan kesilmiş
 * cümlesini konuşmaya devam ederdi.
 */
class LiveVoiceClient(
    private val relayBase: String,
    private val hermesToken: String,
    private val apiKeyOverride: String? = null,
    /** Ses çıkışı yönlendirmesi; null ise sistem varsayılanı. */
    private val router: AudioRouter? = null,
    /** Röleye iletilecek ek ayarlar: model, ses karakteri, kişilik yönergesi. */
    private val liveModel: String = "",
    private val liveVoice: String = "",
    private val systemInstruction: String = "",
    /** Telefon araçlarını yürüten katman; null ise araçlar tanıtılmaz. */
    private val phoneTools: PhoneTools? = null,
    /** Shizuku hazır mı — derin araçların tanıtılıp tanıtılmayacağını belirler. */
    private val shizukuReady: Boolean = false,
) {
    enum class State { Idle, Connecting, Listening, Speaking, Error }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    // Tur-2 K3(a): yakalanmayan coroutine hatasi izsiz dusmesin.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler)

    private val http = OkHttpClient.Builder()
        .connectTimeout(SocketTuning.RELAY_CONNECT_SECONDS, TimeUnit.SECONDS)

        // Röle zaten 20 sn'de bir ping atıyor. İstemci de 20 sn'de bir atıp
        // 20 sn'de pong bekleyince, uzun bir `hermes_ask` sırasında (ölçtük:
        // 60 sn) pong ses kareleri arasında sıkışıyor ve bağlantı ölü sayılıyor
        // — "sent ping but didn't receive pong within 20000ms" hatası buydu.
        // 45 sn hem araç çağrısını aşıyor hem ölü bağlantıyı yakalamaya yetiyor.
        //
        // Tur-10 (F2): bu değer BİLEREK diğer kanallardan yüksek tutuldu
        // ([SocketTuning.RELAY_PING_SECONDS] = 45); ws (15) ve köprü (20)
        // kısaldı. Rölede ping penceresini kısaltmak, ölçülmüş tur-2 hatasını
        // (uzun araç çağrısı sırasında yanlış "koptu") geri getirirdi.
        .pingInterval(SocketTuning.RELAY_PING_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private var reconnectAttempt = 0
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var captureJob: Job? = null
    private var closedByUser = false

    private val _state = MutableStateFlow(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _userTranscript = MutableStateFlow("")
    /** Kullanıcının söylediğinin yazıya dökümü (sunucu `inputTranscription`). */
    val userTranscript: StateFlow<String> = _userTranscript.asStateFlow()

    private val _modelTranscript = MutableStateFlow("")
    /** Asistanın söylediğinin yazıya dökümü — altyazı olarak gösterilir. */
    val modelTranscript: StateFlow<String> = _modelTranscript.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _level = MutableStateFlow(0f)
    /** 0..1 mikrofon seviyesi — dalga animasyonu için. */
    val level: StateFlow<Float> = _level.asStateFlow()

    fun start() {
        if (_state.value != State.Idle && _state.value != State.Error) return
        closedByUser = false
        reconnectAttempt = 0
        _error.value = null
        _userTranscript.value = ""
        _modelTranscript.value = ""
        _state.value = State.Connecting
        // Kulak hoparlörü yerine hoparlöre yönlendir; kamerayı doğrultmuşken
        // kulağa dayamak mümkün değil.
        router?.begin()

        socket = http.newWebSocket(Request.Builder().url(buildUrl()).build(), Listener())
    }

    /**
     * Kayda giden adres. `DiagLog` zaten `token=` ayıklıyor ama buraya da
     * koyuyoruz: iki katman, birinin atlanması durumunda tokenin sızmaması için.
     */
    private fun redactedUrl(): String = DiagLog.redact(buildUrl())

    private fun buildUrl(): String {
        return buildString {
            append(relayBase.trimEnd('/'))
            append("/live?token=")
            append(hermesToken)
            apiKeyOverride?.takeIf { it.isNotBlank() }?.let {
                append("&api_key=").append(it)
            }
            // Ayarlardan gelenler; boşsa röle kendi varsayılanını kullanır.
            liveModel.takeIf { it.isNotBlank() }?.let {
                append("&model=").append(java.net.URLEncoder.encode(it, "UTF-8"))
            }
            liveVoice.takeIf { it.isNotBlank() }?.let {
                append("&voice=").append(java.net.URLEncoder.encode(it, "UTF-8"))
            }
            systemInstruction.takeIf { it.isNotBlank() }?.let {
                append("&system=").append(java.net.URLEncoder.encode(it, "UTF-8"))
            }
            // Telefon araçları yalnız bu uygulama bağlandığında tanıtılsın —
            // başka bir istemci onları yürütemez.
            if (phoneTools != null) {
                append("&phone_tools=1")
                // Shizuku hazırsa derin araçlar da tanıtılsın.
                if (shizukuReady) append("&shizuku=1")
            }
        }
    }

    fun stop() {
        closedByUser = true
        captureJob?.cancel()
        stopCapture()
        stopPlayback()
        socket?.close(1000, "user stopped")
        socket = null
        router?.end()
        _state.value = State.Idle
        _level.value = 0f
    }

    fun release() {
        stop()
        scope.cancel()
    }

    /**
     * En son gönderilen kamera karesi.
     *
     * Canlı akışta kareler `realtimeInput` ile gidiyor; ama kullanıcı **yazarak**
     * soru sorduğunda o soru `clientContent` ile ayrı bir tur açıyor ve realtime
     * arabelleğindeki görüntüyü görmüyor (testte model kareyi göremeyip
     * uydurdu). Bu yüzden son kare saklanıp yazılı soruya iliştiriliyor.
     */
    @Volatile
    private var lastFrame: String? = null

    /** Kamera karesi gönderir — model canlı görüntü üzerine konuşabilir. */
    fun sendVideoFrame(jpegBytes: ByteArray) {
        val encoded = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        lastFrame = encoded
        val ws = socket ?: return
        val frame = buildJsonObject {
            put("realtimeInput", buildJsonObject {
                put("mediaChunks", buildJsonArray {
                    add(buildJsonObject {
                        put("mimeType", JsonPrimitive("image/jpeg"))
                        put("data", JsonPrimitive(encoded))
                    })
                })
            })
        }
        ws.send(frame.toString())
    }

    /** Sesli oturuma metin de yazılabilir; kamera açıksa son kare iliştirilir. */
    fun sendText(text: String) {
        val ws = socket ?: return
        val snapshot = lastFrame
        val frame = buildJsonObject {
            put("clientContent", buildJsonObject {
                put("turns", buildJsonArray {
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("parts", buildJsonArray {
                            if (snapshot != null) {
                                add(buildJsonObject {
                                    put("inlineData", buildJsonObject {
                                        put("mimeType", JsonPrimitive("image/jpeg"))
                                        put("data", JsonPrimitive(snapshot))
                                    })
                                })
                            }
                            add(buildJsonObject { put("text", JsonPrimitive(text)) })
                        })
                    })
                })
                put("turnComplete", JsonPrimitive(true))
            })
        }
        ws.send(frame.toString())
    }

    fun clearLastFrame() {
        lastFrame = null
    }

    /** Telefon aracının sonucunu modele geri gönderir. */
    private fun sendToolResponse(id: String, name: String, result: String) {
        val ws = socket ?: return
        val frame = buildJsonObject {
            put("toolResponse", buildJsonObject {
                put("functionResponses", buildJsonArray {
                    add(buildJsonObject {
                        put("id", JsonPrimitive(id))
                        put("name", JsonPrimitive(name))
                        put("response", buildJsonObject {
                            put("result", JsonPrimitive(result))
                        })
                    })
                })
            })
        }
        ws.send(frame.toString())
    }

    // ── Mikrofon ──────────────────────────────────────────────────────

    private fun startCapture() {
        val minBuf = AudioRecord.getMinBufferSize(
            INPUT_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(CHUNK_BYTES * 2)

        val rec = try {
            @Suppress("MissingPermission")
            AudioRecord(
                // VOICE_COMMUNICATION donanım yankı/gürültü bastırmayı açar —
                // hoparlörden çıkan asistan sesinin mikrofona geri dönüp
                // kendi kendini tetiklemesini önler.
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf,
            )
        } catch (e: Exception) {
            _error.value = "Mikrofon açılamadı: ${e.message}"
            _state.value = State.Error
            return
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            _error.value = "Mikrofon izni yok ya da kullanımda"
            _state.value = State.Error
            rec.release()
            return
        }

        recorder = rec
        rec.startRecording()
        _state.value = State.Listening

        captureJob = scope.launch {
            val buf = ByteArray(CHUNK_BYTES)
            while (!closedByUser) {
                val read = rec.read(buf, 0, buf.size)
                if (read <= 0) continue
                val chunk = if (read == buf.size) buf else buf.copyOf(read)
                _level.value = rms(chunk)
                val ws = socket ?: break
                val frame = buildJsonObject {
                    put("realtimeInput", buildJsonObject {
                        put("audio", buildJsonObject {
                            put("mimeType", JsonPrimitive("audio/pcm;rate=$INPUT_RATE"))
                            put("data", JsonPrimitive(Base64.encodeToString(chunk, Base64.NO_WRAP)))
                        })
                    })
                }
                ws.send(frame.toString())
            }
        }
    }

    private fun stopCapture() {
        runCatching {
            recorder?.stop()
            recorder?.release()
        }
        recorder = null
    }

    /** Kaba ses seviyesi — dalga çubukları için yeterli. */
    private fun rms(pcm: ByteArray): Float {
        var sum = 0L
        var i = 0
        while (i + 1 < pcm.size) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort()
            sum += abs(s.toInt())
            i += 2
        }
        val avg = if (pcm.size >= 2) sum / (pcm.size / 2) else 0
        return (avg / 8000f).coerceIn(0f, 1f)
    }

    // ── Hoparlör ──────────────────────────────────────────────────────

    private fun ensurePlayer(): AudioTrack {
        player?.let { return it }
        val minBuf = AudioTrack.getMinBufferSize(
            OUTPUT_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OUTPUT_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        player = track
        return track
    }

    private fun stopPlayback() {
        runCatching {
            player?.pause()
            player?.flush()
            player?.release()
        }
        player = null
    }

    /** Barge-in: kuyruktaki asistan sesini at, kullanıcıya söz ver. */
    private fun flushPlayback() {
        runCatching {
            player?.pause()
            player?.flush()
            player?.play()
        }
    }

    // ── Sunucu çerçeveleri ────────────────────────────────────────────

    private fun handleFrame(text: String) {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return

        if (root.containsKey("setupComplete")) {
            startCapture()
            return
        }

        // Telefon araçları: röle bunları yürütmüyor, çerçeve bize kadar geliyor.
        root["toolCall"]?.jsonObject?.get("functionCalls")?.let { calls ->
            runCatching {
                calls.jsonArray.forEach { call ->
                    val obj = call.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull().orEmpty()
                    if (!PhoneTools.isPhoneTool(name)) return@forEach
                    val tools = phoneTools ?: return@forEach
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull().orEmpty()
                    val args = obj["args"]?.jsonObject ?: JsonObject(emptyMap())
                    val result = tools.execute(name, args)
                    sendToolResponse(id, name, result)
                }
            }
            return
        }

        val server = root["serverContent"]?.jsonObject ?: return

        // Model konuşurken kullanıcı araya girdi — biriken sesi at.
        if (server["interrupted"]?.jsonPrimitive?.booleanOrNull() == true) {
            flushPlayback()
            _state.value = State.Listening
            return
        }

        server["inputTranscription"]?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull()
            ?.let { chunk -> _userTranscript.update { it + chunk } }

        server["outputTranscription"]?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNull()
            ?.let { chunk -> _modelTranscript.update { it + chunk } }

        server["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
            val inline = part.jsonObject["inlineData"]?.jsonObject ?: return@forEach
            val mime = inline["mimeType"]?.jsonPrimitive?.contentOrNull().orEmpty()
            if (!mime.startsWith("audio/pcm")) return@forEach
            val data = inline["data"]?.jsonPrimitive?.contentOrNull() ?: return@forEach
            val pcm = runCatching { Base64.decode(data, Base64.DEFAULT) }.getOrNull() ?: return@forEach
            _state.value = State.Speaking
            runCatching { ensurePlayer().write(pcm, 0, pcm.size) }
        }

        if (server["turnComplete"]?.jsonPrimitive?.booleanOrNull() == true) {
            _state.value = State.Listening
            // Yeni tur için altyazıyı sıfırla, eskisi ekranda birikmesin.
            _userTranscript.value = ""
        }
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // setup'ı röle gönderiyor; setupComplete beklenir.
            DiagLog.i("live", "relay connected (http ${response.code})")
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handleFrame(text)

        override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) =
            handleFrame(bytes.utf8())

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            stopCapture()
            stopPlayback()
            if (closedByUser) {
                _state.value = State.Idle
                return
            }
            // Token/anahtar hataları yeniden denemekle düzelmez — kullanıcıya söyle.
            if (code in FATAL_CODES) {
                DiagLog.e("live", "fatal close code=$code reason=$reason")
                _error.value = describeClose(code, reason)
                _state.value = State.Error
                return
            }
            DiagLog.w("live", "closed code=$code reason=${reason.ifBlank { "-" }}")
            journal("closed code=$code reason=${reason.ifBlank { "-" }}")
            scheduleReconnect(describeClose(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            stopCapture()
            stopPlayback()
            if (closedByUser) return
            // Röle adresi yanlış türetildiğinde tek görünen belirti buydu.
            // Adres kaydı şart: sızıntı yok, buildUrl token'ı ayıklıyor.
            DiagLog.e("live", "failed url=${redactedUrl()} http=${response?.code ?: "-"}", t)
            journal(t.message ?: "Bağlantı hatası", response?.code)
            scheduleReconnect(t.message ?: "Bağlantı hatası")
        }

        /**
         * Tur-10 (F2): röle kopmasını da ortak deftere yaz — üç kanalın
         * (ws/köprü/röle) kopmaları aynı saniyede oluyorsa sorun ağ yolundadır,
         * tek kanalda oluyorsa o kanalın kendi sorunudur. Bu ayrım saha
         * logunda yapılamıyordu.
         */
        private fun journal(reason: String, httpCode: Int? = null) {
            val r = if (httpCode != null) "HTTP $httpCode $reason" else reason
            ConnectionJournal.record("relay", r, reason)
            DiagLog.w("conn", ConnectionJournal.summary("relay"))
        }
    }

    /**
     * Kopan bağlantıyı üssel geri çekilmeyle yeniden kurar.
     *
     * Röle yeniden başlatıldığında (sunucu güncellemesi gibi) ya da ağ kısa
     * süre koptuğunda oturumun kendiliğinden toparlanması gerekiyor; sürüşte
     * telefona uzanıp yeniden başlatmak mümkün değil.
     */
    private fun scheduleReconnect(reason: String) {
        if (closedByUser) return
        reconnectAttempt++
        if (reconnectAttempt > MAX_RECONNECT) {
            _error.value = "$reason — yeniden bağlanılamadı"
            _state.value = State.Error
            return
        }
        val delayMs = minOf(15_000L, 1_000L * (1L shl minOf(reconnectAttempt, 4)))
        _error.value = "$reason — " + com.hermes.mobile.ui.tr(
            "yeniden bağlanılıyor ($reconnectAttempt)",
            "reconnecting (attempt $reconnectAttempt)",
        )
        _state.value = State.Connecting
        scope.launch {
            kotlinx.coroutines.delay(delayMs)
            if (closedByUser) return@launch
            val url = buildUrl()
            socket = http.newWebSocket(Request.Builder().url(url).build(), Listener())
        }
    }

    private fun describeClose(code: Int, reason: String): String = when (code) {
        4401 -> com.hermes.mobile.ui.tr(
            "Token reddedildi — Sunucular ekranından tokeni kontrol edin",
            "Token rejected — check the token on the Servers screen",
        )
        4402 -> com.hermes.mobile.ui.tr(
            "Sunucuda Gemini API anahtarı yok",
            "No Gemini API key on the server",
        )
        4403 -> com.hermes.mobile.ui.tr(
            "Gemini anahtarı geçersiz ya da kotası dolu",
            "Gemini key invalid or out of quota",
        )
        4504 -> com.hermes.mobile.ui.tr(
            "Gemini yanıt vermedi (zaman aşımı)",
            "Gemini did not respond (timeout)",
        )
        else -> reason.ifBlank {
            com.hermes.mobile.ui.tr("Bağlantı kapandı ($code)", "Connection closed ($code)")
        }
    }

    companion object {
        /** Yeniden denemenin düzeltemeyeceği kapanış kodları. */
        private val FATAL_CODES = setOf(4401, 4402, 4403)
        private const val MAX_RECONNECT = 6

        const val INPUT_RATE = 16_000
        const val OUTPUT_RATE = 24_000

        /** ~64 ms'lik parça: gecikme ile çerçeve sayısı arasında denge. */
        private const val CHUNK_BYTES = 2048
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

private fun kotlinx.serialization.json.JsonPrimitive.booleanOrNull(): Boolean? =
    runCatching { boolean }.getOrNull()
