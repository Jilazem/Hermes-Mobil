package com.hermes.mobile.data

import android.content.Context
import android.content.SharedPreferences

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

    /** Oturumun saklanan son-okunan damgası; hiç okunmadıysa null. */
    fun okunanZaman(sessionId: String): Double? =
        prefs.getString(sessionId, null)?.toDoubleOrNull()

    /** En altta okunduğunda en yeni damgayı yazar (geriye gitmez). */
    fun kaydet(sessionId: String, ts: Double?) {
        if (ts == null || sessionId.isBlank()) return
        prefs.edit().putString(sessionId, ts.toString()).apply()
    }

    /** Oturum kaydı silindiğinde konumu da temizler. */
    fun sil(sessionId: String) {
        prefs.edit().remove(sessionId).apply()
    }
}
