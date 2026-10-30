package pl.kuweta.patrol

import android.app.Application
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.Repo
import pl.kuweta.patrol.push.EventService
import pl.kuweta.patrol.push.Notifier
import pl.kuweta.patrol.push.Watchdog

class KuwetaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Repo.loadCached(this)
        if (Prefs(this).token != null) {
            Watchdog.schedule(this)
            EventService.start(this)
        }
    }
}
