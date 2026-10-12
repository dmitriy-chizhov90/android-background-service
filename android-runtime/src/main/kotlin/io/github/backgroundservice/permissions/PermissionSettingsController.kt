package io.github.backgroundservice.permissions

import android.app.Activity
import io.github.backgroundservice.android.BackgroundDiagnostics

/** Activity-scoped coordinator. All requests are explicit; resume always reads Android again. */
class PermissionSettingsController(
    private val activity: Activity,
    namespace: String,
    channelIds: List<String> = emptyList(),
    private val enabledCapabilities: () -> Set<PermissionCapability> = { PermissionCapability.entries.toSet() },
    private val onChanged: (PermissionManagerState) -> Unit = {},
) {
    private val persistence = SharedPreferencesPermissionStatePersistence(activity, "background.permissions.$namespace")
    private val diagnostics = BackgroundDiagnostics(activity, namespace)
    private val platform = ActivityPermissionPlatform(activity, channelIds)
    private val adapter = DomainPermissionEffectExecutorAdapter(platform)
    private var manager = PermissionManager(persistence.load(), persistence, enabledCapabilities())
    val state: PermissionManagerState get() = manager.state
    val capabilities: Set<PermissionCapability> get() = enabledCapabilities()
    val oem = OemGuidance(activity, "background.oem.$namespace")

    fun refresh() {
        // Completing or abandoning external UI never implies a grant.
        manager = PermissionManager(persistence.load(), persistence, enabledCapabilities())
        execute(manager.dispatch(PermissionEvent.ApplicationStarted))
    }

    fun request(capability: PermissionCapability) {
        if (activity.isFinishing || activity.isDestroyed || state.requestInFlight != null) return
        diagnostics.record("permission", "request", capability.name)
        if (state.capabilities[capability]?.grantState == PermissionGrantState.DENIED) {
            execute(manager.dispatch(PermissionEvent.ResolutionRequested(capability)))
        } else if (capability in enabledCapabilities()) {
            try { platform.openPermissionSettings(PlatformPermissionCapability.valueOf(capability.name)) }
            catch (error: RuntimeException) { diagnostics.record("permission", "request_failed", error.javaClass.simpleName) }
        }
    }

    fun openOemSettings(): Boolean {
        val opened = OemSettings.openAutostartSettings(activity)
        oem.complete(opened)
        diagnostics.record("permission", "oem_settings", "opened=$opened; verified=false")
        return opened
    }

    private fun execute(effects: List<PermissionEffect>) {
        val queue = ArrayDeque(effects)
        while (queue.isNotEmpty()) {
            val effect = queue.removeFirst()
            try {
                adapter.execute(effect, manager.state).forEach { event ->
                    if (event is PermissionEvent.PermissionObserved) {
                        diagnostics.record("permission", "observed", "${event.capability}=${event.grantState}")
                    }
                    queue.addAll(manager.dispatch(event))
                }
            } catch (error: RuntimeException) {
                diagnostics.record("permission", "request_failed", error.javaClass.simpleName)
                val pending = manager.state.requestInFlight
                if (pending != null) queue.addAll(manager.dispatch(
                    PermissionEvent.PermissionRequestResult(pending, System.currentTimeMillis()),
                ))
            }
        }
        onChanged(manager.state)
    }
}
