package pl.kuweta.patrol.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import pl.kuweta.patrol.data.Api
import pl.kuweta.patrol.data.ApiException
import pl.kuweta.patrol.data.Prefs
import pl.kuweta.patrol.data.Repo
import pl.kuweta.patrol.push.EventService
import pl.kuweta.patrol.push.Watchdog

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transparent = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(transparent, transparent),
            navigationBarStyle = SystemBarStyle.dark(transparent),
        )
        setContent {
            KuwetaTheme {
                Root()
            }
        }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    var loggedIn by remember { mutableStateOf(Prefs(context).token != null) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Yellow)
            .statusBarsPadding()
            .imePadding()
    ) {
        if (loggedIn) {
            MainScaffold(onLogout = { loggedIn = false })
        } else {
            SetupScreen(onDone = { loggedIn = true })
        }
    }
}

private enum class Tab(val label: String) { START("Start"), SCHEDULE("Grafik"), RANKING("Ranking"), ADMIN("Admin") }

@Composable
private fun MainScaffold(onLogout: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Repo.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.START) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        EventService.start(context)
        Watchdog.schedule(context)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { Repo.refresh(context) }
    }

    val tabs = Tab.entries.filter { it != Tab.ADMIN || state?.me?.isAdmin == true }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.START -> HomeScreen(onLogout = onLogout)
                Tab.SCHEDULE -> ScheduleScreen()
                Tab.RANKING -> RankingScreen()
                Tab.ADMIN -> AdminScreen()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ink)
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEach { t ->
                val selected = t == tab
                Text(
                    t.label.uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = dsp(12)),
                    color = if (selected) Ink else Yellow,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(if (selected) Yellow else Ink)
                        .border(2.dp, Yellow)
                        .clickable { tab = t }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    var url by rememberSaveable { mutableStateOf(prefs.serverUrl) }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Sticker("Kuweta Patrol")
        Text("Parowanie\ntelefonu".uppercase(), style = MaterialTheme.typography.headlineLarge)
        BrutalCard {
            Text("Admin wpisuje swój kod admina z pliku .env na serwerze. Druga osoba wpisuje 6-znakowy kod, który admin wygeneruje w zakładce Admin.")
        }
        BrutalField(url, { url = it }, "Adres serwera (np. kuweta.twojadomena.pl)")
        BrutalField(name, { name = it }, "Twoje imię")
        BrutalField(code, { code = it }, "Kod parowania")
        error?.let { Text(it, color = Orange, style = MaterialTheme.typography.bodyLarge) }
        BrutalButton(
            if (busy) "Łączę…" else "Połącz",
            enabled = !busy && url.isNotBlank() && name.isNotBlank() && code.isNotBlank(),
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        prefs.serverUrl = Api.normalizeUrl(url)
                        prefs.token = null
                        val out = Api(prefs).register(name.trim(), code.trim())
                        prefs.token = out.token
                        prefs.lastEventId = 0
                        Repo.clear()
                        Repo.refresh(context)
                        EventService.start(context)
                        Watchdog.schedule(context)
                        onDone()
                    } catch (e: ApiException) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            },
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Serwer: kontener Docker na homelabie, wystawiony przez cloudflared.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
