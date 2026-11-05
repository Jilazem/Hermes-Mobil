package com.hermes.mobile

import android.app.Application
import android.content.Context
import com.hermes.mobile.car.*
import com.hermes.mobile.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MolaProfilesV732Test {
    private val app get() = RuntimeEnvironment.getApplication()
    private val prefs get() = app.getSharedPreferences("car-profile-test", Context.MODE_PRIVATE)
    private fun device(address: String, name: String = "Test car", car: Boolean = true) =
        MolaCarDevice(requireNotNull(MolaCarProfiles.deviceId(address)), name, car)
    @Before fun reset() { prefs.edit().clear().commit() }

    @Test fun reconnectRestoresTheSameProfileAndLastPageAcrossStoreInstances() {
        val car = device("AA:BB:CC:DD:EE:01")
        val store = MolaCarProfiles(prefs)
        val first = requireNotNull(store.resolve(listOf(car), learnCarAudio = true))
        store.setLastPage(first.id, "youtube")
        val next = MolaCarProfiles(prefs)
        assertEquals(first.copy(lastPage = "youtube"), next.resolve(listOf(car.copy(name = "Renamed"))))
        assertEquals(1, next.all().size)
        assertFalse(prefs.all.toString().contains("AA:BB:CC:DD:EE:01"))
    }
    @Test fun differentCarsDoNotSharePreferencesAndAmbiguousConnectionIsNotGuessed() {
        val a = device("AA:BB:CC:DD:EE:01")
        val b = device("AA:BB:CC:DD:EE:02")
        val store = MolaCarProfiles(prefs)
        store.remember(a); store.remember(b)
        store.setLastPage(a.id, "mirror"); store.setLastPage(b.id, "game")
        assertEquals("mirror", store.resolve(listOf(a))?.lastPage)
        assertEquals("game", store.resolve(listOf(b))?.lastPage)
        assertNull(store.resolve(listOf(a,b), true))
        assertNull(store.resolve(emptyList(), true))
    }
    @Test fun headphonesAreNotAutoEnrolledButExplicitOnceOnlyAssociationWorks() {
        val device = device("AA:BB:CC:DD:EE:03", "Audio device", false)
        val store = MolaCarProfiles(prefs)
        assertNull(store.resolve(listOf(device), true))
        assertTrue(store.all().isEmpty())
        store.remember(device)
        assertNotNull(store.resolve(listOf(device)))
        store.forget(device.id)
        assertNull(store.resolve(listOf(device)))
    }
    @Test fun missingPermissionAndCorruptPreferencesHaveSafeFallbacks() = runBlocking {
        assertFalse(MolaConnectedCars.permitted(app))
        assertTrue(MolaConnectedCars.read(app).isEmpty())
        prefs.edit().putString("profiles", "broken").commit()
        assertTrue(MolaCarProfiles(prefs).all().isEmpty())
        assertNull(MolaCarProfiles.deviceId("02:00:00:00:00:00"))
        assertNull(MolaCarProfiles.deviceId(""))
        assertEquals(MolaCarProfiles.deviceId("aa:bb:cc:dd:ee:01"), MolaCarProfiles.deviceId("AA:BB:CC:DD:EE:01"))
    }
    @Test fun unknownActionsCannotBecomeRestoredPagesAndCapacityDoesNotOverwriteSavedCars() {
        val store = MolaCarProfiles(prefs)
        repeat(20) { index -> store.remember(device("AA:BB:CC:DD:EE:%02X".format(index))) }
        assertNull(store.remember(device("AA:BB:CC:DD:EE:FF")))
        assertEquals(20, store.all().size)
        val id = store.all().first().id
        store.setLastPage(id, "disable-driving-restrictions")
        assertEquals("home", store.all().first().lastPage)
    }
    @Test fun everySettingsDestinationHasOneGroupAndPrivacyStaysVisible() {
        val all = SettingsGroups.destinations.values.flatten()
        assertEquals(SettingsCategory.entries.toSet(), all.toSet())
        assertEquals(all.size, all.distinct().size)
        assertTrue(SettingsGroups.destinations.getValue(SettingsGroup.Privacy).contains(SettingsCategory.Privacy))
        assertEquals(listOf(SettingsCategory.Developer), SettingsGroups.destinations[SettingsGroup.Advanced])
    }
}
