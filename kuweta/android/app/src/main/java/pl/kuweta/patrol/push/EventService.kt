package pl.kuweta.patrol.push

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.PushEvent
import pl.kuweta.patrol.data.Repo

/**
 * Trzyma stałe połączenie SSE z serwerem na homelabie i zamienia zdarzenia
 * na powiadomienia. Zastępuje Firebase, więc działa bez usług Google.
 */
class EventService : Service() {

    companion object {
        fun start(context: Context) {
            if (Prefs(context).token == null) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, EventService::class.java))
            } catch (_: Exception) {
                // System nie pozwolił wystartować z tła. Watchdog spróbuje ponownie.
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EventService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var source: EventSource? = null
    private var reconnectJob: Job? = null
    private var attempt = 0
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground(connected = false)
        watchNetwork()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(Repo.connected.value)
        if (Prefs(this).token == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (source == null && reconnectJob?.isActive != true) connect()
        return START_STICKY
    }

    override fun onDestroy() {
        networkCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        source?.cancel()
        source = null
        Repo.connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground(connected: Boolean) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifier.ID_SERVICE, Notifier.serviceNotification(this, connected), type)
    }

    private fun updateServiceNotification(connected: Boolean) {
        Repo.connected.value = connected
        if (Notifier.canPost(this)) {
            try {
                NotificationManagerCompat.from(this)
                    .notify(Notifier.ID_SERVICE, Notifier.serviceNotification(this, connected))
            } catch (_: SecurityException) {
            }
        }
    }

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Nowa sieć (np. Wi-Fi -> LTE): stare gniazdo pewnie już nie żyje.
                reconnectNow()
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        } catch (_: Exception) {
        }
    }

    @Synchronized
    private fun reconnectNow() {
        reconnectJob?.cancel()
        source?.cancel()
        source = null
        attempt = 0
        connect()
    }

    @Synchronized
    private fun connect() {
        val prefs = Prefs(this)
        val token = prefs.token ?: return
        val after = prefs.lastEventId
        val url = prefs.serverUrl.trimEnd('/') + "/api/events" + if (after > 0) "?after=$after" else ""
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "text/event-stream")
            .build()
        source = EventSources.createFactory(Api.streamClient).newEventSource(request, listener)
    }

    private fun scheduleReconnect(slow: Boolean = false) {
        source = null
        reconnectJob?.cancel()
        val seconds = if (slow) 300L else minOf(60L, 1L shl minOf(attempt, 6))
        attempt++
        reconnectJob = scope.launch {
            delay(seconds * 1000)
            connect()
        }
    }

    private val listener = object : EventSourceListener() {
        override fun onOpen(eventSource: EventSource, response: Response) {
            attempt = 0
            updateServiceNotification(true)
            scope.launch { Repo.refresh(this@EventService) }
        }

        override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
            val prefs = Prefs(this@EventService)
            if (type == "hello") {
                val lastId = runCatching {
                    Api.json.parseToJsonElement(data).jsonObject["last_id"]?.jsonPrimitive?.longOrNull
                }.getOrNull()
                if (prefs.lastEventId == 0L && lastId != null) prefs.lastEventId = lastId
                return
            }
            val event = runCatching { Api.json.decodeFromString<PushEvent>(data) }.getOrNull()
            val eventId = id?.toLongOrNull()
            if (eventId != null) {
                if (!EventSink.accept(this@EventService, eventId, event)) return
            } else if (event?.type == "notify") {
                Notifier.show(this@EventService, event)
            }
            scope.launch { Repo.refresh(this@EventService) }
        }

        override fun onClosed(eventSource: EventSource) {
            if (eventSource !== source) return
            updateServiceNotification(false)
            scheduleReconnect()
        }

        override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
            if (eventSource !== source) return
            updateServiceNotification(false)
            scheduleReconnect(slow = response?.code == 401)
        }
    }
}
