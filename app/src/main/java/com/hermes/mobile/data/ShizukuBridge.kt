package com.hermes.mobile.data

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Shizuku köprüsü — kabuk (UID 2000) yetkisiyle komut çalıştırma.
 *
 * **Neden Shizuku, neden erişilebilirlik servisi değil:** Android 17 Beta 2,
 * Advanced Protection açıkken erişilebilirlik aracı olmayan uygulamaların
 * `AccessibilityService`'e erişimini engelliyor (Tasker ve MacroDroid dahil), ve
 * Play politikası otomasyonda özerk kullanımı yasaklıyor. O temele bina dikmek
 * her Android sürümünde kırılırdı. Shizuku ise ADB arayüzünü kullanıyor:
 * politika riski yok, root gerekmiyor.
 *
 * **Kullanıcı ne yapmak zorunda:** Shizuku uygulamasını kurup kablosuz hata
 * ayıklama ile başlatmak. Android 11+ bunu cihazda yapabiliyor, bilgisayar
 * gerekmiyor — ama **her yeniden başlatmada tekrar** başlatmak gerekiyor.
 * Bu yüzden varsayılan olarak kapalı, isteyen açıyor.
 *
 * Shizuku yoksa uygulama tamamen normal çalışır; bu katman yalnız ek araçlar
 * açar, hiçbir mevcut işlevin önkoşulu değil.
 */
class ShizukuBridge {

    enum class State {
        /** Shizuku kurulu değil ya da servis çalışmıyor. */
        Unavailable,

        /** Servis var, izin verilmemiş. */
        NeedsPermission,

        /** Hazır — komut çalıştırılabilir. */
        Ready,
    }

    private val _state = MutableStateFlow(State.Unavailable)
    val state: StateFlow<State> = _state.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { _state.value = State.Unavailable }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    /**
     * Dinleyicileri bağlar.
     *
     * Shizuku sınıfına dokunmak bile Shizuku kurulu değilse
     * `ClassNotFoundException`/`NoClassDefFoundError` atabiliyor — sağlayıcı
     * yoksa sınıf yüklenemiyor. Bu yüzden her erişim korumalı.
     */
    fun attach() {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        }
        refresh()
    }

    fun detach() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
    }

    fun refresh() {
        _state.value = runCatching {
            when {
                !Shizuku.pingBinder() -> State.Unavailable
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.Ready
                else -> State.NeedsPermission
            }
        }.getOrDefault(State.Unavailable)
    }

    /** İzin ister. Sonuç dinleyiciden gelir, dönüş değeri yok. */
    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    val isReady: Boolean get() = _state.value == State.Ready

    /**
     * Kabuk komutu çalıştırır ve çıktısını döner.
     *
     * `Shizuku.newProcess` kütüphanede `@RestrictTo` ile işaretli: doğrudan
     * çağırmak derlemede uyarı/hata veriyor ve sürümler arasında imzası
     * değişebiliyor. Yansıma ile çağırıp her hatayı yakalıyoruz — kaybolursa
     * uygulama çökmüyor, yalnız bu katman kullanılamaz hale geliyor.
     */
    suspend fun exec(command: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isReady) return@withContext Result.failure(IllegalStateException("Shizuku hazır değil"))
        runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }

            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null,
            ) ?: error("newProcess null döndü")

            val cls = process.javaClass
            val out = cls.getMethod("getInputStream").invoke(process) as java.io.InputStream
            val err = cls.getMethod("getErrorStream").invoke(process) as java.io.InputStream
            val text = out.bufferedReader().use { it.readText() }
            val errText = err.bufferedReader().use { it.readText() }
            runCatching { cls.getMethod("waitFor").invoke(process) }
            runCatching { cls.getMethod("destroy").invoke(process) }

            val combined = (text + errText).trim()
            combined.ifBlank { "tamam" }
        }
    }

    private companion object {
        const val REQUEST_CODE = 5001
    }
}
