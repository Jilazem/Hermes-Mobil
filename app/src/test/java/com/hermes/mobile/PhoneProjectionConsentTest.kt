package com.hermes.mobile

import android.Manifest
import android.app.Application
import com.hermes.mobile.data.MirrorConsentActivity
import com.hermes.mobile.data.ScreenMirror
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PhoneProjectionConsentTest {
    @Test fun foregroundConsentDoesNotRequireNotificationPermissionOrOpenMainActivity() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(ScreenMirror.requestFromPhone(app))
        assertEquals(MirrorConsentActivity::class.java.name, shadowOf(app).nextStartedActivity.component?.className)
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
