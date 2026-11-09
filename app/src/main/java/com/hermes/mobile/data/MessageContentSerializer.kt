package com.hermes.mobile.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** Desktop multimodal content must not make the entire conversation fail to decode. */
object MessageContentSerializer : KSerializer<String?> {
    override val descriptor = PrimitiveSerialDescriptor("HermesMessageContent", PrimitiveKind.STRING).nullable
    override fun serialize(encoder: Encoder, value: String?) {
        (encoder as JsonEncoder).encodeJsonElement(value?.let(::JsonPrimitive) ?: JsonNull)
    }
    override fun deserialize(decoder: Decoder): String? = messageContentText((decoder as JsonDecoder).decodeJsonElement())
}

internal fun messageContentText(element: JsonElement): String? = when(element) {
    JsonNull -> null
    is JsonPrimitive -> element.contentOrNull
    is JsonArray -> element.mapNotNull { block ->
        if (block is JsonPrimitive) block.contentOrNull else (block as? JsonObject)?.let { row ->
            val type = (row["type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            when (type) {
                "text", "input_text", "output_text" -> (row["text"] as? JsonPrimitive)?.contentOrNull
                "image_url", "input_image", "image", "audio_url", "video_url" -> {
                    val source = row["image_url"] ?: row["audio_url"] ?: row["video_url"] ?: row["url"]
                    val url = ((source as? JsonObject)?.get("url") as? JsonPrimitive)?.contentOrNull ?: (source as? JsonPrimitive)?.contentOrNull
                    if (url == null) "[Medya bağlantısı bulunamadı]"
                    else if (safeExternalUrl(url) != null || isServerMediaPath(url) || (type in setOf("image_url", "input_image", "image") && isInlineConversationImage(url))) {
                        if (type in setOf("image_url", "input_image", "image")) "![Görsel]($url)" else url
                    } else "[Bu medya bağlantısı önizlenemiyor]"
                }
                else -> "[Desteklenmeyen içerik: ${type.take(60)}]"
            }
        }
    }.joinToString("\n\n")
    is JsonObject -> (element["text"] as? JsonPrimitive)?.contentOrNull ?: "[Yapılandırılmış içerik]"
}
