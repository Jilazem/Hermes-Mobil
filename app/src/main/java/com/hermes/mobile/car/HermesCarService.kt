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
import kotlinx.coroutines.launch

/**
 * Android Auto servisi.
 *
 * Android Auto yalnız **şablon** çizdirir — kendi görünümünü koyamazsın, liste /
 * mesaj / gezinme şablonlarından seçersin ve sürüş sırasında satır sayısı
 * sınırlıdır. Bu yüzden buradaki amaç sohbet etmek değil: **durumu göstermek ve
 * sürüş kipini telefonda başlatmak.** Asıl sesli sohbet telefonun Gemini Live
 * bağlantısı üzerinden yürüyor (araç hoparlörüne Bluetooth'tan çıkıyor).
 *
 * ⚠️ Play Store'un onaylı kategorileri navigasyon/ses/mesajlaşma. Genel amaçlı
 * asistan için resmî yol yok — bu uygulama yan yüklendiği için çalışıyor.
 * Android Auto geliştirici ayarlarında "Bilinmeyen kaynaklar" açık olmalı.
 */
class HermesCarService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        // Yan yüklenen geliştirme sürümü: tüm host'lara izin ver. Yayınlanacak
        // olsaydı ALLOW_ALL yerine imza doğrulaması gerekirdi.
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        // V3: açılış ekranı telefon ekranı yansıtması; "Durum" ile bu ekrana geçilir.
        override fun onCreateScreen(intent: Intent): Screen = MirrorScreen(carContext)
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

    private var loading = true
    private var error: String? = null
    private var statusLine = ""
    private var systemLine = ""
    private var sessionLines: List<String> = emptyList()

    init {
        refresh()
    }

    private fun refresh() {
        val profile = store.active()
        if (profile == null || profile.token.isBlank()) {
            loading = false
            error = "Telefonda sunucu profili ayarlanmamış"
            invalidate()
            return
        }

        scope.launch {
            val client = HermesClient(profile)
            runCatching {
                val status = client.status()
                val stats = client.systemStats()
                val sessions = client.sessions()

                statusLine = "Gateway ${status.gatewayState} · v${status.version}" +
                    " · ${status.activeAgents} ajan"
                systemLine = "CPU %${stats.cpuPercent.toInt()} · " +
                    "RAM %${stats.memory.percent.toInt()} · " +
                    "Disk %${stats.disk.percent.toInt()}"
                sessionLines = sessions
                    .filter { it.isActive }
                    .take(4)
                    .map { "${it.title.take(40)} · ${it.messageCount} mesaj" }
                error = null
            }.onFailure {
                error = "Sunucuya ulaşılamadı"
            }
            loading = false
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        if (loading) {
            return MessageTemplate.Builder("Hermes'e bağlanılıyor…")
                .setTitle("Hermes")
                .setLoading(true)
                .build()
        }

        error?.let { message ->
            return MessageTemplate.Builder(message)
                .setTitle("Hermes")
                .addAction(
                    Action.Builder()
                        .setTitle("Yeniden dene")
                        .setOnClickListener { loading = true; invalidate(); refresh() }
                        .build()
                )
                .build()
        }

        val list = ItemList.Builder().apply {
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
            .setHeaderAction(Action.BACK)
            .setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Konuş")
                            .setOnClickListener { startVoiceOnPhone() }
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

    /**
     * Telefonda sürüş kipini başlatır.
     *
     * Sesli konuşma araç ekranında değil telefonda yürüyor: Car App Library
     * yalnız şablon çizdiriyor, mikrofon/hoparlör akışına karışamıyor. Ses
     * zaten Bluetooth üzerinden aracın hoparlörüne gidiyor, dolayısıyla
     * kullanıcı açısından fark yok — araçtaki düğme sadece tetikleyici.
     */
    private fun startVoiceOnPhone() {
        runCatching {
            carContext.startActivity(
                android.content.Intent(carContext, com.hermes.mobile.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("hermes_action", "driving")
            )
            androidx.car.app.CarToast
                .makeText(carContext, "Telefonda sürüş kipi açıldı", androidx.car.app.CarToast.LENGTH_SHORT)
                .show()
        }.onFailure {
            androidx.car.app.CarToast
                .makeText(carContext, "Telefon kilitliyse önce açman gerekir", androidx.car.app.CarToast.LENGTH_LONG)
                .show()
        }
    }
}
