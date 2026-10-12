package io.github.backgroundservice.diagnostics

import android.app.Activity
import android.app.AlertDialog
import android.widget.Toast
import io.github.backgroundservice.permissions.*

/** Optional shared presentation; applications may render the same controller in their own UI. */
object PermissionSettingsDialog {
    fun show(activity: Activity, controller: PermissionSettingsController) {
        controller.refresh()
        val capabilities = controller.capabilities.toList()
        val entries = capabilities.map { capability ->
            "${title(capability)}: ${status(controller.state.capabilities.getValue(capability).grantState)}"
        }.toMutableList()
        if (capabilities.isNotEmpty() && OemSettings.needsAutostartPrompt()) entries += if (controller.oem.confirmedByUser)
            "Autostart: confirmed by you (cannot be verified)" else "Autostart: check manufacturer settings"
        AlertDialog.Builder(activity).setTitle("Background work and notifications")
            .setItems(entries.toTypedArray()) { _, index ->
                if (index < capabilities.size) {
                    val capability = capabilities[index]
                    val dialog = AlertDialog.Builder(activity).setTitle(title(capability)).setMessage(explanation(capability))
                    if (controller.state.capabilities[capability]?.grantState != PermissionGrantState.NOT_APPLICABLE) {
                        dialog.setPositiveButton("Open settings / Allow") { _, _ -> controller.request(capability) }
                    }
                    dialog.setNegativeButton("Close", null).show()
                } else {
                    AlertDialog.Builder(activity).setTitle("Autostart")
                        .setMessage("Allow autostart and background activity in your manufacturer's settings. Android cannot verify this setting.")
                        .setPositiveButton("Open settings") { _, _ ->
                            if (!controller.openOemSettings()) Toast.makeText(activity, "Settings are unavailable on this device", Toast.LENGTH_LONG).show()
                        }
                        .setNeutralButton("I checked the setting") { _, _ -> controller.oem.complete(false, true) }
                        .setNegativeButton("Later") { _, _ -> controller.oem.complete(false) }.show()
                }
            }.setNegativeButton("Close", null).show()
    }

    private fun title(capability: PermissionCapability): String = when (capability) {
        PermissionCapability.POST_NOTIFICATIONS -> "Notifications"
        PermissionCapability.NOTIFICATION_CHANNELS -> "Notification channels"
        PermissionCapability.EXACT_ALARMS -> "Exact reminder timing"
        PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION -> "Battery optimization exemption"
    }
    private fun status(state: PermissionGrantState): String = when (state) {
        PermissionGrantState.GRANTED -> "allowed"
        PermissionGrantState.DENIED -> "restricted"
        PermissionGrantState.UNKNOWN -> "not checked"
        PermissionGrantState.NOT_APPLICABLE -> "not required"
    }
    private fun explanation(capability: PermissionCapability): String = when (capability) {
        PermissionCapability.POST_NOTIFICATIONS -> "Notifications are needed to show reminders and messages."
        PermissionCapability.NOTIFICATION_CHANNELS -> "A disabled channel prevents its notifications from appearing. Sound and visibility are controlled by Android."
        PermissionCapability.EXACT_ALARMS -> "Without exact alarm access, scheduled reminders can arrive late."
        PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION -> "Battery optimization can delay background work. An exemption is optional and does not guarantee continuous execution."
    }
}
