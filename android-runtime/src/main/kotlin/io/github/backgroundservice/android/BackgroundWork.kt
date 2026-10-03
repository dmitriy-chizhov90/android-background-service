package io.github.backgroundservice.android

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/** Reusable scheduling; the host owns Worker implementations and network policy. */
class BackgroundWork(context: Context) {
    private val manager = WorkManager.getInstance(context.applicationContext)
    fun periodic(name: String, worker: Class<out ListenableWorker>, intervalMinutes: Long, network: Boolean, tag: String) {
        require(intervalMinutes >= 15)
        val request = PeriodicWorkRequest.Builder(worker, intervalMinutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (network) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).addTag(tag).build()
        manager.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
    fun once(name: String, worker: Class<out ListenableWorker>, network: Boolean = false) {
        val request = OneTimeWorkRequest.Builder(worker)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (network) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED).build()).build()
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
