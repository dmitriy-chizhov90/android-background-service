package io.github.backgroundservice

import org.junit.Assert.*
import org.junit.Test

class ExecutionCoordinatorTest {
    private class Host : ExecutionHost {
        val calls = mutableListOf<String>()
        override fun activate() { calls += "active" }
        override fun deactivate() { calls += "release" }
        override fun useForegroundService() { calls += "fgs" }
        override fun scheduleWorker() { calls += "worker" }
        override fun cancelWorker() { calls += "cancel" }
        override fun stopForegroundService() { calls += "stop" }
    }
    @Test fun `active to service to worker releases ownership and preserves enabled state`() {
        val host = Host()
        val coordinator = ExecutionCoordinator(host, StateStore {})
        coordinator.configure(true)
        coordinator.applicationActive(true)
        coordinator.serviceObserved(ComponentStatus.RUNNING)
        coordinator.applicationActive(false)
        assertEquals(listOf("worker", "cancel", "active", "release", "cancel", "fgs"), host.calls)
        coordinator.serviceObserved(ComponentStatus.STOPPED)
        assertEquals(ExecutionMode.WORKER, coordinator.state.mode)
        assertTrue(coordinator.state.enabled)
        coordinator.configure(false)
        coordinator.serviceObserved(ComponentStatus.STOPPED)
        assertEquals(ExecutionMode.DISABLED, coordinator.state.mode)
        assertEquals(listOf("cancel", "stop"), host.calls.takeLast(2))
    }
    @Test fun `restart invalidates old callbacks and restores fallback`() {
        val coordinator = ExecutionCoordinator(Host(), StateStore {})
        coordinator.processStarted(true)
        coordinator.serviceObserved(ComponentStatus.RUNNING, generation = 0)
        assertEquals(ExecutionMode.WORKER, coordinator.state.mode)
    }
    @Test fun `lease excludes concurrent owners and can be reacquired after release`() {
        val leases = ExecutionLease()
        val first = requireNotNull(leases.acquire("sync", "active"))
        assertNull(leases.acquire("sync", "worker"))
        first.close()
        assertNotNull(leases.acquire("sync", "worker"))
    }
    @Test fun `cadence backs off and remains bounded`() {
        assertEquals(5L, PollCadence().delaySeconds(true, 0))
        assertEquals(240L, PollCadence().delaySeconds(false, 900_000, 10))
    }
    @Test fun `duplicate lease close does not release a later run with the same owner name`() {
        val leases = ExecutionLease()
        val first = requireNotNull(leases.acquire("sync", "worker"))
        first.close()
        val second = requireNotNull(leases.acquire("sync", "worker"))
        first.close()
        assertNull(leases.acquire("sync", "active"))
        second.close()
        assertNotNull(leases.acquire("sync", "active"))
    }
    @Test fun `presence tolerates overlapping activities and unbalanced pause`() {
        var time = 100L
        val presence = ActivityPresence { time }
        presence.resumed()
        presence.resumed()
        presence.paused()
        assertTrue(presence.isActive())
        time = 200
        presence.paused()
        presence.paused()
        assertFalse(presence.isActive())
        assertEquals(50L, presence.idleMillis(250))
        assertEquals(0L, presence.idleMillis(50))
    }

}
