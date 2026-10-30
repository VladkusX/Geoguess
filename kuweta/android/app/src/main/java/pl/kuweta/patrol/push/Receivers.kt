package pl.kuweta.patrol.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.ApiException
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.Repo
import java.util.concurrent.TimeUnit

/** Przyciski w powiadomieniach: ogarnięte, prośba, zgoda, odmowa. */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val CLEAN = "pl.kuweta.patrol.CLEAN"
        const val EXTEND = "pl.kuweta.patrol.EXTEND"
        const val APPROVE = "pl.kuweta.patrol.APPROVE"
        const val REJECT = "pl.kuweta.patrol.REJECT"
        const val EXTRA_EXT_ID = "ext_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            val api = Api(Prefs(app))
            val extId = intent.getIntExtra(EXTRA_EXT_ID, 0)
            try {
                when (intent.action) {
                    CLEAN -> {
                        api.clean()
                        Notifier.cancel(app, Notifier.ID_REMINDER)
                        Notifier.simple(app, "Zapisane ✔", "Kuweta ogarnięta. Druga osoba już wie.", Notifier.ID_INFO)
                    }
                    EXTEND -> {
                        api.requestExtension()
                        Notifier.cancel(app, Notifier.ID_REMINDER)
                        Notifier.simple(app, "Prośba wysłana", "Czekasz na zgodę. Do tego czasu przypomnienia lecą dalej.", Notifier.ID_INFO)
                    }
                    APPROVE, REJECT -> {
                        api.decideExtension(extId, intent.action == APPROVE)
                        Notifier.cancel(app, Notifier.ID_EXT_BASE + extId)
                    }
                }
                Repo.refresh(app)
            } catch (e: ApiException) {
                Notifier.simple(app, "Nie udało się", e.message ?: "Błąd")
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            EventService.start(context)
            Watchdog.schedule(context)
        }
    }
}

/** Co 15 minut: odśwież stan i postaw usługę z powrotem, jeśli system ją ubił. */
class Watchdog(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<Watchdog>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("kuweta-watchdog", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    override suspend fun doWork(): Result {
        if (Prefs(applicationContext).token == null) return Result.success()
        EventService.start(applicationContext)
        EventSink.catchUp(applicationContext)
        Repo.refresh(applicationContext)
        return Result.success()
    }
}
