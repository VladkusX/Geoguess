package pl.kuweta.patrol.push

import android.content.Context
import kotlinx.serialization.SerializationException
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.ApiException
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.PushEvent

/**
 * Jedno miejsce, przez które przechodzą zdarzenia z serwera: z SSE i z zapasowego
 * sprawdzania w watchdogu. Numer zdarzenia pilnuje, żeby nic nie pokazało się dwa razy.
 */
object EventSink {

    /** Zwraca false, jeśli to zdarzenie już było obsłużone. */
    @Synchronized
    fun accept(context: Context, id: Long, event: PushEvent?): Boolean {
        val prefs = Prefs(context)
        if (id <= prefs.lastEventId) return false
        prefs.lastEventId = id
        if (event?.type == "notify") Notifier.show(context, event)
        return true
    }

    /**
     * SSE potrafi paść po cichu (uśpiony telefon, zmiana sieci). Watchdog co 15 min
     * dopytuje serwer o zaległe zdarzenia, żeby np. info o sprzątnięciu nie przepadło.
     */
    suspend fun catchUp(context: Context) {
        val prefs = Prefs(context)
        if (prefs.token == null) return
        try {
            val after = prefs.lastEventId
            val out = Api(prefs).missedEvents(after)
            if (after == 0L) {
                synchronized(this) { if (prefs.lastEventId == 0L) prefs.lastEventId = out.lastId }
                return
            }
            out.events.forEach { accept(context, it.id, it.payload) }
        } catch (_: ApiException) {
        } catch (_: SerializationException) {
        }
    }
}
