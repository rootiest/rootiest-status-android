package dev.rootiest.status

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.rootiest.status.data.Change

// Two channels, so Android's own settings can treat them apart: outages loud, recoveries quiet, by default.
enum class Channel(val id: String, val title: String, val description: String, val importance: Int) {
    Outages("outages", "Outages", "A service or server went down or degraded", NotificationManagerCompat.IMPORTANCE_HIGH),
    Recoveries("recoveries", "Recoveries", "A service or server came back", NotificationManagerCompat.IMPORTANCE_LOW),
}

object Notifier {
    private const val OLD_CHANNEL = "changes" // the single channel before 1.4.0
    private const val TEST_ID = 1

    // Creates the channels (again is harmless), so they show in the phone's settings before the first change.
    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.deleteNotificationChannel(OLD_CHANNEL)
        manager.createNotificationChannelsCompat(
            Channel.entries.map {
                NotificationChannelCompat.Builder(it.id, it.importance).setName(it.title).setDescription(it.description).build()
            },
        )
    }

    // The permission (Android 13+) and the app-wide switch in the phone's settings.
    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    // False when the user turned this one channel off.
    fun channelOn(context: Context, channel: Channel): Boolean =
        NotificationManagerCompat.from(context).getNotificationChannelCompat(channel.id)?.importance != NotificationManagerCompat.IMPORTANCE_NONE

    // The phone's notification page for this app, or for one channel of it.
    fun settingsIntent(context: Context, channel: Channel? = null): Intent =
        Intent(if (channel == null) Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .apply { if (channel != null) putExtra(Settings.EXTRA_CHANNEL_ID, channel.id) }

    fun notify(context: Context, changes: List<Change>) {
        if (changes.isEmpty() || !canNotify(context)) return
        createChannels(context)
        for (change in changes) {
            val title = if (change.recovered) "${change.name} is back ${change.to}" else "${change.name} is ${change.to}"
            val channel = if (change.recovered) Channel.Recoveries else Channel.Outages
            post(context, idFor(change.name), channel, title, "was ${change.from}", muteName = change.name)
        }
    }

    // one id per name, so a recovery replaces the "down" notification (ids are per app, not per channel)
    fun idFor(name: String) = name.hashCode()

    // What an outage looks and sounds like, from the settings screen.
    fun test(context: Context) {
        if (!canNotify(context)) return
        createChannels(context)
        post(context, TEST_ID, Channel.Outages, "Test: Example is down", "was up. This is how an outage looks.")
    }

    // With a muteName, the notification has a Mute button for it (MuteReceiver).
    private fun post(context: Context, id: Int, channel: Channel, title: String, text: String, muteName: String? = null) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_status)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .apply {
                if (muteName != null) {
                    val mute = PendingIntent.getBroadcast(
                        context,
                        id, // one per name, so each notification's button mutes its own
                        Intent(context, MuteReceiver::class.java).putExtra(MuteReceiver.EXTRA_NAME, muteName),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    addAction(R.drawable.ic_notifications_off, "Mute", mute)
                }
            }
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // permission revoked between the check and the post
        }
    }
}
