package dev.rootiest.status

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.rootiest.status.data.Change

object Notifier {
    private const val CHANNEL = "changes"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notify(context: Context, changes: List<Change>) {
        if (changes.isEmpty() || !canNotify(context)) return
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Service changes")
                .setDescription("A service or server went down or came back")
                .build(),
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        for (change in changes) {
            val recovered = change.to == "up" || change.to == "healthy"
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_status)
                .setContentTitle(if (recovered) "${change.name} is back ${change.to}" else "${change.name} is ${change.to}")
                .setContentText("was ${change.from}")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            // one id per name, so a recovery replaces the "down" notification
            try {
                manager.notify(change.name.hashCode(), notification)
            } catch (_: SecurityException) {
                // permission revoked between the check and the post
            }
        }
    }
}
