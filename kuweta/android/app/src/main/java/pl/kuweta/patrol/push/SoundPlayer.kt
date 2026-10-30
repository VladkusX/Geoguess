package pl.kuweta.patrol.push

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import pl.kuweta.patrol.R
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.SoundRef
import java.io.File

/**
 * Dźwięk powiadomień. Kanały Androida nie pozwalają podmienić dźwięku po utworzeniu,
 * więc aplikacja gra go sama: domyślne miau albo plik wgrany przez admina.
 */
object SoundPlayer {
    private const val MAX_MS = 10_000L
    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    private fun dir(context: Context) = File(context.filesDir, "sounds").apply { mkdirs() }

    fun file(context: Context, ref: SoundRef) = File(dir(context), "${ref.id}.${ref.ext}")

    private fun slotsPrefs(context: Context) =
        context.getSharedPreferences("kuweta_sounds", Context.MODE_PRIVATE)

    /** Pobiera brakujące pliki i zapamiętuje, który dźwięk gra w którym rodzaju powiadomień. */
    suspend fun sync(context: Context, slots: Map<String, SoundRef?>) {
        val api = Api(Prefs(context))
        val editor = slotsPrefs(context).edit()
        for (slot in listOf("reminder", "partner")) {
            val ref = slots[slot]
            if (ref == null) {
                editor.remove(slot)
                continue
            }
            val f = file(context, ref)
            if (f.exists() || api.downloadSound(ref.id, f)) editor.putString(slot, f.name) else editor.remove(slot)
        }
        editor.apply()
        val keep = slots.values.filterNotNull().map { "${it.id}.${it.ext}" }.toSet()
        dir(context).listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    private fun canMakeNoise(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm.currentInterruptionFilter <= NotificationManager.INTERRUPTION_FILTER_ALL
    }

    fun play(context: Context, slot: String) {
        if (!canMakeNoise(context)) return
        val custom = slotsPrefs(context).getString(slot, null)?.let { File(dir(context), it) }?.takeIf { it.exists() }
        val uri = custom?.let { Uri.fromFile(it) } ?: defaultUri(context, slot)
        start(context, uri)
    }

    fun defaultUri(context: Context, slot: String): Uri {
        val raw = if (slot == "reminder") R.raw.meow else R.raw.mrrp
        return Uri.parse("android.resource://${context.packageName}/$raw")
    }

    /** Odsłuch w panelu admina, gra niezależnie od trybu cichego. */
    fun preview(context: Context, uri: Uri) = start(context, uri)

    private fun start(context: Context, uri: Uri) {
        main.post {
            stop()
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context.applicationContext, uri)
                    setOnCompletionListener { stop() }
                    setOnErrorListener { _, _, _ -> stop(); true }
                    prepare()
                    start()
                }
                main.postDelayed({ stop() }, MAX_MS)
            } catch (_: Exception) {
                stop()
            }
        }
    }

    private fun stop() {
        main.removeCallbacksAndMessages(null)
        player?.runCatching { release() }
        player = null
    }
}
