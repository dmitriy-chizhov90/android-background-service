package io.github.backgroundservice.permissions

import android.content.Context

/** User acknowledgement is never evidence that the manufacturer enabled autostart. */
class OemGuidance(context: Context, preferencesName: String = "background.oem") {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    val shown: Boolean get() = prefs.getBoolean("oem_dialog_shown_v1", false)
    val confirmedByUser: Boolean get() = prefs.getBoolean("confirmed_by_user", false)
    val lastOpenedMillis: Long get() = prefs.getLong("last_opened", 0)
    fun complete(opened: Boolean, confirmed: Boolean = false) {
        prefs.edit().putBoolean("oem_dialog_shown_v1", true)
            .putBoolean("confirmed_by_user", confirmed).apply {
                if (opened) putLong("last_opened", System.currentTimeMillis())
            }.commit()
    }
}
