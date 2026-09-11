package com.hermes.mobile.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Kayıtlı prompt — kısa etiket + tam metin.
 *
 * Etiket yalnız listeyi taranabilir tutmak için; asıl yük metinde. Etiket
 * boşsa ilk satırdan türetilir (kullanıcı "Kaydet"e basıp geçebilsin).
 */
@kotlinx.serialization.Serializable
data class SavedPrompt(
    val id: String,
    val label: String,
    val text: String,
)

@kotlinx.serialization.Serializable
private data class SavedPromptList(val prompts: List<SavedPrompt> = emptyList())

/**
 * Kayıtlı promptlar — kullanıcıdan gelen, tekrar tekrar kullanılacak istekler.
 *
 * Neden Room değil: SessionFlagsStore'daki aynı gerekçe — tek bir JSON yığını,
 * sorgu yok, ilişki yok. Neden sunucuda değil: relay'de prompt mutation ucu
 * yok; cihazda yaşamak, çevrimdışı da çalışmak demek.
 *
 * Yazmalar senkron ve kilitli: liste küçük, çağıran katman (AppViewModel)
 * main'den çağırıyor; bir dosya yazımı mikro-saniyeler sürer. Bozuk dosya
 * yüklemeyi kırmaz — [yukle] boş listeye düşer, sonraki [kaydet] yığını
 * yeniden kurar (SessionFlagsStore'un en iyi çaba ilkesi).
 */
class SavedPromptsStore(private val dir: File) {

    constructor(context: Context) : this(File(context.filesDir, "cache").apply { mkdirs() })

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private val lock = Any()

    /** Testin de bildiği sabit ad — bozuk dosya senaryosu buna yazar. */
    private val file = File(dir, "saved-prompts.json")

    /** Yığını olduğu sırayla döndürür (ekleme sırası; en eski üstte). */
    fun yukle(): List<SavedPrompt> = synchronized(lock) { read() }

    /** Yeni kayıt ekler ve eklenen kaydı döndürür. Etiket boşsa metinden türetilir. */
    fun kaydet(etiket: String, metin: String): SavedPrompt = synchronized(lock) {
        val items = read()
        val body = metin.trim()
        val prompt = SavedPrompt(
            id = UUID.randomUUID().toString(),
            label = etiket.trim().ifBlank { baslikEt(body) },
            text = body,
        )
        write(items + prompt)
        prompt
    }

    /** Kayıt yoksa sessiz geçerr (yarış: bir satır silinmiş olabilir). */
    fun guncelle(id: String, etiket: String, metin: String) = synchronized(lock) {
        val items = read()
        if (items.none { it.id == id }) return@synchronized
        val body = metin.trim()
        write(
            items.map {
                if (it.id == id) it.copy(label = etiket.trim().ifBlank { baslikEt(body) }, text = body)
                else it
            }
        )
    }

    fun sil(id: String) = synchronized(lock) {
        val items = read()
        if (items.none { it.id == id }) return@synchronized
        write(items.filterNot { it.id == id })
    }

    /** Kilitle korunan her yerden çağrılır; dosya yoksa/bozuksa boş liste. */
    private fun read(): List<SavedPrompt> = runCatching {
        if (!file.exists()) return@runCatching emptyList()
        json.decodeFromString(SavedPromptList.serializer(), file.readText()).prompts
    }.getOrDefault(emptyList())

    private fun write(items: List<SavedPrompt>) {
        runCatching {
            file.writeText(json.encodeToString(SavedPromptList.serializer(), SavedPromptList(items)))
        }
    }

    /** Etiket boşsa: ilk satır, en fazla 32 karakter. */
    private fun baslikEt(metin: String): String =
        metin.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            .take(32)
}
