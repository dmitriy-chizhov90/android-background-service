package io.github.backgroundservice.permissions

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PermissionPlatformTest {
    @Test fun `denial after prior request redirects to settings`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(activity.application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val platform = ActivityPermissionPlatform(activity)
        val observed = platform.observe(PlatformPermissionCapability.POST_NOTIFICATIONS, true)
        assertEquals(PlatformPermissionGrant.DENIED, observed.grant)
        assertFalse(observed.canRequestAgain)
        platform.openPermissionSettings(PlatformPermissionCapability.POST_NOTIFICATIONS)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, shadowOf(activity).nextStartedActivity.action)
    }

    @Test fun `disabled channel is observed independently and opens that channel`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("reminders", "Reminders", NotificationManager.IMPORTANCE_NONE),
        )
        val platform = ActivityPermissionPlatform(activity, listOf("reminders"))
        assertEquals(PlatformPermissionGrant.DENIED, platform.observe(PlatformPermissionCapability.NOTIFICATION_CHANNELS, false).grant)
        platform.openPermissionSettings(PlatformPermissionCapability.NOTIFICATION_CHANNELS)
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals("reminders", intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test @Config(sdk = [26]) fun `old Android does not offer runtime notification permission or exact alarms`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val platform = ActivityPermissionPlatform(activity)
        assertFalse(platform.observe(PlatformPermissionCapability.POST_NOTIFICATIONS, false).canRequestAgain)
        assertEquals(PlatformPermissionGrant.NOT_APPLICABLE, platform.observe(PlatformPermissionCapability.EXACT_ALARMS, false).grant)
    }

    @Test @Config(sdk = [31]) fun `exact alarm changes are read again after returning from settings`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val platform = ActivityPermissionPlatform(activity)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertEquals(PlatformPermissionGrant.DENIED, platform.observe(PlatformPermissionCapability.EXACT_ALARMS, false).grant)
        platform.openPermissionSettings(PlatformPermissionCapability.EXACT_ALARMS)
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, shadowOf(activity).nextStartedActivity.action)
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertEquals(PlatformPermissionGrant.GRANTED, platform.observe(PlatformPermissionCapability.EXACT_ALARMS, true).grant)
    }

    @Test fun `v1 history is migrated without resetting prior attempts`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.getSharedPreferences("permission_domain_state", Context.MODE_PRIVATE).edit().putString("state_v1",
            """{"capabilities":[{"capability":"POST_NOTIFICATIONS","grantState":"DENIED","canRequestAgain":false,"lastObservedMillis":5,"lastRequestMillis":3}],"requestInFlight":"POST_NOTIFICATIONS","warningCapabilities":["POST_NOTIFICATIONS"]}"""
        ).commit()
        val persistence = SharedPreferencesPermissionStatePersistence(activity)
        val state = persistence.load()
        assertEquals(3L, state.capabilities.getValue(PermissionCapability.POST_NOTIFICATIONS).lastRequestMillis)
        persistence.save(state)
        assertEquals(state, persistence.load())
    }

    @Test fun `OEM acknowledgement never claims a verified grant and preserves legacy shown flag`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.getSharedPreferences("onboarding_prefs", Context.MODE_PRIVATE).edit().putBoolean("oem_dialog_shown_v1", true).commit()
        val guidance = OemGuidance(activity, "onboarding_prefs")
        assertTrue(guidance.shown)
        assertFalse(guidance.confirmedByUser)
        guidance.complete(true)
        assertFalse(guidance.confirmedByUser)
        assertTrue(guidance.lastOpenedMillis > 0)
    }
}
