package com.hermes.mobile.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Google Artemis (github.com/google/artemis) istemcisinin SAF çekirdeği.
 *
 * Artemis telefonda değil sunucuda (ör. 192.168.1.101) koşar ve telefonu
 * ADB ile (USB ya da kablosuz hata ayıklama) bir insan gibi kullanır: ekranı
 * görür, dokunur, yazar, uygulamalar arası iş yapar. Bu dosya yalnız sözleşme
 * kurallarını tutar (resmî `artemis-client` Python paketiyle aynı): hangi
 * durum bitti sayılır, çıktı nereden okunur, hangi cihaz seçilir.
 * Ağ kısmı [ArtemisClient]'ta.
 */
object ArtemisLogic {

    /** `artemis_client.models.TERMINAL_TASK_STATUSES` ile birebir. */
    val TERMINAL = setOf("completed", "success", "failed", "cancelled", "canceled", "rejected")
    val SUCCESS = setOf("completed", "success")

    /** Sohbette Artemis'e giden komut önekleri. */
    private val PREFIXES = listOf("/telefon", "/artemis", "/phone")

    /**
     * "/telefon Ayarlar'dan pil yüzdesini söyle" → "Ayarlar'dan pil yüzdesini söyle".
     * Önek yoksa ya da görev boşsa null (mesaj normal yoldan ajana gider).
     */
    fun parseCommand(text: String): String? {
        val t = text.trim()
        val p = PREFIXES.firstOrNull {
            t.equals(it, ignoreCase = true) || t.startsWith("$it ", ignoreCase = true)
        } ?: return null
        return t.substring(p.length).trim().takeIf { it.isNotEmpty() }
    }

    data class Device(val serial: String, val state: String, val model: String?, val busy: Boolean)

    data class Task(
        val id: String,
        val status: String,
        val output: String?,
        val error: String?,
        val turns: Int?,
    ) {
        val done: Boolean get() = status in TERMINAL
        val succeeded: Boolean get() = status in SUCCESS
    }

    private fun JsonElement?.str(): String? = when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        else -> toString()
    }

    /** `/api/devices` — liste ya da {"devices":[…]}. Seri numarası olmayan kayıt atlanır. */
    fun parseDevices(root: JsonElement): List<Device> {
        val arr = when (root) {
            is JsonArray -> root
            is JsonObject -> root["devices"] as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val serial = (o["serial"] ?: o["device_serial"] ?: o["device_id"]).str() ?: return@mapNotNull null
            val state = ((o["state"] ?: o["status"]).str() ?: "unknown").lowercase()
            val busy = ((o["busy"] ?: o["is_busy"]) as? JsonPrimitive)?.booleanOrNull == true ||
                state in setOf("busy", "running", "locked")
            Device(serial, state, o["model"].str(), busy)
        }
    }

    /** `/api/run` yanıtından kabul edilen görevin kimliği; reddedildiyse hata metniyle Left. */
    fun parseSubmit(root: JsonObject, fallbackId: String): Result<String> {
        val status = root["status"].str()?.lowercase() ?: "unknown"
        val tasks = root["tasks"] as? JsonArray
        if (status == "rejected" || (tasks != null && tasks.isEmpty())) {
            return Result.failure(IllegalStateException(root["error"].str() ?: "Artemis görevi reddetti"))
        }
        val first = tasks?.firstOrNull() as? JsonObject
            ?: return Result.failure(IllegalStateException("Artemis yanıtında görev yok"))
        return Result.success((first["task_id"] ?: first["session_id"] ?: first["id"]).str() ?: fallbackId)
    }

    /** `/api/sessions/{id}` — çıktı sırası: output → result → summary (resmî istemciyle aynı). */
    fun parseTask(o: JsonObject, id: String): Task = Task(
        id = (o["task_id"] ?: o["session_id"] ?: o["trace_id"] ?: o["id"]).str() ?: id,
        status = (o["status"].str() ?: "unknown").lowercase(),
        output = (o["output"]?.takeIf { it != JsonNull } ?: o["result"]?.takeIf { it != JsonNull } ?: o["summary"]).str(),
        error = (o["error"] ?: o["error_message"]).str(),
        turns = ((o["turns"] ?: o["current_turn"]) as? JsonPrimitive)?.intOrNull,
    )

    /**
     * Bu telefon hangisi? Öncelik: ayarda elle yazılan seri → kablosuz ADB
     * serisi telefonun Wi-Fi IP'siyle başlayan → tek hazır cihaz → null
     * (sunucu kendi seçsin). Birden çok cihaz varken TAHMİN YOK.
     */
    fun pickDevice(devices: List<Device>, configured: String, phoneIp: String?): String? {
        configured.trim().takeIf { it.isNotEmpty() }?.let { return it }
        val ready = devices.filter { it.state == "device" || it.state == "online" || it.state == "idle" }
        if (!phoneIp.isNullOrBlank()) {
            ready.firstOrNull { it.serial.startsWith("$phoneIp:") }?.let { return it.serial }
        }
        return ready.singleOrNull()?.serial
    }

    /** Sohbete yazılacak sonuç metni. */
    fun resultText(t: Task, en: Boolean): String = when {
        t.succeeded -> (if (en) "📱 Artemis done" else "📱 Artemis tamamladı") +
            (t.turns?.let { if (en) " ($it steps)" else " ($it adım)" } ?: "") +
            (t.output?.let { "\n\n$it" } ?: "")
        t.status == "cancelled" || t.status == "canceled" -> if (en) "📱 Artemis task cancelled" else "📱 Artemis görevi iptal edildi"
        else -> (if (en) "📱 Artemis failed" else "📱 Artemis başarısız") +
            (t.error?.let { ": $it" } ?: " (${t.status})")
    }
}
