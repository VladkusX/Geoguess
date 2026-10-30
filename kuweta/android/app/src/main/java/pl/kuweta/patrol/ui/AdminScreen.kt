package pl.kuweta.patrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.kuweta.patrol.data.AdminOverview
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.ApiException
import pl.kuweta.patrol.data.PairCode
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.Repo

private val INTERVALS = listOf(10, 15, 20, 30, 45, 60, 90, 120)
private val LEVELS = mapOf(1 to "łagodnie", 2 to "z jajem", 3 to "ostro", 4 to "bez litości")
private val CATEGORIES = linkedMapOf(
    "reminder" to "Przypomnienia",
    "extension" to "Po przedłużeniu",
    "partner" to "Donos do drugiej osoby",
    "cleaned" to "Po sprzątnięciu",
    "ext_request" to "Prośba o +1 dzień",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<AdminOverview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pair by remember { mutableStateOf<PairCode?>(null) }

    fun act(block: suspend (Api) -> AdminOverview?, done: String? = null) {
        scope.launch {
            try {
                block(Api(Prefs(context)))?.let { data = it }
                message = done
                Repo.refresh(context)
            } catch (e: ApiException) {
                message = e.message
            }
        }
    }

    LaunchedEffect(Unit) { act({ it.admin() }) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Sticker("Panel admina", bg = Ink, fg = Yellow)
        message?.let { Text(it, fontWeight = FontWeight.Bold) }
        val d = data ?: run {
            Text("Ładuję…")
            return@Column
        }

        BrutalCard {
            SectionTitle("Kuweta co ile dni")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("−", false, { if (d.intervalDays > 1) act({ it.adminSettings(intervalDays = d.intervalDays - 1) }) })
                Text("${d.intervalDays} ${daysWord(d.intervalDays)}", style = MaterialTheme.typography.titleLarge)
                Chip("+", false, { if (d.intervalDays < 14) act({ it.adminSettings(intervalDays = d.intervalDays + 1) }) })
            }
            Text("Zmiana działa od następnego sprzątnięcia.", style = MaterialTheme.typography.bodySmall)
        }

        BrutalCard {
            SectionTitle("Częstotliwość przypomnień")
            d.users.forEach { u ->
                Text(u.name.uppercase(), fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    INTERVALS.forEach { m ->
                        Chip("$m min", u.notifyIntervalMin == m, { act({ it.adminSettings(notify = mapOf(u.id to m)) }) })
                    }
                }
            }
        }

        BrutalCard {
            SectionTitle("Maksymalna wulgarność")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LEVELS.forEach { (lvl, name) ->
                    Chip("$lvl · $name", d.maxLevel == lvl, { act({ it.adminSettings(maxLevel = lvl) }) })
                }
            }
            Text("Im dłużej kuweta czeka, tym ostrzejsze teksty, aż do tego poziomu.", style = MaterialTheme.typography.bodySmall)
        }

        BrutalCard {
            SectionTitle("Dyżur teraz")
            Text("Ręczna korekta: kto ma dyżur i na kiedy.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                d.users.forEach { u ->
                    Chip(u.name, d.assigneeId == u.id, {
                        act({ it.adminState(u.id, d.dueDate ?: java.time.LocalDate.now().toString()) })
                    })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Termin:", modifier = Modifier.weight(1f))
                Chip(d.dueDate ?: "wybierz", false, {
                    pickDate(context, d.dueDate) { picked ->
                        val who = d.assigneeId ?: d.users.first().id
                        act({ it.adminState(who, picked) }, "Termin ustawiony")
                    }
                })
            }
        }

        BrutalCard {
            SectionTitle("Parowanie")
            Text("Kod jest ważny 24 godziny. Druga osoba wpisuje go w aplikacji razem z adresem serwera.", style = MaterialTheme.typography.bodySmall)
            if (d.users.size < 2) {
                BrutalButton("Kod dla drugiej osoby", {
                    scope.launch {
                        try { pair = Api(Prefs(context)).pairCode() } catch (e: ApiException) { message = e.message }
                    }
                }, small = true)
            }
            d.users.filter { !it.isAdmin }.forEach { u ->
                BrutalButton("Nowy telefon: ${u.name}", {
                    scope.launch {
                        try { pair = Api(Prefs(context)).pairCode(u.id) } catch (e: ApiException) { message = e.message }
                    }
                }, bg = Paper, fg = Ink, small = true)
            }
            pair?.let { p ->
                SelectionContainer {
                    Column {
                        Text(p.code, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, letterSpacing = 6.sp))
                        Text("Serwer: ${Prefs(context).serverUrl}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        MessagesCard(d, onAdd = { cat, lvl, text -> act({ it.addMessage(cat, lvl, text) }, "Dodane") }, onDelete = { id ->
            act({ it.deleteMessage(id) })
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessagesCard(d: AdminOverview, onAdd: (String, Int, String) -> Unit, onDelete: (Int) -> Unit) {
    var category by remember { mutableStateOf("reminder") }
    var level by remember { mutableIntStateOf(2) }
    var text by remember { mutableStateOf("") }

    BrutalCard {
        SectionTitle("Teksty powiadomień")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CATEGORIES.forEach { (key, label) -> Chip(label, category == key, { category = key }) }
        }
        d.messages.filter { it.category == category }.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (category == "reminder") Text("${m.level}", fontWeight = FontWeight.Bold)
                Text(m.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Chip("✕", false, { onDelete(m.id) })
            }
        }
        if (category == "reminder") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LEVELS.keys.forEach { l -> Chip("poz. $l", level == l, { level = l }) }
            }
        }
        BrutalField(text, { text = it }, "Nowy tekst", singleLine = false)
        Text("Zmienne: {name} – kto ma dyżur, {partner} – druga osoba, {days} – dni spóźnienia", style = MaterialTheme.typography.bodySmall)
        BrutalButton("Dodaj tekst", {
            onAdd(category, if (category == "reminder") level else 0, text)
            text = ""
        }, enabled = text.trim().length >= 3, small = true)
    }
}
