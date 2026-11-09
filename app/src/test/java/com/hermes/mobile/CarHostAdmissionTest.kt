package com.hermes.mobile

import androidx.car.app.HostInfo
import com.hermes.mobile.car.HermesCarService
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CarHostAdmissionTest {
    @Test fun unknownHostCannotAcquireThePhoneCarService() {
        val controller = Robolectric.buildService(HermesCarService::class.java).create()
        try {
            assertFalse(controller.get().createHostValidator().isValidHost(HostInfo("untrusted.example.host", 123456)))
        } finally { controller.destroy() }
    }
}
