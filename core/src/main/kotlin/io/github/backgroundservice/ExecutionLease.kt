package io.github.backgroundservice

/** Process-scoped mutual exclusion. Hosts must retain the lease until cancellation completes. */
class ExecutionLease {
    private val owners = mutableMapOf<String, Any>()
    @Synchronized fun acquire(task: String, owner: String): AutoCloseable? {
        require(task.isNotBlank() && owner.isNotBlank())
        if (task in owners) return null
        val token = Any()
        owners[task] = token
        return AutoCloseable { synchronized(this) { if (owners[task] === token) owners.remove(task) } }
    }
}

data class PollCadence(val recent: Long = 5, val warm: Long = 10, val cool: Long = 30, val idle: Long = 60) {
    fun delaySeconds(active: Boolean, idleMillis: Long, failures: Int = 0): Long {
        val base = when {
            active || idleMillis < 120_000 -> recent
            idleMillis < 300_000 -> warm
            idleMillis < 600_000 -> cool
            else -> idle
        }
        val multiplier = when { failures < 3 -> 1; failures < 6 -> 2; else -> 4 }
        return (base * multiplier).coerceAtMost(300)
    }
}
