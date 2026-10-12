package io.github.backgroundservice.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class PlatformPermissionCapability {
    POST_NOTIFICATIONS,
    BATTERY_OPTIMIZATION_EXEMPTION,
    EXACT_ALARMS,
    NOTIFICATION_CHANNELS,
}

enum class PlatformPermissionGrant {
    UNKNOWN,
    GRANTED,
    DENIED,
    NOT_APPLICABLE,
}

data class ObservedPermission(
    val grant: PlatformPermissionGrant,
    val canRequestAgain: Boolean,
)

interface PermissionEffectExecutor {
    fun observe(
        capability: PlatformPermissionCapability,
        hasRequestedBefore: Boolean,
    ): ObservedPermission

    fun requestRuntimePermission(capability: PlatformPermissionCapability)

    fun openPermissionSettings(capability: PlatformPermissionCapability)
}

class ActivityPermissionPlatform(
    activity: Activity,
    private val channelIds: List<String> = emptyList(),
) : PermissionEffectExecutor {
    private val reference = java.lang.ref.WeakReference(activity)
    private val activity: Activity get() = checkNotNull(reference.get())

    override fun observe(
        capability: PlatformPermissionCapability,
        hasRequestedBefore: Boolean,
    ): ObservedPermission = when (capability) {
        PlatformPermissionCapability.POST_NOTIFICATIONS ->
            observeNotificationPermission(hasRequestedBefore)
        PlatformPermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION -> observeBatteryPermission()
        PlatformPermissionCapability.EXACT_ALARMS -> ObservedPermission(
            if (Build.VERSION.SDK_INT < 31) PlatformPermissionGrant.NOT_APPLICABLE
            else grant(io.github.backgroundservice.android.BackgroundCapabilities.read(activity).exactAlarms), false,
        )
        PlatformPermissionCapability.NOTIFICATION_CHANNELS -> ObservedPermission(
            if (channelIds.isEmpty()) PlatformPermissionGrant.NOT_APPLICABLE
            else channelGrant(), false,
        )
    }

    private fun channelGrant(): PlatformPermissionGrant {
        val manager = activity.getSystemService(android.app.NotificationManager::class.java)
        val channels = channelIds.map(manager::getNotificationChannel)
        if (channels.any { it?.importance == android.app.NotificationManager.IMPORTANCE_NONE }) return PlatformPermissionGrant.DENIED
        if (channels.any { it == null }) return PlatformPermissionGrant.UNKNOWN
        return PlatformPermissionGrant.GRANTED
    }

    private fun grant(enabled: Boolean) = if (enabled) PlatformPermissionGrant.GRANTED else PlatformPermissionGrant.DENIED

    private fun observeBatteryPermission(): ObservedPermission {
        val powerManager = activity.getSystemService(Context.POWER_SERVICE) as PowerManager
        return ObservedPermission(
            grant = if (powerManager.isIgnoringBatteryOptimizations(activity.packageName)) {
                PlatformPermissionGrant.GRANTED
            } else {
                PlatformPermissionGrant.DENIED
            },
            canRequestAgain = true,
        )
    }

    override fun requestRuntimePermission(capability: PlatformPermissionCapability) {
        require(capability == PlatformPermissionCapability.POST_NOTIFICATIONS) {
            "$capability is not a runtime permission"
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NOTIFICATION_PERMISSION_REQUEST_CODE,
        )
    }

    override fun openPermissionSettings(capability: PlatformPermissionCapability) {
        when (capability) {
            PlatformPermissionCapability.POST_NOTIFICATIONS -> openNotificationSettings()
            PlatformPermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION -> openBatterySettings()
            PlatformPermissionCapability.EXACT_ALARMS -> {
                if (Build.VERSION.SDK_INT >= 31) openSafely(io.github.backgroundservice.android.BackgroundCapabilities.exactAlarmSettings(activity))
            }
            PlatformPermissionCapability.NOTIFICATION_CHANNELS -> {
                val blocked = channelIds.firstOrNull { !io.github.backgroundservice.android.BackgroundCapabilities.read(activity, it).channelEnabled }
                openSafely(io.github.backgroundservice.android.BackgroundCapabilities.notificationSettings(activity, blocked))
            }
        }
    }

    private fun observeNotificationPermission(hasRequestedBefore: Boolean): ObservedPermission {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            val enabled = io.github.backgroundservice.android.BackgroundCapabilities.read(activity).notifications
            return ObservedPermission(
                if (enabled) PlatformPermissionGrant.NOT_APPLICABLE else PlatformPermissionGrant.DENIED,
                canRequestAgain = false,
            )
        }
        return ObservedPermission(
            grant = notificationGrant(),
            canRequestAgain = canRequestNotificationAgain(hasRequestedBefore),
        )
    }

    private fun notificationGrant(): PlatformPermissionGrant = if (notificationsGranted()) {
        PlatformPermissionGrant.GRANTED
    } else {
        PlatformPermissionGrant.DENIED
    }

    private fun notificationsGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED &&
            io.github.backgroundservice.android.BackgroundCapabilities.read(activity).notifications

    private fun canRequestNotificationAgain(hasRequestedBefore: Boolean): Boolean =
        !hasRequestedBefore || ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        )

    private fun openNotificationSettings() {
        openSafely(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
            },
        )
    }

    @SuppressLint("BatteryLife")
    private fun openBatterySettings() {
        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${activity.packageName}"),
        )
        val declared = ContextCompat.checkSelfPermission(activity, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS) == PackageManager.PERMISSION_GRANTED
        openSafely(if (declared) directIntent else Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun openSafely(intent: Intent) {
        try { activity.startActivity(intent) } catch (_: android.content.ActivityNotFoundException) {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
        } catch (_: SecurityException) {
            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
        }
    }

    companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
