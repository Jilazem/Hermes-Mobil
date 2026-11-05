package com.hermes.mobile

import com.hermes.mobile.data.ShellProcessRunner
import com.hermes.mobile.data.ShizukuBridge
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ShizukuV73Test {
    @Test fun readinessIsFreshForShortLivedVoiceToolInstances() {
        var status = ShizukuBridge.State.Ready
        val bridge = ShizukuBridge { status }
        assertTrue(bridge.isReady)
        status = ShizukuBridge.State.NeedsPermission
        assertFalse(bridge.isReady)
        status = ShizukuBridge.State.Ready
        assertTrue(bridge.isReady)
    }
    @Test fun failedProcessIsNotReportedAsCompleted() {
        val p = FakeProcess("", "permission denied", 7)
        val failure = runCatching { ShellProcessRunner.run(p) }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty().contains("7"))
        assertTrue(p.destroyed)
    }
    @Test fun bothStreamsDrainAndSuccessfulOutputIsPreserved() {
        val p = FakeProcess("output", "diagnostic", 0)
        assertEquals("output\ndiagnostic",ShellProcessRunner.run(p))
        assertTrue(p.destroyed)
    }
    @Test fun oversizedOutputTerminatesInsteadOfBlocking() {
        val p = FakeProcess("x".repeat(1000),"",0)
        assertTrue(runCatching { ShellProcessRunner.run(p,limit=20) }.isFailure)
        assertTrue(p.destroyed)
    }
    @Test fun timedOutAndCancelledCommandsAreDestroyed() {
        val p = FakeProcess("","",0,neverEnds=true)
        assertTrue(runCatching { ShellProcessRunner.run(p,timeoutMs=20) }.isFailure)
        assertTrue(p.destroyed)
        val p2 = FakeProcess("","",0,neverEnds=true)
        assertTrue(runCatching { ShellProcessRunner.run(p2,cancelled={true}) }.isFailure)
        assertTrue(p2.destroyed)
    }
    private class FakeProcess(out: String,err: String,private val code: Int,private val neverEnds: Boolean = false): Process() {
        var destroyed = false
        private val output = ByteArrayInputStream(out.toByteArray())
        private val error = ByteArrayInputStream(err.toByteArray())
        override fun getInputStream() = output
        override fun getErrorStream() = error
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor() = code
        override fun waitFor(timeout: Long,unit: TimeUnit) = !neverEnds
        override fun exitValue() = code
        override fun destroy() { destroyed = true }
    }
}
