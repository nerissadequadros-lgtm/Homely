package app.homely.platform

import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.homely.MainActivity
import app.homely.R
import app.homely.data.*
import kotlinx.coroutines.*

/** Durable, network-constrained delivery. Android may defer jobs in Doze or battery saver. */
object DiscoveryNotifications {
    fun schedule(ctx: Context) {
        val scheduler = ctx.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(807) != null) return
        scheduler.schedule(
            JobInfo.Builder(807, ComponentName(ctx, DiscoveryJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(30 * 60 * 1000L)
                .build()
        )
    }

    fun deliver(ctx: Context, inbox: DiscoveryInbox) {
        if (!DiscoveryClient(ctx).enabled()) return
        val hour = java.time.ZonedDateTime.now().hour
        if (hour !in 8..22) return
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    ctx,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        )
            return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                "discovery",
                "Descobertas diárias",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        if (
            !nm.areNotificationsEnabled() ||
                nm.getNotificationChannel("discovery").importance ==
                    NotificationManager.IMPORTANCE_NONE
        )
            return
        val prefs = ctx.getSharedPreferences("discovery-delivery", 0)
        val today = java.time.LocalDate.now().toString()
        if (prefs.getString("day", null) == today) return
        val item =
            inbox.items.firstOrNull {
                !it.read &&
                    java.time.Instant.parse(it.createdAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                        .toString() == today &&
                    it.id != prefs.getString("last", null)
            } ?: return
        val intent =
            Intent(ctx, MainActivity::class.java)
                .putExtra("notifications", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending =
            PendingIntent.getActivity(
                ctx,
                807,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        nm.notify(
            807,
            NotificationCompat.Builder(ctx, "discovery")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(item.title)
                .setContentText("Abra sua seleção de temas no Homely.")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build(),
        )
        prefs.edit().putString("day", today).putString("last", item.id).apply()
    }
}

class DiscoveryJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        job = scope.launch {
            val retry = runCatching {
                val inbox = DiscoveryClient(this@DiscoveryJobService).sync()
                DiscoveryNotifications.deliver(this@DiscoveryJobService, inbox)
            }.isFailure
            withContext(Dispatchers.Main) { jobFinished(params, retry) }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        job?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
