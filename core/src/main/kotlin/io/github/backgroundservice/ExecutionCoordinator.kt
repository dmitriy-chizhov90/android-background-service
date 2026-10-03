package io.github.backgroundservice

enum class ExecutionMode { DISABLED, ACTIVE, FGS, WORKER }
enum class ComponentStatus { STOPPED, STARTING, RUNNING, FAILED }
data class ExecutionState(
    val enabled: Boolean = false,
    val active: Boolean = false,
    val service: ComponentStatus = ComponentStatus.STOPPED,
    val mode: ExecutionMode = ExecutionMode.DISABLED,
    val generation: Long = 0,
    val reason: String = "initial",
)

/** A host implements effects; no network, Android or application payload enters this contract. */
interface ExecutionHost {
    fun activate()
    fun deactivate()
    fun useForegroundService()
    fun scheduleWorker()
    fun cancelWorker()
    fun stopForegroundService()
}
fun interface StateStore { fun save(state: ExecutionState) }

/** Serial transitions and generation checks protect handoff against late callbacks.
 * The host must synchronously release the previous execution lease before acquiring another.
 * OS scheduling is deliberately separate from actual execution.
 */
class ExecutionCoordinator(
    private val host: ExecutionHost,
    private val store: StateStore,
    initial: ExecutionState = ExecutionState(),
) {
    var state: ExecutionState = initial
        private set

    @Synchronized fun configure(enabled: Boolean, reason: String = "configured") {
        transition(state.copy(enabled = enabled), reason)
    }
    @Synchronized fun applicationActive(active: Boolean) {
        transition(state.copy(active = active), if (active) "application_active" else "application_background")
    }
    @Synchronized fun serviceObserved(status: ComponentStatus, generation: Long = state.generation, reason: String = status.name) {
        if (generation != state.generation) return
        transition(state.copy(service = status), reason)
    }
    @Synchronized fun processStarted(enabled: Boolean) {
        transition(ExecutionState(enabled = enabled, generation = state.generation + 1), "process_started", true)
    }
    @Synchronized fun reconcile() = transition(state, "reconcile", true)

    private fun transition(next: ExecutionState, reason: String, force: Boolean = false) {
        val mode = when {
            !next.enabled -> ExecutionMode.DISABLED
            next.active -> ExecutionMode.ACTIVE
            next.service == ComponentStatus.RUNNING -> ExecutionMode.FGS
            else -> ExecutionMode.WORKER
        }
        val previous = state.mode
        state = next.copy(mode = mode, reason = reason)
        store.save(state)
        if (previous == mode && !force) return
        // Withdraw old ownership before issuing commands for the new owner.
        if (previous == ExecutionMode.ACTIVE || force) host.deactivate()
        when (mode) {
            ExecutionMode.DISABLED -> { host.cancelWorker(); host.stopForegroundService() }
            ExecutionMode.ACTIVE -> { host.cancelWorker(); host.activate() }
            ExecutionMode.FGS -> { host.cancelWorker(); host.useForegroundService() }
            ExecutionMode.WORKER -> host.scheduleWorker()
        }
    }
}
