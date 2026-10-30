package pl.kuweta.patrol.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import pl.kuweta.patrol.push.SoundPlayer
import pl.kuweta.patrol.widget.KuwetaWidget

/** Ostatni znany stan dyżuru, wspólny dla ekranu, widżetu i powiadomień. */
object Repo {
    val state = MutableStateFlow<AppState?>(null)
    val error = MutableStateFlow<String?>(null)
    val connected = MutableStateFlow(false)

    fun loadCached(context: Context) {
        if (state.value != null) return
        val cached = Prefs(context).stateJson ?: return
        state.value = runCatching { Api.json.decodeFromString<AppState>(cached) }.getOrNull()
    }

    suspend fun refresh(context: Context) {
        val prefs = Prefs(context)
        if (prefs.token == null) return
        try {
            val s = Api(prefs).state()
            state.value = s
            prefs.stateJson = Api.json.encodeToString(s)
            error.value = null
            KuwetaWidget.updateAll(context)
            SoundPlayer.sync(context, s.sounds)
        } catch (e: ApiException) {
            error.value = e.message
        }
    }

    fun clear() {
        state.value = null
        error.value = null
        connected.value = false
    }
}
