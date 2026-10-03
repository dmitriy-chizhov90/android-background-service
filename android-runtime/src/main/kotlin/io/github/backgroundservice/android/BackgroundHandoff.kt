package io.github.backgroundservice.android

/** Hosts preserve their work names and persisted settings during migration. */
interface HandoffActions {
    fun markServiceMode()
    fun markWorkerMode()
    fun cancelFallbackAlarm()
    fun cancelWorker()
    fun scheduleFallbackAlarm(reason: String)
    fun scheduleWorker()
}
class BackgroundHandoff(private val actions: HandoffActions) {
    fun serviceRunning() = synchronized(lock) {
        actions.markServiceMode()
        actions.cancelFallbackAlarm()
        actions.cancelWorker()
    }
    fun serviceStopped(enabled: Boolean, reason: String) = synchronized(lock) {
        if (!enabled) return@synchronized
        actions.markWorkerMode()
        actions.scheduleFallbackAlarm(reason)
        actions.scheduleWorker()
    }
    companion object { private val lock = Any() }
}
