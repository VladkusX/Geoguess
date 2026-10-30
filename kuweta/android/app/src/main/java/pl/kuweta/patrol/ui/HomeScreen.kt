package pl.kuweta.patrol.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.ApiException
import pl.kuweta.patrol.data.AppState
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.Repo
import pl.kuweta.patrol.push.EventService
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val error by Repo.error.collectAsState()
    val connected by Repo.connected.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClean by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    fun act(block: suspend (Api) -> Unit, done: String? = null) {
        busy = true
        message = null
        scope.launch {
            try {
                block(Api(Prefs(context)))
                Repo.refresh(context)
                message = done
            } catch (e: ApiException) {
                message = e.message
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val s = state
        if (s == null) {
            Sticker("Kuweta Patrol")
            Text("Ładuję…".uppercase(), style = MaterialTheme.typography.headlineLarge)
            error?.let { Text(it, color = Orange) }
            return@Column
        }
        val duty = s.duty

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                duty == null -> Sticker("Czekam na drugą osobę", bg = Ink, fg = Yellow)
                duty.isMe -> Sticker("Twój dyżur")
                else -> Sticker("Dyżur: ${duty.assigneeName}", bg = Ink, fg = Yellow)
            }
            Spacer(Modifier.weight(1f))
            ConnectionDot(connected)
        }

        Text(headline(s).uppercase(), style = MaterialTheme.typography.headlineLarge)

        if (duty != null && s.cats.isNotEmpty() && duty.daysLeft <= 0) {
            Text(catsLine(s.cats, duty.daysLeft), fontWeight = FontWeight.Bold)
        }

        if (duty != null && duty.extended > 0) {
            Text("Przedłużone ${duty.extended}x. Koty pamiętają.", fontWeight = FontWeight.Bold)
        }

        s.pendingExtension?.let { ext ->
            BrutalCard(bg = if (ext.mine) Paper else Orange) {
                if (ext.mine) {
                    Text("PROŚBA WYSŁANA", style = MaterialTheme.typography.titleMedium)
                    Text("Czekasz na zgodę: ${s.partner?.name ?: "druga osoba"}. Do tego czasu przypomnienia lecą dalej.")
                } else {
                    Text("${ext.requesterName.uppercase()} PROSI O +1 DZIEŃ", style = MaterialTheme.typography.titleMedium, color = Paper)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrutalButton("Zgoda", { act({ it.decideExtension(ext.id, true) }, "Zgoda wysłana") }, Modifier.weight(1f), bg = Paper, fg = Ink, enabled = !busy, small = true)
                        BrutalButton("Odmowa", { act({ it.decideExtension(ext.id, false) }, "Odmowa wysłana") }, Modifier.weight(1f), enabled = !busy, small = true)
                    }
                }
            }
        }

        TodayCard(s)
        ArcadeCard(s)

        if (duty != null) {
            BrutalButton(
                if (duty.isMe) "Ogarnięte ✔" else "Ogarnięte za ${duty.assigneeName}",
                onClick = { confirmClean = true },
                enabled = !busy,
            )
            if (duty.isMe && s.pendingExtension == null && duty.daysLeft <= 1) {
                BrutalButton(
                    "Nie dam rady → +1 dzień",
                    onClick = { act({ it.requestExtension() }, "Prośba wysłana do: ${s.partner?.name ?: "drugiej osoby"}") },
                    bg = Paper, fg = Ink, enabled = !busy,
                )
            }
        }
        message?.let { Text(it, fontWeight = FontWeight.Bold) }
        error?.let { Text("Offline: $it", color = Orange) }

        BatteryCard()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrutalButton("Test powiadomienia", { act({ it.testNotify() }, "Wysłane. Powinno przyjść za sekundę.") }, Modifier.weight(1f), bg = Paper, fg = Ink, small = true, enabled = !busy)
            BrutalButton("Rozłącz", { confirmLogout = true }, Modifier.weight(1f), bg = Paper, fg = Ink, small = true)
        }
    }

    if (confirmClean) {
        AlertDialog(
            onDismissRequest = { confirmClean = false },
            title = { Text("Na pewno ogarnięte?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Kłamstwo zostanie zapamiętane przez koty. Druga osoba dostanie powiadomienie.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClean = false
                    act({ it.clean() }, "Zapisane. Szacun.")
                }) { Text("TAK, CZYSTO", color = Ink, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmClean = false }) { Text("Jeszcze nie", color = Ink) } },
            containerColor = Paper,
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Rozłączyć telefon?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Przestaną przychodzić powiadomienia. Żeby wrócić, potrzebny będzie nowy kod parowania.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    EventService.stop(context)
                    Prefs(context).logout()
                    Repo.clear()
                    onLogout()
                }) { Text("ROZŁĄCZ", color = Orange, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Anuluj", color = Ink) } },
            containerColor = Paper,
        )
    }
}

private fun catsLine(cats: List<String>, daysLeft: Int): String {
    val who = if (cats.size == 1) cats[0] else cats.dropLast(1).joinToString(", ") + " i " + cats.last()
    val plural = cats.size > 1
    return when {
        daysLeft < 0 -> "$who ${if (plural) "planują" else "planuje"} zemstę."
        else -> "$who ${if (plural) "czekają" else "czeka"} na czystą kuwetę."
    }
}

private fun headline(s: AppState): String {
    val duty = s.duty ?: return "Sparuj drugą osobę"
    val d = duty.daysLeft
    return if (duty.isMe) when {
        d > 0 -> "Kuweta\nza $d ${daysWord(d)}"
        d == 0 -> "Kuweta\ndziś!"
        else -> "Spóźnienie\n${-d} ${daysWord(-d)}!"
    } else when {
        d > 0 -> "Luz.\n${duty.assigneeName} za $d ${daysWord(d)}"
        d == 0 -> "Luz.\n${duty.assigneeName} ma dziś"
        else -> "${duty.assigneeName}\nolewa ${-d} ${daysWord(-d)}"
    }
}

@Composable
private fun ConnectionDot(connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .background(if (connected) Mint else Orange)
                .border(1.5.dp, Ink)
        )
        Text(if (connected) "online" else "offline", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TodayCard(s: AppState) {
    val today = LocalDate.now()
    val dayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pl"))
    val duty = s.duty
    val windowToday = duty != null && duty.isMe && duty.daysLeft <= 0
    BrutalCard {
        Text("Dziś · $dayName · ${SHIFT_NAMES[s.today.code] ?: s.today.code}", fontWeight = FontWeight.Bold)
        Row(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .border(2.dp, Ink)
        ) {
            s.today.timeline.forEach { seg ->
                val color = when (seg.kind) {
                    "work" -> Ink
                    "sleep" -> Grey
                    else -> if (windowToday) Orange else Paper
                }
                Box(
                    Modifier
                        .weight(seg.minutes.toFloat())
                        .fillMaxSize()
                        .background(color)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Legend(Grey, "sen/cisza")
            Legend(Ink, "robota")
            Legend(if (windowToday) Orange else Paper, if (windowToday) "okno kuwety" else "wolne")
        }
        Text(
            if (s.today.canNotifyNow) "Teraz można cię męczyć." else "Teraz masz spokój od powiadomień.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            s.upcoming.take(10).forEach { day ->
                val date = LocalDate.parse(day.date)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pl")).take(2),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                    )
                    val bg = SHIFT_COLORS[day.code] ?: Paper
                    Text(
                        if (day.code == "-") "·" else day.code,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = if (bg == Ink) Yellow else Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .border(1.5.dp, Ink)
                            .padding(vertical = 2.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier
                .size(9.dp)
                .background(color)
                .border(1.5.dp, Ink)
        )
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp))
    }
}

/** Element z wersji "Arcade": streak bez przedłużeń i kolejny gracz. */
@Composable
private fun ArcadeCard(s: AppState) {
    val duty = s.duty
    BrutalCard(bg = Ink) {
        Text("TABLICA WYNIKÓW", style = MaterialTheme.typography.titleMedium, color = Yellow)
        s.streaks.forEach { st ->
            val me = st.userId == s.me.id
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (me) "▶ " else "  ") + st.name.uppercase(),
                    color = Paper, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                Text("🔥 ${st.current}", color = Yellow, fontWeight = FontWeight.Bold)
                Text("  rekord ${st.best}", color = Grey, style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("streak = ogarnięte na czas, bez przedłużania", color = Grey, style = MaterialTheme.typography.bodySmall)
        if (duty != null) {
            Spacer(Modifier.height(4.dp))
            val d = duty.daysLeft
            val now = when {
                d > 0 -> "za $d ${daysWord(d)}"
                d == 0 -> "dziś"
                else -> "spóźnienie ${-d} ${daysWord(-d)}"
            }
            Text("TERAZ GRA: ${duty.assigneeName.uppercase()} · $now", color = Mint, fontWeight = FontWeight.Bold)
            Text(
                "NASTĘPNY GRACZ: ${duty.nextName.uppercase()} · za ${duty.nextInDays} ${daysWord(duty.nextInDays)}",
                color = Paper, fontWeight = FontWeight.Bold,
            )
            if (duty.remindersToday > 0) {
                Text("Przypomnień dziś: ${duty.remindersToday}", color = Grey, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Instrukcja dla Xiaomi/Samsunga, żeby system nie ubijał połączenia z serwerem. */
@SuppressLint("BatteryLife")
@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val ignoring = remember(tick) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }
    val maker = Build.MANUFACTURER.lowercase()
    val xiaomi = maker.contains("xiaomi") || maker.contains("poco") || maker.contains("redmi")
    val samsung = maker.contains("samsung")
    if (ignoring && !xiaomi && !samsung) return

    BrutalCard {
        Text("ŻEBY POWIADOMIENIA DOCHODZIŁY", style = MaterialTheme.typography.titleMedium)
        if (!ignoring) {
            Text("System usypia aplikację. Zezwól na pracę w tle bez ograniczeń.")
            BrutalButton("Bateria: bez ograniczeń", {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                )
            }, small = true)
        }
        if (xiaomi) {
            Text("Xiaomi/POCO: włącz Autostart i ustaw Oszczędzanie baterii na „Bez ograniczeń”. Zablokuj też aplikację w ostatnich (kłódka).")
            BrutalButton("Otwórz Autostart", { openXiaomiAutostart(context) }, bg = Paper, fg = Ink, small = true)
        }
        if (samsung) {
            Text("Samsung: Ustawienia → Bateria → Limity użycia w tle → dodaj Kuweta Patrol do „Aplikacje nigdy nieusypiane”.")
        }
        BrutalButton("Ustawienia aplikacji", { openAppSettings(context) }, bg = Paper, fg = Ink, small = true)
    }
}

private fun openXiaomiAutostart(context: Context) {
    val intent = Intent().setComponent(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
    )
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppSettings(context)
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
}
