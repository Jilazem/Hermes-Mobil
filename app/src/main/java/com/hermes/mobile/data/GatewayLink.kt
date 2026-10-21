package com.hermes.mobile.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * İş #1021 — TEK KAYNAK: uygulamadaki tek WS istemcisinin bağlantı durumu.
 *
 * Eskiden iki ekran farklı kaynaktan okuyordu:
 *  - Ayarlar > Sunucular: HTTP `/api/status` yoklaması (`ProbeResult.Ok`) → "çevrimiçi"
 *  - Sohbet: `GatewayWsClient.connection` → `Open` olmadığı için "Sunucu bağlı değil"
 * HTTP 200 dönse bile WS upgrade'i reddedilebiliyor (token yok/yanlış, 403), bu
 * yüzden iki gösterge çelişiyordu. Artık iki ekran da buradaki `connection`
 * akışını okuyor; "bağlı" YALNIZCA gerçek `ConnectionState.Open` iken denir.
 *
 * `ChatViewModel.bind()` istemciyi buraya bağlar, `teardown()` bırakır.
 */
object GatewayLink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val bound = MutableStateFlow<GatewayWsClient?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val connection: StateFlow<ConnectionState> =
        bound.flatMapLatest { gw -> gw?.connection ?: flowOf<ConnectionState>(ConnectionState.Idle) }
            .stateIn(scope, SharingStarted.Eagerly, ConnectionState.Idle)

    /** Şu an durumu yayınlanan istemci (test/telemetri için). */
    val client: GatewayWsClient? get() = bound.value

    fun bind(gw: GatewayWsClient) {
        bound.value = gw
    }

    /** Yalnızca aynı istemci hâlâ bağlıysa bırak (geç teardown yeni soketi silmesin). */
    fun release(gw: GatewayWsClient) {
        if (bound.value === gw) bound.value = null
    }
}
