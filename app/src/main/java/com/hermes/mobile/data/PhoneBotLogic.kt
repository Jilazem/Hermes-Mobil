package com.hermes.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Telefon botu kartının metin mantığı.
 *
 * Kart "salt-okunur" varsayılanı gösterir: ajan telefonu okur ama dokunmaz.
 * Buradaki fonksiyonlar yalnız metin üretir (Compose'dan bağımsız), böylece
 * JVM testinde doğrulanabilir.
 */
object PhoneBotLogic {

    /** Kartta gösterilecek en fazla ekran satırı. */
    const val PREVIEW_LINES = 6

    /** Kartta gösterilecek en fazla denetim günlüğü kaydı (patron kararı). */
    const val MAX_AUDIT = 20

    /** ACİL DURDUR açıkken yazma reddinin sebebi. */
    const val WHY_EMERGENCY_STOP = "acil durdur açık"

    /** Erişilebilirlik servisi yokken otomatik modun açılamama sebebi. */
    const val WHY_NO_ACCESSIBILITY = "erişilebilirlik izni yok"

    /** Yazma sayılan araç mı: tam kontrol yazma araçları + ajan yazma araçları. */
    fun isWriteTool(tool: String): Boolean =
        tool in FullControl.WRITE_TOOLS || tool in PhoneTools.AGENT_WRITE_TOOLS ||
            tool == "phone_task" || tool == "phone_reply"

    /** A single policy snapshot governs both advertised tools and actual command admission. */
    fun commandBlockReason(settings: AppSettings, tool: String): String? {
        if (!settings.agentMayUsePhone) return FullControl.WHY_AGENT_OFF
        if (isWriteTool(tool)) {
            if (settings.agentEmergencyStop) return WHY_EMERGENCY_STOP
            if (settings.agentReadOnly) return FullControl.WHY_READ_ONLY
            if (!settings.agentAutoWrite) return "otomatik yazma kapalı"
        }
        if (FullControl.isFullControlTool(tool)) return FullControl.guardReason(
            settings.agentMayUsePhone, settings.agentReadOnly, settings.fullControl, tool,
        )
        return when (tool) {
            "phone_reply" -> if (!settings.agentMayReply) "sohbete yanıt izni kapalı" else null
            "phone_task" -> when {
                !settings.fullControl -> FullControl.WHY_FULL_CONTROL_OFF
                settings.artemisUrl.isBlank() -> "Artemis adresi eksik"
                else -> null
            }
            in PhoneTools.READ_TOOL_NAMES, in PhoneTools.AGENT_WRITE_TOOLS -> null
            else -> "tool not enabled"
        }
    }

    fun advertisedTools(settings: AppSettings): List<String> =
        (PhoneTools.READ_TOOL_NAMES + PhoneTools.AGENT_WRITE_TOOLS + FullControl.ALL_TOOLS +
            setOf("phone_reply", "phone_task"))
            .filter { commandBlockReason(settings, it) == null }

    enum class ArtemisEmergencyStop { NotRequested, Confirmed, Unconfirmed }

    /** Confirmation comes only from the real client's checked stop ACK, never a local flag. */
    internal suspend fun stopArtemisForEmergency(
        current: () -> AppSettings,
        taskId: String,
        stop: suspend (String) -> Unit,
    ): ArtemisEmergencyStop {
        if (!current().agentEmergencyStop) return ArtemisEmergencyStop.NotRequested
        return try {
            stop(taskId)
            ArtemisEmergencyStop.Confirmed
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ArtemisEmergencyStop.Unconfirmed
        }
    }

    /**
     * Otomatik yazma gerçekten yürür mü: anahtar açık, ACİL DURDUR kapalı ve
     * erişilebilirlik servisi bağlı olmalı. Üçü birden yoksa ajan dokunamaz.
     */
    fun autoWriteActive(autoWrite: Boolean, emergencyStop: Boolean, accessibilityEnabled: Boolean): Boolean =
        autoWrite && !emergencyStop && accessibilityEnabled

    /**
     * Otomatik modu açmayı engelleyen sebep; yoksa null. Servis kapalıyken
     * anahtar açılamaz — açık görünen ama hiçbir şey yapamayan anahtar
     * kullanıcıyı yanıltıyor.
     */
    fun autoWriteBlockReason(accessibilityEnabled: Boolean, emergencyStop: Boolean): String? = when {
        emergencyStop -> WHY_EMERGENCY_STOP
        !accessibilityEnabled -> WHY_NO_ACCESSIBILITY
        else -> null
    }

    /** ACİL DURDUR düğmesinin altındaki tek satırlık durum. */
    fun emergencyStopLine(emergencyStop: Boolean): String =
        if (emergencyStop) "ACİL DURDUR AÇIK · tüm yazma durdu, okuma sürüyor"
        else "Yazma açık · durdurmak için ACİL DURDUR"

    /**
     * Denetim günlüğü satırı: "14:32:07 · phone_tap · tamam".
     * [detail] boş değilse sona eklenir; satır 160 karakterde kırpılır.
     */
    fun auditLine(atEpochMs: Long, tool: String, ok: Boolean, detail: String = ""): String {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(atEpochMs))
        val outcome = if (ok) "tamam" else "hata"
        val tail = detail.trim().take(80)
        val line = if (tail.isEmpty()) "$time · $tool · $outcome" else "$time · $tool · $outcome · $tail"
        return line.take(160)
    }

    /** Günlüğü son [max] kayıtla sınırla (eskiler düşer). */
    fun trimAudit(lines: List<String>, max: Int = MAX_AUDIT): List<String> =
        if (max <= 0) emptyList() else lines.filter { it.isNotBlank() }.takeLast(max)



    /** Tek satırın üst sınırı — kart taşmasın. */
    const val MAX_LINE_CHARS = 80

    /**
     * [HermesAccessibilityService.dumpScreen] JSON'undan okunabilir satırlar.
     *
     * Ağaç iç içe geldiği için derinlik önce yürünür; aynı etiket tekrar
     * ederse bir kez gösterilir (ekranlarda çok kez geçen "Tamam" gibi).
     */
    fun previewLines(dumpJson: String?, limit: Int = PREVIEW_LINES): List<String> {
        if (dumpJson.isNullOrBlank() || limit <= 0) return emptyList()
        val root = runCatching { Json.parseToJsonElement(dumpJson) }.getOrNull() as? JsonObject ?: return emptyList()
        val tree = root["tree"] as? JsonObject ?: return emptyList()

        val seen = LinkedHashSet<String>()
        fun walk(node: JsonObject) {
            if (seen.size >= limit) return
            val text = (node["text"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val desc = (node["desc"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val label = text.ifBlank { desc }
            if (label.isNotBlank() && label.length <= MAX_LINE_CHARS) seen.add(label)
            (node["children"] as? JsonArray)?.forEach { child ->
                if (child is JsonObject) walk(child)
            }
        }
        walk(tree)
        return seen.take(limit)
    }

    /** Ekranın paket/aktivite başlığı: "com.whatsapp · ChatActivity". */
    fun screenHeader(dumpJson: String?): String? {
        if (dumpJson.isNullOrBlank()) return null
        val root = runCatching { Json.parseToJsonElement(dumpJson) }.getOrNull() as? JsonObject ?: return null
        val pkg = (root["package"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        val activity = (root["activity"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (pkg.isBlank()) return null
        return if (activity.isBlank()) pkg else "$pkg · $activity"
    }

    /** Ajanın bu telefondan hangi araçları görebileceği — salt-okunur sayımı. */
    fun capabilityLabel(mayUsePhone: Boolean, readOnly: Boolean, fullControl: Boolean): String = when {
        !mayUsePhone -> "Kapalı · ajan telefonu kullanmıyor"
        fullControl && !readOnly -> "Tam kontrol · okuma + dokunma + görev"
        readOnly -> "Salt-okunur · ${PhoneTools.READ_TOOL_NAMES.size} okuma aracı"
        else -> "Okuma + yazma · ${(PhoneTools.READ_TOOL_NAMES + PhoneTools.AGENT_WRITE_TOOLS).distinct().size} araç"
    }

    /**
     * Salt-okunur anahtarını çevirmek güvenli yönde serbest, tehlikeli yönde
     * onay ister: okumaya dönüş hemen uygulanır, yazma açmak ayarlar
     * ekranındaki tam kontrol onayına yönlendirilir.
     */
    fun readOnlyToggleNeedsConfirm(targetReadOnly: Boolean): Boolean = !targetReadOnly

    /** Kartın tek satırlık durumu — bağlı/izin yok ayrımı. */
    fun statusLine(enabled: Boolean, running: Boolean): String = when {
        enabled && running -> "Bağlı · ekran okunuyor"
        enabled && !running -> "İzin var, servis henüz bağlanmadı"
        else -> "Erişilebilirlik izni yok"
    }
}
