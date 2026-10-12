package io.github.backgroundservice.permissions

import android.content.Context

object PermissionDiagnostics {
    fun report(context: Context, permissionPreferences: String, oemPreferences: String): String {
        val state = SharedPreferencesPermissionStatePersistence(context, permissionPreferences).load()
        val oem = OemGuidance(context, oemPreferences)
        return "Last observed permissions: $state\n" +
            "OEM autostart: verified=false, guidance_shown=${oem.shown}, " +
            "confirmed_by_user=${oem.confirmedByUser}, last_opened=${oem.lastOpenedMillis}"
    }
}
