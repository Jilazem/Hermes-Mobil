package com.hermes.mobile.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import com.hermes.mobile.data.CrashGuard
import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.ServerProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android Auto screen and direct car-microphone Hermes assistant. */
class HermesCarService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        // Yan yüklenen geliştirme sürümü: tüm host'lara izin ver. Yayınlanacak
        // olsaydı ALLOW_ALL yerine imza doğrulaması gerekirdi.
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        // Always render a supported template; screen projection needs a separate phone consent.
        override fun onCreateScreen(intent: Intent): Screen = HermesCarScreen(carContext)
    }
}

/**
 * Araç ekranı — sunucu durumu ve canlı oturumlar.
 *
 * Sürüşte okunabilirlik için her satır tek satırlık özet; ayrıntı yok.
 */
class HermesCarScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler)
    private val store = ServerProfileStore(carContext)

    private var voiceLine: String? = null
    private val voice = CarVoiceSession(carContext) { line, _ ->
        voiceLine = line
        android.os.Handler(android.os.Looper.getMainLooper()).post { invalidate() }
    }

    private var loading = true
    private var error: String? = null
    private var statusLine = ""
    private var systemLine = ""
    private var sessionLines: List<String> = emptyList()

    init {
        // P5: Screen onDestroy override etmez; DefaultLifecycleObserver ile
        // ekran yığından çıkarken scope iptal edilir.
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) {
                voice.release()
                scope.cancel()
            }
        })
        refresh()
    }

    private fun refresh() {
        // P5: store.active() disk I/O yapabilir; ana thread'de değil scope'ta oku.
        scope.launch {
            val profile = runCatching { store.active() }.getOrNull()
            if (profile == null || profile.token.isBlank()) {
                withContext(Dispatchers.Main) {
                    loading = false
                    error = "Hermes'i telefonda açıp sunucu adresini, anahtarını ve EMA bağlantısını tamamla."
                    invalidate()
                }
                return@launch
            }

            val client = HermesClient(profile)
            val result = runCatching {
                val status = client.status()
                val stats = runCatching { client.systemStats() }.getOrNull()
                val sessions = runCatching { client.sessions() }.getOrDefault(emptyList())

                Triple(
                    "Gateway ${status.gatewayState} · v${status.version} · ${status.activeAgents} ajan",
                    stats?.let { "CPU %${it.cpuPercent.toInt()} · RAM %${it.memory.percent.toInt()} · Disk %${it.disk.percent.toInt()}" }
                        ?: "Sistem istatistikleri alınamadı",
                    sessions.filter { it.isActive }.take(3).map { "${it.title.take(40)} · ${it.messageCount} mesaj" },
                )
            }
            withContext(Dispatchers.Main) {
                result.onSuccess { (status, system, sessions) ->
                    statusLine = status
                    systemLine = system
                    sessionLines = sessions
                    error = null
                }.onFailure {
                    error = "Sunucuya ulaşılamadı. Telefonun ağını ve Hermes sunucu profilini kontrol et."
                }
                loading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        voiceLine?.let { line ->
            return MessageTemplate.Builder(line).setTitle("Hermes · EMA").setHeaderAction(Action.APP_ICON)
                .addAction(Action.Builder().setTitle(if (voice.busy) "İptal" else "Konuş")
                    .setOnClickListener { voice.toggle() }.build())
                .addAction(Action.Builder().setTitle("Durum")
                    .setOnClickListener { voice.cancel(); voiceLine = null; invalidate() }.build())
                .build()
        }
        if (loading) {
            return MessageTemplate.Builder("Hermes'e bağlanılıyor…")
                .setTitle("Hermes")
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
        }

        error?.let { message ->
            return MessageTemplate.Builder(message)
                .setTitle("Hermes")
                .setHeaderAction(Action.APP_ICON)
                .addAction(Action.Builder().setTitle("Konuş").setOnClickListener { startCarVoice() }.build())
                .addAction(
                    Action.Builder()
                        .setTitle("Yeniden dene")
                        .setOnClickListener { loading = true; invalidate(); refresh() }
                        .build()
                )
                .build()
        }

        val list = ItemList.Builder().apply {
            addItem(Row.Builder().setTitle("Hermes Asistan")
                .addText("Araç mikrofonuyla Hermes’e sor")
                .setOnClickListener { startCarVoice() }.build())
            addItem(
                Row.Builder()
                    .setTitle("Durum")
                    .addText(statusLine)
                    .addText(systemLine)
                    .build()
            )
            if (sessionLines.isEmpty()) {
                addItem(Row.Builder().setTitle("Etkin oturum yok").build())
            } else {
                sessionLines.forEach { line ->
                    addItem(Row.Builder().setTitle("Etkin oturum").addText(line).build())
                }
            }
        }.build()

        return ListTemplate.Builder()
            .setTitle("Hermes")
            .setSingleList(list)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Konuş")
                            .setOnClickListener { startCarVoice() }
                            .build()
                    )
                    .addAction(
                        Action.Builder()
                            .setTitle("Yenile")
                            .setOnClickListener { loading = true; invalidate(); refresh() }
                            .build()
                    )
                    .build()
            )
            .build()
    }

    private fun startCarVoice() { voice.toggle() }
}
