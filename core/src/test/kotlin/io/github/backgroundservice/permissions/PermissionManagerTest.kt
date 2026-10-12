package io.github.backgroundservice.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionManagerTest {
    @Test
    fun `application start checks every capability and clears stale request`() {
        val state = PermissionManagerState(
            requestInFlight = PermissionCapability.POST_NOTIFICATIONS,
        )
        val persistence = FakePermissionPersistence(state)
        val manager = PermissionManager(state, persistence, capabilities = setOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION), automaticRequests = listOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION).toSet())

        val effects = manager.dispatch(PermissionEvent.ApplicationStarted)

        assertEquals(
            listOf(PermissionEffect.CheckPermissions(listOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION).toSet())),
            effects,
        )
        assertEquals(null, manager.state.requestInFlight)
        assertEquals(manager.state, persistence.saved)
    }

    @Test
    fun `missing permissions are requested sequentially`() {
        val manager = manager()

        val notification = manager.dispatch(
            observed(PermissionCapability.POST_NOTIFICATIONS, PermissionGrantState.DENIED),
        )
        val batteryWhileBusy = manager.dispatch(
            observed(PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION, PermissionGrantState.DENIED),
        )
        val notificationResult = manager.dispatch(
            PermissionEvent.PermissionRequestResult(PermissionCapability.POST_NOTIFICATIONS, 20L),
        )
        val notificationStillDenied = manager.dispatch(
            observed(PermissionCapability.POST_NOTIFICATIONS, PermissionGrantState.DENIED, 30L),
        )

        assertTrue(
            notification.contains(
                PermissionEffect.ShowDegradedWarning(setOf(PermissionCapability.POST_NOTIFICATIONS)),
            ),
        )
        assertTrue(
            notification.contains(
                PermissionEffect.RequestRuntimePermission(PermissionCapability.POST_NOTIFICATIONS),
            ),
        )
        assertFalse(batteryWhileBusy.any { it is PermissionEffect.OpenPermissionSettings })
        assertEquals(
            listOf(PermissionEffect.CheckPermissions(setOf(PermissionCapability.POST_NOTIFICATIONS))),
            notificationResult,
        )
        assertTrue(
            notificationStillDenied.contains(
                PermissionEffect.OpenPermissionSettings(
                    PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION,
                ),
            ),
        )
    }

    @Test
    fun `dialog result never grants permission without platform observation`() {
        val manager = manager()
        manager.dispatch(observed(PermissionCapability.POST_NOTIFICATIONS, PermissionGrantState.DENIED))

        manager.dispatch(
            PermissionEvent.PermissionRequestResult(PermissionCapability.POST_NOTIFICATIONS, 20L),
        )

        assertEquals(
            PermissionGrantState.DENIED,
            manager.state.capabilities.getValue(PermissionCapability.POST_NOTIFICATIONS).grantState,
        )
    }

    @Test
    fun `granted permissions clear degraded warning without restart`() {
        val initial = PermissionManagerState(
            capabilities = listOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION).associateWith {
                PermissionCapabilityState(
                    grantState = PermissionGrantState.DENIED,
                    lastRequestMillis = 1L,
                )
            },
            warningCapabilities = listOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION).toSet(),
        )
        val manager = PermissionManager(initial, FakePermissionPersistence(initial), capabilities = setOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION))

        val first = manager.dispatch(
            observed(PermissionCapability.POST_NOTIFICATIONS, PermissionGrantState.GRANTED),
        )
        val second = manager.dispatch(
            observed(PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION, PermissionGrantState.GRANTED),
        )

        assertEquals(
            PermissionEffect.ShowDegradedWarning(
                setOf(PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION),
            ),
            first.single(),
        )
        assertEquals(listOf(PermissionEffect.HideDegradedWarning), second)
        assertTrue(manager.state.warningCapabilities.isEmpty())
    }

    @Test
    fun `not applicable capability neither warns nor requests`() {
        val manager = manager()

        val effects = manager.dispatch(
            observed(PermissionCapability.POST_NOTIFICATIONS, PermissionGrantState.NOT_APPLICABLE),
        )

        assertTrue(effects.isEmpty())
        assertTrue(manager.state.warningCapabilities.isEmpty())
        assertEquals(null, manager.state.requestInFlight)
    }

    @Test
    fun `repeated foreground does not duplicate in flight request`() {
        val manager = manager()
        manager.dispatch(observed(PermissionCapability.POST_NOTIFICATIONS, PermissionGrantState.DENIED))

        val firstResume = manager.dispatch(PermissionEvent.ApplicationForegrounded)
        val secondResume = manager.dispatch(PermissionEvent.ApplicationForegrounded)

        assertTrue(firstResume.isEmpty())
        assertTrue(secondResume.isEmpty())
        assertEquals(PermissionCapability.POST_NOTIFICATIONS, manager.state.requestInFlight)
    }

    @Test
    fun `foreground rechecks known denial changed in system settings`() {
        val initial = PermissionManagerState(
            capabilities = listOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION).associateWith {
                PermissionCapabilityState(
                    grantState = PermissionGrantState.DENIED,
                    lastRequestMillis = 1L,
                )
            },
            warningCapabilities = listOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION).toSet(),
        )
        val manager = PermissionManager(initial, FakePermissionPersistence(initial), capabilities = setOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION))

        val effects = manager.dispatch(PermissionEvent.ApplicationForegrounded)

        assertEquals(
            listOf(PermissionEffect.CheckPermissions(listOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION).toSet())),
            effects,
        )
    }

    @Test
    fun `permanent denial opens settings on explicit resolution`() {
        val initial = PermissionManagerState(
            capabilities = mapOf(
                PermissionCapability.POST_NOTIFICATIONS to PermissionCapabilityState(
                    grantState = PermissionGrantState.DENIED,
                    canRequestAgain = false,
                    lastRequestMillis = 10L,
                ),
            ),
        )
        val manager = PermissionManager(initial, FakePermissionPersistence(initial), capabilities = setOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION))

        val effects = manager.dispatch(
            PermissionEvent.ResolutionRequested(PermissionCapability.POST_NOTIFICATIONS),
        )

        assertEquals(
            listOf(PermissionEffect.OpenPermissionSettings(PermissionCapability.POST_NOTIFICATIONS)),
            effects,
        )
    }

    private fun manager(): PermissionManager {
        val state = PermissionManagerState()
        return PermissionManager(state, FakePermissionPersistence(state), automaticRequests = listOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION).toSet())
    }

    private fun observed(
        capability: PermissionCapability,
        state: PermissionGrantState,
        atMillis: Long = 10L,
    ) = PermissionEvent.PermissionObserved(
        capability = capability,
        grantState = state,
        canRequestAgain = true,
        observedAtMillis = atMillis,
    )

    private class FakePermissionPersistence(
        initial: PermissionManagerState,
    ) : PermissionStatePersistence {
        var saved: PermissionManagerState = initial

        override fun load(): PermissionManagerState = saved

        override fun save(state: PermissionManagerState) {
            saved = state
        }
    }
}
