package io.github.backgroundservice.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRuntimeTest {
    @Test
    fun `process check waits for activity executor then starts sequential flow`() {
        val runtime = runtime()
        val executor = FakeExecutor()

        runtime.processStarted()
        assertTrue(executor.executed.isEmpty())
        runtime.attach(executor)

        assertTrue(executor.executed.first() is PermissionEffect.CheckPermissions)
        assertTrue(
            executor.executed.contains(
                PermissionEffect.RequestRuntimePermission(
                    PermissionCapability.POST_NOTIFICATIONS,
                ),
            ),
        )
        assertEquals(setOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION), runtime.warningCapabilities)
    }

    @Test
    fun `permission result is rechecked before battery settings start`() {
        val runtime = runtime()
        val executor = FakeExecutor()
        runtime.processStarted()
        runtime.attach(executor)

        runtime.notificationPermissionRequestCompleted()

        assertTrue(
            executor.executed.contains(
                PermissionEffect.CheckPermissions(
                    setOf(PermissionCapability.POST_NOTIFICATIONS),
                ),
            ),
        )
        assertTrue(
            executor.executed.contains(
                PermissionEffect.OpenPermissionSettings(
                    PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION,
                ),
            ),
        )
    }

    @Test
    fun `foreground after settings return rechecks capability and reconciles all permissions`() {
        val runtime = runtime()
        val executor = FakeExecutor()
        runtime.processStarted()
        runtime.attach(executor)
        runtime.notificationPermissionRequestCompleted()
        val checksBefore = executor.checkCount()

        runtime.foregrounded()

        assertEquals(checksBefore + 2, executor.checkCount())
    }

    @Test
    fun `warning resolution delegates to domain request effect`() {
        val runtime = runtime()
        val executor = FakeExecutor()
        runtime.processStarted()
        runtime.attach(executor)
        runtime.notificationPermissionRequestCompleted()
        runtime.foregrounded()

        runtime.resolutionRequested(PermissionCapability.POST_NOTIFICATIONS)

        assertTrue(
            executor.executed.last() is PermissionEffect.RequestRuntimePermission ||
                executor.executed.last() is PermissionEffect.OpenPermissionSettings,
        )
    }

    private fun runtime(): PermissionRuntime {
        val persistence = InMemoryPersistence()
        return PermissionRuntime(PermissionManager(persistence.load(), persistence, capabilities = setOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION), automaticRequests = setOf(PermissionCapability.POST_NOTIFICATIONS, PermissionCapability.BATTERY_OPTIMIZATION_EXEMPTION)))
    }

    private class FakeExecutor : DomainPermissionEffectExecutor {
        val executed = mutableListOf<PermissionEffect>()

        override fun execute(
            effect: PermissionEffect,
            state: PermissionManagerState,
        ): List<PermissionEvent> {
            executed += effect
            if (effect !is PermissionEffect.CheckPermissions) return emptyList()
            return effect.capabilities.map { capability ->
                PermissionEvent.PermissionObserved(
                    capability,
                    PermissionGrantState.DENIED,
                    canRequestAgain = true,
                    observedAtMillis = 1L,
                )
            }
        }

        override fun requestCompleted(capability: PermissionCapability) =
            PermissionEvent.PermissionRequestResult(capability, 2L)

        override fun settingsReturned(capability: PermissionCapability) =
            PermissionEvent.PermissionSettingsReturned(capability, 3L)

        fun checkCount() = executed.filterIsInstance<PermissionEffect.CheckPermissions>().size
    }

    private class InMemoryPersistence : PermissionStatePersistence {
        private var state = PermissionManagerState()

        override fun load(): PermissionManagerState = state

        override fun save(state: PermissionManagerState) {
            this.state = state
        }
    }
}
