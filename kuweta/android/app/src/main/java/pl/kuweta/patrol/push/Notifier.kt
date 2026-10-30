package pl.kuweta.patrol.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.kuweta.patrol.R
import pl.kuweta.patrol.data.PushEvent
import pl.kuweta.patrol.ui.MainActivity

object Notifier {
    // Dźwięk kanału da się ustawić tylko przy tworzeniu, stąd nowe ID z "meow".
    const val CH_REMIND = "reminders_meow"
    const val CH_PARTNER = "partner_meow"
    const val CH_SERVICE = "service"

    const val ID_SERVICE = 1
    const val ID_REMINDER = 100
    const val ID_EXT_BASE = 200
    const val ID_INFO = 300
    const val ID_ERROR = 900

    private fun sound(context: Context, raw: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$raw")

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        listOf("reminders", "partner").forEach { nm.deleteNotificationChannel(it) }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Przypomnienia o kuwecie", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Co ileś minut, dopóki kuweta nie będzie ogarnięta. Dźwięk: miauczenie"
                setSound(sound(context, R.raw.meow), attrs)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_PARTNER, "Od drugiej osoby", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Prośby o przedłużenie, info o sprzątnięciu, donosy. Dźwięk: krótkie mrrp"
                setSound(sound(context, R.raw.mrrp), attrs)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Połączenie z serwerem", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Stałe powiadomienie, dzięki któremu przypomnienia dochodzą na czas"
                setShowBadge(false)
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
            .setContentText("Możesz ukryć to powiadomienie w ustawieniach kanału")
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
