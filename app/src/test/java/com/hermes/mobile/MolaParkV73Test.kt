package com.hermes.mobile

import android.content.Intent
import android.net.Uri
import com.hermes.mobile.car.MolaWebPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MolaParkV73Test {
    @Test fun youtubeNavigationRejectsExternalAndCredentialUrls() {
        listOf("https://m.youtube.com/watch?v=test","https://www.youtube.com","https://youtu.be/test").forEach {
            assertTrue(it,MolaWebPolicy.allows(Uri.parse(it)))
        }
        listOf("http://youtube.com","https://youtube.com.attacker.invalid","javascript:alert(1)","file:///sdcard/test","https://user:pass@youtube.com","https://youtube.com:8443").forEach {
            assertFalse(it,MolaWebPolicy.allows(Uri.parse(it)))
        }
    }
    @Test fun parkedAndYoutubeEntriesHaveOwnIdentityAndNoDrivingExemption() {
        val app=RuntimeEnvironment.getApplication()
        assertEquals(android.content.pm.ApplicationInfo.CATEGORY_GAME,app.applicationInfo.category)
        val intent=Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.CAR_LAUNCHER").setPackage(app.packageName)
        val activities=app.packageManager.queryIntentActivities(intent,android.content.pm.PackageManager.GET_META_DATA)
        val names=activities.map { it.activityInfo.name }
        assertTrue(names.any { it.endsWith(".MainActivity") })
        assertTrue(names.any { it.endsWith(".YouTubeAlias") })
        activities.forEach { assertFalse(it.activityInfo.metaData?.getBoolean("distractionOptimized",false) ?: false) }
    }
}
