package com.hermes.mobile.data

import android.content.Context
import java.io.File

/**
 * Telefon botunun yerel denetim günlüğü — yalnız cihazda, ağa gitmez.
 *
 * Her yazma eylemi (dokunma, yazma, kaydırma, tuş, uygulama açma/kapatma,
 * görev) buraya bir satır olarak düşer; dosya son [PhoneBotLogic.MAX_AUDIT]
 * kayıtla sınırlanır, böylece kartta "son 20 kayıt" gösterilebilir ve dosya
 * büyümez. Kayıt tutma hatası eylemi engellemez: günlük ikincil kanıttır,
 * asıl karar `PhoneBotLogic`/`FullControl` kapılarında verilir.
 */
object PhoneAuditLog {

    private const val FILE_NAME = "phone-audit.log"

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** Son kayıtlar — en eski önce, en yeni sonda. */
    fun read(context: Context): List<String> = runCatching {
        val f = file(context)
        if (!f.exists()) emptyList() else PhoneBotLogic.trimAudit(f.readLines())
    }.getOrDefault(emptyList())

    /** Bir yazma eylemini günlüğe ekle; döndürülen liste güncel son 20 kayıttır. */
    @Synchronized
    fun record(context: Context, tool: String, ok: Boolean, detail: String = ""): List<String> {
        val line = PhoneBotLogic.auditLine(System.currentTimeMillis(), tool, ok, detail)
        val kept = PhoneBotLogic.trimAudit(read(context) + line)
        runCatching { file(context).writeText(kept.joinToString("\n")) }
        return kept
    }

    /** ACİL DURDUR gibi kullanıcı kararları da günlüğe yazılır. */
    fun recordDecision(context: Context, decision: String): List<String> =
        record(context, "kullanıcı", true, decision)

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }
}
