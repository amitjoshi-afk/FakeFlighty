package app.contrail.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.contrail.ContrailApp
import app.contrail.MainActivity
import app.contrail.R
import app.contrail.core.model.FlightChange
import java.util.concurrent.TimeUnit

/**
 * Polls flights departing in the next two days and posts an alert for each gate, delay or status change.
 * WorkManager's shortest period is 15 minutes; 30 keeps AeroDataBox usage modest
 * (about 48 calls a day per watched flight).
 */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as ContrailApp).repository
        if (repo.settings.apiKey.isBlank()) return Result.success()
        var failed = false
        for (f in repo.flightsToWatch()) {
            runCatching { repo.refresh(f.id) }
                .onSuccess { changes -> if (repo.settings.alertsEnabled) changes.forEach { Notifier.post(applicationContext, it) } }
                .onFailure { failed = true }
        }
        return if (failed) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "flight-refresh"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

object Notifier {
    const val CHANNEL = "flight-changes"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Flight changes", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Gate, terminal, delay and status changes for your flights"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun post(context: Context, change: FlightChange) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, change.flightId.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_FLIGHT_ID, change.flightId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(change.title)
            .setContentText(change.message)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify((change.flightId + change.title).hashCode(), n)
    }
}
