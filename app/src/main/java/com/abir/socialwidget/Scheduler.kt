package com.abir.socialwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.refreshAll(applicationContext)
        return Result.success()
    }
}

/**
 * Safety net for the widget's "Updating" state. A refresh normally ends it itself, but if the work never
 * ran (no network for a long time) or the process was killed, this clears it and redraws, so the widget
 * can never stay stuck on "Updating".
 */
class RefreshGuardWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (isStaleRefresh(Store.refreshingAt(app), System.currentTimeMillis())) Store.setRefreshingAt(app, 0)
        Repo.redrawWidgets(app)
        return Result.success()
    }
}

object Scheduler {
    private const val GUARD = "stats_refresh_guard"

    private const val PERIODIC = "stats_periodic"
    private const val ONCE = "stats_once"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun refreshNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(online).build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
    }

    /** Arm the guard for a refresh the user just started. A newer tap replaces it. */
    fun guardRefresh(context: Context) {
        val req = OneTimeWorkRequestBuilder<RefreshGuardWorker>()
            .setInitialDelay(REFRESH_STALE_MS + 5_000, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(GUARD, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    /** Stop the 15-minute refresh only when no widget is placed and no alert needs it. */
    fun cancelIfUnused(context: Context) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val placed = listOf(StatsWidgetReceiver::class.java, CompactWidgetReceiver::class.java)
            .any { mgr.getAppWidgetIds(ComponentName(app, it)).isNotEmpty() }
        if (!placed && !Store.loadDisplay(app).anyAlerts) cancel(app)
    }
}
