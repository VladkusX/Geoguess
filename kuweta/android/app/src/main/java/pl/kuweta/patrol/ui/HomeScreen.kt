package pl.kuweta.patrol.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import pl.kuweta.patrol.data.DayCode
import pl.kuweta.patrol.data.Segment
import java.time.LocalTime
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
fun HomeScreen(onLogout: () -> Unit, onRanking: () -> Unit) {
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
            // Przewijanie zostaje tylko jako zabezpieczenie (np. karta baterii). Normalnie wszystko ma się zmieścić.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val s = state
        if (s == null) {
            Brand()
            Text("Ładuję…".uppercase(), style = MaterialTheme.typography.headlineLarge)
            error?.let { Text(it, color = Orange) }
            return@Column
        }
        val duty = s.duty

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Brand()
            Spacer(Modifier.weight(1f))
            ConnectionDot(connected)
        }
        when {
            duty == null -> Sticker("Czekam na drugą osobę", bg = Ink, fg = Yellow)
            duty.isMe -> Sticker("Twój dyżur")
            else -> Sticker("Dyżur: ${duty.assigneeName}", bg = Ink, fg = Yellow)
        }

        Text(
            headline(s).uppercase(),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = dsp(28), lineHeight = dsp(30)),
        )

        if (duty != null && s.cats.isNotEmpty() && duty.daysLeft <= 0) {
            Text(catsLine(s.cats, duty.daysLeft), fontWeight = FontWeight.Bold)
        }

        if (duty != null && duty.autoExtended > 0) {
            Text(
                "Przedłużone automatycznie (${duty.autoExtended} ${if (duty.autoExtended == 1) "dzień" else "dni"}). Bez prośby, więc obelgi od razu na maksa.",
                fontWeight = FontWeight.Bold,
            )
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

        s.pendingReview?.let { review ->
            BrutalCard(bg = if (review.mine) Paper else Orange) {
                if (review.mine) {
                    Text("CZEKA NA POTWIERDZENIE", style = MaterialTheme.typography.titleMedium)
                    Text("${s.partner?.name ?: "Druga osoba"} musi potwierdzić, że kuweta lśni. Do tego czasu dyżur jest twój.")
                } else {
                    Text("${review.cleanerName.uppercase()} MÓWI, ŻE OGARNIĘTE", style = MaterialTheme.typography.titleMedium, color = Paper)
                    Text("Zajrzyj do kuwety. Lśni czy syf?", color = Paper)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrutalButton("Lśni ✔", { act({ it.reviewCleaning(review.id, true) }, "Potwierdzone. Dyżur przechodzi dalej.") }, Modifier.weight(1f), bg = Paper, fg = Ink, enabled = !busy, small = true)
                        BrutalButton("Syf ✖", { act({ it.reviewCleaning(review.id, false) }, "Odrzucone. Przypomnienia wracają.") }, Modifier.weight(1f), enabled = !busy, small = true)
                    }
                }
            }
        }

        TodayCard(s)
        ArcadeCard(s)

        if (duty != null && s.pendingReview == null) {
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
        BrutalButton("Ranking", onRanking, bg = Paper, fg = Ink, small = true)
    }

    if (confirmClean) {
        AlertDialog(
            onDismissRequest = { confirmClean = false },
            title = { Text("Na pewno ogarnięte?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Kłamstwo zostanie zapamiętane przez koty. Druga osoba musi potwierdzić, że lśni.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClean = false
                    act({ it.clean() }, "Zgłoszone. Czekasz, aż druga osoba potwierdzi.")
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
    val days = s.upcoming.ifEmpty { listOf(DayCode(LocalDate.now().toString(), s.today.code, s.today.timeline)) }
    var selected by remember { mutableIntStateOf(0) }
    val index = selected.coerceIn(0, days.lastIndex)
    val day = days[index]
    val date = LocalDate.parse(day.date)
    val timeline = if (index == 0) s.today.timeline else day.timeline
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pl"))
    val duty = s.duty
    // Okno kuwety: mój dyżur i dzień terminu albo później.
    val window = duty != null && duty.isMe && index >= duty.daysLeft
    var drag by remember { mutableFloatStateOf(0f) }

    BrutalCard(
        Modifier.pointerInput(days.size) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = {
                    val step = 60.dp.toPx()
                    // Tu nie używamy `index`, bo lambda zapamiętuje go z pierwszego rysowania.
                    val last = days.size - 1
                    if (drag <= -step) selected = (selected + 1).coerceAtMost(last)
                    if (drag >= step) selected = (selected - 1).coerceAtLeast(0)
                    drag = 0f
                },
            ) { change, amount ->
                change.consume()
                drag += amount
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹",
                style = MaterialTheme.typography.titleLarge,
                color = if (index > 0) Ink else Grey,
                modifier = Modifier
                    .clickable(enabled = index > 0) { selected = index - 1 }
                    .padding(horizontal = 6.dp),
            )
            val label = when (index) {
                0 -> "Dziś"
                1 -> "Jutro"
                else -> "%d.%02d".format(date.dayOfMonth, date.monthValue)
            }
            Text(
                "$label · $dayName · ${SHIFT_NAMES[day.code] ?: day.code}",
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = if (index < days.lastIndex) Ink else Grey,
                modifier = Modifier
                    .clickable(enabled = index < days.lastIndex) { selected = index + 1 }
                    .padding(horizontal = 6.dp),
            )
        }
        DayBar(timeline, window, nowFraction = if (index == 0) nowFraction() else null)
        HourScale()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Legend(Grey, "sen/cisza")
            Legend(Ink, "robota")
            Legend(if (window) Orange else Paper, if (window) "okno kuwety" else "wolne")
        }
        Text(
            when {
                index > 0 -> "Przesuń palcem, żeby zmienić dzień."
                s.today.canNotifyNow -> "Teraz można cię męczyć."
                else -> "Teraz masz spokój od powiadomień."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(2.dp))
        // Jeden rząd 7 dni: tydzień, w którym jest wybrany dzień (oszczędza miejsce na ekranie).
        val weekStart = index / 7 * 7
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            val week = days.drop(weekStart).take(7)
            week.forEachIndexed { col, d ->
                val i = weekStart + col
                DayChip(d, selected = i == index, onClick = { selected = i }, modifier = Modifier.weight(1f))
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private fun nowFraction(): Float {
    val t = LocalTime.now()
    return (t.hour * 60 + t.minute) / (24f * 60f)
}

/** Pasek doby z liniami co 2 godziny i (dla dziś) znacznikiem aktualnej godziny. */
@Composable
private fun DayBar(timeline: List<Segment>, window: Boolean, nowFraction: Float?) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .border(2.dp, Ink)
    ) {
        Row(Modifier.matchParentSize()) {
            timeline.forEach { seg ->
                val color = when (seg.kind) {
                    "work" -> Ink
                    "sleep" -> Grey
                    else -> if (window) Orange else Paper
                }
                Box(
                    Modifier
                        .weight(seg.minutes.toFloat())
                        .fillMaxSize()
                        .background(color)
                )
            }
        }
        Canvas(Modifier.matchParentSize()) {
            for (h in 2 until 24 step 2) {
                val x = size.width * h / 24f
                drawLine(Color(0x88888888), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            }
            nowFraction?.let {
                val x = size.width * it
                drawLine(Color(0xFFFF5A36), Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
            }
        }
    }
}

/** Podziałka 0, 2, 4 ... 24 ustawiona dokładnie pod liniami paska. */
@Composable
private fun HourScale() {
    BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
        val labelWidth = 22.dp
        for (h in 0..24 step 2) {
            val x = (maxWidth * (h / 24f) - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth)
            Text(
                "$h",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = bsp(9)),
                textAlign = when (h) {
                    0 -> androidx.compose.ui.text.style.TextAlign.Start
                    24 -> androidx.compose.ui.text.style.TextAlign.End
                    else -> androidx.compose.ui.text.style.TextAlign.Center
                },
                modifier = Modifier
                    .width(labelWidth)
                    .offset(x = x),
            )
        }
    }
}

@Composable
private fun DayChip(day: DayCode, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val date = LocalDate.parse(day.date)
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pl")).take(2) + " " + date.dayOfMonth,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = bsp(9),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
            maxLines = 1,
        )
        val bg = SHIFT_COLORS[day.code] ?: Paper
        Text(
            if (day.code == "-") "·" else day.code,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = if (bg == Ink) Yellow else Ink,
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .border(if (selected) 3.dp else 1.5.dp, if (selected) Orange else Ink)
                .padding(vertical = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
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
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = bsp(10)))
    }
}

/** Tablica wyników w wersji kompaktowej: serie i kto gra teraz/następny. */
@Composable
private fun ArcadeCard(s: AppState) {
    val duty = s.duty
    BrutalCard(bg = Ink) {
        Text("TABLICA WYNIKÓW", style = MaterialTheme.typography.titleMedium.copy(fontSize = dsp(14)), color = Yellow)
        s.streaks.forEach { st ->
            val me = st.userId == s.me.id
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (me) "▶ " else "  ") + st.name.uppercase(),
                    color = Paper, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text("🔥 ${st.current}", color = Yellow, fontWeight = FontWeight.Bold)
                Text("  rek. ${st.best}", color = Grey, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (duty != null) {
            val d = duty.daysLeft
            val now = when {
                d > 0 -> "za $d ${daysWord(d)}"
                d == 0 -> "dziś"
                else -> "spóźn. ${-d} ${daysWord(-d)}"
            }
            Text(
                "${duty.assigneeName.uppercase()} $now → ${duty.nextName.uppercase()} za ${duty.nextInDays} ${daysWord(duty.nextInDays)}",
                color = Mint, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
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
    // Autostartu i kłódki na Xiaomi nie da się odczytać, więc po zrobieniu użytkownik chowa kartę sam.
    // Karta wraca, jeśli system znowu zacznie usypiać aplikację.
    val prefs = remember { context.getSharedPreferences("kuweta_ui", Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean("battery_card_done", false)) }
    if (ignoring && (dismissed || (!xiaomi && !samsung))) return

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
            Text("Samsung: Ustawienia → Bateria → Limity użycia w tle → dodaj CnC do „Aplikacje nigdy nieusypiane”.")
        }
        BrutalButton("Ustawienia aplikacji", { openAppSettings(context) }, bg = Paper, fg = Ink, small = true)
        if (ignoring) {
            BrutalButton("Zrobione, ukryj", {
                prefs.edit().putBoolean("battery_card_done", true).apply()
                dismissed = true
            }, small = true)
        }
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
