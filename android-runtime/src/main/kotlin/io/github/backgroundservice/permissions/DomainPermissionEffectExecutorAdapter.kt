package io.github.backgroundservice.permissions


class DomainPermissionEffectExecutorAdapter(
    private val platform: PermissionEffectExecutor,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val record: (String) -> Unit = {},
) : DomainPermissionEffectExecutor {
    override fun execute(
        effect: PermissionEffect,
        state: PermissionManagerState,
    ): List<PermissionEvent> = when (effect) {
        is PermissionEffect.CheckPermissions -> effect.capabilities.map { capability ->
            platform.observe(
                capability.toPlatform(),
                hasRequestedBefore = state.capabilities[capability]?.lastRequestMillis != null,
            ).toDomainEvent(capability).also { record("${it.capability}=${it.grantState}; observed=${it.observedAtMillis}") }
        }
        is PermissionEffect.RequestRuntimePermission -> {
            record("request_runtime=${effect.capability}")
            launch { platform.requestRuntimePermission(effect.capability.toPlatform()) }
            emptyList()
        }
        is PermissionEffect.OpenPermissionSettings -> {
            record("open_settings=${effect.capability}")
            launch { platform.openPermissionSettings(effect.capability.toPlatform()) }
            emptyList()
        }
        is PermissionEffect.ShowDegradedWarning,
        PermissionEffect.HideDegradedWarning,
        -> emptyList()
    }

    private fun launch(action: () -> Unit) {
        try { action() } catch (error: RuntimeException) {
            record("request_failed=${error.javaClass.simpleName}")
            throw error
        }
    }

    override fun requestCompleted(capability: PermissionCapability): PermissionEvent =
        PermissionEvent.PermissionRequestResult(capability, nowMillis())

    override fun settingsReturned(capability: PermissionCapability): PermissionEvent =
        PermissionEvent.PermissionSettingsReturned(capability, nowMillis())

    private fun ObservedPermission.toDomainEvent(capability: PermissionCapability) =
        PermissionEvent.PermissionObserved(
            capability = capability,
            grantState = when (grant) {
                PlatformPermissionGrant.UNKNOWN -> PermissionGrantState.UNKNOWN
                PlatformPermissionGrant.GRANTED -> PermissionGrantState.GRANTED
                PlatformPermissionGrant.DENIED -> PermissionGrantState.DENIED
                PlatformPermissionGrant.NOT_APPLICABLE -> PermissionGrantState.NOT_APPLICABLE
            },
            canRequestAgain = canRequestAgain,
            observedAtMillis = nowMillis(),
        )

    private fun PermissionCapability.toPlatform(): PlatformPermissionCapability = PlatformPermissionCapability.valueOf(name)
}
