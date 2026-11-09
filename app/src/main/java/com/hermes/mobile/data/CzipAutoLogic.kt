package com.hermes.mobile.data

/**
 * V5-4 CZIP AUTO & GOREV-CZIP — arka plan paketleme güvencesi, Dual-Tier (%60/%75)
 * oturum sürekliliği ve 6 Bölümlü AI Görev Sözleşmesi (`czip-promt`) yardımcıları.
 *
 * AGENTS.md kuralı: czip-auto HER yeni oturumda açık başlar. Sunucu tarafı
 * kalıcıdır (`.../czip/ayar.json` `oto=true` + `gorev-czip` / `czip-auto` eklentileri).
 * Mobil istemci aynı davranışı garanti eder: yeni oturumda `czip oto durum` ile
 * doğrular, kapalıysa `czip oto aç` ile açar ve sonucu ekranda tek satır bildirimle kanıtlar.
 */

/** czip otomatik paketleme durumu. */
enum class CzipAutoState { ON, OFF, UNKNOWN }

object CzipAutoLogic {
    /** Durum sorgusu — `czip oto durum` eşdeğeri. */
    const val STATUS_COMMAND = "/czip-auto durum"

    /** Açma — `czip oto aç` eşdeğeri. */
    const val ENABLE_COMMAND = "/czip-auto ac"

    /** Birleşik görev ve oturum czip durumu (`gorev-czip`). */
    const val GOREV_CZIP_STATUS_COMMAND = "/gorev-czip durum"

    /** Prompt normalizasyonu (`czip-promt` 6 Bölümlü AI Görev Sözleşmesi). */
    const val PROMPT_CONTRACT_COMMAND = "/czip-promt"

    /** Varsayılan Dual-Tier eşikleri: %60 kontrol noktası, %75 oturum devri. */
    const val DEFAULT_CHECKPOINT_PCT = 60
    const val DEFAULT_ROTATE_PCT = 75

    /**
     * Durum/açma çıktısından `oto` bayrağını okur.
     *
     * Biçimler (motor `hkp.py` ayar dalı ve `gorev-czip`):
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
     * `czip oto durum` veya `/gorev-czip durum` çıktısından Dual-Tier (%60 kontrol noktası,
     * %75 devir) eşiklerini ayrıştırır. Çıktıda açıkça belirtilmemişse (60, 75) döner.
     */
    fun parseDualTierThresholds(statusOutput: String): Pair<Int, Int> {
        val pctRegex = Regex("%([0-9]{2})")
        val matches = pctRegex.findAll(statusOutput).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
        if (60 in matches && 75 in matches) return 60 to 75
        if (matches.size >= 2) {
            val sorted = matches.filter { it in 30..95 }.distinct().sorted()
            if (sorted.size >= 2) return sorted[0] to sorted[1]
        }
        return DEFAULT_CHECKPOINT_PCT to DEFAULT_ROTATE_PCT
    }

    /**
     * Ham görev istemini Hermes `czip-promt` 6 Bölümlü AI Görev Sözleşmesi
     * (`AMAÇ`, `KAPSAM VE DOSYALAR`, `KISITLAR VE KURALLAR`, `ADIMLAR`, `BEKLENEN ÇIKTI`, `DOĞRULAMA`)
     * yapısına dönüştürür. Zaten sözleşme başlıklarını içeriyorsa dokunmaz.
     */
    fun normalizePromptToContract(rawPrompt: String): String {
        val trimmed = rawPrompt.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.contains("## 1. AMAÇ") && trimmed.contains("## 6. DOĞRULAMA")) return trimmed
        val firstLine = trimmed.lineSequence().firstOrNull()?.trim().orEmpty().take(160)
        return buildString {
            appendLine("## 1. AMAÇ")
            appendLine(trimmed)
            appendLine()
            appendLine("## 2. KAPSAM VE DOSYALAR")
            appendLine("- İlgili çalışma dizini ve görev kapsamındaki kaynak dosyalar.")
            appendLine()
            appendLine("## 3. KISITLAR VE KURALLAR")
            appendLine("- Mevcut çalışan davranışları ve test sözleşmelerini bozma.")
            appendLine("- Ortak kod kütüphanesi ve Czip ön hafıza kurallarına uy.")
            appendLine()
            appendLine("## 4. ADIMLAR")
            appendLine("1. Mevcut durumu ve ilgili dosyaları incele ($firstLine).")
            appendLine("2. Değişiklikleri en az yan etkiyle uygula.")
            appendLine("3. Test ve doğrulama kontrollerini çalıştır.")
            appendLine()
            appendLine("## 5. BEKLENEN ÇIKTI")
            appendLine("- Tamamlanan değişikliklerin ve doğrulama sonuçlarının kısa özeti.")
            appendLine()
            appendLine("## 6. DOĞRULAMA")
            append("- Değişikliklerin derleme/test veya çalışma zamanı kontrolüyle doğrulandığını kanıtla.")
        }
    }

    /**
     * Yeni oturumdaki güvence adımları: durum açıksa ek komut yok; kapalıysa
     * ya da tanınamadıysa `oto aç` gönderilir (açmak idempotenttir).
     */
    fun ensureCommands(statusOutput: String): List<String> =
        if (state(statusOutput) == CzipAutoState.ON) emptyList() else listOf(ENABLE_COMMAND)

    /** Ekrandaki kanıt satırı (TR) — [on] = doğrulanan/açılan durum. */
    fun noticeTr(on: Boolean): String =
        if (on) "Czip arka plan paketleme (oto): AÇIK (%60 kontrol noktası / %75 devir) — yeni oturumlar kendiliğinden paketlenir"
        else "Czip arka plan paketleme (oto) doğrulanamadı — sunucuda `czip oto durum` ile kontrol edin"

    /** Ekrandaki kanıt satırı (EN). */
    fun noticeEn(on: Boolean): String =
        if (on) "Czip background packing (auto): ON (60% checkpoint / 75% handoff) — new sessions pack automatically"
        else "Czip background packing (auto) could not be verified — run `czip oto durum` on the server"
}
