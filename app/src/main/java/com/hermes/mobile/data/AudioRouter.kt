package com.hermes.mobile.data

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Konuşma sesinin nereden çıkacağını yönetir.
 *
 * `USAGE_VOICE_COMMUNICATION` ile açılan ses varsayılan olarak **kulak
 * hoparlöründen** çıkıyor — telefonu elinde tutup kamerayı bir şeye
 * doğrultmuşken bu kullanılamaz durumda. Bu yüzden varsayılanı hoparlör
 * yapıyoruz ve kullanıcıya seçim bırakıyoruz.
 *
 * Android 12'den (API 31) itibaren `setCommunicationDevice` doğru API; daha
 * eskilerde `isSpeakerphoneOn` + Bluetooth SCO ile idare ediliyor.
 */
class AudioRouter(context: Context) {

    /**
     * Etiketler iki dilli: sabit `label` kullanılınca arayüz İngilizceye
     * geçtiğinde ses çıkışı hâlâ "Hoparlör" yazıyordu.
     */
    enum class Route(private val tr: String, private val en: String) {
        Speaker("Hoparlör", "Speaker"),
        Earpiece("Kulaklık (telefon)", "Earpiece"),
        Bluetooth("Bluetooth", "Bluetooth"),
        Wired("Kablolu kulaklık", "Wired headset"),
        ;

        val label: String get() = com.hermes.mobile.ui.tr(tr, en)
    }

    private val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _route = MutableStateFlow(Route.Speaker)
    val route: StateFlow<Route> = _route.asStateFlow()

    private val _available = MutableStateFlow(listOf(Route.Speaker, Route.Earpiece))
    val available: StateFlow<List<Route>> = _available.asStateFlow()

    /** Sesli oturum başlarken çağrılır: iletişim kipine geç, cihazları tara. */
    fun begin() {
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        refreshAvailable()
        apply(_route.value)
    }

    /** Oturum biterken normal kipe dön; yoksa telefonun sesi kısık kalır. */
    fun end() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
                @Suppress("DEPRECATION")
                am.stopBluetoothSco()
            }
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    fun refreshAvailable() {
        val routes = mutableListOf(Route.Speaker, Route.Earpiece)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.availableCommunicationDevices.forEach { dev ->
                when (dev.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    -> if (Route.Bluetooth !in routes) routes += Route.Bluetooth

                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    -> if (Route.Wired !in routes) routes += Route.Wired
                }
            }
        } else {
            @Suppress("DEPRECATION")
            if (am.isBluetoothScoAvailableOffCall) routes += Route.Bluetooth
            @Suppress("DEPRECATION")
            if (am.isWiredHeadsetOn) routes += Route.Wired
        }

        _available.value = routes
    }

    fun select(route: Route) {
        _route.value = route
        apply(route)
    }

    /** Sıradaki kullanılabilir çıkışa geçer — tek düğmeyle döngü. */
    fun cycle() {
        val list = _available.value
        if (list.isEmpty()) return
        val next = list[(list.indexOf(_route.value) + 1).mod(list.size)]
        select(next)
    }

    private fun apply(route: Route) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val wanted = am.availableCommunicationDevices.firstOrNull { dev ->
                    when (route) {
                        Route.Speaker -> dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        Route.Earpiece -> dev.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                        Route.Bluetooth -> dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                        Route.Wired -> dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET
                    }
                }
                if (wanted != null) am.setCommunicationDevice(wanted)
                else am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                when (route) {
                    Route.Bluetooth -> {
                        am.startBluetoothSco()
                        am.isBluetoothScoOn = true
                        am.isSpeakerphoneOn = false
                    }
                    Route.Speaker -> {
                        am.stopBluetoothSco()
                        am.isBluetoothScoOn = false
                        am.isSpeakerphoneOn = true
                    }
                    else -> {
                        am.stopBluetoothSco()
                        am.isBluetoothScoOn = false
                        am.isSpeakerphoneOn = false
                    }
                }
            }
        }
    }
}
