package pl.kuweta.patrol.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : Exception(message)

class Api(private val prefs: Prefs) {

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // Serwer wysyła ping co 25 s, więc 70 s ciszy oznacza zerwane połączenie.
        val streamClient: OkHttpClient = client.newBuilder()
            .readTimeout(70, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        }
    }

    private suspend fun call(method: String, path: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(prefs.serverUrl.trimEnd('/') + path)
        prefs.token?.let { builder.header("Authorization", "Bearer $it") }
        if (method == "GET") builder.get() else builder.method(method, (body ?: "{}").toRequestBody(JSON_MEDIA))
        try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = runCatching {
                        json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content
                    }.getOrNull() ?: "Błąd serwera (${response.code})"
                    throw ApiException(message, response.code)
                }
                text
            }
        } catch (e: IOException) {
            throw ApiException("Brak połączenia z serwerem. Sprawdź internet i adres serwera.")
        }
    }

    suspend fun register(name: String, code: String): RegisterOut {
        val body = buildJsonObject { put("name", name); put("code", code) }.toString()
        return json.decodeFromString(call("POST", "/api/register", body))
    }

    suspend fun state(): AppState = json.decodeFromString(call("GET", "/api/state"))

    suspend fun clean() { call("POST", "/api/clean") }

    suspend fun requestExtension() { call("POST", "/api/extensions") }

    suspend fun decideExtension(id: Int, approve: Boolean) {
        call("POST", "/api/extensions/$id/decide", buildJsonObject { put("approve", approve) }.toString())
    }

    suspend fun testNotify() { call("POST", "/api/test-notify") }

    suspend fun schedule(): ScheduleSettings = json.decodeFromString(call("GET", "/api/schedule"))

    suspend fun saveSchedule(s: ScheduleSettings): ScheduleSettings =
        json.decodeFromString(call("PUT", "/api/schedule", json.encodeToString(s)))

    suspend fun saveScheduleFor(userId: Int, s: ScheduleSettings): ScheduleSettings =
        json.decodeFromString(call("PUT", "/api/admin/users/$userId/schedule", json.encodeToString(s)))

    suspend fun presets(): Presets = json.decodeFromString(call("GET", "/api/presets"))

    suspend fun ranking(month: String): Ranking = json.decodeFromString(call("GET", "/api/ranking?month=$month"))

    suspend fun admin(): AdminOverview = json.decodeFromString(call("GET", "/api/admin"))

    suspend fun adminSettings(intervalDays: Int? = null, maxLevel: Int? = null, notify: Map<Int, Int> = emptyMap()): AdminOverview {
        val body = buildJsonObject {
            intervalDays?.let { put("interval_days", it) }
            maxLevel?.let { put("max_level", it) }
            putJsonObject("notify_interval") { notify.forEach { (uid, min) -> put(uid.toString(), min) } }
        }
        return json.decodeFromString(call("PUT", "/api/admin/settings", body.toString()))
    }

    suspend fun adminState(assigneeId: Int, dueDate: String): AdminOverview {
        val body = buildJsonObject { put("assignee_id", assigneeId); put("due_date", dueDate) }
        return json.decodeFromString(call("PUT", "/api/admin/state", body.toString()))
    }

    suspend fun setCats(cats: List<String>): AdminOverview {
        val body = buildJsonObject { putJsonArray("cats") { cats.forEach { add(it) } } }
        return json.decodeFromString(call("PUT", "/api/admin/cats", body.toString()))
    }

    suspend fun pairCode(userId: Int? = null): PairCode =
        json.decodeFromString(call("POST", "/api/admin/pair-code" + (userId?.let { "?user_id=$it" } ?: "")))

    suspend fun addMessage(category: String, level: Int, text: String): AdminOverview {
        val body = buildJsonObject { put("category", category); put("level", level); put("text", text) }
        return json.decodeFromString(call("POST", "/api/admin/messages", body.toString()))
    }

    suspend fun deleteMessage(id: Int): AdminOverview =
        json.decodeFromString(call("DELETE", "/api/admin/messages/$id"))
}
