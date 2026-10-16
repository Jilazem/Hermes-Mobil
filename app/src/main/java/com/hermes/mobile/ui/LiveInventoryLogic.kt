package com.hermes.mobile.ui

import com.hermes.mobile.data.ArtemisTaskInfo
import com.hermes.mobile.data.BridgeState
import com.hermes.mobile.data.LiveSession

/**
 * K2 — "Canlı" envanter mantığı (saf, Compose'suz, birim testli).
 *
 * phone-harness `cloud ls` deseninin karşılığı: telefon tarafında "şu an ne
 * çalışıyor?" sorusunun TEK cevabı — sohbet oturumları + köprü WS + Artemis
 * görevleri tek listede. Renkler [drawerStatusDot] semantiğinden (yeşil =
 * çalışıyor, sarı = bekliyor, gri = sonlandı) — ayrı bir renk sözlüğü yok.
 */

/** Envanter satırı — `kind`: chat | bridge | artemis. */
data class LiveInventoryRow(
    val kind: String,
    val label: String,
    val detail: String,
    /** drawerStatusDot renk adı: green · yellow · grey · "" (bilinmiyor). */
    val dot: String,
)

/** Köprü durumu → aynı renk semantiği. */
private fun bridgeDot(bridge: BridgeState): String = when (bridge) {
    BridgeState.CONNECTED -> drawerStatusDot(
        working = true, waitingOrStarting = false,
        sessionActive = true, sessionRecord = true,
    )
    BridgeState.CONNECTING, BridgeState.RETRYING, BridgeState.TAKEN_OVER -> drawerStatusDot(
        working = false, waitingOrStarting = true,
        sessionActive = false, sessionRecord = true,
    )
    BridgeState.OFF -> drawerStatusDot(
        working = false, waitingOrStarting = false,
        sessionActive = false, sessionRecord = true,
    )
}

/** Artemis görevi → çalışan yeşil, biten gri. */
private fun artemisDot(task: ArtemisTaskInfo): String = drawerStatusDot(
    working = !task.done,
    waitingOrStarting = false,
    sessionActive = !task.done,
    sessionRecord = true,
)

/**
 * Sıra: köprü → sohbet oturumları (çalışanlar önce) → Artemis görevleri
 * (çalışanlar önce). Uydurma satır YOK: veri yoksa o bölüm hiç çizilmez.
 *
 * @param liveTitle satır başlığı — ham oturum id ASLA ([displayLabel] ile
 *   çözümlenmiş olmalı; çağıran taraf SessionDrawer'daki `liveTitleOf`).
 */
fun liveInventory(
    bridge: BridgeState,
    liveSessions: List<LiveSession>,
    artemisTasks: List<ArtemisTaskInfo>,
    liveTitle: (LiveSession) -> String = { it.title },
    maxRows: Int = 6,
): List<LiveInventoryRow> {
    val out = ArrayList<LiveInventoryRow>()

    out += LiveInventoryRow(
        kind = "bridge",
        label = tr("Köprü WS (ajan kanalı)", "Bridge WS (agent channel)"),
        detail = when (bridge) {
            BridgeState.CONNECTED -> tr("bağlı", "connected")
            BridgeState.CONNECTING -> tr("bağlanıyor", "connecting")
            BridgeState.RETRYING -> tr("yeniden bağlanıyor", "reconnecting")
            BridgeState.TAKEN_OVER -> tr("başka cihaz devraldı", "taken over by another device")
            BridgeState.OFF -> tr("kapalı", "off")
        },
        dot = bridgeDot(bridge),
    )

    liveSessions
        .sortedByDescending { if (it.isWorking) 1 else 0 }
        .take(maxRows)
        .forEach { s ->
            out += LiveInventoryRow(
                kind = "chat",
                label = displayLabel(liveTitle(s)),
                detail = when {
                    s.isWorking -> tr("çalışıyor", "working")
                    s.isWaiting -> tr("bekliyor", "waiting")
                    s.isStarting -> tr("başlıyor", "starting")
                    else -> tr("canlı listede", "in the live list")
                },
                // DrawerLiveRow ile birebir aynı renk sonucu (çalışan=green,
                // bekleyen=yellow, değilse grey) — drawerStatusDot üzerinden.
                dot = drawerStatusDot(
                    working = s.isWorking,
                    waitingOrStarting = s.isWaiting || s.isStarting,
                    sessionActive = s.isWorking,
                    sessionRecord = true,
                ),
            )
        }

    artemisTasks
        .sortedByDescending { if (it.done) 0 else 1 }
        .take(maxRows)
        .forEach { t ->
            out += LiveInventoryRow(
                kind = "artemis",
                label = tr("Artemis görevi", "Artemis task") + " ${t.id}",
                detail = t.goal + " · " + t.status,
                dot = artemisDot(t),
            )
        }
    return out
}