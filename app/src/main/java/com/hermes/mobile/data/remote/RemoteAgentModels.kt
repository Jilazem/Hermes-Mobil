package com.hermes.mobile.data.remote

import kotlinx.serialization.Serializable
import java.io.IOException
import java.util.UUID

@Serializable
enum class RemoteAgentKind { MIKA, PHONGENT }

/** Credentials are persisted only through RemoteAgentConnectionStore's encrypted preferences. */
@Serializable
data class RemoteAgentConnection(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val kind: RemoteAgentKind,
    val baseUrl: String,
    val token: String,
) {
    override fun toString() = "RemoteAgentConnection(id=$id, label=$label, kind=$kind, baseUrl=$baseUrl, token=[hidden])"
}

data class RemoteAgentTask(
    val id: String,
    val conversation: String = "",
    val goal: String = "",
    val status: String,
    val progress: String? = null,
    val answer: String? = null,
    val question: String? = null,
    val options: List<String> = emptyList(),
    val step: Int? = null,
    val answerable: Boolean = false,
    val verified: Boolean? = null,
)

data class RemoteCommandReceipt(
    val status: String,
    val taskId: String? = null,
    val conversationId: String? = null,
    val confirmedTerminal: Boolean = false,
    val accepted: Boolean = false,
    val messageId: String? = null,
)

class RemoteProtocolException(message: String, cause: Throwable? = null) : IOException(message, cause)
class RemoteApiException(val statusCode: Int, val code: String? = null) : IOException(
    when (statusCode) {
        401 -> "Erişim tokenı kabul edilmedi (401)."
        403 -> "Bu bağlantı için erişim izni yok (403)."
        404 -> "Bu adres beklenen uzak ajan servisini sunmuyor (404)."
        409 -> "Görev şu anda bu işlemi kabul etmiyor (409)."
        429 -> "Çok fazla istek; daha sonra yeniden deneyin (429)."
        else -> "Uzak ajan isteği reddetti (HTTP $statusCode${code?.let { ", $it" }.orEmpty()})."
    },
)

/** A write may have reached the phone. Query its task; never automatically replay it. */
class RemoteOutcomeUnknownException(cause: Throwable? = null) : IOException(
    "İstek ulaşmış olabilir; sonuç doğrulanamadı. Aynı işi yeniden göndermeden görev durumunu kontrol edin.", cause,
)
