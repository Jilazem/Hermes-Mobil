package com.hermes.mobile.data

/** Bot Arena kipleri. */
enum class ArenaMode {
    /** Tek bot: konu, tek cevap. */
    SINGLE,

    /** Kapışma: tur 1 bağımsız cevaplar → tur 2 her bot diğerlerini görüp revize eder. */
    BATTLE,

    /** Beyin fırtınası: bağımsız fikirler → sentez botu 5 maddelik final verir. */
    BRAINSTORM,
}

/** Bir botun bir turdaki cevabı. */
data class ArenaAnswer(
    val bot: String,
    val round: Int,
    val text: String,
    val ok: Boolean = true,
    val error: String? = null,
)

/**
 * Arena tur 2 / sentez promptları — UI'dan bağımsız saf fonksiyonlar,
 * test edilebilirlik için ayrı duruyor.
 */
object ArenaPrompts {
    /** Tur 1: her bot konuyu bağımsız yanıtlar. */
    fun round1(topic: String): String =
        "Bot Arena görevi: \"$topic\"\n\nSorunu net, gerekçeli ve somut yanıtla."

    /**
     * Kapışma tur 2: bot kendi cevabını koruyabilir ama diğer botların
     * yanıtlarını görerek eleştirip gerekçeli revize etmeli.
     */
    fun battleRound2(
        topic: String,
        self: String,
        others: Map<String, String>,
    ): String {
        val alt = others.entries.joinToString("\n") { (b, t) ->
            "### $b\n$t"
        }
        return (
            "Bot Arena kapışma tur 2 — konu: \"$topic\"\n\n" +
                "Önceki cevabın:\n$self" +
                "\n\nDiğer botların cevapları:\n$alt" +
                "\n\nGörev: Diğer botların cevaplarını eleştir. Kendi cevabının zayıf yanlarını" +
                " belirt. Gerekçeleriyle birlikte revize et; sonunda revize edilmiş cevabı yaz."
            )
    }

    /** Beyin fırtınası: bağımsız fikirler. */
    fun brainstormIdeas(topic: String): String =
        "Bot Arena beyin fırtınası — konu: \"$topic\"\n\nYaratıcı ve özgün fikirler üret; sayı sınası yok."

    /**
     * Sentez promptu: tüm fikirleri birleştirir, tekrarları atar,
     * 5 maddelik final listesi verir.
     */
    fun synthesis(
        topic: String,
        ideas: Map<String, String>,
    ): String {
        val kısımlar = ideas.entries.joinToString("\n") { (bot, text) ->
            "### $bot\n$text"
        }
        return (
            "Bot Arena sentez — konu: \"$topic\"\n\n" +
                "Aşağıdaki botların beyin fırtınası fikirlerini oku:\n\n" +
                kısımlar +
                "\n\nGörev: Fikirleri birleştir, tekrarları at ve tam olarak 5 maddelik" +
                " final bir liste ver. Maddeler somut ve uygulanabilir olsun."
            )
    }
}
