package io.github.backgroundservice.permissions



enum class PermissionCapability {
    POST_NOTIFICATIONS,
    BATTERY_OPTIMIZATION_EXEMPTION,
    EXACT_ALARMS,
    NOTIFICATION_CHANNELS,
}

enum class PermissionGrantState {
    UNKNOWN,
    GRANTED,
    DENIED,
    NOT_APPLICABLE,
}

data class PermissionCapabilityState(
    val grantState: PermissionGrantState = PermissionGrantState.UNKNOWN,
    val canRequestAgain: Boolean = true,
    val lastObservedMillis: Long = 0L,
    val lastRequestMillis: Long? = null,
)

data class PermissionManagerState(
    val capabilities: Map<PermissionCapability, PermissionCapabilityState> =
        PermissionCapability.entries.associateWith { PermissionCapabilityState() },
    val requestInFlight: PermissionCapability? = null,
    val warningCapabilities: Set<PermissionCapability> = emptySet(),
)

sealed interface PermissionEvent {
    data object ApplicationStarted : PermissionEvent
    data object ApplicationForegrounded : PermissionEvent

    data class PermissionObserved(
        val capability: PermissionCapability,
        val grantState: PermissionGrantState,
        val canRequestAgain: Boolean,
        val observedAtMillis: Long,
    ) : PermissionEvent

    /** A dialog/settings flow finished; the result is deliberately not trusted as grant state. */
    data class PermissionRequestResult(
        val capability: PermissionCapability,
        val completedAtMillis: Long,
    ) : PermissionEvent

    data class PermissionSettingsReturned(
        val capability: PermissionCapability,
        val completedAtMillis: Long,
    ) : PermissionEvent

    data class ResolutionRequested(val capability: PermissionCapability) : PermissionEvent
}

sealed interface PermissionEffect {
    data class CheckPermissions(val capabilities: Set<PermissionCapability>) : PermissionEffect
    data class RequestRuntimePermission(val capability: PermissionCapability) : PermissionEffect
    data class OpenPermissionSettings(val capability: PermissionCapability) : PermissionEffect
    data class ShowDegradedWarning(val capabilities: Set<PermissionCapability>) : PermissionEffect
    data object HideDegradedWarning : PermissionEffect
}

/**
 * Persistent domain owner of permissions required for reliable push delivery.
 * Platform code reports observed facts and executes effects; it must not infer
 * grants from dialog/activity results.
 */
class PermissionManager(
    initialState: PermissionManagerState,
    private val persistence: PermissionStatePersistence,
    private val capabilities: Set<PermissionCapability> = PermissionCapability.entries.toSet(),
    private val automaticRequests: Set<PermissionCapability> = emptySet(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    var state: PermissionManagerState = initialState.withAllCapabilities()
        private set

    @Synchronized
    fun dispatch(event: PermissionEvent): List<PermissionEffect> {
        val effects = when (event) {
            PermissionEvent.ApplicationStarted -> onApplicationStarted()
            PermissionEvent.ApplicationForegrounded -> onApplicationForegrounded()
            is PermissionEvent.PermissionObserved -> onPermissionObserved(event)
            is PermissionEvent.PermissionRequestResult -> onRequestCompleted(
                event.capability,
                event.completedAtMillis,
            )
            is PermissionEvent.PermissionSettingsReturned -> onRequestCompleted(
                event.capability,
                event.completedAtMillis,
            )
            is PermissionEvent.ResolutionRequested -> requestResolution(event.capability)
        }
        persistence.save(state)
        return effects
    }

    private fun onApplicationStarted(): List<PermissionEffect> {
        // An in-flight platform request cannot survive process death. Re-check it,
        // but keep lastRequestMillis so the system UI is not reopened automatically.
        state = state.copy(requestInFlight = null)
        return listOf(PermissionEffect.CheckPermissions(capabilities))
    }

    private fun onApplicationForegrounded(): List<PermissionEffect> {
        if (state.requestInFlight != null) return emptyList()
        return listOf(PermissionEffect.CheckPermissions(capabilities))
    }

    private fun onPermissionObserved(event: PermissionEvent.PermissionObserved): List<PermissionEffect> {
        if (event.capability !in capabilities) return emptyList()
        val previous = state.capability(event.capability)
        state = state.copy(
            capabilities = state.capabilities + (
                event.capability to previous.copy(
                    grantState = event.grantState,
                    canRequestAgain = event.canRequestAgain,
                    lastObservedMillis = event.observedAtMillis,
                )
                ),
            requestInFlight = state.requestInFlight.takeUnless { it == event.capability },
        )
        return reconcile(startAutomaticRequest = true)
    }

    private fun onRequestCompleted(
        capability: PermissionCapability,
        completedAtMillis: Long,
    ): List<PermissionEffect> {
        if (state.requestInFlight != capability) return emptyList()
        val previous = state.capability(capability)
        state = state.copy(
            capabilities = state.capabilities + (
                capability to previous.copy(lastRequestMillis = completedAtMillis)
                ),
            requestInFlight = null,
        )
        return listOf(PermissionEffect.CheckPermissions(setOf(capability)))
    }

    private fun requestResolution(capability: PermissionCapability): List<PermissionEffect> {
        if (state.requestInFlight != null) return emptyList()
        if (capability !in capabilities) return emptyList()
        val capabilityState = state.capability(capability)
        if (capabilityState.grantState != PermissionGrantState.DENIED) return emptyList()
        return listOf(beginRequest(capability, capabilityState))
    }

    private fun reconcile(startAutomaticRequest: Boolean): List<PermissionEffect> {
        val effects = mutableListOf<PermissionEffect>()
        val degraded = state.capabilities
            .filter { (key, value) -> key in capabilities && value.grantState == PermissionGrantState.DENIED }
            .keys
            .toSet()
        if (degraded != state.warningCapabilities) {
            state = state.copy(warningCapabilities = degraded)
            effects += if (degraded.isEmpty()) {
                PermissionEffect.HideDegradedWarning
            } else {
                PermissionEffect.ShowDegradedWarning(degraded)
            }
        }

        if (startAutomaticRequest && state.requestInFlight == null) {
            val next = capabilities.firstOrNull { capability ->
                val current = state.capability(capability)
                current.grantState == PermissionGrantState.DENIED &&
                    current.lastRequestMillis == null && capability in automaticRequests
            }
            if (next != null) effects += beginRequest(next, state.capability(next))
        }
        return effects
    }

    private fun beginRequest(
        capability: PermissionCapability,
        capabilityState: PermissionCapabilityState,
    ): PermissionEffect {
        state = state.copy(
            requestInFlight = capability,
            capabilities = state.capabilities + (capability to capabilityState.copy(lastRequestMillis = nowMillis())),
        )
        return if (
            capability == PermissionCapability.POST_NOTIFICATIONS &&
            capabilityState.canRequestAgain
        ) {
            PermissionEffect.RequestRuntimePermission(capability)
        } else {
            PermissionEffect.OpenPermissionSettings(capability)
        }
    }

    private fun PermissionManagerState.capability(
        capability: PermissionCapability,
    ): PermissionCapabilityState = capabilities[capability] ?: PermissionCapabilityState()

    private fun PermissionManagerState.withAllCapabilities(): PermissionManagerState = copy(
        capabilities = PermissionCapability.entries.associateWith { this.capability(it) },
        warningCapabilities = warningCapabilities.intersect(this@PermissionManager.capabilities),
    )
}


interface PermissionStatePersistence {
    fun load(): PermissionManagerState
    fun save(state: PermissionManagerState)
}
