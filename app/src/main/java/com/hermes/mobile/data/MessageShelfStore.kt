package com.hermes.mobile.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class PinnedChatMessage(val scope: String, val key: String, val role: String, val text: String, val savedAt: Long)

fun togglePinnedMessage(current: List<PinnedChatMessage>, message: PinnedChatMessage): List<PinnedChatMessage> {
    require(message.scope.isNotBlank() && message.key.isNotBlank())
    require(message.text.length <= 20_000) { "Bu mesaj çok uzun; Markdown olarak dışa aktarın." }
    val exists = current.any { it.scope == message.scope && it.key == message.key }
    val rest = current.filterNot { it.scope == message.scope && it.key == message.key }
    return if (exists) rest else (listOf(message) + rest).take(40)
}

/** Local encrypted bookmarks, isolated by server/profile and canonical conversation id. */
class MessageShelfStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = EncryptedSharedPreferences.create(context.applicationContext, "message-shelf-v7",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private fun load(): List<PinnedChatMessage> = runCatching {
        json.decodeFromString<List<PinnedChatMessage>>(prefs.getString("messages", "[]").orEmpty())
    }.getOrDefault(emptyList())
    private val state = MutableStateFlow(load())
    val messages = state.asStateFlow()
    fun refresh() { state.value = load() }
    fun toggle(message: PinnedChatMessage) = synchronized(lock) {
        val next = togglePinnedMessage(load(), message)
        prefs.edit().putString("messages", json.encodeToString(next)).apply()
        state.value = next
    }
    companion object { private val lock = Any() }
}
