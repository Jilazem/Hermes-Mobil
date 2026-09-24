package com.hermes.mobile

import com.hermes.mobile.data.AppSettings
import com.hermes.mobile.data.FullControl
import com.hermes.mobile.data.withFullControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** "Tam kontrol" tek anahtarı: açınca dokunma/yazma araçları gerçekten serbest kalmalı. */
class FullControlSwitchTest {

    @Test
    fun `varsayilan ayarla acinca yazma araclari reddedilmez`() {
        val s = AppSettings().withFullControl(true)
        assertNull(FullControl.guardReason(s.agentMayUsePhone, s.agentReadOnly, s.fullControl, FullControl.TAP))
        assertEquals(FullControl.ALL_TOOLS, FullControl.advertise(s.agentMayUsePhone, s.fullControl))
    }

    @Test
    fun `kapatinca yalniz tam kontrol kapanir`() {
        val s = AppSettings().withFullControl(true).withFullControl(false)
        assertFalse(s.fullControl)
        assertEquals(true, s.agentMayUsePhone)
    }
}
