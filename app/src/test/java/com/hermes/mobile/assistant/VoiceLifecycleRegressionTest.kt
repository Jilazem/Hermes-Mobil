package com.hermes.mobile.assistant

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Looper
import com.hermes.mobile.data.DrivingModeService
import com.hermes.mobile.data.VoiceSessions
import com.hermes.mobile.data.VoiceSessionOwners
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class VoiceLifecycleRegressionTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val voices = mutableListOf<FakeVoice>()
    private val engines = mutableListOf<JarvisEngine>()
    private fun engine(): JarvisEngine = JarvisEngine(context, voiceFactory = {
        FakeVoice().also { voices += it }
    }, settingsProvider = { com.hermes.mobile.data.AppSettings() }, onHide = {}).also { engines += it }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    @After fun cleanup() { engines.forEach { it.release() }; VoiceSessions.owners.stopAll(); WakeWordControl.resume() }

    @Test fun ttsErrorIsTerminalAndLateDoneCannotRestartListening() {
        val engine = engine()
        engine.ask("teşekkür ederim")
        val voice = voices.last()
        val done = voice.onDone!!
        val level = voice.onLevel!!
        voice.onError!!("EMA sentetik hata")
        idle()
        assertEquals(JarvisPhase.Error, engine.state.value.phase)
        assertTrue(voice.stops > 0)
        assertEquals(0, VoiceSessions.owners.count)
        done(); level(0.9f); idle()
        assertEquals(JarvisPhase.Error, engine.state.value.phase)
        assertEquals(0f, engine.state.value.level)
    }
    @Test fun oldVoiceErrorCannotFailReplacementTurn() {
        val engine = engine()
        engine.ask("teşekkür ederim")
        val oldError = voices.last().onError!!
        engine.ask("teşekkür ederim")
        oldError("gecikmiş hata"); idle()
        assertEquals(JarvisPhase.Speaking, engine.state.value.phase)
        assertEquals(1, VoiceSessions.owners.count)
    }
    @Test fun deniedFocusPreventsVoiceAndReleasesLease() {
        val audio = context.getSystemService(AudioManager::class.java)
        shadowOf(audio).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        val engine = engine()
        engine.ask("teşekkür ederim")
        assertEquals(JarvisPhase.Error, engine.state.value.phase)
        assertTrue(voices.all { it.sentences == 0 })
        assertEquals(0, VoiceSessions.owners.count)
    }
    @Test fun focusLossStopsVoiceAndOldFocusLossCannotKillNewTurn() {
        val audio = context.getSystemService(AudioManager::class.java)
        val engine = engine()
        engine.ask("teşekkür ederim")
        val oldRequest = shadowOf(audio).lastAudioFocusRequest
        oldRequest.listener!!.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        idle()
        assertEquals(JarvisPhase.Error, engine.state.value.phase)
        engine.ask("teşekkür ederim")
        oldRequest.listener!!.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        idle()
        assertEquals(JarvisPhase.Speaking, engine.state.value.phase)
    }
    @Test fun notificationStopCancelsEveryOwnerAndPhoneToIdle() {
        val engine = engine()
        engine.ask("teşekkür ederim")
        var liveStopped = false
        var carStopped = false
        DrivingModeService.acquire(context, "live-test") { liveStopped = true }
        DrivingModeService.acquire(context, "car-test", true) { carStopped = true }
        val service = Robolectric.buildService(DrivingModeService::class.java).create().get()
        service.onStartCommand(Intent(context, DrivingModeService::class.java).setAction(DrivingModeService.ACTION_STOP), 0, 1)
        assertTrue(liveStopped); assertTrue(carStopped)
        assertEquals(JarvisPhase.Idle, engine.state.value.phase)
        assertEquals(0, VoiceSessions.owners.count)
        assertFalse(WakeWordControl.paused.value)
        service.onDestroy()
    }
    @Test fun oneForegroundOwnerReleaseDoesNotReleaseAnotherOrWakeword() {
        DrivingModeService.acquire(context, "car-test", true) {}
        DrivingModeService.acquire(context, "live-test") {}
        DrivingModeService.stop(context, "car-test")
        assertTrue(VoiceSessions.owners.foreground)
        assertFalse(VoiceSessions.owners.driving)
        assertTrue(WakeWordControl.paused.value)
        DrivingModeService.stop(context, "car-test") // stale release is harmless
        assertEquals(1, VoiceSessions.owners.count)
        DrivingModeService.stop(context, "live-test")
        assertFalse(VoiceSessions.owners.foreground)
        assertFalse(WakeWordControl.paused.value)
    }
    @Test fun serviceRemainsForegroundUntilLastOwnerAndRejectsOwnerlessRestart() {
        val controller = Robolectric.buildService(DrivingModeService::class.java).create()
        val service = controller.get()
        DrivingModeService.acquire(context, "car-test", true) {}
        DrivingModeService.acquire(context, "live-test") {}
        service.onStartCommand(Intent(context, DrivingModeService::class.java), 0, 1)
        assertTrue(shadowOf(service).isLastForegroundNotificationAttached)
        DrivingModeService.stop(context, "car-test")
        service.onStartCommand(Intent(context, DrivingModeService::class.java), 0, 2)
        assertTrue(shadowOf(service).isLastForegroundNotificationAttached)
        assertEquals(1, VoiceSessions.owners.count)
        DrivingModeService.stop(context, "live-test")
        service.onStartCommand(null, 0, 3)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }
    @Test fun wakeHandoffWaitsForRecorderRelease() = kotlinx.coroutines.runBlocking {
        WakeWordControl.recording = true
        try {
            val timedOut = kotlinx.coroutines.withTimeoutOrNull(30) { WakeWordControl.awaitIdle(); true }
            assertNull(timedOut)
            WakeWordControl.recording = false
            assertTrue(kotlinx.coroutines.withTimeout(500) { WakeWordControl.awaitIdle(); true })
        } finally { WakeWordControl.recording = false }
    }
    @Test fun failingCancelDoesNotSkipOtherOwnerCleanup() {
        val owners = VoiceSessionOwners()
        var stopped = false
        owners.acquire("bad", true, false) { error("cleanup failure") }
        owners.acquire("good", true, true) { stopped = true; owners.release("good") }
        owners.stopAll(); owners.stopAll()
        assertTrue(stopped); assertEquals(0, owners.count)
        assertFalse(owners.paused.value)
    }
    @Test fun wakeTriggerResumeCannotUnpauseOtherOwners() {
        WakeWordControl.pause()
        VoiceSessions.owners.acquire("car-test", true, true) {}
        WakeWordControl.resume()
        assertTrue(WakeWordControl.paused.value)
        VoiceSessions.owners.release("car-test")
        assertFalse(WakeWordControl.paused.value)
    }
    private class FakeVoice : JarvisVoice {
        override var onDone: (() -> Unit)? = null
        override var onLevel: ((Float) -> Unit)? = null
        override var onError: ((String) -> Unit)? = null
        var stops = 0
        var sentences = 0
        override fun say(sentence: String) { sentences++ }
        override fun finish() {}
        override fun stop() { stops++ }
        override fun release() { stop() }
    }
}
