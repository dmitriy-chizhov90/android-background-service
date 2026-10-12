package io.github.backgroundservice.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

object OemSettings {

    private const val TAG = "OemHelper"

    enum class Vendor { XIAOMI, HUAWEI, OPPO, VIVO, SAMSUNG, OTHER }

    fun detect(): Vendor = detect(Build.MANUFACTURER, Build.BRAND)

    fun detect(manufacturer: String, brand: String): Vendor {
        val m = manufacturer.lowercase()
        val b = brand.lowercase()
        return when {
            m.contains("xiaomi") || b.contains("xiaomi") || b.contains("redmi") ||
                b.contains("poco") -> Vendor.XIAOMI
            m.contains("huawei") || b.contains("huawei") || b.contains("honor") -> Vendor.HUAWEI
            m.contains("oppo") || b.contains("oppo") || b.contains("realme") -> Vendor.OPPO
            m.contains("vivo") || b.contains("vivo") || b.contains("iqoo") -> Vendor.VIVO
            m.contains("samsung") -> Vendor.SAMSUNG
            else -> Vendor.OTHER
        }
    }

    fun needsAutostartPrompt(): Boolean =
        detect() in setOf(Vendor.XIAOMI, Vendor.HUAWEI, Vendor.OPPO, Vendor.VIVO)

    fun vendorDisplayName(): String = when (detect()) {
        Vendor.XIAOMI -> "Xiaomi"
        Vendor.HUAWEI -> "Huawei"
        Vendor.OPPO -> "Oppo"
        Vendor.VIVO -> "Vivo"
        Vendor.SAMSUNG -> "Samsung"
        Vendor.OTHER -> ""
    }

    fun openAutostartSettings(context: Context): Boolean {
        val intents = intentsFor(detect())
        var success = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                success = true
                break
            } catch (e: Exception) {
                Log.d(TAG, "openAutostartSettings failed for $intent", e)
            }
        }
        if (!success) {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(fallback)
                success = true
            } catch (e: Exception) {
                Log.d(TAG, "openAutostartSettings fallback failed", e)
            }
        }
        return success
    }

    private fun intentsFor(v: Vendor): List<Intent> = when (v) {
        Vendor.XIAOMI -> listOf(
            Intent().setComponent(ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
        )
        Vendor.HUAWEI -> listOf(
            Intent().setComponent(ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"))
        )
        Vendor.OPPO -> listOf(
            Intent().setComponent(ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"))
        )
        Vendor.VIVO -> listOf(
            Intent().setComponent(ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            Intent().setComponent(ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"))
        )
        else -> emptyList()
    }
}
