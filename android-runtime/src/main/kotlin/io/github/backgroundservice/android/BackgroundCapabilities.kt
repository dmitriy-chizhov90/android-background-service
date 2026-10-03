package io.github.backgroundservice.android

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

data class BackgroundCapabilities(
    val notifications: Boolean,
    val channelEnabled: Boolean,
    val exactAlarms: Boolean,
    val batteryExempt: Boolean,
) {
    companion object {
        fun read(context: Context, channelId: String? = null): BackgroundCapabilities {
            val notifications = context.getSystemService(NotificationManager::class.java)
            val channel = channelId?.let(notifications::getNotificationChannel)
            return BackgroundCapabilities(
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
                channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE,
                Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
                context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            )
        }
        fun notificationSettings(context: Context, channelId: String? = null): Intent =
            Intent(if (channelId == null) Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .apply { channelId?.let { putExtra(Settings.EXTRA_CHANNEL_ID, it) } }
        fun exactAlarmSettings(context: Context): Intent =
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
