package com.hermes.mobile.assistant

import android.content.Context
import com.hermes.mobile.data.ConnectionState
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.GatewayWsClient
import com.hermes.mobile.data.LocalModelClient
import com.hermes.mobile.data.LocalModelLogic
import com.hermes.mobile.data.ServerProfile
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.data.SettingsStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Asistanın beyni — soruyu Hermes ajanına (ya da yerel modele) sorar, yanıtı
 * AKIŞ hâlinde verir.
 *
 * Hermes: tek bir "Jarvis" oturumu [JarvisLogic.SESSION_TTL_MS] boyunca
 * yeniden kullanılır — "peki yarın?" gibi devam soruları bağlamı bilir.
 * Oturum kimliği SharedPreferences'ta; uygulama sohbetinden ayrı.
 */
class JarvisBrain(private val context: Context, private val scope: CoroutineScope) {

    /** Akış olayları: metin parçası, araç kullanımı, onay isteği. */
    interface Sink {
        fun onDelta(text: String)
        fun onTool(name: String)
        fun onNeedsApproval(text: String)
    }

    private val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    private var gw: GatewayWsClient? = null
    private var gwProfileKey: String? = null
    @Volatile private var sessionId: String? = null
    @Volatile private var running: CompletableDeferred<String>? = null

    /** Son kullanılan asistan oturumu — "sohbette aç" için. */
    val lastSessionId: String? get() = sessionId ?: prefs.getString(KEY_SID, null)

    /**
     * Soruyu sorar, yanıt tamamlanınca TAM metni döner. Parçalar [sink]'e akar.
     * Hata: açıklayıcı mesajlı istisna (çağıran sesle söyler).
     */
    suspend fun ask(question: String, sink: Sink): String {
        val s = SettingsStore(context).settings.value
        return if (s.assistantBrain == "yerel") askLocal(question, s.localLlmUrl, s.localLlmModel, s.assistantAddress, sink)
        else askHermes(question, s.assistantAddress, sink)
    }

    private suspend fun askHermes(question: String, address: String, sink: Sink): String {
        val profile = ServerProfileStore(context).active()
            ?: throw IllegalStateException("Sunucu profili yok — uygulamada sunucu ekle")
        if (profile.token.isBlank()) throw IllegalStateException("Sunucu anahtarı (token) girilmemiş")
        val client = client(profile)
        val opened = withTimeoutOrNull(15_000) { client.connection.first { it is ConnectionState.Open } }
            ?: throw IllegalStateException("Sunucuya bağlanılamadı")
        check(opened is ConnectionState.Open)

        val now = System.currentTimeMillis()
        var sid = sessionId
        var fresh = false
        if (sid == null) {
            val saved = prefs.getString(KEY_SID, null)
            if (JarvisLogic.sessionReusable(prefs.getLong(KEY_AT, 0), now, saved)) {
                val alive = runCatching { client.activateSession(saved!!) }
                    .recoverCatching { client.resumeSession(saved!!) }.isSuccess
                if (alive) sid = saved
            }
        }
        if (sid == null) {
            sid = client.createSession(null)
            fresh = true
        }
        sessionId = sid
        prefs.edit().putString(KEY_SID, sid).putLong(KEY_AT, now).apply()

        val full = StringBuilder()
        val done = CompletableDeferred<String>()
        running = done
        // UNDISPATCHED: abonelik soru gönderilmeden ÖNCE kurulur (SharedFlow
        // geçmişi tutmaz; ilk parçalar kaçmasın).
        val collector = scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            client.events.collect { e ->
                if (e.sessionId != null && e.sessionId != sid) return@collect
                when (e.type) {
                    "message.delta" -> e.text?.let { full.append(it); sink.onDelta(it) }
                    "message.complete" -> {
                        if (full.isEmpty()) e.text?.let { full.append(it); sink.onDelta(it) }
                        done.complete(full.toString())
                    }
                    "tool.start" -> e.toolName?.let { sink.onTool(it) }
                    "approval.request", "sudo.request", "clarify.request", "secret.request" -> {
                        sink.onNeedsApproval(e.text.orEmpty())
                        done.complete(full.toString())
                    }
                    "error" -> done.completeExceptionally(IllegalStateException(e.text ?: "Ajan hatası"))
                }
            }
        }
        try {
            val text = if (fresh) JarvisLogic.voicePrefix(address) + question else question
            client.submitPrompt(sid, text)
            // Araç kullanan uzun işler olabilir: 3 dk tavan, sonra kibarca kes.
            return withTimeoutOrNull(180_000) { done.await() }
                ?: run {
                    runCatching { client.interrupt(sid) }
                    throw IllegalStateException("Yanıt çok uzun sürdü")
                }
        } finally {
            collector.cancel()
            running = null
        }
    }

    private suspend fun askLocal(q: String, url: String, model: String, address: String, sink: Sink): String {
        val llm = LocalModelClient(url.trim())
        if (!llm.isConfigured()) throw IllegalStateException("Yerel model adresi girilmemiş")
        val answer = llm.chat(
            text = q,
            model = model.ifBlank { LocalModelLogic.DEFAULT_MODEL },
            system = JarvisLogic.voicePrefix(address).trim(),
        )
        sink.onDelta(answer)
        return answer
    }

    /** Süren yanıtı keser (kullanıcı araya girdi). */
    fun interrupt() {
        val sid = sessionId ?: return
        val c = gw ?: return
        running?.complete("")
        scope.launch { runCatching { c.interrupt(sid) } }
    }

    /** Yeni konu: bir sonraki soru yeni oturumda. */
    fun forgetSession() {
        sessionId = null
        prefs.edit().remove(KEY_SID).remove(KEY_AT).apply()
    }

    private fun client(p: ServerProfile): GatewayWsClient {
        val key = "${p.id}|${p.token}|${p.normalizedUrl}|${p.normalizedRemote}"
        gw?.takeIf { gwProfileKey == key }?.let { it.connect(); return it }
        gw?.close()
        return GatewayWsClient(p).also {
            gw = it
            gwProfileKey = key
            it.connect()
            DiagLog.i("jarvis", "gateway bağlantısı açılıyor")
        }
    }

    fun release() {
        gw?.close()
        gw = null
    }

    private companion object {
        const val KEY_SID = "sid"
        const val KEY_AT = "at"
    }
}
