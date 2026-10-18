package com.hermes.mobile.data

import android.content.Context
import android.net.Uri
import com.hermes.mobile.ui.theme.HermesPalette
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * K22 — kişisel veri yedekleme (export/import).
 *
 * TEK JSON dosyasında: bot tanımları (BotRoster), kayıtlı prompt'lar
 * (SavedPrompts), profil/bağlantı ayarları (ServerProfile), oturum
 * bayrakları (hidden/archived/pinned/renames/followed) ve kullanıcı
 * tercihleri (AppSettings) + özel temalar.
 *
 * SIR KURALI: gateway token'ları (ServerProfile.token, geminiApiKey) ve
 * AppSettings.emaToken YEDEKLENMEZ — [YedekMantik.gizsizlestir] export
 * öncesi bunları boşaltır ve dosya `gizlilikNotu` ile işaretlenir. Geri
 * yükleme sonrası token'lar kullanıcı tarafından yeniden girilir. Gerekçe
 * (README'de de yazılır): yedek dosyası Documents/Drive gibi sırsız
 * yerlere gider; sırrı taşıyan dosya sırsız yerde yaşayamaz.
 */
const val YEDEK_SCHEMA_VERSION = 1

/** Profil kimliğine bağlı oturum bayrakları (SessionFlagsStore kalıbı). */
@Serializable
data class ProfilYedekBayraklari(
    val profileId: String,
    val flags: SessionFlags,
)

/**
 * Yedek dosyasının kendisi. `schemaVersion` ZORUNLU alan — eksik/uyumsuz
 * dosya parse'ta reddedilir (bozuk dosyada çökme yok, anlaşılır hata var).
 */
@Serializable
data class YedekDosya(
    val schemaVersion: Int,
    val exportedAt: Long = 0L,
    val gizlilikNotu: String = "token: yok — geri yükleme sonrası yeniden girilir",
    val botlar: List<BotCard> = emptyList(),
    val promptlar: List<SavedPrompt> = emptyList(),
    val profiller: List<ServerProfile> = emptyList(),
    val aktifProfilId: String? = null,
    val oturumBayraklari: List<ProfilYedekBayraklari> = emptyList(),
    val ayarlar: AppSettings = AppSettings(),
    val ozelTemalar: List<HermesPalette> = emptyList(),
)

sealed interface YedekOkuma {
    data class Tamam(val dosya: YedekDosya) : YedekOkuma
    data class Hata(val mesaj: String) : YedekOkuma
}

/** İç aktarım önizleme sayıları — UI yerelleştirilmiş metne çevirir. */
data class YedekSayim(
    val botSayisi: Int,
    val promptSayisi: Int,
    val profilSayisi: Int,
    val bayrakKumesiSayisi: Int,
    val temaSayisi: Int,
)

/** Yedek iç aktarım çakışma politikası — kullanıcı Ayarlar'dan seçer. */
enum class YedekModu { Birlestir, UzerineYaz }

/**
 * Yedekleme saf mantığı — UI'dan ve Android'den bağımsız, JVM'de test
 * edilir (BotRosterLogic / SessionFlagsStore kalıbı: kural testle kilitlenir).
 */
object YedekMantik {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Sırları boşaltır: profil token'ları, Gemini anahtarı ve EMA token'ı.
     * Export SERİALİZASYONUNDAN ÖNCE çağrılır — gizsizleşmemiş YedekDosya
     * hiçbir yola yazılamaz (fail-closed; test bunu kilitler).
     */
    fun gizsizlestir(d: YedekDosya): YedekDosya = d.copy(
        profiller = d.profiller.map { it.copy(token = "", geminiApiKey = "") },
        ayarlar = d.ayarlar.copy(emaToken = ""),
    )

    /**
     * BİRLEŞTİR: cihazdaki kayıtlar korunur; yedekte olup cihazda olmayanlar
     * (id bazında) eklenir. Aynı id'de cihazdaki kazanır — özellikle
     * profillerde cihazdaki token'ın bozulmaması için. Bayraklar küme
     * birleşimi; yeniden adlandırmada cihazdaki kazanır. Ayarlar tek
     * nesne olduğundan yedekteki tercihler uygulanır, yalnız cihazdaki
     * EMA token'ı korunur; cihazın aktif profili değişmez.
     */
    fun birlestir(mevcut: YedekDosya, yedek: YedekDosya): YedekDosya {
        val botlar = mevcut.botlar + yedek.botlar.filter { y -> mevcut.botlar.none { it.id == y.id } }
        val promptlar = mevcut.promptlar + yedek.promptlar.filter { y -> mevcut.promptlar.none { it.id == y.id } }
        val temalar = mevcut.ozelTemalar + yedek.ozelTemalar.filter { y -> mevcut.ozelTemalar.none { it.id == y.id } }
        val profiller = mevcut.profiller + yedek.profiller.filter { y -> mevcut.profiller.none { it.id == y.id } }
        val pidler = mevcut.oturumBayraklari.map { it.profileId }.toSet() +
            yedek.oturumBayraklari.map { it.profileId }.toSet()
        val bayraklar = pidler.map { pid ->
            val m = mevcut.oturumBayraklari.firstOrNull { it.profileId == pid }?.flags ?: SessionFlags()
            val y = yedek.oturumBayraklari.firstOrNull { it.profileId == pid }?.flags ?: SessionFlags()
            ProfilYedekBayraklari(
                profileId = pid,
                flags = SessionFlags(
                    pinned = m.pinned + y.pinned,
                    archived = m.archived + y.archived,
                    hidden = m.hidden + y.hidden,
                    renames = y.renames + m.renames,
                    followed = m.followed + y.followed,
                ),
            )
        }
        return YedekDosya(
            schemaVersion = YEDEK_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            botlar = botlar,
            promptlar = promptlar,
            profiller = profiller,
            aktifProfilId = mevcut.aktifProfilId,
            oturumBayraklari = bayraklar,
            ayarlar = yedek.ayarlar.copy(emaToken = mevcut.ayarlar.emaToken),
            ozelTemalar = temalar,
        )
    }

    /**
     * ÜZERİNE YAZ: yedek, cihazdaki karşılıklarını bütünüyle değiştirir.
     * Yedek sırsız olduğundan profil token'ları ve EMA token'ı boş gelir —
     * kullanıcı geri yükleme sonrası yeniden girer.
     */
    fun uzerineYaz(mevcut: YedekDosya, yedek: YedekDosya): YedekDosya = yedek.copy(
        schemaVersion = YEDEK_SCHEMA_VERSION,
        exportedAt = System.currentTimeMillis(),
        profiller = yedek.profiller.map { it.copy(token = "", geminiApiKey = "") },
        ayarlar = yedek.ayarlar.copy(emaToken = ""),
    )

    /**
     * Yedeği gizsizleştirip JSON metnine çevirir. Bu fonksiyon export'un
     * TEK yoludur: gizsizleşmemiş YedekDosya bu kapıdan geçmeden dosyaya
     * yazılamaz (fail-closed — YedekMantikTest kilitler).
     */
    fun serialize(d: YedekDosya): String = json.encodeToString(gizsizlestir(d))

    fun sayim(d: YedekDosya): YedekSayim = YedekSayim(
        botSayisi = d.botlar.size,
        promptSayisi = d.promptlar.size,
        profilSayisi = d.profiller.size,
        bayrakKumesiSayisi = d.oturumBayraklari.size,
        temaSayisi = d.ozelTemalar.size,
    )

    /**
     * Yedek metnini okur. Bozuk/eksik/yeni-sürümlü dosyada HATA döner —
     * asla istisna kaçırmaz, asla varsayılanla geçiştirmez (fail-closed).
     */
    fun parse(text: String): YedekOkuma {
        val temiz = text.trim()
        if (temiz.isEmpty()) return YedekOkuma.Hata("Yedek dosyası boş")
        val d = try {
            json.decodeFromString(YedekDosya.serializer(), temiz)
        } catch (e: kotlinx.serialization.SerializationException) {
            return YedekOkuma.Hata("Yedek dosyası okunamadı: geçersiz ya da bozuk JSON")
        } catch (e: IllegalArgumentException) {
            return YedekOkuma.Hata("Yedek dosyası okunamadı: geçersiz ya da bozuk JSON")
        }
        if (d.schemaVersion > YEDEK_SCHEMA_VERSION) {
            return YedekOkuma.Hata(
                "Bu yedek daha yeni bir sürümde (v${d.schemaVersion}) oluşturuldu; " +
                    "uygulamayı güncelleyin",
            )
        }
        if (d.schemaVersion < 1) {
            return YedekOkuma.Hata("Yedek dosyasında geçersiz sürüm alanı (v${d.schemaVersion})")
        }
        return YedekOkuma.Tamam(d)
    }
}

/**
 * Yedek dosyası G/Ç'si — SAF uri'lerine yazar/okur. Serileştirme BURADA
 * gerçekleşir ve her yoldan ÖNCE [YedekMantik.gizsizlestir] geçer.
 */
class YedekStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    /** Gizsizleştirilmiş yedeği JSON metnine çevirir — tek kapı [YedekMantik.serialize]. */
    fun serialize(d: YedekDosya): String = YedekMantik.serialize(d)

    /** Yedeği SAF uri'sine yazar. Başarısızlık Result ile döner — çökme yok. */
    fun export(uri: Uri, d: YedekDosya): Result<Unit> = runCatching {
        val out = context.contentResolver.openOutputStream(uri, "wt")
            ?: throw IllegalStateException("Dosya açılamadı")
        out.use { it.write(serialize(d).toByteArray(Charsets.UTF_8)) }
    }

    /** Yedek dosyasını SAF uri'sinden metin olarak okur. */
    fun importText(uri: Uri): Result<String> = runCatching {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Dosya okunamadı")
        input.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
