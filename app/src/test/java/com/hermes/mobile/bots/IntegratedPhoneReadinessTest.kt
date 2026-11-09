package com.hermes.mobile.bots

import com.hermes.mobile.ui.bots.IntegratedPhoneStage
import com.hermes.mobile.ui.bots.integratedPhoneStage
import org.junit.Assert.assertEquals
import org.junit.Test

class IntegratedPhoneReadinessTest {
    private fun state(
        configured: Boolean = true, stop: Boolean = false, permission: Boolean = true,
        service: Boolean = true, authorized: Boolean = true, bridge: Boolean = true, readOnly: Boolean = false,
    ) = integratedPhoneStage(configured, stop, permission, service, authorized, bridge, readOnly)

    @Test fun emergencyStopBlocksAConnectedAuthorizedPhone() {
        assertEquals(IntegratedPhoneStage.EmergencyStopped, state(stop = true))
    }

    @Test fun aSavedAndroidPermissionDoesNotProveThatTheServiceRuns() {
        assertEquals(IntegratedPhoneStage.NeedsAccessibility, state(permission = true, service = false))
    }

    @Test fun phonePermissionCannotReplaceDashboardCredentialsOrLocalAuthorization() {
        assertEquals(IntegratedPhoneStage.NeedsDashboard, state(configured = false))
        assertEquals(IntegratedPhoneStage.NeedsPhoneAuthorization, state(authorized = false))
    }

    @Test fun aDisconnectedBridgeDoesNotAdvertisePhoneReadiness() {
        assertEquals(IntegratedPhoneStage.NeedsBridge, state(bridge = false))
    }

    @Test fun readOnlyReadinessRemainsDistinctFromActionReadiness() {
        assertEquals(IntegratedPhoneStage.ReadyReadOnly, state(readOnly = true))
        assertEquals(IntegratedPhoneStage.Ready, state(readOnly = false))
    }
}
