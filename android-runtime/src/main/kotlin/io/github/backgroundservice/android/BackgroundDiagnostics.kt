package io.github.backgroundservice.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bounded persisted history. Only pass operational identifiers, never task contents or credentials. */
class BackgroundDiagnostics(context: Context, private val namespace: String) {
    private val prefs = context.applicationContext.getSharedPreferences("background.diagnostics.$namespace", Context.MODE_PRIVATE)
    fun record(component: String, state: String, reason: String = "") = synchronized(lock) {
        val old = runCatching { JSONArray(prefs.getString("events", "[]")) }.getOrDefault(JSONArray())
        val events = JSONArray()
        for (index in (old.length() - 199).coerceAtLeast(0) until old.length()) events.put(old.get(index))
        events.put(JSONObject().put("at", System.currentTimeMillis()).put("component", component)
            .put("state", state).put("reason", reason.take(256)))
        prefs.edit().putString("events", events.toString()).commit()
        Unit
    }
    fun report(): String = synchronized(lock) {
        "namespace=$namespace\n" + runCatching { JSONArray(prefs.getString("events", "[]")).toString(2) }.getOrDefault("[]")
    }
    companion object { private val lock = Any() }
}
