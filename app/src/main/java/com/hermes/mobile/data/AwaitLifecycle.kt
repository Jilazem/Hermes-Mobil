package com.hermes.mobile.data

/**
 * `AwaitReplyService` start/stop karar makinesi — **saf mantık**, Android'e bağlı değil.
 *
 * Neden ayrı duruyor: [AwaitReplyService] bir Android `Service`'tir ve JVM birim
 * testinde koşmaz. Sahada hâlâ görülen çökme
 * (`RemoteServiceException$ForegroundServiceDidNotStartInTimeException`) bir
 * **yarış**tır: sistem `startForegroundService` çağrısından sonra 5 sn içinde
 * `startForeground` bekler; bu arada uygulama `stopService` isterse eski kod
 * servisi onay gelmeden düşürüyordu → sistem yine de zaman aşımını fırlatıyor.
 *
 * Tur-10'da kapatılan üç yarış:
 *  1. **stop → start**: `stopService` çağrıldıktan sonra gelen geç `start`
 *     isteği yeni bir servis başlatmaz (start/stop serileştirilir).
 *  2. **start → stop (onay beklerken)**: bu durumda `stopService` ÇAĞRILMAZ;
 *     servis `startForeground`'u tamamlar (sistemin sözleşmesi) ve
 *     `onForegroundConfirmed()` `true` dönünce **kendini durdurur**.
 *  3. **çift start**: onay gelmeden gelen ikinci istek ikinci bir
 *     `startForegroundService` üretmez (sistem sayaçları şişmez).
 *
 * Kural: her `START` isteği bir servis örneğine ulaşır; her servis örneği
 * `startForeground`'u çağırır; "dur" isteği yalnız onaydan sonra `stopService`
 * olur, onaydan önce ise `stopSelf`'e dönüşür.
 */
class AwaitLifecycle(private val now: () -> Long = { System.currentTimeMillis() }) {

    /** Çağıranın atması gereken adım. */
    enum class Action {
        /** `startForegroundService` çağır. */
        START,

        /** `stopService` çağır (servis onaylı çalışıyor). */
        STOP,

        /** Hiçbir şey yapma (durum zaten istenen hâlde ya da onay bekleniyor). */
        NONE,
    }

    private var desired = false
    private var startIssued = false
    private var startIssuedAt = 0L
    private var confirmed = false
    private var pendingStop = false

    /** Uygulamanın istediği durum (yanıt bekleniyor mu). */
    @Synchronized
    fun awaiting(): Boolean = desired

    /** `startForegroundService` verildi ama onay (`startForeground`) gelmedi. */
    @Synchronized
    fun isStartPending(): Boolean = startIssued && !confirmed

    /** Servis ayakta ve `startForeground` tamam (sistem sözleşmesi karşılandı). */
    @Synchronized
    fun isConfirmed(): Boolean = confirmed

    /** Uygulamanın isteğini bildirir; sıradaki adımı döner. */
    @Synchronized
    fun setAwaiting(next: Boolean): Action {
        if (next) {
            desired = true
            // Onay beklerken gelen "devam" isteği, bekleyen durdurmayı iptal eder.
            pendingStop = false
            if (confirmed || startIssued) return Action.NONE
            startIssued = true
            startIssuedAt = now()
            return Action.START
        }
        desired = false
        if (!startIssued && !confirmed) return Action.NONE
        if (!confirmed) {
            // KRİTİK: onay gelmeden stopService çağırmak çökmeyi üretiyordu.
            // Servis startForeground'u çağırıp kendini durduracak.
            pendingStop = true
            return Action.NONE
        }
        confirmed = false
        startIssued = false
        pendingStop = false
        return Action.STOP
    }

    /**
     * Servis `startForeground`'u tamamladı.
     *
     * @return `true` ise servis kendini HEMEN durdurmalı (`stopSelf`): bu arada
     *   "bekleme bitti" istenmiş demektir.
     */
    @Synchronized
    fun onForegroundConfirmed(): Boolean {
        startIssued = false
        if (!desired && pendingStop) {
            confirmed = false
            pendingStop = false
            return true
        }
        confirmed = true
        pendingStop = false
        return false
    }

    /** Servis örneği yok edildi (stopSelf/stopService/sistem). */
    @Synchronized
    fun onServiceDestroyed() {
        desired = false
        startIssued = false
        confirmed = false
        pendingStop = false
    }

    /**
     * `startForegroundService` çağrıldı ama [timeoutMs] içinde onay gelmedi.
     *
     * Bayrağı serbest bırakır ki sonraki istek yeniden başlatabilsin; aksi
     * hâlde `startIssued` sonsuza dek takılı kalır ve bekleme bir daha
     * başlatılamazdı (sessiz kilitlenme).
     *
     * @return `true` ise gerçekten bir zaman aşımı temizlendi (log için).
     */
    @Synchronized
    fun onStartTimeout(timeoutMs: Long): Boolean {
        if (!startIssued || confirmed) return false
        if (now() - startIssuedAt < timeoutMs) return false
        startIssued = false
        return true
    }
}
