package com.hermes.mobile.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Ajanın telefona ulaşmasını sağlayan giden bağlantı.
 *
 * Bugüne kadar telefon eylemleri yalnız **kullanıcı** yazdığında ya da
 * konuştuğunda çalışıyordu; ajanın kendisi telefona hiç ulaşamıyordu. Bu,
 * "sabah 8'de bildirimlerimi oku" gibi bir cron işini imkânsız kılıyordu.
 *
 * Çözüm yönü tersine çevirmek: telefon sunucuya **dışarı doğru** kalıcı bir
 * WebSocket açıyor. Böylece NAT, CGNAT ya da mobil veri fark etmiyor; port
 * yönlendirmesi, VPN ya da USB gerekmiyor. Sunucu tarafı `relay/phone_bridge.py`.
 *
 * Güvenlik kararları:
 * - **Varsayılan kapalı.** Ajanın istenmediği anda telefonu kullanabilmesi,
 *   açıkça istenmesi gereken bir yetki.
 * - **Kararı telefon veriyor.** Köprü yalnız taşıyor; hangi aracın
 *   çalışacağına buradaki ayarlar karar veriyor. Sunucuda bir izin listesi
 *   tutmak, gerçekte uygulanmayan bir güvenlik hissi yaratırdı.
 * - **Salt okunur kip** var: ajan telefonu görebilsin ama değiştiremesin.
 * - **Geri alınamaz eylem yok.** Arama çeviriciyi açıyor, SMS taslak
 *   kalıyor — bu kısıtlar ajan çağırınca da geçerli.
 * - Her çağrı [DiagLog]'a yazılıyor; ajanın telefonda ne yaptığı sonradan
 *   görülebilir olmalı.
 */
class PhoneBridgeService : Service() {

    private var socket: WebSocket? = null
    private var closedByUser = false
    private var attempt = 0

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Sunucu 30 sn'de bir ping atıyor; istemci de atarak ölü bağlantıyı
        // erken yakalıyor. Uzun boşluklar bu bağlantının normal hali —
        // ajan günde bir kez de çağırabilir.
        .pingInterval(40, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private lateinit var settings: SettingsStore
    private lateinit var profiles: ServerProfileStore
    private lateinit var tools: PhoneTools

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        profiles = ServerProfileStore(this)
        tools = PhoneTools(this, ShizukuBridge())
        startForeground(NOTIF_ID, buildNotification())
    }

    /**
     * Kalici bir baglanti tutuyoruz, yani On Plan servisi zorunlu. Bildirim
     * IMPORTANCE_MIN: kullanicinin dikkatini cekmesi gerekmiyor ama ajanin
     * telefona erisiminin ACIK oldugu her zaman gorunur olmali -- gizli bir
     * uzaktan erisim kanali olmamali.
     */
    private fun buildNotification(): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                BRIDGE_CHANNEL, "Ajan erişimi", android.app.NotificationManager.IMPORTANCE_MIN,
            ).apply { setShowBadge(false) }
        )
        val open = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, com.hermes.mobile.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val readOnly = settings.settings.value.agentReadOnly
        return androidx.core.app.NotificationCompat.Builder(this, BRIDGE_CHANNEL)
            .setSmallIcon(com.hermes.mobile.R.drawable.ic_stat_hermes)
            .setContentTitle(com.hermes.mobile.ui.tr("Ajan telefona bağlı", "Agent connected to phone"))
            .setContentText(
                if (readOnly) com.hermes.mobile.ui.tr("yalnız okuma", "read-only")
                else com.hermes.mobile.ui.tr("okuma ve eylem", "read and act")
            )
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            closedByUser = true
            socket?.close(1000, "stopped")
            stopSelf()
            return START_NOT_STICKY
        }
        connect()
        // Süreç öldürülürse yeniden başlasın: kanalın açık kalması bu
        // özelliğin tamamı.
        return START_STICKY
    }

    private fun connect() {
        val p = profiles.active() ?: run {
            DiagLog.w("bridge", "no server profile, not connecting")
            stopSelf()
            return
        }
        if (p.token.isBlank()) {
            DiagLog.w("bridge", "no token, not connecting")
            stopSelf()
            return
        }
        val url = bridgeUrl(p) ?: run {
            DiagLog.e("bridge", "could not derive bridge url")
            stopSelf()
            return
        }
        DiagLog.i("bridge", "connecting to ${DiagLog.redact(url)}")
        socket = http.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    /**
     * Köprü adresi. Röle ile aynı mantık: ev ağında doğrudan port, dışarıda
     * Hermes'in ters vekilindeki yol — ayrı port yönlendirmesi gerekmiyor.
     */
    private fun bridgeUrl(p: ServerProfile): String? {
        val base = p.activeUrl ?: p.normalizedUrl
        val uri = runCatching { java.net.URI(base) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        val token = java.net.URLEncoder.encode(p.token, "UTF-8")
        return if (isPrivate(host)) {
            "ws://$host:$LAN_PORT/phone?token=$token"
        } else {
            val port = uri.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
            "wss://$host$port/phone-bridge/phone?token=$token"
        }
    }

    private fun isPrivate(host: String): Boolean {
        if (host.equals("localhost", true) || host.endsWith(".local", true)) return true
        val o = host.split(".").mapNotNull { it.toIntOrNull() }
        if (o.size != 4) return false
        return o[0] == 10 || o[0] == 127 ||
            (o[0] == 192 && o[1] == 168) || (o[0] == 172 && o[1] in 16..31)
    }

    /** Ajana tanıtılacak araçlar — kullanıcının o anki ayarına göre. */
    private fun advertised(): List<String> {
        val s = settings.settings.value
        if (!s.agentMayUsePhone) return emptyList()
        val read = PhoneTools.READ_TOOL_NAMES.toList()
        return if (s.agentReadOnly) read else (read + PhoneTools.AGENT_WRITE_TOOLS).distinct()
    }

    private inner class Listener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            attempt = 0
            val list = advertised()
            DiagLog.i("bridge", "connected, advertising ${list.size} tools")
            webSocket.send(
                buildJsonObject {
                    put("hello", buildJsonObject {
                        put("model", JsonPrimitive("${Build.MANUFACTURER} ${Build.MODEL}"))
                        put("android", JsonPrimitive(Build.VERSION.SDK_INT))
                        put("tools", buildJsonArray { list.forEach { add(JsonPrimitive(it)) } })
                    })
                }.toString()
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            val id = frame["id"]?.jsonPrimitive?.content ?: return
            val tool = frame["tool"]?.jsonPrimitive?.content.orEmpty()
            val args = frame["args"] as? JsonObject ?: JsonObject(emptyMap())

            val allowed = advertised()
            if (tool !in allowed) {
                // Reddi sebebiyle birlikte söylüyoruz: ajan "yapamadım" deyip
                // geçmek yerine kullanıcıya neyi açması gerektiğini söyleyebilsin.
                val why = when {
                    !settings.settings.value.agentMayUsePhone -> "agent access is off"
                    settings.settings.value.agentReadOnly -> "read-only mode is on"
                    else -> "tool not enabled"
                }
                DiagLog.w("bridge", "denied $tool ($why)")
                reply(webSocket, id, false, "denied: $why")
                return
            }

            DiagLog.i("bridge", "agent called $tool")
            val result = runCatching { tools.execute(tool, args) }
                .getOrElse { e ->
                    DiagLog.e("bridge", "$tool failed", e)
                    reply(webSocket, id, false, e.message ?: "failed")
                    return
                }
            reply(webSocket, id, true, result)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            DiagLog.w("bridge", "closed code=$code reason=${reason.ifBlank { "-" }}")
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            DiagLog.e("bridge", "failed http=${response?.code ?: "-"}", t)
            scheduleReconnect()
        }
    }

    private fun reply(ws: WebSocket, id: String, ok: Boolean, payload: String) {
        ws.send(
            buildJsonObject {
                put("id", JsonPrimitive(id))
                put("ok", JsonPrimitive(ok))
                if (ok) put("result", JsonPrimitive(payload))
                else put("error", JsonPrimitive(payload))
            }.toString()
        )
    }

    /**
     * Üssel geri çekilme, 5 dakikada sınırlı. Bu bağlantının saniyeler içinde
     * geri gelmesi gerekmiyor — ajan zaten "telefon bağlı değil" cevabı
     * alıyor — ama pili boşaltan bir yeniden bağlanma döngüsü de olmamalı.
     */
    private fun scheduleReconnect() {
        if (closedByUser) return
        attempt++
        val delayMs = minOf(300_000L, 2_000L * (1L shl minOf(attempt, 7)))
        DiagLog.d("bridge", "reconnect #$attempt in ${delayMs}ms")
        android.os.Handler(mainLooper).postDelayed({
            if (!closedByUser) connect()
        }, delayMs)
    }

    override fun onDestroy() {
        closedByUser = true
        socket?.close(1000, "service destroyed")
        socket = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 4813
        private const val LAN_PORT = 9180
        private const val BRIDGE_CHANNEL = "hermes-bridge"
        const val ACTION_STOP = "com.hermes.mobile.BRIDGE_STOP"

        fun start(context: Context) {
            val s = SettingsStore(context).settings.value
            if (!s.agentMayUsePhone) return
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context, Intent(context, PhoneBridgeService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, PhoneBridgeService::class.java).setAction(ACTION_STOP),
                )
            }
        }
    }
}
