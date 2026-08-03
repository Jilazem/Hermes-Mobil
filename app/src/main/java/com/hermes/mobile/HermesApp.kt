package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.data.DiagLog

/**
 * Yalnız [DiagLog]'u ayağa kaldırmak için var. Çökme yakalayıcısının Activity
 * değil Application içinde kurulması gerekiyor: çökme, Activity yaratılmadan
 * önce de olabiliyor (servisler, döşeme, araç ekranı aynı süreçte).
 */
class HermesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
    }
}
