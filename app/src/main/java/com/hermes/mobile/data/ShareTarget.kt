package com.hermes.mobile.data

/**
 * WhatsApp paylaşımda hedefi kim belirleyeceğini saf bir kararlaşıma katmanı.
 *
 * Gerçek ucu: hermes-agent'in web router'ında `POST /api/files/upload-stream`
 * (multipart dosya yükleme) mevcut; Android bunu `HermesClient.uploadFile` ile
 * zaten kullanıyor (görüntü/dosya ek). Dolayısıyla paylaşım niyeti aşağıdaki gibi:
 *  - yalnız metin (text/plain) → mevcut oturum seçilir veya yeni konu;
 *  - dosya (EXTRA_STREAM) ekleniyorsa → seçilen oturuma ek olarak yüklenir;
 *  - dosya+metin yoksa → varsayılan yeni konu.
 *
 * [resolveShareTarget] kararı üretir; [ShareTarget] sonucu veri taşıyıcısıdır.
 * UI katmanı bu fonksiyona bakar ve ona göre EXTRA_TEXT gönderir.
 */

/** Paylaşım hedefi tipi. */
enum class ShareTargetKind {
    /** Mevcut bir oturuma gönder. */
    Existing,
    /** Yeni bir konu / sohbet başlat. */
    New,
}

/** Hedef çözümü sonucu. */
data class ShareTarget(
    val kind: ShareTargetKind,
    /** [kind] == [ShareTargetKind.Existing] için oturum kimliği. */
    val sessionId: String? = null,
    /** Dosya ekleniyor muydu (metinle birlikte). */
    val hasFile: Boolean = false,
    /**
     * Hedef seçim sayfasını (ShareTargetScreen) açmak gerekir mi?
     * Gerçek bir paylaşım (metin veya dosya) varken kullanıcıya seçim
     * şansı vermek içindir; boş paylaşım açmaz.
     */
    val wantsTargetPicker: Boolean = false,
)

/**
 * Paylaşım niyetini hedefe çevirir (saf, UI'siz).
 *
 * @param sharedText Paylaşımın metni (EXTRA_TEXT). Boşsa metin yok.
 * @param sharedFile Dosya adı (EXTRA_STREAM → contentResolver displayName).
 *                   null ya da boş ise yalnız metin paylaşımı.
 * @param selectedSession Kullanıcının seçtiği oturum (boşsa seçim yok).
 *
 * Karar matrisi:
 *  1. Dosya var + oturum seçildi  → Existing(sessionId, hasFile=true, picker=false)
 *  2. Dosya var + oturum yok      → New(hasFile=true, picker=true)
 *  3. Yalnız metin + oturum seçildi → Existing(sessionId, hasFile=false, picker=false)
 *  4. Yalnız metin + oturum yok    → New(hasFile=false, picker=true)
 *  5. Ne metin ne dosya            → New(hasFile=false, picker=false) // boş
 */
fun resolveShareTarget(
    sharedText: String?,
    sharedFile: String?,
    selectedSession: String?,
): ShareTarget {
    val hasFile = !sharedFile.isNullOrBlank()
    val session = selectedSession?.takeIf { it.isNotBlank() }
    val hasText = !sharedText.isNullOrBlank()
    val isRealShare = hasText || hasFile

    return when {
        // Oturum seçildiyse oraya git; seçim sayfası gerekmez (bilinçli seçim).
        session != null -> ShareTarget(
            kind = ShareTargetKind.Existing,
            sessionId = session,
            hasFile = hasFile,
            wantsTargetPicker = false,
        )
        // Gerçek paylaşım var ama oturum seçilmedi → seçim sayfası aç.
        isRealShare -> ShareTarget(
            kind = ShareTargetKind.New,
            hasFile = hasFile,
            wantsTargetPicker = true,
        )
        // Boş paylaşım → sessiz varsayılan, sayfa açma.
        else -> ShareTarget(
            kind = ShareTargetKind.New,
            hasFile = false,
            wantsTargetPicker = false,
        )
    }
}

/** "Yeni konu" butonunu temsil eden sabit hedef. */
val NEW_TOPIC_TARGET = ShareTarget(ShareTargetKind.New, null, false)

/** Hedefi insan okunur bir etikete çevirir (bilgi amaçlı; UI'da gösterim). */
fun ShareTarget.label(lang: String = "tr"): String = when {
    kind == ShareTargetKind.Existing && sessionId != null ->
        (if (lang == "tr") "Oturuma ekle: " else "Add to session: ") + sessionId
    hasFile -> (if (lang == "tr") "Yeni konu (dosya ekli)" else "New topic (with file)")
    else -> (if (lang == "tr") "Yeni konu" else "New topic")
}
