package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.data.DiagLog
import com.hermes.mobile.data.ShareStaging

/**
 * Yalnız [DiagLog]'u ayağa kaldırmak için var. Çökme yakalayıcısının Activity
 * değil Application içinde kurulması gerekiyor: çökme, Activity yaratılmadan
 * önce de olabiliyor (servisler, döşeme, araç ekranı aynı süreçte).
 */
class HermesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DiagLog.init(this)
        // Çift emniyet (denetmen #1): paylaşım ortasında süreç ölürse consume
        // temizliği hiç çalışmaz; 1 saatini aşan staging kopyalarını süpür.
        ShareStaging.purgeStale(ShareStaging.inboxDir(cacheDir), olderThanMs = 3_600_000L)
    }
}
