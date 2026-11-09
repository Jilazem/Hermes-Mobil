package com.hermes.mobile

import android.app.Application
import com.hermes.mobile.data.HermesSession
import com.hermes.mobile.data.SessionCache
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SessionCacheV75Test {
    @Test fun offlineSessionInventoryIsNotTruncatedToOneHundred() {
        val cache=SessionCache(RuntimeEnvironment.getApplication())
        val sessions=(0 until 1500).map {HermesSession(id="archive-$it")}
        cache.save("pagination-test",sessions)
        assertEquals(sessions,cache.load("pagination-test")?.sessions)
        cache.clear("pagination-test")
    }
}
