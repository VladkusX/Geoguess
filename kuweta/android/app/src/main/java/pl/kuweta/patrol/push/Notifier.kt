package pl.kuweta.patrol.push

import android.Manifest
import android.app.Notification
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
import pl.kuweta.patrol.R
import pl.kuweta.patrol.data.PushEvent
import pl.kuweta.patrol.ui.MainActivity

object Notifier {
    const val CH_REMIND = "reminders_v3"
    const val CH_PARTNER = "partner_v3"
    const val CH_SERVICE = "service"

    const val ID_SERVICE = 1
    const val ID_REMINDER = 100
    const val ID_EXT_BASE = 200
    const val ID_INFO = 300
    const val ID_ERROR = 900

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf("reminders", "partner", "reminders_meow", "partner_meow").forEach { nm.deleteNotificationChannel(it) }
        // Kanały są ciche, dźwięk (miau albo wgrany przez admina) gra SoundPlayer.
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Przypomnienia o kuwecie", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Co ileś minut, dopóki kuweta nie będzie ogarnięta"
                setSound(null, null)
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PARTNER, "Od drugiej osoby", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Prośby o przedłużenie, info o sprzątnięciu, donosy"
                setSound(null, null)
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Połączenie z serwerem", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Stałe powiadomienie, dzięki któremu przypomnienia dochodzą na czas. Można je wyłączyć, połączenie zostaje."
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun action(context: Context, action: String, extId: Int = 0): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java).setAction(action).putExtra(ActionReceiver.EXTRA_EXT_ID, extId)
        return PendingIntent.getBroadcast(
            context, action.hashCode() + extId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun serviceNotification(context: Context, connected: Boolean): Notification =
        NotificationCompat.Builder(context, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_cat)
            .setContentTitle(if (connected) "Kuweta Patrol czuwa" else "Łączenie z serwerem…")
            .setContentText("Przytrzymaj, żeby wyłączyć ten kanał. Przypomnienia dalej będą działać.")
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    fun show(context: Context, ev: PushEvent) {
        val title = ev.title ?: "Kuweta"
        val body = ev.body ?: ""
        val (channel, id) = when (ev.kind) {
            "reminder", "test" -> CH_REMIND to ID_REMINDER
            "ext_request" -> CH_PARTNER to ID_EXT_BASE + (ev.extensionId ?: 0)
            else -> CH_PARTNER to ID_INFO + (ev.kind?.hashCode()?.and(0xff) ?: 0)
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_cat)
            .setColor(0xFFFF5A36.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        when (ev.kind) {
            "reminder", "test" -> {
                builder.addAction(0, "Ogarnięte ✔", action(context, ActionReceiver.CLEAN))
                builder.addAction(0, "Prośba +1 dzień", action(context, ActionReceiver.EXTEND))
            }
            "ext_request" -> {
                val extId = ev.extensionId ?: 0
                builder.addAction(0, "Zgoda", action(context, ActionReceiver.APPROVE, extId))
                builder.addAction(0, "Odmowa", action(context, ActionReceiver.REJECT, extId))
            }
        }
        post(context, id, builder.build())
        if (canPost(context)) SoundPlayer.play(context, if (channel == CH_REMIND) "reminder" else "partner")
    }

    fun simple(context: Context, title: String, body: String, id: Int = ID_ERROR) {
        val n = NotificationCompat.Builder(context, CH_PARTNER)
            .setSmallIcon(R.drawable.ic_stat_cat)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        post(context, id, n)
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun post(context: Context, id: Int, n: Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}
