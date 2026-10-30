package pl.kuweta.patrol.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserPub(
    val id: Int,
    val name: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("notify_interval_min") val notifyIntervalMin: Int = 30,
)

@Serializable
data class Duty(
    @SerialName("assignee_id") val assigneeId: Int,
    @SerialName("assignee_name") val assigneeName: String,
    @SerialName("is_me") val isMe: Boolean,
    @SerialName("due_date") val dueDate: String,
    @SerialName("days_left") val daysLeft: Int,
    val extended: Int = 0,
    @SerialName("interval_days") val intervalDays: Int = 2,
    @SerialName("next_name") val nextName: String,
    @SerialName("next_in_days") val nextInDays: Int,
    @SerialName("last_cleaned_at") val lastCleanedAt: String? = null,
    @SerialName("reminders_today") val remindersToday: Int = 0,
)

@Serializable
data class PendingExt(
    val id: Int,
    @SerialName("requester_id") val requesterId: Int,
    @SerialName("requester_name") val requesterName: String,
    @SerialName("created_at") val createdAt: String,
    val mine: Boolean,
)

@Serializable
data class Streak(
    @SerialName("user_id") val userId: Int,
    val name: String,
    val current: Int,
    val best: Int,
)

@Serializable
data class Segment(val from: String, val minutes: Int, val kind: String)

@Serializable
data class Today(
    val code: String,
    val timeline: List<Segment>,
    @SerialName("can_notify_now") val canNotifyNow: Boolean,
)

@Serializable
data class DayCode(val date: String, val code: String)

@Serializable
data class AppState(
    @SerialName("server_time") val serverTime: String,
    val me: UserPub,
    val partner: UserPub? = null,
    val duty: Duty? = null,
    @SerialName("pending_extension") val pendingExtension: PendingExt? = null,
    val streaks: List<Streak> = emptyList(),
    val today: Today,
    val upcoming: List<DayCode> = emptyList(),
    @SerialName("max_level") val maxLevel: Int = 4,
    val cats: List<String> = emptyList(),
)

@Serializable
data class ScheduleSettings(
    val cycle: List<String>,
    @SerialName("cycle_start") val cycleStart: String? = null,
    val shifts: Map<String, List<String>> = emptyMap(),
    @SerialName("commute_min") val commuteMin: Int = 30,
    @SerialName("grace_min") val graceMin: Int = 60,
    @SerialName("sleep_after_night_h") val sleepAfterNightH: Int = 7,
    @SerialName("quiet_start") val quietStart: String = "23:00",
    @SerialName("quiet_end") val quietEnd: String = "08:00",
    val overrides: Map<String, String> = emptyMap(),
)

@Serializable
data class RankPlayer(
    @SerialName("user_id") val userId: Int,
    val name: String,
    val cleanings: Int,
    @SerialName("on_time") val onTime: Int,
    val covers: Int,
    val extensions: Int,
    @SerialName("current_streak") val currentStreak: Int,
    @SerialName("best_streak") val bestStreak: Int,
)

@Serializable
data class RankHistory(
    val name: String,
    val at: String,
    @SerialName("on_time") val onTime: Boolean,
    val extensions: Int,
    val cover: Boolean,
)

@Serializable
data class Ranking(val month: String, val players: List<RankPlayer>, val history: List<RankHistory>)

@Serializable
data class AdminUser(
    val id: Int,
    val name: String,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("notify_interval_min") val notifyIntervalMin: Int = 30,
    val schedule: ScheduleSettings,
)

@Serializable
data class Message(val id: Int, val category: String, val level: Int, val text: String, val builtin: Int = 0)

@Serializable
data class AdminOverview(
    @SerialName("interval_days") val intervalDays: Int,
    @SerialName("max_level") val maxLevel: Int,
    @SerialName("assignee_id") val assigneeId: Int? = null,
    @SerialName("due_date") val dueDate: String? = null,
    val cats: List<String> = emptyList(),
    val users: List<AdminUser>,
    val messages: List<Message>,
)

@Serializable
data class PairCode(val code: String, @SerialName("expires_at") val expiresAt: String)

@Serializable
data class RegisterOut(val token: String, @SerialName("user_id") val userId: Int)

@Serializable
data class PushEvent(
    val type: String,
    val kind: String? = null,
    val title: String? = null,
    val body: String? = null,
    @SerialName("extension_id") val extensionId: Int? = null,
    val level: Int? = null,
)

@Serializable
data class Presets(
    @SerialName("5bryg") val fiveBrigade: List<String>,
    @SerialName("3zm") val threeShifts: List<String>,
    val shifts: Map<String, List<String>>,
)
