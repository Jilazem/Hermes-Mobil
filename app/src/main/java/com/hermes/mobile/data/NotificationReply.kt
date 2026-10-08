package com.hermes.mobile.data

/** Only a completed turn may be announced or spoken; deltas are fragments. */
class NotificationReply(private val sessionId: String) {
    private val fragments = StringBuilder()
    var result: String? = null
        private set

    fun accept(event: GatewayEvent): Boolean {
        if (event.sessionId != sessionId || result != null) return false
        when (event.type) {
            "message.delta" -> fragments.append(event.text.orEmpty())
            "message.complete" -> result = fragments.toString().ifBlank { event.text.orEmpty() }
                .ifBlank { "Yanıt boş geldi — sohbetten yeniden deneyebilirsin" }
            "approval.request", "sudo.request", "clarify.request", "secret.request" ->
                result = "Devam etmek için onayın veya ek bilgin gerekiyor. Telefonun sohbet ekranından devam edebilirsin."
            "error" -> result = "Yanıt tamamlanamadı — sohbet ekranından yeniden deneyebilirsin"
        }
        return result != null
    }
}
