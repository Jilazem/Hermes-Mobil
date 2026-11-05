package com.hermes.mobile

import android.app.Application
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import com.hermes.mobile.data.BridgeState
import com.hermes.mobile.data.PhoneBridgeService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class BridgeTimeoutV741Test {
    @Test fun exhaustedBudgetStopsForegroundAndRejectsStickyRestart() {
        // Attach the service without opening encrypted profile storage (not provided by Robolectric).
        val controller=Robolectric.buildService(PhoneBridgeService::class.java)
        val service=controller.get()
        service.onTimeout(1,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        assertEquals(BridgeState.OFF,PhoneBridgeService.state)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(Service.START_NOT_STICKY,service.onStartCommand(Intent().setAction(PhoneBridgeService.ACTION_RECONNECT),0,2))
        controller.destroy()
    }
}
