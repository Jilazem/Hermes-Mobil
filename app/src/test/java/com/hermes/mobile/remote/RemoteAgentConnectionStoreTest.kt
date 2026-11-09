package com.hermes.mobile.remote

import android.content.Context
import com.hermes.mobile.data.remote.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Storage transaction semantics; Android encrypted storage is exercised by device acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class RemoteAgentConnectionStoreTest {
    private fun preferences() = RuntimeEnvironment.getApplication().getSharedPreferences(
        "remote-test-${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE,
    )

    @Test fun staleStoreUpdateDoesNotRemoveAnotherDeviceOrMixCredentials() {
        val prefs = preferences()
        val a = RemoteAgentConnectionStore(prefs)
        val b = RemoteAgentConnectionStore(prefs)
        val mika = RemoteAgentConnection(id = "phone-a", label = "Mika", kind = RemoteAgentKind.MIKA,
            baseUrl = "http://127.0.0.1:8765/", token = "token-a")
        val phongent = RemoteAgentConnection(id = "phone-b", label = "Phongent", kind = RemoteAgentKind.PHONGENT,
            baseUrl = "http://100.64.1.2:8765", token = "token-b")
        a.upsert(mika)
        b.upsert(phongent)
        assertEquals(2, RemoteAgentConnectionStore(prefs).connections.value.size)
        assertEquals("token-a", b.get("phone-a")!!.token)
        assertEquals("token-b", a.get("phone-b")!!.token)
        b.remove("phone-a")
        assertNull(a.get("phone-a"))
        assertEquals("token-b", a.get("phone-b")!!.token)
    }

    @Test fun invalidCredentialsDoNotReplaceTheExistingDevice() {
        val store = RemoteAgentConnectionStore(preferences())
        val saved = store.upsert(RemoteAgentConnection(id = "phone-a", label = "Telefon", kind = RemoteAgentKind.MIKA,
            baseUrl = "https://relay.example/proxy/", token = "token-a"))
        assertEquals("https://relay.example/proxy", saved.baseUrl)
        assertTrue(runCatching { store.upsert(saved.copy(token = "")) }.isFailure)
        assertEquals("token-a", store.get("phone-a")!!.token)
    }
}
