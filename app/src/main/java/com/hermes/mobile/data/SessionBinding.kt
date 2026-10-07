package com.hermes.mobile.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.CancellationException

/** The gateway remints runtime ids when it restores a durable conversation. */
data class SessionBinding(val runtimeId: String, val storedId: String) {
    companion object {
        fun parse(result: JsonElement?, requestedId: String): SessionBinding {
            val obj = result as? JsonObject
                ?: throw IllegalStateException("Oturum sunucudan doğrulanamadı")
            fun value(key: String) = (obj[key] as? JsonPrimitive)?.takeUnless { it.isString.not() }
                ?.content?.takeIf { it.isNotBlank() }
            val runtime = value("session_id")
                ?: throw IllegalStateException("Sunucu oturum kimliği döndürmedi")
            return SessionBinding(runtime, value("session_key") ?: value("stored_session_id")
                ?: value("resumed") ?: requestedId.ifBlank { runtime })
        }
    }
}

/** Restore exactly the selected conversation; failures never create a blank chat. */
suspend fun restoreConversation(
    runtimeId: String,
    storedId: String,
    activate: suspend (String) -> String,
    resume: suspend (String) -> String,
): String = try {
    activate(runtimeId)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    resume(storedId)
}
