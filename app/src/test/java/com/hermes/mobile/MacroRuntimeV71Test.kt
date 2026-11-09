package com.hermes.mobile

import android.media.AudioManager
import android.app.NotificationChannel
import android.app.NotificationManager
import com.hermes.mobile.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MacroRuntimeV71Test {
    @Test fun localMacroDoesNotRequirePhoneAgentCredentialsOrAndroidKeyStore() {
        val context = RuntimeEnvironment.getApplication()
            val rule = MacroRule(name = "Independent local recipe", actions = listOf(MacroAction("volume", "0")))
            MacroStore(context).save(listOf(rule))
            assertTrue(MacroRunner(context).execute(rule.id, manual = true).startsWith("Tamamlandı"))
    }
    @Test fun eventQueueBoundsPendingWorkAndDestroyCancelsItOffMainThread() {
        val caller = Thread.currentThread().id
        val worker = AtomicLong(caller)
        val entered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val observedStop = AtomicBoolean(false)
        val queue = MacroEventQueue(capacity = 1) { _, stopped ->
            calls.incrementAndGet()
            worker.set(Thread.currentThread().id)
            entered.countDown()
            try { release.await(5, TimeUnit.SECONDS) } catch (_: InterruptedException) { }
            observedStop.set(stopped())
            finished.countDown()
        }
        try {
            assertTrue(queue.submit("charging"))
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertNotEquals(caller, worker.get())
            assertTrue(queue.submit("unplugged"))
            assertFalse(queue.submit("headset"))
            queue.close()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertTrue(observedStop.get())
            assertFalse(queue.submit("unlocked"))
            assertEquals(1, calls.get())
        } finally { queue.close(); release.countDown() }
    }
    @Test fun lifecycleStopPreventsRemainingActionsInAnAdmittedRun() {
        val context = RuntimeEnvironment.getApplication()
        val rule = MacroRule(name = "Stop between actions", actions = listOf(MacroAction("volume", "0"), MacroAction("volume", "100")))
        MacroStore(context).save(listOf(rule))
        val audio = context.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
        val checks = AtomicInteger()
        val result = MacroRunner(context, stopped = { checks.incrementAndGet() >= 3 }).execute(rule.id, manual = true)
        assertTrue(result.startsWith("Durdu · 1/2"))
        assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    @Test fun interruptedAtomicWriteRestoresBackupBeforeReturningRules() {
        val context = RuntimeEnvironment.getApplication()
        val rule = MacroRule(name = "Recovered", actions = listOf(MacroAction("volume", "35")))
        val store = MacroStore(context)
        store.save(listOf(rule))
        val file = File(context.filesDir, "macros-v1.json")
        val backup = File(file.path + ".bak")
        assertTrue(file.renameTo(backup))
        assertEquals(listOf(rule), store.rules())
        assertTrue(file.exists())
        assertFalse(backup.exists())
    }
    @Test fun unreadableAtomicBackupCannotBeReplacedByNewEmptyStore() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "macros-v1.json")
        val backup = File(file.path + ".bak")
        backup.writeText("unreadable interrupted original")
        try { MacroStore(context).save(emptyList()); fail("backup overwritten") } catch (_: Exception) { }
        assertEquals("unreadable interrupted original", file.readText())
    }
    @Test fun blockedNotificationChannelPreventsEveryStepBeforeSideEffects() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("hermes-macro-events", "Blocked", NotificationManager.IMPORTANCE_NONE))
        val rule = MacroRule(name = "Channel blocked", actions = listOf(MacroAction("volume", "0"), MacroAction("notify", "Should not silently disappear")))
        MacroStore(context).save(listOf(rule))
        val audio = context.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)
        assertEquals("Makro bildirim kanalı kapalı; uygulama bildirim ayarlarından açın", MacroRunner(context, stopped = { false }).execute(rule.id, manual = true))
        assertEquals(5, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTrue(MacroStore(context).deadlines().isEmpty())
    }
    @Test fun brokenStoreIsPreservedRatherThanReplacedByEmptyList() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "macros-v1.json");file.writeText("broken original")
        try { MacroStore(context).save(emptyList()); fail("corrupt source overwritten") } catch (_: Exception) { }
        assertEquals("broken original",file.readText())
    }
    @Test fun unattendedRunRequiresBothMasterAndRuleSwitch() {
        val context = RuntimeEnvironment.getApplication()
        val rule = MacroRule(name="Pause test",trigger="charging",actions=listOf(MacroAction("volume","0")),enabled=true)
        val store = MacroStore(context);store.save(listOf(rule));store.enabled=false
        val audio=context.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC,5,0)
        assertEquals("Makro duraklatıldı",MacroRunner(context).execute(rule.id))
        assertEquals(5,audio.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
    @Test fun manualNativeActionRunsAndCooldownSurvivesNewRunner() {
        val context = RuntimeEnvironment.getApplication()
        val rule = MacroRule(name="Media",actions=listOf(MacroAction("volume","0")))
        MacroStore(context).save(listOf(rule))
        assertTrue(MacroRunner(context, stopped = { false }).execute(rule.id,manual=true).startsWith("Tamamlandı"))
        val audio=context.getSystemService(AudioManager::class.java)
        assertEquals(0,audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTrue(MacroRunner(context, stopped = { false }).execute(rule.id,manual=true).startsWith("Bekleme süresi"))
    }
    @Test fun emergencyStopPreemptsManualActions() {
        val context = RuntimeEnvironment.getApplication()
        val rule = MacroRule(name="Stop",actions=listOf(MacroAction("volume","0")))
        MacroStore(context).save(listOf(rule))
        assertEquals("Acil durdurma etkin",MacroRunner(context, stopped = { true }).execute(rule.id,manual=true))
    }
}
