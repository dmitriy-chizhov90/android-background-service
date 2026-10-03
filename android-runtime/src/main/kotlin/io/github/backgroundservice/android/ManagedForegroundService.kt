package io.github.backgroundservice.android

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import io.github.backgroundservice.ComponentStatus

/** Android service lifetime only; the host owns workload, notification and lawful service type.
 * Stop from the notification withdraws FGS ownership; a host can then schedule worker fallback.
 * force-stop/process death cannot rely on onDestroy: hosts must register durable recovery work.
 */
abstract class ManagedForegroundService : Service() {
    protected abstract val notificationId: Int
    protected abstract val serviceType: Int
    protected abstract val idleTimeoutMillis: Long
    protected abstract val stopAction: String
    protected abstract val refreshAction: String
    protected abstract fun enabled(): Boolean
    protected abstract fun notification(): Notification
    protected abstract fun observed(status: ComponentStatus, reason: String)
    protected abstract fun execute(intent: Intent?)
    protected abstract fun release()
    protected lateinit var backgroundHandler: Handler
        private set
    private lateinit var thread: HandlerThread
    private lateinit var lease: ForegroundLease
    private var started = false
    private var reason = "service_destroyed"

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread(javaClass.simpleName).apply { start() }
        backgroundHandler = Handler(thread.looper)
        lease = ForegroundLease(backgroundHandler, idleTimeoutMillis) {
            reason = "idle_timeout"
            stopSelf()
        }
    }

    final override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == stopAction || !enabled()) {
            reason = if (intent?.action == stopAction) "user_stopped_service" else "disabled"
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            try {
                if (Build.VERSION.SDK_INT >= 29) startForeground(notificationId, notification(), serviceType)
                else startForeground(notificationId, notification())
                started = true
                observed(ComponentStatus.RUNNING, "service_started")
            } catch (error: RuntimeException) {
                reason = "start_failed:${error.javaClass.simpleName}"
                observed(ComponentStatus.FAILED, reason)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        lease.refresh()
        if (intent?.action != refreshAction) {
            try { execute(intent) } catch (error: RuntimeException) {
                reason = "work_failed:${error.javaClass.simpleName}"
                observed(ComponentStatus.FAILED, reason)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int) { reason = "system_timeout"; stopSelf() }
    override fun onTimeout(startId: Int, fgsType: Int) { reason = "system_timeout"; stopSelf() }

    final override fun onDestroy() {
        lease.close()
        try { release() } finally {
            observed(ComponentStatus.STOPPED, reason)
            backgroundHandler.removeCallbacksAndMessages(null)
            thread.quitSafely()
            super.onDestroy()
        }
    }
    final override fun onBind(intent: Intent?): IBinder? = null
}
