package pl.kuweta.patrol.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.kuweta.patrol.data.AdminUser
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.ApiException
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.Repo
import pl.kuweta.patrol.data.ScheduleSettings
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val PRESET_5BRYG = listOf("R", "R", "P", "P", "N", "N", "-", "-", "-", "-")
private val PRESET_3ZM = List(5) { "N" } + listOf("-", "-") + List(5) { "P" } + listOf("-", "-") + List(5) { "R" } + listOf("-", "-")
private val DEFAULT_SHIFTS = mapOf("R" to listOf("06:00", "14:00"), "P" to listOf("14:00", "22:00"), "N" to listOf("22:00", "06:00"))
private val CODES = listOf("R", "P", "N", "-")

fun shiftCodeFor(s: ScheduleSettings, day: LocalDate): String {
    s.overrides[day.toString()]?.let { return it }
    if (s.cycle.isEmpty()) return "-"
    val start = s.cycleStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: day
    val diff = ChronoUnit.DAYS.between(start, day)
    return s.cycle[Math.floorMod(diff, s.cycle.size.toLong()).toInt()]
}

fun pickDate(context: Context, initial: String?, onPicked: (String) -> Unit) {
    val d = initial?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
    DatePickerDialog(context, { _, y, m, day -> onPicked(LocalDate.of(y, m + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    val isAdmin = state?.me?.isAdmin == true

    var people by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var targetId by remember { mutableStateOf<Int?>(null) } // null = ja
    var s by remember { mutableStateOf<ScheduleSettings?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun save(next: ScheduleSettings, done: String = "Zapisane.") {
        s = next
        busy = true
        scope.launch {
            try {
                val api = Api(Prefs(context))
                s = targetId?.let { api.saveScheduleFor(it, next) } ?: api.saveSchedule(next)
                message = done
                Repo.refresh(context)
            } catch (e: ApiException) {
                message = e.message
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(targetId) {
        try {
            val api = Api(Prefs(context))
            if (isAdmin) people = api.admin().users
            s = if (targetId == null) api.schedule() else people.first { it.id == targetId }.schedule
            message = null
        } catch (e: ApiException) {
            message = e.message
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Sticker("Grafik zmian")
        Text("Grafik ustawiasz raz: cykl powtarza się sam w nieskończoność. Urlop, zamianę albo nadgodziny poprawiasz, stukając dzień w kalendarzu.", style = MaterialTheme.typography.bodySmall)
        if (isAdmin && people.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("Mój", targetId == null, { targetId = null })
                people.filter { it.id != state?.me?.id }.forEach { p ->
                    Chip(p.name, targetId == p.id, { targetId = p.id })
                }
            }
        }
        val cur = s
        if (cur == null) {
            Text(message ?: "Ładuję…")
            return@Column
        }
        fun update(f: ScheduleSettings.() -> ScheduleSettings) { s = cur.f() }

        BrutalCard {
            SectionTitle("Szablon")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("5-brygadówka", cur.cycle == PRESET_5BRYG, {
                    update { copy(cycle = PRESET_5BRYG) }
                }, Modifier.weight(1f))
                Chip("Pn–Pt 3 zmiany", cur.cycle == PRESET_3ZM, {
                    val monday = LocalDate.now().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
                    update { copy(cycle = PRESET_3ZM, cycleStart = monday.toString()) }
                }, Modifier.weight(1f))
            }
            Text("Stuknij dzień, żeby zmienić: R → P → N → wolne.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cur.cycle.forEachIndexed { i, code ->
                    val bg = SHIFT_COLORS[code] ?: Paper
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${i + 1}", style = MaterialTheme.typography.bodySmall.copy(fontSize = bsp(9)))
                        Text(
                            if (code == "-") "·" else code,
                            fontWeight = FontWeight.Bold,
                            color = if (bg == Ink) Yellow else Ink,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(30.dp)
                                .background(bg)
                                .border(2.dp, Ink)
                                .clickable {
                                    val next = CODES[(CODES.indexOf(code) + 1) % CODES.size]
                                    update { copy(cycle = cycle.toMutableList().also { it[i] = next }) }
                                }
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalButton("+ dzień", { update { copy(cycle = cycle + "-") } }, Modifier.weight(1f), bg = Paper, fg = Ink, small = true)
                BrutalButton("− dzień", { if (cur.cycle.size > 1) update { copy(cycle = cycle.dropLast(1)) } }, Modifier.weight(1f), bg = Paper, fg = Ink, small = true)
            }
            Text("Dzień 1 cyklu to data, od której liczy się kolumna „1” powyżej (np. twoja pierwsza ranna zmiana).", style = MaterialTheme.typography.bodySmall)
            BrutalButton(
                "📅 Dzień 1 cyklu: ${prettyDate(cur.cycleStart)} · zmień",
                { pickDate(context, cur.cycleStart) { picked -> update { copy(cycleStart = picked) } } },
                bg = Yellow, fg = Ink, small = true,
            )
        }

        MonthCalendar(cur) { date, code ->
            val key = date.toString()
            val base = shiftCodeFor(cur.copy(overrides = emptyMap()), date)
            val next = if (code == null || code == base) cur.copy(overrides = cur.overrides - key)
            else cur.copy(overrides = cur.overrides + (key to code))
            save(next, "Zapisane: $key → ${SHIFT_NAMES[code ?: base] ?: base}")
        }

        BrutalCard {
            SectionTitle("Godziny zmian")
            listOf("R", "P", "N").forEach { code ->
                val hours = cur.shifts[code] ?: DEFAULT_SHIFTS.getValue(code)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(SHIFT_NAMES.getValue(code), modifier = Modifier.width(100.dp), fontWeight = FontWeight.Bold)
                    TimeField(hours[0], { v -> update { copy(shifts = shifts + (code to listOf(v, hours[1]))) } }, "od", Modifier.weight(1f))
                    TimeField(hours[1], { v -> update { copy(shifts = shifts + (code to listOf(hours[0], v))) } }, "do", Modifier.weight(1f))
                }
            }
        }

        BrutalCard {
            SectionTitle("Kiedy dać spokój")
            NumberRow("Dojazd przed zmianą (min)", cur.commuteMin) { update { copy(commuteMin = it) } }
            NumberRow("Męczenie po robocie (min)", cur.graceMin) { update { copy(graceMin = it) } }
            NumberRow("Sen po nocce (godz.)", cur.sleepAfterNightH) { update { copy(sleepAfterNightH = it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField(cur.quietStart, { v -> update { copy(quietStart = v) } }, "Cisza nocna od", Modifier.weight(1f))
                TimeField(cur.quietEnd, { v -> update { copy(quietEnd = v) } }, "do", Modifier.weight(1f))
            }
            Text("Po rannej zmianie nie ma snu. Po popołudniówce i nocce przez „męczenie po robocie” powiadomienia jeszcze lecą, potem cisza.", style = MaterialTheme.typography.bodySmall)
        }

        message?.let { Text(it, fontWeight = FontWeight.Bold) }
        BrutalButton(if (busy) "Zapisuję…" else "Zapisz grafik", enabled = !busy, onClick = { save(cur) })
    }
}

@Composable
private fun NumberRow(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Chip("−", false, { onChange((value - if (value > 20) 15 else 1).coerceAtLeast(0)) })
        Text("$value", fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        Chip("+", false, { onChange(value + if (value >= 20) 15 else 1) })
    }
}

private val WEEKDAYS = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")

/** Miesiąc z grafiku. Stuknięcie dnia pozwala go nadpisać (urlop, zamiana, nadgodziny). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthCalendar(s: ScheduleSettings, onDayChange: (LocalDate, String?) -> Unit) {
    var month by remember { mutableStateOf(YearMonth.now()) }
    var editing by remember { mutableStateOf<LocalDate?>(null) }
    val today = LocalDate.now()

    BrutalCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("◀", false, { month = month.minusMonths(1) })
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("pl")).uppercase() + " " + month.year,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Chip("▶", false, { month = month.plusMonths(1) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            WEEKDAYS.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        val offset = month.atDay(1).dayOfWeek.value - 1
        val days = month.lengthOfMonth()
        val rows = (offset + days + 6) / 7
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - offset + 1
                    if (dayNum < 1 || dayNum > days) {
                        Spacer(Modifier.weight(1f))
                        continue
                    }
                    val date = month.atDay(dayNum)
                    val code = shiftCodeFor(s, date)
                    val changed = s.overrides.containsKey(date.toString())
                    val bg = SHIFT_COLORS[code] ?: Paper
                    val fg = if (bg == Ink) Yellow else Ink
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .background(bg)
                            .border(if (changed || date == today) 3.dp else 1.5.dp, if (changed) Orange else Ink)
                            .clickable { editing = date }
                            .padding(vertical = 3.dp),
                    ) {
                        Text("$dayNum", style = MaterialTheme.typography.bodySmall.copy(fontSize = bsp(10)), color = fg)
                        Text(if (code == "-") "·" else code, fontWeight = FontWeight.Bold, color = fg)
                    }
                }
            }
        }
        Text("Gruba czarna ramka: dziś. Pomarańczowa: dzień zmieniony ręcznie.", style = MaterialTheme.typography.bodySmall)
    }

    editing?.let { date ->
        val label = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pl")) + ", " + date
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = Paper,
            title = { Text(label, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Co tego dnia?")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        CODES.forEach { c ->
                            Chip(SHIFT_NAMES.getValue(c), shiftCodeFor(s, date) == c, {
                                onDayChange(date, c)
                                editing = null
                            })
                        }
                    }
                    if (s.overrides.containsKey(date.toString())) {
                        Chip("↺ jak w grafiku", false, {
                            onDayChange(date, null)
                            editing = null
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editing = null }) { Text("Zamknij", color = Ink) } },
        )
    }
}

/** Godzina wybierana z zegara, bez klawiatury. */
@Composable
private fun TimeField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(Paper)
                .border(2.dp, Ink)
                .clickable {
                    val parts = value.split(":")
                    val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    TimePickerDialog(context, { _, hh, mm -> onChange("%02d:%02d".format(hh, mm)) }, h, m, true).show()
                }
                .padding(vertical = 12.dp),
        )
    }
}

fun prettyDate(iso: String?): String =
    iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?.let { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) } ?: "nie ustawiony"
