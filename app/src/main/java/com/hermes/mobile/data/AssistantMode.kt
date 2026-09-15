package com.hermes.mobile.data

/**
 * Telefon asistanı (tur-13) — **saf** karar katmanı.
 *
 * Neden ayrı: "Hermes telefonda varsayılan asistan mı?" ve "asistan akışında
 * yanıt kendiliğinden okunsun mu?" sorularının cevabı Android'den bağımsız
 * olarak sınanabilmeli. Rol sorgusu ([AssistantRole]) ve sistem diyaloğu
 * Android tarafında kalır; karar burada tek yerde verilir.
 *
 * Kapsam kuralı: **varsayılan sesli yol YEREL** (voice_api :8174 → whisper +
 * kahya). Gemini Live canlı ses özelliği opsiyon olarak kalır, varsayılan
 * asistan akışı oradan geçmez.
 */
object AssistantModeLogic {

    /** `ROLE_ASSISTANT` yalnız API 29 (Android 10) ve üstünde atanabiliyor. */
    const val ROLE_MIN_API = 29

    /**
     * Asistan rolünü tutabilen bilinen uygulamaların okunabilir adı.
     *
     * Kullanıcı satırda paket adı değil ne gördüğünü okumalı; bilinmeyen
     * paket olduğu gibi gösterilir (uydurma etiket yok).
     */
    fun holderLabel(pkg: String): String = when (pkg) {
        "com.google.android.googlequicksearchbox" -> "Google"
        "com.google.android.googleassistant" -> "Google Asistanı"
        "com.google.android.apps.googleassistant" -> "Google Asistanı"
        "com.samsung.android.bixby.agent" -> "Bixby"
        "com.android.intelligence" -> "Android Asistanı"
        else -> pkg
    }

    enum class RoleState {
        /** Hermes rolü tutuyor. */
        Hermes,

        /** Başka bir uygulama tutuyor (ör. Google). */
        Other,

        /** Kimse atanmamış. */
        None,

        /** Bu Android sürümünde rol atanamıyor (API < 29). */
        Unsupported,
    }

    data class RoleStatus(val state: RoleState, val holder: String = "")

    /**
     * Rol sahiplerinden durum çıkarımı.
     *
     * @param selfPackage bizim paket adı (`applicationId` sonekli debug paketi
     *   dahil — karşılaştırma tam eşitlik)
     * @param holders rolü tuttuğu düşünülen paketler (en güvenilir önce)
     * @param apiLevel `Build.VERSION.SDK_INT`
     * @param selfHolds `isRoleHeld(ROLE_ASSISTANT)` — biz mi tutuyoruz;
     *   kesin cevap, ipuçlarından önce gelir
     */
    fun roleStatus(
        selfPackage: String,
        holders: List<String>,
        apiLevel: Int,
        selfHolds: Boolean = false,
    ): RoleStatus {
        if (apiLevel < ROLE_MIN_API) return RoleStatus(RoleState.Unsupported)
        if (selfHolds) return RoleStatus(RoleState.Hermes)
        val normalized = holders.map { it.trim() }.filter { it.isNotEmpty() }
        if (normalized.isEmpty()) return RoleStatus(RoleState.None)
        if (normalized.any { it == selfPackage }) return RoleStatus(RoleState.Hermes)
        return RoleStatus(RoleState.Other, holderLabel(normalized.first()))
    }

    /** Ayarlar → "Telefon asistanı" bölümünde görünen rol satırı. */
    fun roleLine(status: RoleStatus, t: (String, String) -> String): String = when (status.state) {
        RoleState.Hermes -> t("Hermes: varsayılan asistan ✓", "Hermes: default assistant ✓")
        RoleState.Other -> t("Şu an: ${status.holder}", "Currently: ${status.holder}")
        RoleState.None -> t("Şu an: atanmamış", "Currently: not assigned")
        RoleState.Unsupported -> t(
            "Bu Android sürümünde rol atanamıyor — Ayarlar'dan elle seç",
            "The role cannot be assigned on this Android version — pick it manually in Settings",
        )
    }

    /** Sistem diyaloğu açılabilir mi; açılamıyorsa elle atama adımları gösterilir. */
    fun canRequestRole(status: RoleStatus): Boolean = status.state != RoleState.Unsupported

    /** Rol desteklenmediğinde gösterilen kısa adımlar. */
    fun manualSteps(t: (String, String) -> String): List<String> = listOf(
        t("Ayarlar → Uygulamalar → Varsayılan uygulamalar", "Settings → Apps → Default apps"),
        t("Dijital asistan → Hermes Asistan", "Digital assistant → Hermes Asistan"),
    )

    // ── Asistan akışı durumu ─────────────────────────────────────────────

    /**
     * Asistan akışının ekranda görünen fazı.
     *
     * Sıra önemli: kayıt/çeviri (kullanıcının kendi eylemi) her zaman önce,
     * sonra seslendirme, en son ajanın çalışması gelir.
     */
    enum class Phase { Off, Ready, Recording, Transcribing, AwaitingReply, Speaking }

    fun phase(
        active: Boolean,
        record: VoiceRecordLogic.State,
        speak: VoiceSpeakLogic.State,
        agentBusy: Boolean,
    ): Phase = when {
        !active -> Phase.Off
        record.phase == VoiceRecordLogic.Phase.Recording -> Phase.Recording
        record.phase == VoiceRecordLogic.Phase.Transcribing -> Phase.Transcribing
        speak.playing -> Phase.Speaking
        agentBusy -> Phase.AwaitingReply
        else -> Phase.Ready
    }

    /** Asistan şeridindeki tek satır ipucu. */
    fun bannerText(p: Phase, t: (String, String) -> String): String = when (p) {
        Phase.Off -> ""
        Phase.Ready -> t("Asistan hazır — basılı tut ve konuş", "Assistant ready — hold to talk")
        Phase.Recording -> t("Dinliyorum… bırakınca yazıya çevirir", "Listening… release to transcribe")
        Phase.Transcribing -> t("Yazıya çevriliyor…", "Transcribing…")
        Phase.AwaitingReply -> t("Hermes yanıtlıyor…", "Hermes is replying…")
        Phase.Speaking -> t("Yanıt okunuyor…", "Reading the reply out…")
    }

    /**
     * Asistan yanıtı geldiğinde **kendiliğinden** seslendirilsin mi.
     *
     * Yalnız asistan bağlamında okur: normal sohbette ayar açık olsa bile
     * kullanıcının elinde olmayan bir ses başlamaz (tur-13 şartı: "normal
     * sohbet varsayılanı DEĞİŞMESİN").
     */
    fun shouldAutoRead(assistantMode: Boolean, settingOn: Boolean, reply: String): Boolean =
        assistantMode && settingOn && VoiceSpeakLogic.prepare(reply) != null

    /**
     * Sesle yazılan metin kendiliğinden gönderilsin mi.
     *
     * Asistan akışında EVET: bas-konuş'un amacı soruyu sormak, araya "gönder"
     * dokunuşu koymak akışı bozar. Normal sohbette karar kullanıcı ayarında
     * ([AppSettings.voiceAutoSend], varsayılan kapalı).
     */
    fun autoSendTranscript(assistantMode: Boolean, settingAutoSend: Boolean): Boolean =
        assistantMode || settingAutoSend

    /**
     * Asistan modu açılışında mikrofon izni istenip istenmeyeceği.
     *
     * İzin **istenir** ama kayıt KENDİLİĞİNDEN BAŞLAMAZ: kullanıcı basmadan
     * mikrofon açılmaz (yanlışlıkla kayıt / kötüye kullanım olmasın).
     */
    fun askMicOnEnter(granted: Boolean): Boolean = !granted
}
