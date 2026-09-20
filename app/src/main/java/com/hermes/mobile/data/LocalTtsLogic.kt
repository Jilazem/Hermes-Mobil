package com.hermes.mobile.data

import java.io.File
import java.io.IOException

/**
 * Tur-21 yerel (çevrimdışı) TTS karar katmanı — **saf** (Android yok, JVM testli).
 *
 * Gizlilik kuralı (tur-21 spec): VARSAYILAN ses YEREL — telefon metni buluta
 * gitmez. Bulut motorları (kahya/kadin/chatterbox) ayardan seçilebilir kalır.
 *
 * Model kararı (araştırma + ölçüm, 20.09.2026 — kanıt RAPOR §2):
 *  - Görev metnindeki "tr_TR-fahriyye-medium" ve "tr_TR-faruk-medium" Piper
 *    ses depolarında YOK (rhasspy/piper-voices tam liste + HF arama + 404).
 *    Türkçe'de yalnız `dfki`, `fahrettin`, `fettah` var.
 *  - KADIN seçimi f0 ölçümüyle: fettah 175.8–191.7 Hz (bilinen kadın
 *    referansı irina 168.3 Hz bandında, erkek dfki 98.0 Hz) → fettah.
 *    Kulak doğrulaması emülatör kaydında yapılır; adı listeye ayrıca yazılır.
 *  - Model APK'ya GÖMÜLMEZ (~63MB): ilk kullanımda indirilir, her dosya
 *    SHA256 ile doğrulanır, Ayarlar'dan silinebilir (spec kararı).
 *
 * Motor: sherpa-onnx 1.13.8 (Apache-2.0; app/libs AAR). Piper-vits modeli
 * espeak-ng-data ister; 18MB tam dizin yerine 7 dosyalık minimal TR kümesi
 * yeterlidir — Mac'te sherpa-onnx dry-run ile birebir ses üretildi
 * (39936 örnek @ 22050 Hz, peak 0.397, tam küme ile aynı).
 */
object LocalTtsLogic {

    /** İndirme kökü — k2fsa HF aynası (vits-piper-tr_TR-fettah-medium, main). */
    private const val HF_BASE =
        "https://huggingface.co/csukuangfj/vits-piper-tr_TR-fettah-medium/resolve/main"

    /** Model dosyalarının uygulamadaki alt klasörü (filesDir altında). */
    const val DIR_NAME = "local-tts/tr-fettah"

    /** İndirilen tek dosya tanımı: göreli yol + URL + sha256 + beklenen boyut. */
    data class ModelFile(val relPath: String, val url: String, val sha256: String, val bytes: Long)

    /**
     * İndirme listesi (toplam 63.4 MB).
     *
     * SHA256'lar ÇİFT kaynakla doğrulandı: resmî
     * `vits-piper-tr_TR-fettah-medium.tar.bz2` açılımı == HF tekil indirme
     * (hash'ler birebir aynı; RAPOR §2 tablosu).
     */
    val FILES: List<ModelFile> = listOf(
        ModelFile(
            "tr_TR-fettah-medium.onnx",
            "$HF_BASE/tr_TR-fettah-medium.onnx",
            "31677a00f7927c94a279d210efd11771b9740df24e73c5ebd7a387875b52b2f0",
            63_201_422L,
        ),
        ModelFile(
            "tr_TR-fettah-medium.onnx.json",
            "$HF_BASE/tr_TR-fettah-medium.onnx.json",
            "0738ecd770d4102fa967a784ae89f64d846b1b5f104076e410c978fefa70b3e7",
            4_877L,
        ),
        ModelFile(
            "tokens.txt",
            "$HF_BASE/tokens.txt",
            "87c8ef66eae5473ed0cc0366b3964c736ca6c5f676c979522ea31234e47430b9",
            921L,
        ),
        // ── Minimal espeak-ng-data (TR sentezi için kanıtlı asgari küme) ──
        ModelFile(
            "espeak-ng-data/phondata",
            "$HF_BASE/espeak-ng-data/phondata",
            "4e0288957874029a8c3c9f41a8f517ad4bf18127046decbdd4b9d1d6807ce3a3",
            550_424L,
        ),
        ModelFile(
            "espeak-ng-data/phonindex",
            "$HF_BASE/espeak-ng-data/phonindex",
            "3ca7b8fa3b42624e4b0f152707e7a39245fce569aa99ea47c055d9e622fcf0c4",
            39_074L,
        ),
        ModelFile(
            "espeak-ng-data/phontab",
            "$HF_BASE/espeak-ng-data/phontab",
            "886f3fa402cb0ba73d483aa8ad000af47a6b7cc06293c75a97913fba68a530f6",
            55_796L,
        ),
        ModelFile(
            "espeak-ng-data/lang/trk/tr",
            "$HF_BASE/espeak-ng-data/lang/trk/tr",
            "490eb5a2f777a2f3396f3a824ed8021bd77a94be801214411461bde6689738c9",
            25L,
        ),
        ModelFile(
            "espeak-ng-data/tr_dict",
            "$HF_BASE/espeak-ng-data/tr_dict",
            "311b0557059f2dab5ee2faabb51361f5033368f0e2a54c4bdfe45e730674f26e",
            46_793L,
        ),
        ModelFile(
            "espeak-ng-data/intonations",
            "$HF_BASE/espeak-ng-data/intonations",
            "3f8af65fd3eda9759a10f021d61361c120871f463515229c925995c7f90918cc",
            2_040L,
        ),
        ModelFile(
            "espeak-ng-data/phondata-manifest",
            "$HF_BASE/espeak-ng-data/phondata-manifest",
            "7b387af0702c7cf0b61f0bead68feded0bd8e1620729b0b252e76acbc30d3813",
            21_821L,
        ),
    )

    /** Toplam indirilecek bayt (ilerleme paydası). */
    val TOTAL_BYTES: Long = FILES.sumOf { it.bytes }

    /** Model dizininin tam yolu (filesDir = uygulamanın kalıcı dosya alanı). */
    fun modelDir(filesDir: File): File = File(filesDir, DIR_NAME)

    /** Dosya listesi tam yollarla (durum/dogrulama için). */
    fun fileList(dir: File): List<File> = FILES.map { File(dir, it.relPath) }

    /** Dosyalar DISKTE var mı (boyutu > 0). Hash doğrulaması ayrı adımdır. */
    fun filesPresent(dir: File): Boolean = fileList(dir).all { it.exists() && it.length() > 0 }

    /**
     * Diskteki dosyaların sha256'sını beklenenle karşılaştırır.
     *
     * @return ilk uyuşmayan dosyanın göreli yolu; hepsi tutarsa null.
     *   (Okuma hatası da uyuşmama sayılır — fail-closed.)
     */
    fun firstShaMismatch(dir: File, shaOf: (File) -> String?): String? {
        for (f in FILES) {
            val file = File(dir, f.relPath)
            if (!file.exists()) return f.relPath
            if (shaOf(file) != f.sha256) return f.relPath
        }
        return null
    }

    /**
     * Silme — YALNIZ model alt ağacı; başka hiçbir yere dokunmaz.
     * @return silinen dosya sayısı (0 = zaten yoktu)
     */
    fun deleteModel(dir: File): Int {
        if (!dir.exists()) return 0
        val n = dir.walkBottomUp().count { it.isFile }
        dir.deleteRecursively()
        return n
    }

    // ── Durum makinesi (saf; indirme/çalıştırma Android portunda) ────────

    enum class Phase { NotInstalled, Downloading, Ready, Failed }

    data class State(
        val phase: Phase = Phase.NotInstalled,
        val doneBytes: Long = 0L,
        val currentFile: String = "",
        val message: String = "",
    )

    /** Yüzde — 0..99 tavanlı; 100 yalnız Ready fazında anlam taşır. */
    fun percent(doneBytes: Long): Int = when {
        doneBytes <= 0L || TOTAL_BYTES <= 0L -> 0
        else -> ((doneBytes * 100) / TOTAL_BYTES).toInt().coerceIn(0, 99)
    }

    /** İlerleme tikleri — dosya bittikçe doneBytes büyür, dosya adı taşınır. */
    fun progress(doneBytes: Long, currentFile: String): State =
        State(Phase.Downloading, doneBytes = doneBytes.coerceAtLeast(0L), currentFile = currentFile)

    fun downloaded(): State = State(Phase.Ready, doneBytes = TOTAL_BYTES)

    fun failed(message: String): State = State(Phase.Failed, message = message)

    /**
     * Ekran açılış durumu: disk kontrolünden sonra hangi satır görünecek.
     * Model diskte VE hash'leri doğruysa Ready; eksikse NotInstalled.
     */
    fun startState(installedOk: Boolean, lastMessage: String = ""): State =
        if (installedOk) State(Phase.Ready, doneBytes = TOTAL_BYTES, message = lastMessage)
        else State(Phase.NotInstalled, message = lastMessage)

    /** Kullanıcıya gösterilen durum satırı (Ayarlar kartı). */
    fun statusLine(state: State, t: (String, String) -> String): String = when (state.phase) {
        Phase.Ready -> t("Yerel kadın ses hazır ✓", "Local female voice ready ✓")
        Phase.NotInstalled -> t(
            "Yerel kadın ses ~63 MB — indirince telefondan çıkmaz",
            "Local female voice ~63 MB — stays on the phone once downloaded",
        )
        Phase.Downloading -> t(
            "İndiriliyor · %%%d · ${state.currentFile}",
            "Downloading · %%%d · ${state.currentFile}",
        ).format(percent(state.doneBytes))
        Phase.Failed -> state.message.ifBlank {
            t("Yerel ses indirilemedi", "Local voice download failed")
        }
    }

    // ── Seslendirme kararı (tur-21 fallback zinciri) ────────────────────

    /**
     * Seslendirme karar sonucu: hangi motor üretecek, düşüş oldu mu, hata ne.
     */
    data class SpeakDecision(val useLocal: Boolean, val fellBack: Boolean, val error: String?)

    /**
     * Saf karar (spec 1):
     *  - YEREL seçili + model hazır → yerel üret.
     *  - YEREL seçili + model yok → HATA göster; sessiz buluta geçiş YOK
     *    (gizlilik kuralı: kullanıcı istemeden metin buluta gidemez).
     *  - Bulut motoru seçili ve sentez patladı → model hazırsa YEREL'e düş
     *    ("yerel, TTS hatasına düşüş olarak DA eklenir"), değilse hata kalır.
     *
     * @param engine Ayarlardaki motor tercihi
     * @param localReady model diskte + hash'ler doğru
     * @param cloudError bulut denemesinin hatası (ilk denemede null)
     */
    fun decideSpeak(
        engine: VoiceSpeakLogic.Engine,
        localReady: Boolean,
        cloudError: Throwable? = null,
        t: (String, String) -> String,
    ): SpeakDecision {
        val wantLocal = engine == VoiceSpeakLogic.Engine.YEREL
        return when {
            wantLocal && localReady -> SpeakDecision(useLocal = true, fellBack = false, error = null)
            wantLocal -> SpeakDecision(
                useLocal = false,
                fellBack = false,
                error = t(
                    "Yerel ses indirilmemiş — Ayarlar → Sesli mesaj'dan indir " +
                        "(sessiz bulut geçişi yok)",
                    "Local voice not downloaded — download it under Settings → Voice " +
                        "(there is no silent cloud fallback)",
                ),
            )
            cloudError != null && localReady ->
                SpeakDecision(useLocal = true, fellBack = true, error = null)
            cloudError != null -> SpeakDecision(false, false, cloudError.message)
            else -> SpeakDecision(false, false, null)
        }
    }

    /** Motor listesindeki etiket (yerel seçenek). */
    fun engineLabel(t: (String, String) -> String): String =
        t("Yerel kadın (piper · fettah)", "Local female (piper · fettah)")

    /** Motor açıklama satırı (Ayarlar alt yazısı). */
    fun engineHint(t: (String, String) -> String): String = t(
        "Telefondan çıkmaz · ~63 MB · Piper tr_TR-fettah-medium (CC0) · motor Apache-2.0",
        "Never leaves the phone · ~63 MB · Piper tr_TR-fettah-medium (CC0) · engine Apache-2.0",
    )

    /** SHA tutmazsa kullanıcı satırı (fail-closed: bozuk dosya silinir, tekrar denenir). */
    fun shaMismatchMessage(path: String, expected: String, actual: String?): String =
        "SHA256 uyuşmadı: $path — beklenen ${expected.take(12)}…${
            if (actual == null) " (okunamadı)" else " gelen ${actual.take(12)}…"
        }"

    @Throws(IOException::class)
    fun requireSha(expected: String, actual: String?, path: String) {
        if (expected != actual) throw IOException(shaMismatchMessage(path, expected, actual))
    }
}
