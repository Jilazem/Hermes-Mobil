package com.hermes.mobile.data

/**
 * Paylaşım akışının YÜKLEME KARARI ve VEKİL EL SIKIŞMASI sözleşmesi (saf).
 *
 * Kod denetimi HIGH-2: hedef kararı (resolveShareTarget) saf test edilirken
 * "dosya gerçekten yüklenecek mi, hangi yoldan, temizlik kimin işi" kararı
 * UI içine dağılmıştı; bu yüzden HIGH-1 (dosya sessizce kayboluyor) yeşil
 * testlerin altında yaşadı. Bu dosya o boşluğu kapatır: niyet → yükleme
 * planı kararı burada, UI/ViewModel yalnız planı UYGULAR.
 *
 * Akış sözleşmesi (HIGH-1 teli):
 *  1. ShareProxyActivity EXTRA_STREAM'ı cacheDir/share_inbox'a AKIŞ kopyalar
 *     (büyük dosyada readBytes OOM olmasın — denetmen önerisi #5),
 *  2. kopyanın mutlak yolunu EXTRA_STAGED_FILE ile MainActivity'e taşır,
 *  3. kullanıcı hedefi seçince consumePendingShare planı uygular:
 *     Upload → attachShareFile → HermesClient.uploadManagedFile(File) →
 *     POST /api/files/upload-stream → staging kopyası SİLİNİR,
 *     Unreadable → staging temizliği + DiagLog uyarısı (sessiz kayıp yok),
 *     None → dosya yok; elle temizlense de plan çift emniyet taşır.
 */
sealed interface ShareUploadPlan {
    /** Dosya parçası yok (ya da boş paylaşım): yüklenecek bir şey yok. */
    data object None : ShareUploadPlan

    /**
     * Kopya staging'de okunabilir durumda: seçilen hedefte gerçek yükleme
     * yapılacak. [name] sunucuya gidecek dosya adı, [sizeBytes] boyut ipucu
     * (DiagLog izi için).
     */
    data class Upload(
        val stagedPath: String,
        val name: String,
        val sizeBytes: Long,
    ) : ShareUploadPlan

    /**
     * Dosya vardı ama okunamadı (içerik sağlayıcı kapandı / kopya bozuk):
     * YÜKLEME YOK — ama sessiz kayıp da yok; çağıran DiagLog'a yazar ve
     * [cleanupPaths]'i siler. [stagedPaths] silinecek yarım kopyalar.
     */
    data class Unreadable(
        val name: String,
        val stagedPaths: List<String> = emptyList(),
    ) : ShareUploadPlan
}

/** Planın silmesi gereken staging yolları (cache birikmesin — denetmen #1). */
fun ShareUploadPlan.cleanupPaths(): List<String> = when (this) {
    is ShareUploadPlan.Upload -> listOf(stagedPath)
    is ShareUploadPlan.Unreadable -> stagedPaths
    ShareUploadPlan.None -> emptyList()
}

/**
 * Yükleme kararını üretir (saf, JVM'de test edilir).
 *
 * @param stagedPath  vekilin staging kopyasının yolu; null = kopya yok
 * @param fileNote    "ad (boyut)" etiketi; null/boş = dosya paylaşımı yok
 * @param readable    kopya gerçekten açılabilir mi (vekilde doğrulanır)
 */
fun planShareUpload(
    stagedPath: String?,
    fileNote: String?,
    readable: Boolean = true,
): ShareUploadPlan {
    val hasFile = !fileNote.isNullOrBlank()
    if (!hasFile) return ShareUploadPlan.None
    val name = fileNote.substringBeforeLast(" (").ifBlank { "paylasilan-dosya" }
    if (stagedPath != null && readable) {
        return ShareUploadPlan.Upload(stagedPath = stagedPath, name = name, sizeBytes = -1)
    }
    return ShareUploadPlan.Unreadable(name, stagedPaths = listOfNotNull(stagedPath))
}

/** Vekil → MainActivity niyet el sıkışması: ekstrap anahtarlarının TEK kaynağı. */
object ShareHandoff {
    const val EXTRA_IS_SHARE = "hermes_is_share"
    const val EXTRA_SHARED_TEXT = "hermes_shared_text"
    const val EXTRA_SHARED_FILE = "hermes_shared_file"
    const val EXTRA_SHARE_TOKEN = "hermes_share_token"
    const val EXTRA_STAGED_FILE = "hermes_staged_file"

    /**
     * Handoff ancak vekilin ürettiği nonce token ile kabul edilir. Vekil
     * her seferinde rastgele token basar; dış uygulama token'ı bilmez — bu
     * olmadan dışarıdan taslağa metin enjeksiyonu mümkün olurdu
     * (denetmen önerisi #3, delta öncesi yüzey).
     */
    fun accepted(isShare: Boolean, token: String?): Boolean =
        isShare && !token.isNullOrBlank()

    /** Sözleşme haritası: vekilin koyacağı anahtarlar (test referansı). */
    val contractKeys: Set<String> = setOf(
        EXTRA_IS_SHARE, EXTRA_SHARED_TEXT, EXTRA_SHARED_FILE, EXTRA_SHARE_TOKEN, EXTRA_STAGED_FILE,
    )
}
