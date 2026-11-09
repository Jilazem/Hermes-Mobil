package com.hermes.mobile.car

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Reads existing audio connections. No discovery, bonding, location or automatic permission grant. */
object MolaConnectedCars {
    fun permitted(context: Context) = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun read(context: Context): List<MolaCarDevice> = coroutineScope {
        if (!permitted(context)) return@coroutineScope emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return@coroutineScope emptyList()
        listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP).map { profile -> async {
            withTimeoutOrNull(1_500) {
                suspendCancellableCoroutine<List<MolaCarDevice>> { continuation ->
                    val listener = object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(type: Int, proxy: BluetoothProfile) {
                            try {
                                val devices = runCatching { proxy.connectedDevices.mapNotNull { device ->
                                    val id = MolaCarProfiles.deviceId(device.address) ?: return@mapNotNull null
                                    MolaCarDevice(id, device.name?.take(60) ?: "Aracım",
                                        device.bluetoothClass?.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO)
                                } }.getOrDefault(emptyList())
                                if (continuation.isActive) continuation.resume(devices)
                            } finally { runCatching { adapter.closeProfileProxy(type, proxy) } }
                        }
                        override fun onServiceDisconnected(type: Int) {
                            if (continuation.isActive) continuation.resume(emptyList())
                        }
                    }
                    val accepted = runCatching { adapter.getProfileProxy(context.applicationContext, listener, profile) }.getOrDefault(false)
                    if (!accepted && continuation.isActive) continuation.resume(emptyList())
                }
            }.orEmpty()
        } }.flatMap { it.await() }.groupBy { it.id }.map { (_, devices) -> devices.first().copy(carAudio = devices.any { it.carAudio }) }
    }
}
