package io.github.backgroundservice.permissions

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Reads the existing v1 format; explicit keys stay stable in minified release builds. */
class SharedPreferencesPermissionStatePersistence(
    context: Context,
    preferencesName: String = PREFERENCES_NAME,
) : PermissionStatePersistence {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    override fun load(): PermissionManagerState {
        val raw = preferences.getString(KEY_STATE, null) ?: return PermissionManagerState()
        return try {
            val json = JSONObject(raw)
            val restored = mutableMapOf<PermissionCapability, PermissionCapabilityState>()
            val entries = json.optJSONArray("capabilities") ?: JSONArray()
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val capability = enumValue<PermissionCapability>(entry.optString("capability")) ?: continue
                restored[capability] = PermissionCapabilityState(
                    enumValue<PermissionGrantState>(entry.optString("grantState")) ?: PermissionGrantState.UNKNOWN,
                    entry.optBoolean("canRequestAgain", true),
                    entry.optLong("lastObservedMillis"),
                    if (entry.has("lastRequestMillis") && !entry.isNull("lastRequestMillis")) entry.getLong("lastRequestMillis") else null,
                )
            }
            val warnings = json.optJSONArray("warningCapabilities") ?: JSONArray()
            PermissionManagerState(
                PermissionManagerState().capabilities + restored,
                enumValue<PermissionCapability>(json.optString("requestInFlight")),
                (0 until warnings.length()).mapNotNull { enumValue<PermissionCapability>(warnings.optString(it)) }.toSet(),
            )
        } catch (_: org.json.JSONException) {
            PermissionManagerState()
        }
    }

    override fun save(state: PermissionManagerState) {
        val json = JSONObject().put("capabilities", JSONArray().apply {
            state.capabilities.forEach { (key, value) -> put(JSONObject()
                .put("capability", key.name).put("grantState", value.grantState.name)
                .put("canRequestAgain", value.canRequestAgain).put("lastObservedMillis", value.lastObservedMillis)
                .put("lastRequestMillis", value.lastRequestMillis ?: JSONObject.NULL)) }
        }).put("requestInFlight", state.requestInFlight?.name ?: JSONObject.NULL)
            .put("warningCapabilities", JSONArray(state.warningCapabilities.map { it.name }))
        // Request history must reach disk before opening external UI.
        check(preferences.edit().putString(KEY_STATE, json.toString()).commit()) { "Cannot persist permission request history" }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String): T? = enumValues<T>().firstOrNull { it.name == value }

    companion object {
        const val PREFERENCES_NAME = "permission_domain_state"
        const val KEY_STATE = "state_v1"
    }
}
