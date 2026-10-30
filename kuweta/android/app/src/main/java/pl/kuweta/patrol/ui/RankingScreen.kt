package pl.kuweta.patrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.ApiException
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.Ranking
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun RankingScreen() {
    val context = LocalContext.current
    var month by remember { mutableStateOf(YearMonth.now()) }
    var data by remember { mutableStateOf<Ranking?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(month) {
        try {
            data = Api(Prefs(context)).ranking(month.toString())
            error = null
        } catch (e: ApiException) {
            error = e.message
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Sticker("Ranking miesiąca")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("◀", false, { month = month.minusMonths(1) })
            Text(
                month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("pl")).uppercase() + " " + month.year,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Chip("▶", false, { month = month.plusMonths(1) })
        }
        error?.let { Text(it, color = Orange) }
        val r = data ?: return@Column

        r.players.forEachIndexed { i, p ->
            BrutalCard(bg = if (i == 0 && p.cleanings > 0) Yellow else Paper) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("#${i + 1}", style = MaterialTheme.typography.headlineLarge)
                    Column(Modifier.weight(1f)) {
                        Text(p.name.uppercase(), style = MaterialTheme.typography.titleLarge)
                        if (i == 0 && p.cleanings > 0) Text("Mistrz łopatki", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("🔥 ${p.currentStreak}", style = MaterialTheme.typography.titleMedium)
                }
                Stat("Ogarnięte", p.cleanings)
                Stat("Na czas, bez przedłużeń", p.onTime)
                Stat("Przedłużenia", p.extensions)
                Stat("Ogarnięte za drugą osobę", p.covers)
                Stat("Rekordowy streak", p.bestStreak)
            }
        }

        if (r.history.isNotEmpty()) {
            BrutalCard {
                SectionTitle("Historia")
                val fmt = DateTimeFormatter.ofPattern("d.MM HH:mm")
                r.history.forEach { h ->
                    val time = runCatching { OffsetDateTime.parse(h.at).format(fmt) }.getOrDefault(h.at)
                    val tags = buildList {
                        if (!h.onTime) add("spóźnione")
                        if (h.extensions > 0) add("+${h.extensions} dz.")
                        if (h.cover) add("za kogoś")
                    }.joinToString(" · ")
                    Row(Modifier.fillMaxWidth()) {
                        Text(time, modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.bodySmall)
                        Text(h.name, modifier = Modifier.weight(0.3f), fontWeight = FontWeight.Bold)
                        Text(tags.ifEmpty { "✔" }, modifier = Modifier.weight(0.35f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            Text("W tym miesiącu nikt jeszcze nie ruszył łopatki.")
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        Text("$value", fontWeight = FontWeight.Bold)
    }
}
