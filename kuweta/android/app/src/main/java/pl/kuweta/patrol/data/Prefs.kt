package pl.kuweta.patrol.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("kuweta", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("server_url", "") ?: ""
        set(v) = sp.edit().putString("server_url", v).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    var lastEventId: Long
        get() = sp.getLong("last_event_id", 0)
        set(v) = sp.edit().putLong("last_event_id", v).apply()

    var stateJson: String?
        get() = sp.getString("state_json", null)
        set(v) = sp.edit().putString("state_json", v).apply()

    fun logout() {
        sp.edit().remove("token").remove("last_event_id").remove("state_json").apply()
    }
}
