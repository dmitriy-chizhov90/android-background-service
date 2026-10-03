package io.github.backgroundservice.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/** Debounces transitions between activities, including configuration changes. */
class ApplicationActivityTracker(
    private val application: Application,
    private val onChanged: (Boolean) -> Unit,
    private val graceMillis: Long = 1_500,
) : Application.ActivityLifecycleCallbacks, AutoCloseable {
    private val resumed = mutableSetOf<Activity>()
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private val background = Runnable { if (resumed.isEmpty() && active) { active = false; onChanged(false) } }
    init { application.registerActivityLifecycleCallbacks(this) }
    override fun onActivityResumed(activity: Activity) {
        resumed += activity
        handler.removeCallbacks(background)
        if (!active) { active = true; onChanged(true) }
    }
    override fun onActivityPaused(activity: Activity) {
        resumed -= activity
        if (resumed.isEmpty()) handler.postDelayed(background, graceMillis)
    }
    override fun onActivityDestroyed(activity: Activity) { resumed -= activity }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
    override fun close() { application.unregisterActivityLifecycleCallbacks(this); handler.removeCallbacks(background); resumed.clear() }
}
