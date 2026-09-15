package com.hermes.mobile.data

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 *
 * ## Devralma (tur-7)
 *
 * Sunucudaki yuva tek: aynı anda tek cihaz bağlı kalabiliyor. İkinci cihaz
 * bağlandığında sunucu eskisine `taken_over` karesi + **4001** close kodu
 * gönderiyor. Telefon bunu görünce **kendiliğinden yeniden bağlanmayı
 * bırakıyor** (durum [BridgeState.TAKEN_OVER]); yoksa iki cihaz birbirini
 * sonsuza dek devirir — 2026-09-15'te canlıda 21 dakikada 716 devir ölçüldü.
 * Devralma, "ağ hatası"ndan farklıdır: hatada artan geri çekilmeyle yeniden
 * denenir, devralmada kullanıcı isteyene kadar denenmez.
 */
class PhoneBridgeService : Service() {

    private var socket: WebSocket? = null
    /** Bağlı canlı soketin kim olduğu. Ölü/zombi soketlerin yeniden bağlanma
     *  savaşını önlemek için her kapanışta bu alanla karşılaştırılıyor. */
    private var live = false
    private var closedByUser = false
    private var attempt = 0
    /** Devralma sinyali görüldü: kullanıcı istemeden yeniden bağlanılmaz. */
    private var takenOver = false
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(SocketTuning.BRIDGE_CONNECT_SECONDS, TimeUnit.SECONDS)
        // Sunucu 30 sn'de bir ping atıyor; istemci de atarak ölü bağlantıyı
        // erken yakalıyor. Uzun boşluklar bu bağlantının normal hali —
        // ajan günde bir kez de çağırabilir.
        //
        // Tur-10 (F2): 40→20 sn. 40 sn bekleyen köprü, gerçekte ölü olduğu hâlde
        // "bağlı" görünüyordu (saha logunda 19 pong zaman aşımı, 40 sn'lik
        // pencere). Devralma koruması bu pencereye bağlı değil: ayrım
        // metin karesi + close 4001 ile yapılıyor ([BridgePolicy]).
        .pingInterval(SocketTuning.BRIDGE_PING_SECONDS, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** Son BAŞARILI bağlanma zamanı — sarsıntı tespiti için. */
    private var lastConnectedAt = 0L

    private lateinit var settings: SettingsStore
    private lateinit var profiles: ServerProfileStore
    private lateinit var tools: PhoneTools
    /** Tam kontrol katmanı — erişilebilirlik servisine dayanan komutlar. */
    private lateinit var fullTools: FullControlTools

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        profiles = ServerProfileStore(this)
        tools = PhoneTools(this, ShizukuBridge())
        fullTools = FullControlTools(this)
        state = BridgeState.OFF
        startForeground(NOTIF_ID, buildNotification())
    }

    /**
     * Kalici bir baglanti tutuyoruz, yani On Plan servisi zorunlu. Bildirim
     * IMPORTANCE_MIN: kullanicinin dikkatini cekmesi gerekmiyor ama ajanin
     * telefona erisiminin ACIK oldugu her zaman gorunur olmali -- gizli bir
     * uzaktan erisim kanali olmamali.
     *
     * Durum değişince bildirim de değişiyor: "devralındı" hâlinde kullanıcı
     * bunu kalıcı bildirimden görmeli, uygulamayı açması gerekmemeli.
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
        // Devralındı hâlinde bildirime dokunmak TEK SEFERLİK bağlanma başlatır:
        // kullanıcı "geri al" demek için Ayarlar'ı aramak zorunda kalmamalı.
        val tap = if (state == BridgeState.TAKEN_OVER) {
            android.app.PendingIntent.getService(
                this, 1,
                Intent(this, PhoneBridgeService::class.java).setAction(ACTION_RECONNECT),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            open
        }
        val readOnly = settings.settings.value.agentReadOnly
        val full = settings.settings.value.fullControl
        val (title, text) = when (state) {
            BridgeState.TAKEN_OVER -> com.hermes.mobile.ui.tr(
                "Köprü devralındı",
                "Bridge taken over",
            ) to com.hermes.mobile.ui.tr(
                "Köprü başka bir cihaz tarafından devralındı — yeniden bağlanmak için dokun",
                "The bridge was taken over by another device — tap to reconnect",
            )
            BridgeState.RETRYING -> com.hermes.mobile.ui.tr(
                "Köprü yeniden bağlanıyor",
                "Bridge reconnecting",
            ) to com.hermes.mobile.ui.tr(
                "bağlantı koptu, artan aralıkla denenecek",
                "connection lost, retrying with growing backoff",
            )
            BridgeState.CONNECTING -> com.hermes.mobile.ui.tr(
                "Köprü bağlanıyor",
                "Bridge connecting",
            ) to com.hermes.mobile.ui.tr("bağlanma denemesi", "connect attempt")
            BridgeState.CONNECTED -> com.hermes.mobile.ui.tr(
                "Ajan telefona bağlı",
                "Agent connected to phone",
            ) to when {
                full -> com.hermes.mobile.ui.tr("tam kontrol", "full control")
                readOnly -> com.hermes.mobile.ui.tr("yalnız okuma", "read-only")
                else -> com.hermes.mobile.ui.tr("okuma ve eylem", "read and act")
            }
            BridgeState.OFF -> com.hermes.mobile.ui.tr(
                "Ajan telefona bağlı",
                "Agent connected to phone",
            ) to when {
                full -> com.hermes.mobile.ui.tr("tam kontrol", "full control")
                readOnly -> com.hermes.mobile.ui.tr("yalnız okuma", "read-only")
                else -> com.hermes.mobile.ui.tr("okuma ve eylem", "read and act")
            }
        }
        return androidx.core.app.NotificationCompat.Builder(this, BRIDGE_CHANNEL)
            .setSmallIcon(com.hermes.mobile.R.drawable.ic_stat_hermes)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(tap)
            .build()
    }

    /** Durumu yayınla ve kalıcı bildirimi tazele. */
    private fun setState(next: BridgeState) {
        if (state == next) return
        state = next
        runCatching {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NOTIF_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            closedByUser = true
            live = false
            cancelPending()
            socket?.close(1000, "stopped")
            socket = null
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RECONNECT) {
            // Tek seferlik bağlanma: devralma kilidini burada açıyoruz.
            DiagLog.i("bridge", "kullanici yeniden baglan istedi")
            takenOver = false
            attempt = 0
            cancelPending()
            socket?.cancel()
            socket = null
            live = false
            connect()
            return START_STICKY
        }
        // Idempotent olmalı: MainActivity her recomposition'da start() çağırıyor
        // (ayar state'i her değiştiğinde). Koşulsuz connect() ikinci bir WebSocket
        // açar, sunucu tek bağlantı politikasıyla eskisini düşürür, ölü soketin
        // onClosed'u da ayrı bir yeniden bağlanma döngüsü başlatırdı — telefon
        // 2026-09-08'de 30 dakikada 533 kez böyle kendini yeniden bağladı.
        //
        // DEVRAIMA kuralı (tur-7): devralındıysa burada da bağlanılmaz — yoksa
        // MainActivity'nin her start()'ı kilitli durumu delerdi.
        if (!live && socket == null && !takenOver) connect()
        // Süreç öldürülürse yeniden başlasın: kanalın açık kalması bu
        // özelliğin tamamı.
        return START_STICKY
    }

    private fun connect() {
        val p = profiles.active() ?: run {
            DiagLog.w("bridge", "no server profile, not connecting")
            setState(BridgeState.OFF)
            stopSelf()
            return
        }
        if (p.token.isBlank()) {
            DiagLog.w("bridge", "no token, not connecting")
            setState(BridgeState.OFF)
            stopSelf()
            return
        }
        val url = bridgeUrl(p) ?: run {
            DiagLog.e("bridge", "could not derive bridge url")
            setState(BridgeState.OFF)
            stopSelf()
            return
        }
        DiagLog.i("bridge", "connecting to ${DiagLog.redact(url)}")
        setState(BridgeState.CONNECTING)
        live = false
        socket = http.newWebSocket(Request.Builder().url(url).build(), Listener())
    }

    /**
     * Köprü adresi. Röle ile aynı mantık: ev ağında doğrudan port, dışarıda
     * Hermes'in ters vekilindeki yol — ayrı port yönlendirmesi gerekmiyor.
     * Açık `bridgeUrl` verilmişse port türetmesi yapılmaz (sandbox/özel kurulum).
     */
    private fun bridgeUrl(p: ServerProfile): String? {
        val base = p.effectiveBridgeUrl
        if (base.isBlank()) return null
        val sep = if (base.contains("?")) "&" else "?"
        val token = java.net.URLEncoder.encode(p.token, "UTF-8")
        return "$base$sep" + "token=$token"
    }

    /**
     * Ajana tanıtılacak araçlar — kullanıcının o anki ayarına göre.
     *
     * Tam kontrol araçları yalnız iki anahtar da açıkken duyuruluyor. Kapalı
     * bir aracı duyurmak, modelin onu denemesine ve "yapamıyorum" demek
     * zorunda kalmasına yol açardı.
     */
    private fun advertised(): List<String> {
        val s = settings.settings.value
        if (!s.agentMayUsePhone) return emptyList()
        val read = PhoneTools.READ_TOOL_NAMES.toList()
        val base = if (s.agentReadOnly) read else (read + PhoneTools.AGENT_WRITE_TOOLS).distinct()
        return (base + FullControl.advertise(s.agentMayUsePhone, s.fullControl)).distinct()
    }

    private inner class Listener : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            // Yarışan bir connect() bu soketi bizden sonra açtıysa bu "zombi"dir:
            // hello göndermek sunucuda ikinci bağlantı savaşı başlatır. Sessizce
            // kapat, sahibi olan soket yoluna devam etsin.
            if (webSocket !== socket) {
                DiagLog.d("bridge", "superseded socket opened, closing quietly")
                webSocket.cancel()
                return
            }
            // Bağlanma başarılı: devralma kilidi ve geri çekilme merdiveni sıfır.
            takenOver = false
            live = true
            attempt = 0
            lastConnectedAt = System.currentTimeMillis()
            cancelPending()
            val list = advertised()
            DiagLog.i("bridge", "connected, advertising ${list.size} tools")
            setState(BridgeState.CONNECTED)
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
            // Devralma bildirimi bir "çağrı" değil: id alanı yok, yanıtlanmaz.
            // Önce bu kontrol edilmeli, yoksa aşağıdaki id ayrıştırmasında
            // sessizce düşer ve istemci haberi hiç almaz.
            if (BridgePolicy.isTakeoverFrame(text)) {
                DiagLog.w("bridge", "sunucu devralma bildirdi (taken_over karesi)")
                enterTakenOver()
                return
            }
            val frame = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
            val id = frame["id"]?.jsonPrimitive?.content ?: return
            val tool = frame["tool"]?.jsonPrimitive?.content.orEmpty()
            val args = frame["args"] as? JsonObject ?: JsonObject(emptyMap())

            val allowed = advertised()
            val s = settings.settings.value
            if (tool !in allowed) {
                // Reddi sebebiyle birlikte söylüyoruz: ajan "yapamadım" deyip
                // geçmek yerine kullanıcıya neyi açması gerektiğini söyleyebilsin.
                // Tam kontrol araçlarında sebep (kanal kapalı / tam kontrol
                // kapalı / salt-okunur) FullControl'de tek yerde hesaplanıyor.
                val why = if (FullControl.isFullControlTool(tool)) {
                    FullControl.guardReason(
                        mayUsePhone = s.agentMayUsePhone,
                        readOnly = s.agentReadOnly,
                        fullControl = s.fullControl,
                        tool = tool,
                    ) ?: "tool not enabled"
                } else {
                    when {
                        !s.agentMayUsePhone -> FullControl.WHY_AGENT_OFF
                        s.agentReadOnly -> FullControl.WHY_READ_ONLY
                        else -> "tool not enabled"
                    }
                }
                DiagLog.w("bridge", "denied $tool ($why)")
                reply(webSocket, id, false, "denied: $why")
                return
            }

            DiagLog.i("bridge", "agent called $tool")
            if (fullTools.handles(tool)) {
                // Ekran görüntüsü base64'ü log'a YAZILMAZ: kanıt değeri yok,
                // günlüğü megabaytlarca şişirir.
                DiagLog.i("a11y", "eylem $tool ${briefArgs(tool, args)}")
                val out = fullTools.execute(tool, args)
                DiagLog.i("a11y", "$tool → ${if (out.ok) "tamam" else "hata"}: ${out.text.take(200)}")
                reply(webSocket, id, out.ok, out.text)
                return
            }
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
            journal("closed code=$code reason=${reason.ifBlank { "-" }}")
            // Yalnızca CANLI soket kapanırsa yeniden bağlan: sunucunun
            // "replaced" ile düşürdüğü eski soketin kapanışı yeni bir döngü
            // başlatırsa iki zombi sonsuza dek savaşıyor (2026-09-08
            // fırtınasının kök nedeni).
            if (webSocket !== socket) return
            live = false
            socket = null
            when (BridgePolicy.decision(code, takenOver, closedByUser)) {
                BridgePolicy.Decision.TAKEN_OVER -> {
                    DiagLog.i("bridge", "devralindi (close kodu $code) - yeniden baglanma DURDURULDU")
                    enterTakenOver()
                }
                BridgePolicy.Decision.STOP -> Unit
                BridgePolicy.Decision.RETRY -> {
                    setState(BridgeState.RETRYING)
                    scheduleReconnect()
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            DiagLog.e("bridge", "failed http=${response?.code ?: "-"}", t)
            journal(t.message ?: "bağlantı hatası", response?.code)
            if (webSocket !== socket) return
            live = false
            socket = null
            // Devralma sonrası ortaya çıkan ağ hatası yeniden bağlanma sebebi
            // değildir: sunucu bizi bilerek düşürdü.
            if (takenOver || closedByUser) return
            setState(BridgeState.RETRYING)
            scheduleReconnect()
        }

        /**
         * Tur-10 (F2): kopmayı sınıflandır + son kopma nedenleri özetini tanı
         * kaydına yaz. Devralma olayları ayrı tür olarak işaretlenir ki
         * "devralındı" ile "ağ koptu" raporu karışmasın.
         */
        private fun journal(reason: String, httpCode: Int? = null) {
            val taken = takenOver || reason.contains("${BridgePolicy.CODE_TAKEOVER}")
            val r = when {
                taken -> "taken_over $reason"
                httpCode != null -> "HTTP $httpCode $reason"
                else -> reason
            }
            ConnectionJournal.record("bridge", r, reason)
            DiagLog.w("conn", ConnectionJournal.summary("bridge"))
        }
    }

    /**
     * Devralma: kendiliğinden yeniden bağlanma tamamen durur, durum
     * [BridgeState.TAKEN_OVER] olur ve bildirim "yeniden bağlanmak için dokun"
     * hâline geçer. Bağlanma yalnız elle (bildirim ya da Ayarlar düğmesi)
     * başlatılır.
     */
    private fun enterTakenOver() {
        if (takenOver && state == BridgeState.TAKEN_OVER) return
        takenOver = true
        live = false
        attempt = 0
        cancelPending()
        socket = null
        setState(BridgeState.TAKEN_OVER)
    }

    private fun cancelPending() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }

    /** Log'a yazılacak kısa argüman özeti — büyük alanlar (base64) kırpılır. */
    private fun briefArgs(tool: String, args: JsonObject): String =
        args.entries.joinToString(" ") { (k, v) ->
            val raw = (v as? JsonPrimitive)?.content.orEmpty()
            if (raw.length > 60) "$k=${raw.take(40)}...(${raw.length})" else "$k=$raw"
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
     * Artan geri çekilme ([BridgePolicy.BACKOFF_MS]), en fazla 60 sn.
     *
     * Normal kopmalarda otomatik bağlanma sürer — bu bağlantının saniyeler
     * içinde geri gelmesi gerekmiyor ama pili boşaltan bir döngü de olmamalı.
     * Devralmada buraya hiç gelinmez.
     *
     * Tur-10 (F2): yakın zamanda bağlıydık (≤60 sn) ve sıra uzun basamağa
     * gelmişse tavan [SocketTuning.BRIDGE_FLAP_CAP_MS] (15 sn) — kısa ağ
     * sarsıntısından sonra 30/60 sn beklemek gereksiz. Merdivenin kendisi
     * (devirme fırtınasını yavaşlatan kısım) korunur ve ±%25 jitter eklenir.
     */
    private fun scheduleReconnect() {
        if (closedByUser || takenOver) return
        attempt++
        val sinceOpen = if (lastConnectedAt > 0) System.currentTimeMillis() - lastConnectedAt else null
        val delayMs = SocketTuning.bridgeReconnectMs(
            attempt = attempt,
            msSinceLastOpen = sinceOpen,
            unit = kotlin.random.Random.nextDouble(),
        )
        DiagLog.d("bridge", "reconnect #$attempt in ${delayMs}ms")
        cancelPending()
        val r = Runnable {
            pending = null
            if (!closedByUser && !takenOver) connect()
        }
        pending = r
        handler.postDelayed(r, delayMs)
    }

    override fun onDestroy() {
        closedByUser = true
        cancelPending()
        socket?.close(1000, "service destroyed")
        socket = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 4813
        private const val BRIDGE_CHANNEL = "hermes-bridge"
        const val ACTION_STOP = "com.hermes.mobile.BRIDGE_STOP"
        const val ACTION_RECONNECT = "com.hermes.mobile.BRIDGE_RECONNECT"

        /**
         * Kullanıcıya görünen durum. Ayarlar ekranı bunu doğrudan izliyor;
         * süreç içi tek kaynak, ikinci bir "gerçekten bağlı mı" tahmini yok.
         */
        private val _state = MutableStateFlow(BridgeState.OFF)
        var state: BridgeState
            get() = _state.value
            private set(value) {
                _state.value = value
            }
        val stateFlow: StateFlow<BridgeState> = _state.asStateFlow()

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

        /**
         * Tek seferlik yeniden bağlanma — devralma kilidini açar.
         *
         * Bilerek `start()` değil: `start()` "açık mı" ayarına bakar,
         * bu ise kullanıcının açık isteğini taşır.
         */
        fun reconnect(context: Context) {
            val s = SettingsStore(context).settings.value
            if (!s.agentMayUsePhone) return
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context,
                    Intent(context, PhoneBridgeService::class.java).setAction(ACTION_RECONNECT),
                )
            }
        }
    }
}
