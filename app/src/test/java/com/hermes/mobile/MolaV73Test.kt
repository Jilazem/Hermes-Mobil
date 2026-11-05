package com.hermes.mobile

import android.app.Application
import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Parcel
import android.os.Looper
import android.text.InputType
import android.media.projection.MediaProjection
import com.hermes.mobile.car.*
import com.hermes.mobile.assistant.AssistContextReader
import com.hermes.mobile.data.ScreenMirror
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MolaV73Test {
    @Test fun legacyBinderRejectsUnknownCallerBeforeReadingDataOrInvokingHost() {
        var invoked = false
        val delegate = object : Binder() { override fun onTransact(code: Int,data: Parcel,reply: Parcel?,flags: Int): Boolean { invoked = true; return true } }
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        try {
            runCatching { CheckedCarBinder(delegate) { false }.transact(1,data,reply,0) }
            assertFalse(invoked)
            assertTrue(CheckedCarBinder(delegate) { true }.transact(1,data,reply,0))
            assertTrue(invoked)
        } finally { data.recycle();reply.recycle() }
    }
    @Test fun projectionRoutesSwitchAtomicallyAndKeepOnlyChosenServiceEnabled() {
        val context = RuntimeEnvironment.getApplication()
        val legacy = ComponentName(context, LegacyMolaCarService::class.java)
        val modern = ComponentName(context, HermesCarService::class.java)
        assertTrue(MolaCarMode.select(context,false).isSuccess)
        assertFalse(MolaCarMode.isClassic(context))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, context.packageManager.getComponentEnabledSetting(modern))
        assertTrue(MolaCarMode.select(context,true).isSuccess)
        assertTrue(MolaCarMode.isClassic(context))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, context.packageManager.getComponentEnabledSetting(modern))
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, context.packageManager.getComponentEnabledSetting(legacy))
    }
    @Test fun diagnosticsDistinguishMissingAutoAndLocallyInstalledCompatibilityPath() {
        assertTrue(MolaPrerequisites(null,null).visibilityAdvice.contains("bulunamadı"))
        val local = MolaPrerequisites("17.9", "com.google.android.packageinstaller").visibilityAdvice
        assertTrue(local.contains("Shizuku") && local.contains("KingInstaller"))
        assertTrue(MolaPrerequisites("17.9","com.android.vending").visibilityAdvice.contains("tek başına"))
    }
    @Test fun screenUrlDropsCredentialsQueriesFragmentsAndNonWebIntents() {
        assertEquals("https://example.com/page",AssistContextReader.publicPageUrl("https://example.com/page?token=secret#fragment"))
        assertNull(AssistContextReader.publicPageUrl("https://name:secret@example.com/private"))
        assertNull(AssistContextReader.publicPageUrl("file:///data/private"))
        assertNull(AssistContextReader.publicPageUrl("intent://anything"))
    }
    @Test fun screenInputPrivacyUsesClassAndVariationRatherThanOverlappingBits() {
        assertTrue(AssistContextReader.sensitiveInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(AssistContextReader.sensitiveInput(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertFalse(AssistContextReader.sensitiveInput(InputType.TYPE_CLASS_NUMBER))
        assertFalse(AssistContextReader.sensitiveInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals("", AssistContextReader.read(null,null))
    }
    @Test fun delayedStopOfOldProjectionCannotDestroyNewUserConsent() {
        val context = RuntimeEnvironment.getApplication()
        val old = FakeProjection(context); val next = FakeProjection(context)
        try {
            ScreenMirror.onProjection(old.value,context)
            ScreenMirror.onProjection(next.value,context)
            old.stopped(); shadowOf(Looper.getMainLooper()).idle()
            assertTrue(ScreenMirror.hasPermission)
            assertEquals(ScreenMirror.Status.Ready,ScreenMirror.status.value)
            next.stopped(); shadowOf(Looper.getMainLooper()).idle()
            assertFalse(ScreenMirror.hasPermission)
        } finally { ScreenMirror.release() }
    }
    private class FakeProjection(context: Context) {
        private var callback: Any? = null
        val value: MediaProjection
        init {
            val type = Class.forName("android.media.projection.IMediaProjection")
            val proxy = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                if (method.name == "start") callback = args!![0]
                when (method.returnType) { java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; else -> null }
            }
            value = MediaProjection::class.java.getDeclaredConstructor(Context::class.java,type)
                .apply { isAccessible = true }.newInstance(context,proxy) as MediaProjection
        }
        fun stopped() {
            val type = Class.forName("android.media.projection.IMediaProjectionCallback")
            type.getMethod("onStop").invoke(requireNotNull(callback))
        }
    }
}
