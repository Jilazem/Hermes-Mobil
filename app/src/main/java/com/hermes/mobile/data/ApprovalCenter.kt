package com.hermes.mobile.data

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * K18B-G3 — MERKEZİ ONAY KUTUSU (\"Senin onayın bekleniyor\").
 *
 * Sohbet içindeki `ChatItem.Approval` kartları (`/approve`, `/deny`) dağınık
 * kalıyordu: iki ajan aynı anda riskli iş istediğinde kullanıcı ikisini de
 * ancak ilgili sohbeti gezerken görebiliyordu. Bu katman tüm onay isteklerini
 * TEK listede toplar (MausBot'un \"Waiting on your yes\" deseni — yalnız FİKİR,
 * Apache-2.0; bkz. CREDITS.md). Cevap yolu DEĞİŞMEZ: `/approve` / `/deny`
 * gateway'e aynı `slash.exec` ile gider.
 *
 * Katman saf Kotlin'dir (Android'e dokunmaz) — birim testleri cihazsız koşar.
 */
data class ApprovalEntry(
    /** Sohbet kartı anahtarıyla AYNI kimlik (`ap-N`) — iki görünüm tek kayıt. */
    val id: String,
    /** İsteğin geldiği oturum — cevap O oturuma `slash.exec` ile gider. */
    val sessionId: String?,
    /** Hangi sohbet/akış — kartta görünür (ham id değil, konu). */
    val sessionTitle: String,
    /** Gateway `prompt_id` — `approval.settled` ile çapraz kanal eşleşmesi. */
    val promptId: String? = null,
    /** Ne istiyor (`description` yoksa olay metni). */
    val request: String,
    /** Hangi araç/komut — payload `command` alanı (birinci sözcük). */
    val tool: String? = null,
    /** Argüman özeti — `command`'ın kalan kısmı, kırpılmış. */
    val argsSummary: String? = null,
    val isSudo: Boolean = false,
    val createdAt: Long = 0L,
    val decision: ApprovalDecision = ApprovalDecision.Pending,
)

sealed interface ApprovalDecision {
    data object Pending : ApprovalDecision
    data object Approved : ApprovalDecision
    data class Denied(val reason: String = "") : ApprovalDecision

    /** Başka kanaldan (Telegram/TUI) yanıtlandı — `approval.settled`. */
    data object SettledElsewhere : ApprovalDecision
}

/**
 * Onay kaydı — bekleyenler + geçmiş.
 *
 * Bellek içi: süreç kapanınca geçmiş kaybolur (cihazda kalır, diske yazılmaz —
 * gizlilik tercihi). Bekleyen istekler zaten gateway tarafında yaşar; uygulama
 * yeniden açılınca ajan yeniden `approval.request` yayınlar.
 */
object ApprovalCenter {

    /** Geçmişte tutulan en fazla kayıt — eski kararlar budanır. */
    const val HISTORY_LIMIT = 50

    private val _entries = MutableStateFlow<List<ApprovalEntry>>(emptyList())
    val entries: StateFlow<List<ApprovalEntry>> = _entries.asStateFlow()

    private val seq = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * Yeni onay isteği ekler. Aynı `promptId` zaten BEKLEMEDEyse ikinci kez
     * eklenmez (yeniden bağlanma/tekrar teslim aynı kartı şişirmesin).
     */
    fun submit(
        sessionId: String?,
        sessionTitle: String,
        request: String,
        tool: String? = null,
        argsSummary: String? = null,
        isSudo: Boolean = false,
        promptId: String? = null,
        createdAt: Long = 0L,
        id: String? = null,
    ): ApprovalEntry {
        val entry = ApprovalEntry(
            id = id ?: "ap-${seq.incrementAndGet()}",
            sessionId = sessionId,
            sessionTitle = sessionTitle,
            promptId = promptId,
            request = request,
            tool = tool,
            argsSummary = argsSummary,
            isSudo = isSudo,
            createdAt = createdAt,
        )
        synchronized(this) {
            val current = _entries.value
            if (promptId != null && current.any {
                    it.promptId == promptId && it.decision == ApprovalDecision.Pending
                }
            ) {
                return current.first { it.promptId == promptId && it.decision == ApprovalDecision.Pending }
            }
            _entries.value = current + entry
        }
        return entry
    }

    /**
     * Gateway `approval.request`/`sudo.request` olayından kayıt üretir.
     *
     * Payload alanları HERMES gateway'inin gerçek sözleşmesinden
     * (`CanonicalApprovalPrompt`): `description` (ne istiyor), `command`
     * (hangi araç/komut + argümanlar), `prompt_id` (settled eşleşmesi). Alan
     * yoksa uydurulmaz — kartta o kısım görünmez.
     */
    fun submitFromEvent(
        type: String,
        sessionId: String?,
        sessionTitle: String,
        text: String?,
        toolName: String? = null,
        payload: JsonObject? = null,
        createdAt: Long = 0L,
    ): ApprovalEntry {
        fun field(name: String): String? =
            (payload?.get(name) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        val command = field("command")?.trim()
        return submit(
            sessionId = sessionId,
            // D-05: ham süreç içi id etiket OLMAZ — konu yoksa genel ad.
            sessionTitle = sessionTitle.ifBlank { "Sohbet" },
            request = field("description") ?: text ?: "Ajan tehlikeli bir komut için onay istiyor",
            tool = command?.substringBefore(' ')?.takeIf { it.isNotBlank() } ?: toolName,
            argsSummary = command?.substringAfter(' ', "")?.trim()
                ?.take(140)?.takeIf { it.isNotBlank() },
            isSudo = type == "sudo.request",
            promptId = field("prompt_id"),
            createdAt = createdAt,
        )
    }

    /**
     * Karar verir. `true` = komut GÖNDERİLMELİ (kayıt bekliyordu ya da kayıt
     * yok — eski kart yedeği), `false` = zaten yanıtlanmış (çift dokunuş/çift
     * kanal — ikinci kez `/approve` gitmez).
     */
    fun decide(id: String, approve: Boolean, reason: String = ""): Boolean {
        var alreadyDecided = false
        update(id) { entry ->
            if (entry.decision != ApprovalDecision.Pending) {
                alreadyDecided = true
                null
            } else entry.copy(
                decision = if (approve) ApprovalDecision.Approved
                else ApprovalDecision.Denied(reason.trim()),
            )
        }
        return !alreadyDecided
    }

    /** `approval.settled` — başka kanal yanıtladı; bekleyen kayıt kapanır. */
    fun settleByPrompt(promptId: String): Boolean =
        updateMatching({ it.promptId == promptId && it.decision == ApprovalDecision.Pending }) {
            it.copy(decision = ApprovalDecision.SettledElsewhere)
        } != null

    private fun update(id: String, change: (ApprovalEntry) -> ApprovalEntry?): ApprovalEntry? =
        updateMatching({ it.id == id }, change)

    private inline fun updateMatching(
        match: (ApprovalEntry) -> Boolean,
        change: (ApprovalEntry) -> ApprovalEntry?,
    ): ApprovalEntry? {
        synchronized(this) {
            var changed: ApprovalEntry? = null
            val next = _entries.value.map { e ->
                if (!match(e)) e
                else change(e)?.also { changed = it } ?: e
            }
            if (changed != null) _entries.value = next
            return changed
        }
    }

    fun pending(): List<ApprovalEntry> = _entries.value.filter { it.decision == ApprovalDecision.Pending }

    fun history(): List<ApprovalEntry> = _entries.value.filter { it.decision != ApprovalDecision.Pending }

    /** Kararlı eski kayıtları budar — liste sınırsız büyümesin. */
    fun pruneHistory() {
        synchronized(this) {
            val decided = _entries.value.filter { it.decision != ApprovalDecision.Pending }
            val keep = decided.takeLast(HISTORY_LIMIT).toSet()
            _entries.value = _entries.value.filter { it.decision == ApprovalDecision.Pending || it in keep }
        }
    }

    /** Yalnız testler — kaydı sıfırlar. */
    fun reset() {
        synchronized(this) {
            _entries.value = emptyList()
        }
    }

    /**
     * Cevap komutu — sohbet kartının `/approve` / `/deny [sebep]` diliyle AYNI
     * (tek kaynak: merkezi kutu ile sohbet kartı farklı komut üretemez).
     */
    fun slashCommand(approve: Boolean, reason: String = ""): String = when {
        approve -> "/approve"
        reason.isBlank() -> "/deny"
        else -> "/deny ${reason.trim()}"
    }
}

/**
 * \"Bazı eylemler durmaz\" — guardReason deseni (bkz. [FullControl.guardReason]):
 * tek fonksiyon, net sebep. Onay kutusu yalnız DURDURULAN eylemleri gösterir;
 * bu yüzden hangi sınıfların onaysız devam ettiği AÇIKÇA yazılır (MausBot'un
 * \"approval coverage garanti değildir\" uyarısının karşılığı).
 */
object ApprovalGuard {

    const val WHY_SUDO = "yükseltilmiş izin (sudo) — onay durdurur"
    const val WHY_CHANGES = "cihazı değiştiren eylem — onay durdurur"
    const val WHY_UNKNOWN = "bilinmeyen araç — temkin gereği durur"
    const val NO_STOP_READ_ONLY = "salt-okunur araç — onaysız devam eder"

    /**
     * Eylem onayda DURUR mu? `null` = durmaz (onaysız devam).
     *
     * Sıra [FullControl.guardReason] ile aynı dilde: önce salt-okunur sınıf,
     * sonra sudo, sonra değiştirici/bilinmeyen. Bilinmeyen araç DURUR —
     * fail-closed (temkinli varsayılan).
     */
    fun stopReason(tool: String?, isSudo: Boolean = false): String? = when {
        tool != null && tool in FullControl.READ_TOOLS -> null
        isSudo -> WHY_SUDO
        tool != null && tool in FullControl.WRITE_TOOLS -> WHY_CHANGES
        else -> WHY_UNKNOWN
    }

    /** Onaysız devam eden eylem sınıfları — kutuda görünür uyarı listesi. */
    data class PassThrough(val title: String, val detail: String)

    fun passThroughClasses(): List<PassThrough> = listOf(
        PassThrough(
            "Okuma / salt-okunur",
            "${FullControl.READ_TOOLS.sorted().joinToString(", ")} — ekran okuma, " +
                "ekran görüntüsü, uygulama listesi ($NO_STOP_READ_ONLY)",
        ),
        PassThrough(
            "Bilgi soruları",
            "clarify / secret istekleri onay değil sorudur; ajan yanıt bekler ama " +
                "riskli eylem durdurmaz",
        ),
        PassThrough(
            "Telefon araçları",
            "Köprü (BridgePolicy) ve tam kontrol anahtarları ayrıca [FullControl.guardReason] " +
                "ile denetlenir — onay kutusu bunların yerine geçmez",
        ),
    )
}

/**
 * Sesli onay girişi — \"onayla\" / \"reddet [sebep]\" (MausBot sesli onay deseni,
 * fikir). Sözcük eşleme saf fonksiyondur; mikrofonu Android tarafı yönetir
 * (sistemin konuşma tanıma penceresi), burada yalnız duyulan metin çözülür.
 */
object ApprovalVoice {

    /**
     * @param entryIndex 0 tabanlı hedef istek (sesli \"2\" → index 1)
     * @param approve true = onayla, false = reddet
     * @param reason yalnız reddetmede; onayda boş
     */
    data class Command(val entryIndex: Int, val approve: Boolean, val reason: String)

    private val APPROVE_WORDS = setOf(
        "onayla", "onaylarim", "onayliyorum", "onayver", "onay",
        "approve", "yes", "evet", "tamam", "olur",
    )
    private val DENY_WORDS = setOf(
        "reddet", "reddediyorum", "red", "reject", "deny",
        "hayir", "iptal", "olmaz", "vazgec",
        "onaylama", "onaylamam", "onaylamayorum", "onaylamadim",
    )
    private val INDEX_ORDINALS = mapOf(
        "birinci" to 1, "ikinci" to 2, "ucuncu" to 3, "dorduncu" to 4,
        "besinci" to 5, "altinci" to 6, "yedinci" to 7, "sekizinci" to 8, "dokuzuncu" to 9,
    )
    private val FILLER = setOf("sebep", "neden", "nedeni", "cunku", "diye", "olarak", "icin", "lutfen")

    /**
     * Duyulan metni onay komutuna çevirir. Anlamsız/çelişkili girdi → `null`
     * (sesli girdi ASLA tahminle uygulanmaz).
     *
     * Biçim: `[sıra] onayla|reddet [sıra] [sebep]` — sıra 1 tabanlı ("2" ya da
     * "ikinci"), boşsa en eski bekleyen istek hedeflenir.
     */
    fun parse(heard: String, pendingCount: Int = Int.MAX_VALUE): Command? {
        val words = normalize(heard)
        if (words.isEmpty()) return null

        val approveAt = words.indexOfFirst { it in APPROVE_WORDS }
        val denyAt = words.indexOfFirst { it in DENY_WORDS }
        // İki niyet birden duyulduysa YORUMLANMAZ — kullanıcı ne dediğine emin değil.
        if (approveAt >= 0 && denyAt >= 0) return null
        val intentAt = maxOf(approveAt, denyAt)
        if (intentAt < 0) return null
        val approve = approveAt >= 0

        // Sıra numarası: "2" gibi rakam ya da "ikinci" gibi sıra sıfatı (1 tabanlı).
        // Yoksa hedef en eski bekleyen istektir (index 0).
        var index: Int? = null
        for ((i, w) in words.withIndex()) {
            if (i == intentAt || index != null) continue
            val ord = INDEX_ORDINALS[w]
            val num = w.toIntOrNull()?.takeIf { it in 0..99 }
            if (ord != null || num != null) index = ord ?: num!!
        }
        val target = if (index == null) 0 else index!! - 1
        if (target < 0 || target >= pendingCount) return null

        // Sebep: niyet sözcüğünden SONRA kalan anlamlı kelimeler (yalnız reddetmede).
        val reason = if (approve) "" else words.drop(intentAt + 1)
            .filter { it !in FILLER && it.toIntOrNull() == null && it !in INDEX_ORDINALS }
            .joinToString(" ")
            .take(80)
        return Command(target, approve, reason)
    }

    /**
     * Türkçe büyük/küçük harf tuzağı (`İ` → `i̇` değil, doğrudan `i`) + aksan
     * katlama: konuşma tanıma "hayır"/"hayir" ikisini de verebilir.
     */
    internal fun normalize(text: String): List<String> = text
        .replace('İ', 'i').replace('I', 'i').replace('ı', 'i')
        .replace('Ş', 's').replace('ş', 's')
        .replace('Ğ', 'g').replace('ğ', 'g')
        .replace('Ü', 'u').replace('ü', 'u')
        .replace('Ö', 'o').replace('ö', 'o')
        .replace('Ç', 'c').replace('ç', 'c')
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
}