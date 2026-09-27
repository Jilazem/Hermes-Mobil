package com.hermes.mobile.ui

/**
 * Açık sohbetin taşma (⋯) menüsü (KALAN-2) — saf sözleşme.
 *
 * Tur-4'te sohbet üst şeridi "tek satır konu + ⋯" hâline geldi ama ⋯ yalnız
 * MODEL seçicisini açıyordu: müdahale/durdurma yalnız Canlı sekmesinde ve
 * composer'ın küçük düğmesinde kalıyordu. Ayrıca `onOpenReasoning` parametresi
 * ChatScreen'den ChatHeader'a geçiyor ama menüde HİÇ kullanılmıyordu —
 * "Düşünme" (reasoning) sayfasına sohbetten ulaşmanın yolu yoktu (ölü uç).
 *
 * Yeni sözleşme: menü her zaman model + düşünme; ajan ÇALIŞIYORKEN araya
 * müdahale ve durdurma eklenir (boşta durdurma anlamsız olduğu için gizli).
 *
 * Saf fonksiyon — Compose'suz JVM testi (ChatMenuTest).
 */
enum class ChatMenuAction {
    Model,
    Reasoning,
    Intervene,
    Stop,
}

/**
 * Balona uzun basma menüsü (tur25.3).
 *
 * Kullanıcı isteği: "ana akışta tüm çalışmalar akarken bir konuya basılı
 * tuttuğum ona cevap verme yada o konuya ayrı dal açma" — yani Hermes'in
 * zaten var olan komutları, sohbet balonundan tek bir uzun basmayla.
 * Karşılıkları Telegram bot'undaki `/steer` ve `/branch`.
 */
enum class BubbleAction {
    /** Buna cevap verme — turu kesmeden talimat ekle (`/steer`). */
    Answer,

    /** Ayrı dal aç — oturumu dallandır (`/branch`). */
    Branch,

    /** Seslendir / durdur (mevcut davranış, menüye taşındı). */
    Speak,
}

fun chatMenuActions(agentBusy: Boolean): List<ChatMenuAction> =
    if (agentBusy) {
        listOf(ChatMenuAction.Model, ChatMenuAction.Reasoning, ChatMenuAction.Intervene, ChatMenuAction.Stop)
    } else {
        listOf(ChatMenuAction.Model, ChatMenuAction.Reasoning)
    }
