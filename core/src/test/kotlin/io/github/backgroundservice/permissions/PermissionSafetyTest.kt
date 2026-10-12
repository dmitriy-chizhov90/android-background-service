package io.github.backgroundservice.permissions

import org.junit.Assert.*
import org.junit.Test

class PermissionSafetyTest {
    private val notification = PermissionCapability.POST_NOTIFICATIONS
    private class Storage : PermissionStatePersistence {
        var value = PermissionManagerState()
        override fun load() = value
        override fun save(state: PermissionManagerState) { value = state }
    }
    private fun denied() = PermissionEvent.PermissionObserved(notification, PermissionGrantState.DENIED, true, 10)

    @Test fun `observers get current state and stop after disposal`() {
        val store = Storage()
        val runtime = PermissionRuntime(PermissionManager(store.load(), store))
        var calls = 0
        val observation = runtime.observe { calls++ }
        assertEquals(1, calls)
        runtime.processStarted()
        assertEquals(2, calls)
        observation.close()
        runtime.foregrounded()
        assertEquals(2, calls)
    }

    @Test fun `default policy never launches system UI automatically`() {
        val store = Storage()
        val manager = PermissionManager(store.load(), store)
        val effects = manager.dispatch(denied())
        assertTrue(effects.none { it is PermissionEffect.RequestRuntimePermission || it is PermissionEffect.OpenPermissionSettings })
        assertNull(manager.state.requestInFlight)
    }

    @Test fun `process death during request retains attempt and prevents automatic retry`() {
        val store = Storage()
        val manager = PermissionManager(store.load(), store, automaticRequests = setOf(notification), nowMillis = { 123 })
        manager.dispatch(denied())
        assertEquals(123L, store.value.capabilities.getValue(notification).lastRequestMillis)
        val restored = PermissionManager(store.load(), store, automaticRequests = setOf(notification))
        restored.dispatch(PermissionEvent.ApplicationStarted)
        assertTrue(restored.dispatch(denied()).none { it is PermissionEffect.RequestRuntimePermission })
        assertNull(restored.state.requestInFlight)
    }

    @Test fun `disabling a capability retains history without warning or request`() {
        val store = Storage()
        val manager = PermissionManager(store.load(), store)
        manager.dispatch(denied())
        manager.dispatch(PermissionEvent.ResolutionRequested(notification))
        val attempt = store.value.capabilities.getValue(notification).lastRequestMillis
        val disabled = PermissionManager(store.load(), store, capabilities = emptySet())
        disabled.dispatch(PermissionEvent.ApplicationStarted)
        assertTrue(disabled.state.warningCapabilities.isEmpty())
        assertTrue(disabled.dispatch(PermissionEvent.ResolutionRequested(notification)).isEmpty())
        assertEquals(attempt, store.value.capabilities.getValue(notification).lastRequestMillis)
    }

    @Test fun `failed settings launch releases in flight state and does not retry`() {
        val store = Storage()
        val runtime = PermissionRuntime(PermissionManager(store.load(), store))
        var launches = 0
        runtime.attach(object : DomainPermissionEffectExecutor {
            override fun execute(effect: PermissionEffect, state: PermissionManagerState): List<PermissionEvent> {
                if (effect is PermissionEffect.CheckPermissions) return listOf(denied())
                if (effect is PermissionEffect.RequestRuntimePermission) { launches++; error("No activity") }
                return emptyList()
            }
            override fun requestCompleted(capability: PermissionCapability) = PermissionEvent.PermissionRequestResult(capability, 20)
            override fun settingsReturned(capability: PermissionCapability) = PermissionEvent.PermissionSettingsReturned(capability, 20)
        })
        runtime.processStarted()
        runtime.resolutionRequested(notification)
        runtime.foregrounded()
        assertEquals(1, launches)
        assertNull(store.value.requestInFlight)
    }
}
