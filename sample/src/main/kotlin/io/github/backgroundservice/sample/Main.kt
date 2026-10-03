package io.github.backgroundservice.sample

import io.github.backgroundservice.*

/** Executable host contract example: no Android device, network or credentials required. */
fun main() {
    val host = object : ExecutionHost {
        override fun activate() = println("acquire active workload")
        override fun deactivate() = println("release active workload")
        override fun useForegroundService() = println("acquire service workload")
        override fun scheduleWorker() = println("schedule fallback")
        override fun cancelWorker() = println("cancel fallback")
        override fun stopForegroundService() = println("stop service")
    }
    val runtime = ExecutionCoordinator(host, StateStore { println("mode=${it.mode}, reason=${it.reason}") })
    runtime.processStarted(true)
    runtime.applicationActive(true)
    runtime.serviceObserved(ComponentStatus.RUNNING)
    runtime.applicationActive(false)
    runtime.serviceObserved(ComponentStatus.STOPPED, reason = "idle_timeout")
    runtime.configure(false)
}
