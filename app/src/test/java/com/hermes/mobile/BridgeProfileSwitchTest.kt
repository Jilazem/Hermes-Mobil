package com.hermes.mobile

import com.hermes.mobile.data.BridgePolicy
import com.hermes.mobile.data.ServerProfile
import org.junit.Assert.*
import org.junit.Test

class BridgeProfileSwitchTest {
    private val profile = ServerProfile(id = "one", name = "Home", baseUrl = "http://localhost:9150", token = "test-only")
    @Test fun identityAndCredentialChangesInvalidateChannel() {
        assertFalse(BridgePolicy.sameProfile(profile, profile.copy(id = "two")))
        assertFalse(BridgePolicy.sameProfile(profile, profile.copy(token = "replacement")))
        assertFalse(BridgePolicy.sameProfile(profile, null))
    }
    @Test fun endpointChangeInvalidatesChannel() {
        assertFalse(BridgePolicy.sameProfile(profile, profile.copy(baseUrl = "http://localhost:9151")))
        assertFalse(BridgePolicy.sameProfile(profile, profile.copy(bridgeUrl = "ws://localhost:9180/phone")))
    }
    @Test fun cosmeticChangesDoNotReconnect() {
        assertTrue(BridgePolicy.sameProfile(profile, profile.copy(name = "Renamed", note = "updated")))
    }
}
