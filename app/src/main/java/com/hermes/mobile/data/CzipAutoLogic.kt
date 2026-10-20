package com.hermes.mobile.data

/**
 * V5-4 CZIP AUTO — arka plan paketleme güvencesi (SAF mantık, JVM testli).
 *
 * AGENTS.md kuralı: czip-auto HER yeni oturumda açık başlar. Sunucu tarafı
 * kalıcıdır (`.../czip/ayar.json` `oto=true` + `czip-auto` eklentisinin
 * `on_session_start` hook'u). Mobil istemci aynı davranışı garanti eder:
 * yeni oturumda `czip oto durum` ile doğrular, kapalıysa `czip oto aç` ile
 * açar ve sonucu ekranda tek satır bildirimle kanıtlar.
 *
 * czip komut ailesi: `czip oto aç|kapat|durum`; gateway slash eşdeğerleri
 * `/czip-auto ac|kapat|durum` (bkz. czip eklentisi `_auto`).
 */

/** czip otomatik paketleme durumu. */
enum class CzipAutoState { ON, OFF, UNKNOWN }

object CzipAutoLogic {
    /** Durum sorgusu — `czip oto durum` eşdeğeri. */
    const val STATUS_COMMAND = "/czip-auto durum"

    /** Açma — `czip oto aç` eşdeğeri. */
    const val ENABLE_COMMAND = "/czip-auto ac"

    /**
     * Durum/açma çıktısından `oto` bayrağını okur.
     *
     * Biçimler (motor `hkp.py` ayar dalı):
     *  - `"  oto       : ON   (on = ...)"` — durum tablosu
     *  - `"oto -> ON"` — ayar yazma çıktısı
     *
     * `"oto_pasif"` satırı bilinçli olarak atlanır (anahtar tam "oto"
     * olmalı). Tanınmayan çıktı (traceback, hata, boş) [CzipAutoState.UNKNOWN]
     * döner — asla varsayılan uydurulmaz.
     */
    fun state(output: String): CzipAutoState {
        for (raw in output.lineSequence()) {
            val line = raw.trim()
            val sep = when {
                "->" in line -> "->"
                ':' in line -> ":"
                else -> continue
            }
            if (!line.substringBefore(sep).trim().equals("oto", ignoreCase = true)) continue
            val value = line.substringAfter(sep).trim().lowercase()
            return when {
                value.startsWith("on") || value.startsWith("aç") || value.startsWith("ac") -> CzipAutoState.ON
                value.startsWith("off") || value.startsWith("kapat") || value.startsWith("kapal") -> CzipAutoState.OFF
                else -> CzipAutoState.UNKNOWN
            }
        }
        return CzipAutoState.UNKNOWN
    }

    /**
     * Yeni oturumdaki güvence adımları: durum açıksa ek komut yok; kapalıysa
     * ya da tanınamadıysa `oto aç` gönderilir (açmak idempotenttir).
     */
    fun ensureCommands(statusOutput: String): List<String> =
        if (state(statusOutput) == CzipAutoState.ON) emptyList() else listOf(ENABLE_COMMAND)

    /** Ekrandaki kanıt satırı (TR) — [on] = doğrulanan/açılan durum. */
    fun noticeTr(on: Boolean): String =
        if (on) "Czip arka plan paketleme (oto): AÇIK — yeni oturumlar kendiliğinden paketlenir"
        else "Czip arka plan paketleme (oto) doğrulanamadı — sunucuda `czip oto durum` ile kontrol edin"

    /** Ekrandaki kanıt satırı (EN). */
    fun noticeEn(on: Boolean): String =
        if (on) "Czip background packing (auto): ON — new sessions pack automatically"
        else "Czip background packing (auto) could not be verified — run `czip oto durum` on the server"
}