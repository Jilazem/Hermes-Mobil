package com.hermes.mobile.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Oturum başına model/düşünme seçimi — V5-2 madde 8 ("oturum bazlı hafıza").
 *
 * Oturum kimliği (stored/db id) anahtar; `sessionChoiceBook*` saf
 * fonksiyonları JVM testi görür, depo yalnız JSON okur/yazar.
 */
@Serializable
data class SessionChoice(
    val provider: String? = null,
    val model: String? = null,
    val think: String? = null,
)

/** Defter: ekleme sırası korunur, budama sondan değil baştan siler. */
@Serializable
data class SessionChoiceBook(
    val order: List<String> = emptyList(),
    val choices: Map<String, SessionChoice> = emptyMap(),
)

/** Seçimi yazar, sırayı günceller, en fazla [max] kayıt tutar. */
fun sessionChoiceBookPut(book: SessionChoiceBook, id: String, choice: SessionChoice, max: Int = 50): SessionChoiceBook {
    if (id.isBlank()) return book
    if (choice.model.isNullOrBlank() && choice.think.isNullOrBlank()) return book
    val order = (listOf(id) + book.order.filterNot { it == id }).take(max)
    val choices = book.choices + (id to choice)
    val kept = choices.filterKeys { it in order.toSet() }
    return SessionChoiceBook(order, kept)
}

class SessionChoiceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun load(): SessionChoiceBook =
        prefs.getString(KEY, null)?.let { raw ->
            runCatching { json.decodeFromString<SessionChoiceBook>(raw) }.getOrNull()
        } ?: SessionChoiceBook()

    private fun save(book: SessionChoiceBook) {
        prefs.edit().putString(KEY, json.encodeToString(book)).apply()
    }

    fun get(sessionId: String?): SessionChoice? {
        val id = sessionId?.takeIf { it.isNotBlank() } ?: return null
        return load().choices[id]
    }

    fun put(sessionId: String?, choice: SessionChoice) {
        val id = sessionId?.takeIf { it.isNotBlank() } ?: return
        save(sessionChoiceBookPut(load(), id, choice))
    }

    private companion object {
        const val PREFS = "hermes-session-choices"
        const val KEY = "book"
    }
}