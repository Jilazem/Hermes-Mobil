package com.hermes.mobile

import android.content.Context
import com.hermes.mobile.data.SettingsStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsStoreConsistencyTest {
    private fun preferences() = RuntimeEnvironment.getApplication().getSharedPreferences(
        "settings-test-${java.util.UUID.randomUUID()}", Context.MODE_PRIVATE,
    )

    @Test fun unrelatedUpdateFromAnOlderInstanceCannotClearEmergencyStop() {
        val prefs = preferences()
        val ui = SettingsStore(prefs)
        val service = SettingsStore(prefs)
        assertFalse(service.current().agentEmergencyStop)
        ui.update { it.copy(agentEmergencyStop = true) }
        service.update { it.copy(fontScale = 1.2f) }
        assertTrue(SettingsStore(prefs).current().agentEmergencyStop)
        assertTrue(ui.current().agentEmergencyStop)
        assertEquals(1.2f, ui.current().fontScale)
    }

    @Test fun authorizationReadSeesStopBeforeMainThreadPreferenceCallbacks() {
        val prefs = preferences()
        val service = SettingsStore(prefs)
        val ui = SettingsStore(prefs)
        val worker = Thread { ui.update { it.copy(agentEmergencyStop = true) } }
        worker.start(); worker.join(3000)
        assertFalse(worker.isAlive)
        // Deliberately don't drain the Android main looper.
        assertTrue(service.current().agentEmergencyStop)
    }

    @Test fun concurrentStoresPreserveIndependentChanges() {
        val prefs = preferences()
        val stores = listOf(SettingsStore(prefs), SettingsStore(prefs))
        val start = CountDownLatch(1)
        val finished = CountDownLatch(2)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        stores.forEachIndexed { index, store -> Thread {
            try {
                check(start.await(3, TimeUnit.SECONDS))
                repeat(25) { store.update { s ->
                    s.copy(modelUsage = s.modelUsage + ("p$index" to ((s.modelUsage["p$index"] ?: 0) + 1)))
                } }
            } catch (error: Throwable) { failures.add(error) }
            finally { finished.countDown() }
        }.start() }
        start.countDown()
        assertTrue(finished.await(10, TimeUnit.SECONDS))
        assertTrue(failures.toString(), failures.isEmpty())
        assertEquals(mapOf("p0" to 25, "p1" to 25), SettingsStore(prefs).current().modelUsage)
    }
}
