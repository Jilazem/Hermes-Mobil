package com.hermes.mobile.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * V3 araçlarının tanımları (MCP/JSON-Schema biçimi).
 *
 * Köprü "hello" karesinde araç ADLARINA ek olarak bunları da `specs` alanında
 * gönderir: sunucudaki phone MCP bu alanı okuyup aracı kendiliğinden
 * kaydedebilir (eski sürüm alanı yok sayar, bir şey bozulmaz). Aynı metin
 * docs/PHONE-TOOLS.md'de — sunucuya elle eklemek için.
 */
object PhoneToolSpecs {

    private fun spec(description: String, required: List<String>, props: Map<String, String>): JsonObject =
        buildJsonObject {
            put("description", description)
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties") {
                    props.forEach { (k, d) -> putJsonObject(k) { put("type", "string"); put("description", d) } }
                }
                put("required", buildJsonArray { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }
        }

    val SPECS: Map<String, JsonObject> = mapOf(
        "phone_messages" to spec(
            "Read unread chat messages on the user's phone (WhatsApp, Telegram, SMS, Signal…) " +
                "from their notifications: app, chat name and the last lines of each conversation. " +
                "Use when the user asks what came in / what someone wrote.",
            emptyList(),
            mapOf(
                "app" to "Optional filter: whatsapp | telegram | sms | signal …",
                "chat" to "Optional chat/contact name filter (partial match).",
                "limit" to "Max conversations (default 10).",
            ),
        ),
        "phone_reply" to spec(
            "Send a reply in a chat on the user's phone through the chat's notification reply action " +
                "(same mechanism as Android Auto; works with the screen locked). ONLY when the user " +
                "explicitly asked to send this text to this person. If several chats match, nothing is " +
                "sent and the candidates are returned — ask the user which one.",
            listOf("chat", "text"),
            mapOf(
                "chat" to "Chat/contact name as shown in phone_messages.",
                "text" to "Exact message text to send.",
                "app" to "Optional: whatsapp | telegram | sms …",
            ),
        ),
        "phone_task" to spec(
            "Have Google Artemis operate the phone like a human (sees the screen, taps, types, " +
                "switches apps) for multi-step UI tasks no other phone tool covers. Slow (seconds per " +
                "step). Returns Artemis' result, or a task id if still running after ~4 minutes.",
            listOf("goal"),
            mapOf(
                "goal" to "Natural-language goal, e.g. 'Open Settings and read the battery level'.",
                "profile" to "Optional: flash (fast) | pro (plans and verifies).",
            ),
        ),
    )
}
