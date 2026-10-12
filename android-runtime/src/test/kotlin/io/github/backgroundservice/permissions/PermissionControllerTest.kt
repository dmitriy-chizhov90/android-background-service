package io.github.backgroundservice.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PermissionControllerTest {
    @Test fun `return from settings rechecks actual grant and later revocation`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val app = shadowOf(activity.application)
        app.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val controller = PermissionSettingsController(activity, "return", enabledCapabilities = {
            setOf(PermissionCapability.POST_NOTIFICATIONS)
        })
        controller.refresh()
        assertTrue(PermissionCapability.POST_NOTIFICATIONS in controller.state.warningCapabilities)
        controller.request(PermissionCapability.POST_NOTIFICATIONS)
        assertNotNull(controller.state.requestInFlight)
        app.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        controller.refresh()
        assertNull(controller.state.requestInFlight)
        assertTrue(controller.state.warningCapabilities.isEmpty())
        app.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        controller.refresh()
        assertTrue(PermissionCapability.POST_NOTIFICATIONS in controller.state.warningCapabilities)
        assertNotNull(controller.state.capabilities.getValue(PermissionCapability.POST_NOTIFICATIONS).lastRequestMillis)
        assertNull(controller.state.requestInFlight)
    }

    @Test fun `corrupt or future persistence does not prevent startup`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("future", Context.MODE_PRIVATE)
        prefs.edit().putString("state_v1", "broken").commit()
        val store = SharedPreferencesPermissionStatePersistence(context, "future")
        assertEquals(PermissionManagerState(), store.load())
        prefs.edit().putString("state_v1", """{"capabilities":[{"capability":"FUTURE","grantState":"GRANTED"}]}""").commit()
        assertEquals(PermissionManagerState(), store.load())
    }
}
