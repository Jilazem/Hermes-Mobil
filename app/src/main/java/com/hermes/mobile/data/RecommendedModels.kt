package com.hermes.mobile.data
import com.hermes.mobile.ui.tr

/**
 * Önerilen modeller — sunucuda **ölçülerek** doğrulandı (2026-07-27).
 *
 * Model seçicideki ham liste 37 sağlayıcı ve yüzlerce model içeriyor; çoğu
 * kredi gerektirdiği için kullanılamıyor, bir kısmı da erişilemeyen yerel
 * sunuculara işaret ediyor. Kullanıcı bunların arasından seçim yapmak zorunda
 * kalmamalı — bu liste "aç ve çalışsın" diyebileceği kısa yol.
 *
 * Süreler tek turluk "Sadece: OK" istemiyle ölçüldü; sıralama buna göre.
 */
data class RecommendedModel(
    val provider: String,
    val model: String,
    val label: String,
    /** Ölçülen ilk yanıt süresi, saniye. */
    val seconds: Double,
    val note: String,
    val free: Boolean = true,
    /** Tamamen yerel — internet, kota ve ücret yok. */
    val local: Boolean = false,
)

val RECOMMENDED_MODELS: List<RecommendedModel> = listOf(
    RecommendedModel(
        "gemini", "gemini-3.5-flash", "Gemini 3.5 Flash", 3.2,
        tr("En hızlı · Google AI Studio anahtarı", "Fastest · Google AI Studio key"),
    ),
    RecommendedModel(
        "agnes-ai", "agnes-2.0-flash", "Agnes 2.0 Flash", 3.6,
        tr("Varsayılan · ücretsiz ve dengeli", "Default · free and balanced"),
    ),
    RecommendedModel(
        "cli-bridge", "claude-cli-sonnet", "Claude Sonnet (CLI)", 5.5,
        tr("Güçlü akıl yürütme · CLI köprüsü", "Strong reasoning · CLI bridge"),
    ),
    RecommendedModel(
        "cli-bridge", "gemini-flash", "Gemini Flash (Antigravity)", 8.7,
        tr("Uzun bağlam · CLI köprüsü", "Long context · CLI bridge"),
    ),
    // İkinci the server (node2, node-b) üzerinde vLLM ile koşuyor. İnternete hiç
    // çıkmıyor, kota ve ücret yok — gizli veriyle çalışırken tercih edilmeli.
    //
    // Aynı model Hermes'te üç yoldan tanımlıydı; ikisi temizlendi:
    //   · `laguna-node2` — aynı uç noktaya bakıyordu ama 262k bağlam iddia
    //     ediyordu; vLLM gerçekte 131072 sunuyor (`max_model_len`), yani uzun
    //     bağlamda hata verecekti. Sunucu config'inden kaldırıldı.
    //   · `nous/poolside/laguna-s-2.1:free` — Nous Portal üzerinden aynı model,
    //     internete çıkıyor ve daha yavaş (15.4 sn).
    // Kalan tek kayıt bu: yerel, doğru bağlam, en hızlı.
    RecommendedModel(
        "laguna-local-2", "/home/user/models/hf/Laguna-S-2.1-NVFP4",
        "Laguna-S 2.1", 9.5,
        "2. sunucuda yerel · internete çıkmaz · 131k bağlam",
        local = true,
    ),
    RecommendedModel(
        "nous", "stepfun/step-3.7-flash:free", "StepFun 3.7 Flash", 10.8,
        "Nous Portal ücretsiz katman",
    ),
    RecommendedModel(
        "cli-bridge", "gemini-pro", "Gemini Pro (Antigravity)", 20.4,
        tr("En yetenekli · yavaş", "Most capable · slow"),
    ),
    RecommendedModel(
        "nous", "tencent/hy3:free", "Hunyuan 3", 21.7,
        "Nous Portal ücretsiz katman",
    ),
)

/** Bir modelin önerilenler arasındaki kaydı. */
fun recommendedFor(provider: String, model: String): RecommendedModel? =
    RECOMMENDED_MODELS.firstOrNull { it.provider == provider && it.model == model }
