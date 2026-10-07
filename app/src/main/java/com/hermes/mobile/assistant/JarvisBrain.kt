package com.hermes.mobile.assistant

import com.hermes.mobile.data.emaEnabled

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
 * EMA yapılandırıldığında uygulama sohbetinin kayıtlı oturumu kullanılır.
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
    private val localHistory = mutableListOf<Pair<String, String>>()
    private var localKey: String? = null

    /** Son kullanılan asistan oturumu — "sohbette aç" için. */
    val lastSessionId: String? get() = sessionId ?: prefs.getString(KEY_SID, null)

    /**
     * Soruyu sorar, yanıt tamamlanınca TAM metni döner. Parçalar [sink]'e akar.
     * Hata: açıklayıcı mesajlı istisna (çağıran sesle söyler).
     */
    suspend fun ask(question: String, sink: Sink, forceHermes: Boolean = false): String {
        val s = SettingsStore(context).settings.value
        return if (!forceHermes && s.assistantBrain == "yerel") askLocal(question, s.localLlmUrl, s.localLlmModel, s.assistantAddress, sink)
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
        val settings = SettingsStore(context)
        val unified = settings.settings.value.emaEnabled
        if (unified) {
            val saved = settings.settings.value.lastSession.takeIf { it.startsWith(profile.id + "|") }
                ?.substringAfter('|')?.takeIf { it.isNotBlank() }
            sid = if (saved != null) com.hermes.mobile.data.restoreConversation(
                sid?.takeIf { client.storedSessionId(it) == saved } ?: saved, saved,
                client::activateSession, client::resumeSession,
            ) else null
        } else {
        if (sid == null) {
            val saved = prefs.getString(KEY_SID, null)
            val savedProfile = prefs.getString(KEY_PROFILE, null)
            if ((savedProfile == null || savedProfile == profile.id) &&
                JarvisLogic.sessionReusable(prefs.getLong(KEY_AT, 0), now, saved)) {
                // Resume returns the live runtime ID, which can differ from the stored DB ID.
                // A failed restore must not silently create an empty conversation.
                sid = client.resumeSession(saved!!)
            }
        } else {
            sid = client.activateSession(sid)
        }
        }
        if (sid == null) {
            sid = client.createSession(null)
            fresh = true
        }
        if (fresh) {
            val s = settings.settings.value
            val pref = s.modelsByServer[profile.id] ?: s.lastModel.takeIf { s.modelsByServer.isEmpty() }.orEmpty()
            val parts = pref.split("|", limit = 2)
            if (parts.size == 2) {
                val result = client.slashExec(sid, "/model ${parts[1]} --provider ${parts[0]}")
                check(com.hermes.mobile.data.modelSwitchAccepted(result)) { "Kayıtlı model seçilemedi" }
            }
        }
        sessionId = sid
        if (unified) settings.update { it.copy(lastSession = "${profile.id}|${client.storedSessionId(sid)}") }
        prefs.edit().putString(KEY_SID, client.storedSessionId(sid)).putString(KEY_PROFILE, profile.id).putLong(KEY_AT, now).apply()

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
        val key = "${url.trim()}|$model"
        if (localKey != key) { localHistory.clear(); localKey = key }
        val answer = llm.chat(
            text = q,
            model = model.ifBlank { LocalModelLogic.DEFAULT_MODEL },
            system = JarvisLogic.voicePrefix(address).trim(),
            history = localHistory.toList(),
        )
        localHistory += "user" to q
        localHistory += "assistant" to answer
        while (localHistory.size > 20) { localHistory.removeAt(0); localHistory.removeAt(0) }
        sink.onDelta(answer)
        return answer
    }

    /** Süren yanıtı keser (kullanıcı araya girdi). */
    fun interrupt() {
        if (running == null) return
        val sid = sessionId ?: return
        val c = gw ?: return
        // P4: boş string döndürmek "yanıt bitti" sanılır; kesildiği istisnayla belli olsun.
        running?.completeExceptionally(IllegalStateException("Kesildi"))
        scope.launch { runCatching { c.interrupt(sid) } }
    }

    /** Yeni konu: bir sonraki soru yeni oturumda. */
    fun forgetSession() {
        sessionId = null
        localHistory.clear()
        val settings = SettingsStore(context)
        if (settings.settings.value.emaEnabled) settings.update { it.copy(lastSession = "") }
        prefs.edit().remove(KEY_SID).remove(KEY_AT).remove(KEY_PROFILE).apply()
    }

    /**
     * Sesli verilen Hermes slash komutu (`/branch`, `/steer`, `/undo`, `/new`).
     * Çağıran cihazda yapılamayan iş sunucuda değilse çıktı boş döner —
     * sesli katman "yapıldı" dese de doğrulanmamış olur; bu yüzden hata
     * fırlatmaz, metni döner.
     */
    suspend fun slash(command: String): String {
        val profile = ServerProfileStore(context).active()
            ?: return "Sunucu profili yok — uygulamada sunucu ekle"
        if (profile.token.isBlank()) return "Sunucu anahtarı (token) girilmemiş"
        val c = client(profile)
        val opened = withTimeoutOrNull(15_000) { c.connection.first { it is ConnectionState.Open } }
            ?: return "Sunucuya bağlanılamadı"
        check(opened is ConnectionState.Open)
        // P3: sessionId bellekte null kalmışsa prefs'ten TTL kontrolüyle geri yükle.
        var sid = sessionId
        if (sid == null) {
            val saved = prefs.getString(KEY_SID, null)
            if (JarvisLogic.sessionReusable(prefs.getLong(KEY_AT, 0), System.currentTimeMillis(), saved)) {
                sid = saved
                sessionId = saved
            }
        }
        sid ?: return "Açık oturum yok"
        return runCatching { c.slashExec(sid, command) }.getOrElse { it.message ?: "Hata" }
    }

    private fun client(p: ServerProfile): GatewayWsClient {
        val key = "${p.id}|${p.token}|${p.normalizedUrl}|${p.normalizedRemote}"
        gw?.takeIf { gwProfileKey == key }?.let { it.connect(); return it }
        gw?.close()
        // Session IDs are scoped to a server profile.
        if (gwProfileKey != null && gwProfileKey != key) sessionId = null
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
        const val KEY_PROFILE = "profile"
        const val KEY_SID = "sid"
        const val KEY_AT = "at"
    }
}
