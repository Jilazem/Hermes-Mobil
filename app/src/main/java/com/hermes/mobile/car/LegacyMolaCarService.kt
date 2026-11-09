package com.hermes.mobile.car

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import androidx.car.app.HostInfo
import androidx.car.app.validation.HostValidator
import com.google.android.apps.auto.sdk.CarActivity
import com.google.android.apps.auto.sdk.CarActivityService

/** The same legacy discovery contract as the selected Mola source, with a caller gate. */
class LegacyMolaCarService : CarActivityService() {
    override fun getCarActivity(): Class<out CarActivity> = LegacyMolaActivity::class.java
    override fun onBind(intent: Intent): IBinder? {
        val delegate = super.onBind(intent) ?: return null
        val validator = HostValidator.Builder(this)
            .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()
        return CheckedCarBinder(delegate) { uid ->
            uid == android.os.Process.myUid() || packageManager.getPackagesForUid(uid).orEmpty().any { pkg ->
                runCatching { validator.isValidHost(HostInfo(pkg, uid)) }.getOrDefault(false)
            }
        }
    }
}

internal class CheckedCarBinder(private val delegate: IBinder, private val authorized: (Int) -> Boolean) : Binder() {
    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (!authorized(Binder.getCallingUid())) throw SecurityException("Untrusted Android Auto caller")
        return delegate.transact(code, data, reply, flags)
    }
}
