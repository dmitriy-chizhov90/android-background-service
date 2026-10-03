package io.github.backgroundservice

/** UI adapters report balanced resume/pause events; no Android or transport dependency. */
class ActivityPresence(private val now: () -> Long = System::currentTimeMillis) {
    var count: Int = 0
        private set
    var lastActiveAt: Long = now()
        private set
    @Synchronized fun resumed() { count++; lastActiveAt = now() }
    @Synchronized fun paused() { if (count > 0) count--; if (count == 0) lastActiveAt = now() }
    @Synchronized fun isActive(): Boolean = count > 0
    @Synchronized fun idleMillis(at: Long = now()): Long = (at - lastActiveAt).coerceAtLeast(0)
    @Synchronized fun reset(at: Long = now()) { count = 0; lastActiveAt = at }
}
