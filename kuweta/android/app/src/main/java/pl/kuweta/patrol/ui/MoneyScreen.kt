package pl.kuweta.patrol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.ApiException
import pl.kuweta.patrol.data.AppState
import pl.kuweta.patrol.data.Expense
import pl.kuweta.patrol.data.Money
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.Repo
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val NOTE_COLORS = listOf(Color(0xFFF08A8A), Color(0xFF7FB2F0), Color(0xFFFFA552))
private val QUICK_AMOUNTS = listOf(5, 10, 15, 20, 50)

/** Kwota w eurocentach jako "300 €" albo "12,50 €". */
fun eur(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val abs = kotlin.math.abs(cents)
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
    val rest = abs % 100
    return if (rest == 0L) "$sign$whole €" else "$sign$whole,${rest.toString().padStart(2, '0')} €"
}

/** "12,5" / "12.50" / "-3" -> eurocenty, null gdy się nie da. */
fun parseCents(text: String): Long? = runCatching {
    BigDecimal(text.trim().replace(" ", "").replace(",", ".")).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
}.getOrNull()

private fun monthName(period: String): String = runCatching {
    val ym = YearMonth.parse(period)
    ym.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale("pl")) + " " + ym.year
}.getOrDefault(period)

private fun shortDate(iso: String): String = runCatching {
    val d = if (iso.length > 10) OffsetDateTime.parse(iso).toLocalDate() else LocalDate.parse(iso)
    "%d.%02d".format(d.dayOfMonth, d.monthValue)
}.getOrDefault(iso)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoneyScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var amountDialog by remember { mutableStateOf(false) }
    var balanceDialog by remember { mutableStateOf(false) }
    var expenseDialog by remember { mutableStateOf(false) }
    var deleteExpense by remember { mutableStateOf<Expense?>(null) }

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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Banknotes()
        val s = state
        val m = s?.money
        if (s == null || m == null || !m.configured) {
            BrutalCard {
                Text("HAJS JESZCZE NIE RUSZYŁ", style = MaterialTheme.typography.titleMedium)
                Text("Admin musi ustawić, kto przelewa składkę, a kto trzyma hajs.")
            }
            if (s != null && s.me.isAdmin) RolesCard(s, m, busy) { id -> act({ it.moneyRoles(id) }, "Zapisane") }
            message?.let { Text(it, fontWeight = FontWeight.Bold) }
            return@Column
        }
        val payer = m.role == "payer"

        ContributionCard(m, onEdit = { amountDialog = true })

        m.proposals.forEach { p ->
            val what = if (p.kind == "amount") "składka ${eur(p.valueCents)}" else "skarbonka ${eur(p.valueCents)}"
            BrutalCard(bg = if (p.mine) Paper else Orange) {
                if (p.mine) {
                    Text("CZEKA NA ZGODĘ", style = MaterialTheme.typography.titleMedium)
                    Text("Proponujesz: $what.")
                } else {
                    Text("${p.proposerName.uppercase()} PROPONUJE", style = MaterialTheme.typography.titleMedium, color = Paper)
                    Text(what.replaceFirstChar { it.uppercase() }, color = Paper, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BrutalButton("Zgoda", { act({ it.moneyDecide(p.id, true) }, "Zgoda wysłana") }, Modifier.weight(1f), bg = Paper, fg = Ink, enabled = !busy, small = true)
                        BrutalButton("Odmowa", { act({ it.moneyDecide(p.id, false) }, "Odmowa wysłana") }, Modifier.weight(1f), enabled = !busy, small = true)
                    }
                }
            }
        }

        PeopleCard(m)
        val transferId = m.transferId
        when {
            payer && m.periodStatus in listOf("due", "not_due") -> BrutalButton(
                "Przelane 💶 ${eur(m.transferCents)}",
                { act({ it.moneyTransfer() }, "Zgłoszone. ${m.receiver?.name} sprawdzi konto.") },
                bg = Mint, fg = Ink, enabled = !busy,
            )
            !payer && m.periodStatus == "sent" && transferId != null -> BrutalButton(
                "HAJS SIĘ ZGADZA ✔",
                { act({ it.moneyConfirm(transferId) }, "Potwierdzone. Hajs w skarbonce.") },
                bg = Mint, fg = Ink, enabled = !busy,
            )
        }

        PiggyCard(m, canFix = !payer, onFix = { balanceDialog = true })
        if (!payer) {
            BrutalButton("Przepierdolone 💸", { expenseDialog = true }, bg = Orange, fg = Paper, enabled = !busy)
        }
        message?.let { Text(it, fontWeight = FontWeight.Bold) }
        ExpensesCard(m, canDelete = !payer, onDelete = { deleteExpense = it })
        if (s.me.isAdmin) RolesCard(s, m, busy) { id -> act({ it.moneyRoles(id) }, "Zapisane") }
        Spacer(Modifier.height(8.dp))
    }

    val m = state?.money ?: return
    if (amountDialog) {
        AmountDialog(
            title = "Nowa składka",
            hint = "Druga osoba musi potwierdzić. Jeśli ten miesiąc jest już przelany, nowa kwota wejdzie od następnego.",
            initial = m.amountCents,
            allowNegative = false,
            onDismiss = { amountDialog = false },
        ) { cents ->
            amountDialog = false
            act({ it.moneyPropose("amount", cents) }, "Propozycja wysłana")
        }
    }
    if (balanceDialog) {
        AmountDialog(
            title = "Ile naprawdę jest w skarbonce?",
            hint = "${m.payer?.name ?: "Przelewający"} musi to potwierdzić.",
            initial = m.balanceCents,
            allowNegative = true,
            onDismiss = { balanceDialog = false },
        ) { cents ->
            balanceDialog = false
            act({ it.moneyPropose("balance", cents) }, "Korekta wysłana do potwierdzenia")
        }
    }
    if (expenseDialog) {
        ExpenseDialog(
            categories = m.categories,
            onSaveCategories = { names -> act({ it.moneyCategories(names) }) },
            onDismiss = { expenseDialog = false },
        ) { cents, category, note ->
            expenseDialog = false
            act({ it.moneyExpense(cents, category, note) }, "Zapisane: ${eur(cents)} przepierdolone")
        }
    }
    deleteExpense?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteExpense = null },
            title = { Text("Skasować wydatek?", style = MaterialTheme.typography.titleMedium) },
            text = { Text("${eur(e.amountCents)} · ${e.category.ifBlank { "zakupy" }}. ${m.payer?.name} dostanie powiadomienie.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteExpense = null
                    act({ it.moneyDeleteExpense(e.id) }, "Skasowane")
                }) { Text("SKASUJ", color = Ink, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteExpense = null }) { Text("Zostaw", color = Ink) } },
            containerColor = Paper,
        )
    }
}

/** Wachlarz banknotów i kilka monet, żeby czuć bogactwo w powietrzu. */
@Composable
private fun Banknotes() {
    Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        listOf(-14f to 100, 9f to 50, -3f to 20).forEachIndexed { i, (angle, value) ->
            Note(value, NOTE_COLORS[i], Modifier.rotate(angle).offset(x = ((i - 1) * 14).dp, y = (i * 4 - 4).dp))
        }
        listOf(Triple(-120, -50, 26), Triple(118, -44, 22), Triple(-104, 52, 20), Triple(126, 48, 28)).forEach { (x, y, size) ->
            Coin(Modifier.offset(x = x.dp, y = y.dp), size)
        }
    }
}

@Composable
private fun Note(value: Int, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .size(width = 210.dp, height = 104.dp)
            .background(color)
            .border(3.dp, Ink)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Paper.copy(alpha = 0.75f))
                .border(2.dp, Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("€", style = MaterialTheme.typography.headlineLarge.copy(fontSize = dsp(36)), color = Ink)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("CAT&CASH", style = MaterialTheme.typography.labelLarge.copy(fontSize = dsp(11)), color = Ink)
            Text("$value", style = MaterialTheme.typography.headlineLarge.copy(fontSize = dsp(40)), color = Ink)
            Text("EURO · MIAU", style = MaterialTheme.typography.bodySmall.copy(fontSize = bsp(9)), color = Ink)
        }
    }
}

@Composable
private fun Coin(modifier: Modifier, size: Int) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFC83D))
            .border(2.dp, Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("€", fontWeight = FontWeight.Bold, color = Ink, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ContributionCard(m: Money, onEdit: () -> Unit) {
    BrutalCard(bg = Ink) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SKŁADKA NA MIESIĄC", style = MaterialTheme.typography.titleMedium.copy(fontSize = dsp(14)), color = Yellow, modifier = Modifier.weight(1f))
            Chip("Zmień ✎", false, onEdit)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("✦ ", color = Yellow, style = MaterialTheme.typography.titleLarge)
            Text(
                if (m.amountCents > 0) eur(m.amountCents) else "? €",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = dsp(46), lineHeight = dsp(48)),
                color = Mint,
            )
            Text(" / mies. ✦", color = Yellow, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
        }
        val due = shortDate(m.dueDate)
        val (line, color) = when (m.periodStatus) {
            "due" -> "Za ${monthName(m.period)}: do przelania od $due" to Orange
            "sent" -> "Za ${monthName(m.period)}: przelane, czeka na HAJS SIĘ ZGADZA" to Yellow
            "not_due" -> "Za ${monthName(m.period)}: przelew od $due" to Paper
            else -> "Za ${monthName(m.period)}: zrobione" to Mint
        }
        Text(line, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PeopleCard(m: Money) {
    BrutalCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            NameTag(m.payer?.name ?: "?", "przelewa", Modifier.weight(1f), highlight = m.role == "payer")
            Text("→ 💶 →", fontWeight = FontWeight.Bold)
            NameTag(m.receiver?.name ?: "?", "trzyma hajs", Modifier.weight(1f), highlight = m.role == "receiver")
        }
        val text = when (m.periodStatus) {
            "sent" -> if (m.role == "payer") "Czekasz, aż ${m.receiver?.name} sprawdzi konto." else "Sprawdź konto i potwierdź."
            "due" -> if (m.role == "payer") "Przelej i kliknij niżej." else "${m.payer?.name} jeszcze nie przelał(a)."
            else -> null
        }
        text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun NameTag(name: String, label: String, modifier: Modifier, highlight: Boolean) {
    Column(
        modifier
            .background(if (highlight) Yellow else Paper)
            .border(2.dp, Ink)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name.uppercase(), style = MaterialTheme.typography.titleMedium.copy(fontSize = dsp(15)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = bsp(10)))
    }
}

@Composable
private fun PiggyCard(m: Money, canFix: Boolean, onFix: () -> Unit) {
    val minus = m.balanceCents < 0
    BrutalCard(bg = if (minus) Orange else Paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (minus) "SKARBONKA NA MINUSIE" else "W SKARBONCE", style = MaterialTheme.typography.titleMedium.copy(fontSize = dsp(14)), modifier = Modifier.weight(1f))
            if (canFix) Chip("Popraw", false, onFix)
        }
        Text(eur(m.balanceCents), style = MaterialTheme.typography.headlineLarge.copy(fontSize = dsp(34), lineHeight = dsp(36)))
        Text("W tym miesiącu przepierdolone: ${eur(m.spentMonthCents)}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExpensesCard(m: Money, canDelete: Boolean, onDelete: (Expense) -> Unit) {
    if (m.expenses.isEmpty()) return
    BrutalCard {
        Text("OSTATNIO PRZEPIERDOLONE", style = MaterialTheme.typography.titleMedium.copy(fontSize = dsp(14)))
        m.expenses.forEach { e ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(shortDate(e.at), style = MaterialTheme.typography.bodySmall)
                Text(
                    listOf(e.category, e.note).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "zakupy" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(eur(e.amountCents), fontWeight = FontWeight.Bold)
                if (canDelete) Chip("✖", false, { onDelete(e) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RolesCard(s: AppState, m: Money?, busy: Boolean, onPick: (Int) -> Unit) {
    BrutalCard {
        Text("ADMIN: KTO PRZELEWA SKŁADKĘ?", style = MaterialTheme.typography.titleMedium.copy(fontSize = dsp(14)))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOfNotNull(s.me, s.partner).forEach { u ->
                Chip(u.name, m?.payer?.id == u.id, { if (!busy) onPick(u.id) })
            }
        }
        Text("Druga osoba automatycznie trzyma hajs i zgłasza wydatki.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AmountDialog(
    title: String,
    hint: String,
    initial: Long,
    allowNegative: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var text by remember { mutableStateOf(if (initial != 0L) eur(kotlin.math.abs(initial)).removeSuffix(" €").replace(" ", "") else "") }
    var negative by remember { mutableStateOf(initial < 0) }
    val cents = parseCents(text)?.let { if (negative) -it else it }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (allowNegative) Chip(if (negative) "−" else "+", negative, { negative = !negative })
                    BrutalField(text, { text = it }, "Kwota w €", Modifier.weight(1f), decimal = true)
                }
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && (allowNegative || cents > 0),
                onClick = { cents?.let(onSave) },
            ) { Text("WYŚLIJ", color = Ink, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = Ink) } },
        containerColor = Paper,
    )
}

/** Okno wydatku: kwota (pole albo kafelki), na co (kafelki z edytowalnymi nazwami), opcjonalny opis. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDialog(
    categories: List<String>,
    onSaveCategories: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Long, String, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var cats by remember { mutableStateOf(categories) }
    var editing by remember { mutableStateOf(false) }
    var renameIndex by remember { mutableStateOf<Int?>(null) }
    val cents = parseCents(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PRZEPIERDOLONE 💸", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BrutalField(amount, { amount = it }, "Ile € poszło?", decimal = true)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QUICK_AMOUNTS.forEach { v ->
                        Chip("$v €", cents == v * 100L, { amount = v.toString() })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (editing) "Kliknij kafelek, żeby zmienić nazwę" else "Na co?", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Chip(if (editing) "Gotowe" else "✎", editing, { editing = !editing })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEachIndexed { i, c ->
                        Chip(c, !editing && c == category, {
                            if (editing) renameIndex = i else category = if (category == c) "" else c
                        })
                    }
                    if (editing && cats.size < 8) Chip("+ dodaj", false, { renameIndex = cats.size })
                }
                BrutalField(note, { note = it }, "Opis (nieobowiązkowy)")
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = { cents?.let { onSave(it, category, note) } }) {
                Text("ZAPISZ 💸", color = Ink, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = Ink) } },
        containerColor = Paper,
    )

    renameIndex?.let { i ->
        var name by remember(i) { mutableStateOf(cats.getOrNull(i) ?: "") }
        AlertDialog(
            onDismissRequest = { renameIndex = null },
            title = { Text(if (i < cats.size) "Nazwa kafelka" else "Nowy kafelek", style = MaterialTheme.typography.titleMedium) },
            text = { BrutalField(name, { if (it.length <= 14) name = it }, "Nazwa") },
            confirmButton = {
                TextButton(onClick = {
                    val clean = name.trim()
                    val next = cats.toMutableList()
                    if (clean.isNotEmpty()) {
                        if (i < next.size) next[i] = clean else next.add(clean)
                        cats = next
                        onSaveCategories(next)
                    }
                    renameIndex = null
                }) { Text("ZAPISZ", color = Ink, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (i < cats.size) {
                        TextButton(onClick = {
                            val next = cats.toMutableList().also { it.removeAt(i) }
                            if (category == cats[i]) category = ""
                            cats = next
                            onSaveCategories(next)
                            renameIndex = null
                        }) { Text("Usuń", color = Orange) }
                    }
                    TextButton(onClick = { renameIndex = null }) { Text("Anuluj", color = Ink) }
                }
            },
            containerColor = Paper,
        )
    }
}
