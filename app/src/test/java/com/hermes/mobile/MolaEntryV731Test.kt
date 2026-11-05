package com.hermes.mobile

import android.app.Application
import android.content.Intent
import android.content.ComponentName
import android.content.pm.PackageManager
import com.hermes.mobile.car.ParkedMolaActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class MolaEntryV731Test {
    @Test fun carDescriptorDoesNotAdvertiseAnEmptyMessagingInboxAndPhoneLauncherStillExists() {
        val app=RuntimeEnvironment.getApplication()
        val parser=app.resources.getXml(R.xml.automotive_app_desc)
        val uses=mutableSetOf<String>()
        while(parser.eventType!=XmlPullParser.END_DOCUMENT) {
            if(parser.eventType==XmlPullParser.START_TAG && parser.name=="uses") uses+=parser.getAttributeValue(null,"name")
            parser.next()
        }
        parser.close()
        assertEquals(setOf("service","projection"),uses)
        val phone=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app.packageName)
        assertTrue(app.packageManager.queryIntentActivities(phone,0).any {it.activityInfo.name==MainActivity::class.java.name})
        val parked=app.packageManager.getActivityInfo(ComponentName(app,ParkedMolaActivity::class.java),0)
        assertFalse(parked.exported)
        assertTrue(parked.taskAffinity.endsWith(".parked"))
    }
    @Test fun primaryHermesCarLaunchOpensParkedUiAndRouterLifecycleDoesNotCreatePhoneChat() {
        val app=RuntimeEnvironment.getApplication()
        val intent=Intent(app,MainActivity::class.java).addCategory("android.intent.category.CAR_LAUNCHER")
        val controller=Robolectric.buildActivity(MainActivity::class.java,intent).create()
        val activity=controller.get()
        assertTrue(activity.isFinishing)
        assertEquals(ParkedMolaActivity::class.java.name,shadowOf(activity).nextStartedActivity.component?.className)
        controller.start().resume().pause().stop().destroy()
    }
}
