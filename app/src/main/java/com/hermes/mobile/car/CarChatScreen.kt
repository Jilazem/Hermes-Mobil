package com.hermes.mobile.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarText
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.LongMessageTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.hermes.mobile.data.CrashGuard
import com.hermes.mobile.data.HermesClient
import com.hermes.mobile.data.ServerProfileStore
import com.hermes.mobile.data.SessionMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Araç ekranı — bir oturumun MESAJ AKIŞI.
 *
 * V5-3 kök neden: araç ekranı yalnız durum/oturum listesi çiziyordu; oturum
 * satırları tıklanamıyordu ve hiçbir yerde mesaj içeriği yoktu. Android Auto
 * bu yüzden "yeni mesajınız yok" boş sohbet ekranından öteye geçmiyordu.
 * Bu ekran gerçek `/api/sessions/{id}/messages` dökümünü çizer; satıra
 * dokununca tam metin açılır, "Konuş" araç mikrofonuyla aynı oturuma yanıt yazar.
 */
class CarMessagesScreen(
    carContext: CarContext,
    private val sessionId: String,
    private val sessionTitle: String?,
) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CrashGuard.handler)
    private val store = ServerProfileStore(carContext)

    private var loading = true
    private var error: String? = null
    private var rows: List<CarChatLogic.Row> = emptyList()
    private var voiceLine: String? = null

    private val voice = CarVoiceSession(carContext) { line, _ ->
        voiceLine = line
        android.os.Handler(android.os.Looper.getMainLooper()).post { invalidate() }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                voice.release()
                scope.cancel()
            }
        })
        refresh()
    }

    private fun refresh() {
        loading = true
        error = null
        invalidate()
        scope.launch {
            val loaded = runCatching {
                val profile = store.active() ?: error("Sunucu profili yok")
                if (profile.token.isBlank()) error("Sunucu anahtarı boş")
                HermesClient(profile).sessionMessages(sessionId)
            }
            withContext(Dispatchers.Main) {
                loaded.onSuccess { rows = CarChatLogic.rows(it) }
                    .onFailure { error = "Mesajlar alınamadı: ${it.message ?: "bilinmeyen hata"}" }
                loading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        voiceLine?.let { line ->
            return MessageTemplate.Builder(line)
                .setTitle("Hermes · Sesli yanıt")
                .setHeaderAction(Action.APP_ICON)
                .addAction(Action.Builder().setTitle(if (voice.busy) "İptal" else "Konuş")
                    .setOnClickListener { voice.toggle() }.build())
                .addAction(Action.Builder().setTitle("Akışa dön")
                    .setOnClickListener { voice.cancel(); voiceLine = null; invalidate() }.build())
                .build()
        }

        if (loading || error != null || rows.isEmpty()) {
            val body = CarChatLogic.emptyText(loading, error)
            val builder = MessageTemplate.Builder(body)
                .setTitle(CarChatLogic.title(sessionTitle, sessionId))
                .setHeaderAction(Action.APP_ICON)
                .setLoading(loading)
            if (!loading) {
                builder.addAction(Action.Builder().setTitle("Konuş")
                    .setOnClickListener { voice.toggle() }.build())
                builder.addAction(Action.Builder().setTitle("Yenile")
                    .setOnClickListener { refresh() }.build())
            }
            return builder.build()
        }

        val list = ItemList.Builder().apply {
            rows.forEach { row ->
                addItem(
                    Row.Builder()
                        .setTitle(row.sender)
                        .addText(row.snippet)
                        .setOnClickListener {
                            screenManager.push(
                                CarMessageDetailScreen(carContext, CarChatLogic.title(sessionTitle, sessionId), row)
                            )
                        }
                        .build()
                )
            }
        }.build()

        return ListTemplate.Builder()
            .setTitle(CarChatLogic.title(sessionTitle, sessionId))
            .setSingleList(list)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle("Konuş")
                        .setOnClickListener { voice.toggle() }.build())
                    .addAction(Action.Builder().setTitle("Yenile")
                        .setOnClickListener { refresh() }.build())
                    .build()
            )
            .build()
    }
}

/** Tek mesajın tam metni — araç host'u uzun metni kaydırılabilir çizer. */
class CarMessageDetailScreen(
    carContext: CarContext,
    private val chatTitle: String,
    private val row: CarChatLogic.Row,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        return LongMessageTemplate.Builder(row.fullText)
            .setTitle("${row.sender} · $chatTitle")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
