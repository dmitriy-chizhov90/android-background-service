package io.github.backgroundservice.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context

/** Alarm identity and payload belong to the host. No network is required for delivery. */
class DeadlineScheduler(context: Context) {
    private val alarms = context.applicationContext.getSystemService(AlarmManager::class.java)
    fun schedule(atMillis: Long, operation: PendingIntent): Boolean {
        val exact = android.os.Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()
        if (exact) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
                return true
            } catch (_: SecurityException) { /* Permission may have been revoked since the check. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        return false
    }
    fun cancel(operation: PendingIntent) = alarms.cancel(operation)
}
