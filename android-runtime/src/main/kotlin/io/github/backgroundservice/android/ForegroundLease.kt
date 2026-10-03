package io.github.backgroundservice.android

import android.os.Handler
import android.os.SystemClock

/** Shared idle timeout. Uses monotonic time, so changing wall time cannot extend or shorten a lease. */
class ForegroundLease(
    private val handler: Handler,
    private val timeoutMillis: Long,
    private val onExpired: () -> Unit,
) : AutoCloseable {
    private var deadline = 0L
    private var closed = false
    private val expire = Runnable { synchronized(this) { if (!closed) onExpired() } }
    @Synchronized fun refresh() {
        if (closed) return
        deadline = SystemClock.uptimeMillis() + timeoutMillis
        handler.removeCallbacks(expire)
        handler.postAtTime(expire, deadline)
    }
    @Synchronized override fun close() { closed = true; handler.removeCallbacks(expire) }
}
