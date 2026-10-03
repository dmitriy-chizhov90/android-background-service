package io.github.backgroundservice.android

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.backgroundservice.ComponentStatus
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackgroundRuntimeTest {
    class Service : ManagedForegroundService() {
        val states = mutableListOf<ComponentStatus>()
        var executions = 0
        var released = false
        override val notificationId = 1
        override val serviceType = 0
        override val idleTimeoutMillis = 1000L
        override val stopAction = "stop"
        override val refreshAction = "refresh"
        override fun enabled() = true
        override fun notification(): Notification {
            getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("test", "Test", 2))
            return Notification.Builder(this, "test").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        }
        override fun observed(status: ComponentStatus, reason: String) { states += status }
        override fun execute(intent: Intent?) { executions++ }
        override fun release() { released = true }
    }
    @Test fun `refresh does not rerun workload and destruction releases before stopped`() {
        val controller = Robolectric.buildService(Service::class.java).create()
        val service = controller.get()
        service.onStartCommand(Intent(), 0, 1)
        service.onStartCommand(Intent("refresh"), 0, 2)
        assertEquals(1, service.executions)
        assertEquals(listOf(ComponentStatus.RUNNING), service.states)
        controller.destroy()
        assertTrue(service.released)
        assertEquals(ComponentStatus.STOPPED, service.states.last())
    }
    @Test fun `diagnostics history survives new instance and is bounded`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val diagnostics = BackgroundDiagnostics(context, "bounded")
        repeat(210) { diagnostics.record("component", "event-$it") }
        val report = BackgroundDiagnostics(context, "bounded").report()
        assertTrue(report.contains("event-209"))
        assertFalse(report.contains("\"event-0\""))
    }
    @Test fun `handoff retains fallback when disabled callback arrives late`() {
        val calls = mutableListOf<String>()
        val handoff = BackgroundHandoff(object : HandoffActions {
            override fun markServiceMode() { calls += "service" }
            override fun markWorkerMode() { calls += "worker" }
            override fun cancelFallbackAlarm() { calls += "cancelAlarm" }
            override fun cancelWorker() { calls += "cancelWorker" }
            override fun scheduleFallbackAlarm(reason: String) { calls += reason }
            override fun scheduleWorker() { calls += "scheduleWorker" }
        })
        handoff.serviceRunning()
        handoff.serviceStopped(true, "timeout")
        handoff.serviceStopped(false, "disabled")
        assertEquals(listOf("service", "cancelAlarm", "cancelWorker", "worker", "timeout", "scheduleWorker"), calls)
    }
}
