package io.github.backgroundservice.permissions

/** Application-scoped event/effect loop around the persistent domain aggregate. */
class PermissionRuntime(
    private val manager: PermissionManager,
    private val onStateChanged: (PermissionManagerState) -> Unit = {},
) {
    private val queuedEffects = ArrayDeque<PermissionEffect>()
    private var draining = false
    private var executor: DomainPermissionEffectExecutor? = null
    private var pendingSettingsCapability: PermissionCapability? = null

    val warningCapabilities: Set<PermissionCapability>
        get() = manager.state.warningCapabilities

    @Synchronized
    fun processStarted() = dispatch(PermissionEvent.ApplicationStarted)

    @Synchronized
    fun attach(executor: DomainPermissionEffectExecutor) {
        this.executor = executor
        drain()
    }

    @Synchronized
    fun foregrounded() {
        pendingSettingsCapability?.let { capability ->
            pendingSettingsCapability = null
            dispatch((executor ?: return).settingsReturned(capability))
        }
        dispatch(PermissionEvent.ApplicationForegrounded)
    }

    @Synchronized
    fun notificationPermissionRequestCompleted() {
        val capability = PermissionCapability.POST_NOTIFICATIONS
        if (manager.state.requestInFlight != capability) return
        dispatch((executor ?: return).requestCompleted(capability))
    }

    @Synchronized
    fun resolutionRequested(capability: PermissionCapability) {
        dispatch(PermissionEvent.ResolutionRequested(capability))
    }

    private fun dispatch(event: PermissionEvent) {
        queuedEffects.addAll(manager.dispatch(event))
        onStateChanged(manager.state)
        drain()
    }

    private fun execute(executor: DomainPermissionEffectExecutor, effect: PermissionEffect): List<PermissionEvent> {
        return try { executor.execute(effect, manager.state) } catch (_: RuntimeException) {
            val capability = when (effect) {
                is PermissionEffect.OpenPermissionSettings -> effect.capability
                is PermissionEffect.RequestRuntimePermission -> effect.capability
                else -> return emptyList()
            }
            pendingSettingsCapability = null
            listOf(executor.requestCompleted(capability))
        }
    }

    private fun drain() {
        if (draining) return
        val currentExecutor = executor ?: return
        draining = true
        try {
            while (queuedEffects.isNotEmpty()) {
                val effect = queuedEffects.removeFirst()
                when (effect) {
                    is PermissionEffect.ShowDegradedWarning,
                    PermissionEffect.HideDegradedWarning,
                    -> Unit
                    is PermissionEffect.OpenPermissionSettings -> {
                        pendingSettingsCapability = effect.capability
                        execute(currentExecutor, effect).forEach(::dispatch)
                    }
                    else -> execute(currentExecutor, effect).forEach(::dispatch)
                }
            }
        } finally {
            draining = false
        }
    }
}

interface DomainPermissionEffectExecutor {
    fun execute(
        effect: PermissionEffect,
        state: PermissionManagerState,
    ): List<PermissionEvent>

    fun requestCompleted(capability: PermissionCapability): PermissionEvent
    fun settingsReturned(capability: PermissionCapability): PermissionEvent
}
