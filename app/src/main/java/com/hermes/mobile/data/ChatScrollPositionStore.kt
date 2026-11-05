package com.hermes.mobile.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.MessageDigest

data class ChatReadCursor(val anchor: String, val timestamp: Double? = null)

/**
 * M8 (patron eki): oturum başına son-okunan konumu — cihazda (SharedPreferences).
 *
 * Değer mesaj ZAMAN DAMGASIDIR (epoch saniye), satır indeksi DEĞİL: mesaj
 * sayısı değişse (silme/ekleme) konum kaymaz; ilk okunmamış satır zamana
 * göre hesaplanır (ChatScreen.firstUnreadRowIndex — saf, ChatScrollTest).
 */
class ChatScrollPositionStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chat_scroll_pos", Context.MODE_PRIVATE)

    /** V2 keys include the Dashboard/agent namespace. Old unscoped marks are retained for rollback. */
    private fun key(scope: String) = "v2:" + MessageDigest.getInstance("SHA-256")
        .digest(scope.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun read(scope: String): ChatReadCursor? = runCatching {
        val json = JSONObject(prefs.getString(key(scope), null) ?: return null)
        val anchor = json.getString("anchor")
        if (!anchor.matches(Regex("[a-f0-9]{64}"))) return null
        ChatReadCursor(anchor, json.optDouble("timestamp").takeIf { it.isFinite() && it > 0 })
    }.getOrNull()

    fun save(scope: String, cursor: ChatReadCursor) = synchronized(lock) {
        if (scope.isBlank() || !cursor.anchor.matches(Regex("[a-f0-9]{64}"))) return@synchronized
        val old = read(scope)
        val timestamp = cursor.timestamp?.takeIf { it.isFinite() && it > 0 }
        if (old?.timestamp != null && timestamp != null && timestamp < old.timestamp) return@synchronized
        val next = cursor.copy(timestamp = timestamp ?: old?.timestamp)
        if (next == old) return@synchronized
        val json = JSONObject().put("anchor", next.anchor)
        next.timestamp?.let { json.put("timestamp", it) }
        prefs.edit().putString(key(scope), json.toString()).apply()
    }

    /** Oturumun saklanan son-okunan damgası; hiç okunmadıysa null. */
    fun okunanZaman(sessionId: String): Double? =
        prefs.getString(sessionId, null)?.toDoubleOrNull()

    /** En altta okunduğunda en yeni damgayı yazar (geriye gitmez). */
    fun kaydet(sessionId: String, ts: Double?) {
        if (ts == null || !ts.isFinite() || sessionId.isBlank() || (okunanZaman(sessionId) ?: 0.0) > ts) return
        prefs.edit().putString(sessionId, ts.toString()).apply()
    }

    /** Oturum kaydı silindiğinde konumu da temizler. */
    fun sil(sessionId: String) {
        prefs.edit().remove(sessionId).apply()
    }
    companion object { private val lock = Any() }
}
