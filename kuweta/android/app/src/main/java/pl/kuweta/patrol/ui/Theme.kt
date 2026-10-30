package pl.kuweta.patrol.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.kuweta.patrol.R

/**
 * Motywy aplikacji. Nazwy tokenów (Yellow, Ink...) pochodzą z pierwszego motywu "Brutal",
 * w innych motywach znaczą to samo co tam: Yellow = tło ekranu, Ink = linie i tekst,
 * Paper = karty, Orange = alarm, Grey = sen/wyciszone, Mint = OK, Shadow = twardy cień.
 */
enum class AppTheme(
    val label: String,
    val bg: Long, val line: Long, val card: Long, val alert: Long, val muted: Long, val ok: Long, val shadow: Long,
    val dark: Boolean,
    val display: Int, val body: Int, val bodyBold: Int,
    val displayScale: Float, val bodyScale: Float,
) {
    BRUTAL(
        "Brutal", 0xFFFFE14D, 0xFF111111, 0xFFFFFFFF, 0xFFFF5A36, 0xFFCFCFCF, 0xFF3EF08A, 0xFF111111,
        false, R.font.archivo_black, R.font.space_mono, R.font.space_mono_bold, 1f, 1f,
    ),
    ARCADE(
        "Arcade", 0xFF14102B, 0xFFE8E2FF, 0xFF221A4A, 0xFFFF3FA4, 0xFF3A2F66, 0xFF3EF08A, 0xFF000000,
        true, R.font.press_start, R.font.vt323, R.font.vt323, 0.6f, 1.4f,
    ),
    PASTEL(
        "Pastel", 0xFFF3E8FF, 0xFF3B2F5C, 0xFFFFFFFF, 0xFFFF8FB8, 0xFFDDD3F0, 0xFF7DE0B5, 0xFFC7B6F2,
        false, R.font.baloo_extrabold, R.font.baloo_semibold, R.font.baloo_extrabold, 1f, 1.05f,
    ),
    NOCKA(
        "Nocka", 0xFF121212, 0xFFFFE14D, 0xFF1E1E1E, 0xFFFF5A36, 0xFF3A3A3A, 0xFF3EF08A, 0xFFFFE14D,
        true, R.font.archivo_black, R.font.space_mono, R.font.space_mono_bold, 1f, 1f,
    ),
    PIEKLO(
        "Piekło", 0xFF0B0000, 0xFFFF3B3B, 0xFF1A0606, 0xFFFF8A00, 0xFF3D0C0C, 0xFF9BE22D, 0xFF8B0000,
        true, R.font.grenze_gotisch, R.font.special_elite, R.font.special_elite, 1.1f, 1f,
    ),
}

object ThemeState {
    var theme by mutableStateOf(AppTheme.BRUTAL)
        private set

    private fun prefs(context: Context) = context.getSharedPreferences("kuweta_ui", Context.MODE_PRIVATE)

    fun load(context: Context) {
        theme = runCatching { AppTheme.valueOf(prefs(context).getString("theme", null) ?: "BRUTAL") }
            .getOrDefault(AppTheme.BRUTAL)
    }

    fun set(context: Context, t: AppTheme) {
        theme = t
        prefs(context).edit().putString("theme", t.name).apply()
    }
}

val Yellow: Color get() = Color(ThemeState.theme.bg)
val Ink: Color get() = Color(ThemeState.theme.line)
val Paper: Color get() = Color(ThemeState.theme.card)
val Orange: Color get() = Color(ThemeState.theme.alert)
val Grey: Color get() = Color(ThemeState.theme.muted)
val Mint: Color get() = Color(ThemeState.theme.ok)
val Shadow: Color get() = Color(ThemeState.theme.shadow)

private val fontCache = mutableMapOf<AppTheme, Pair<FontFamily, FontFamily>>()

private fun fonts(t: AppTheme) = fontCache.getOrPut(t) {
    FontFamily(Font(t.display)) to FontFamily(Font(t.body, FontWeight.Normal), Font(t.bodyBold, FontWeight.Bold))
}

val Archivo: FontFamily get() = fonts(ThemeState.theme).first
val Mono: FontFamily get() = fonts(ThemeState.theme).second

/** Rozmiar dla czcionki tytułowej, przeskalowany do motywu (pikselowa jest dużo szersza). */
fun dsp(v: Int): TextUnit = (v * ThemeState.theme.displayScale).sp

/** Rozmiar dla czcionki tekstu, przeskalowany do motywu. */
fun bsp(v: Int): TextUnit = (v * ThemeState.theme.bodyScale).sp

@Composable
fun KuwetaTheme(content: @Composable () -> Unit) {
    val t = ThemeState.theme
    val typography = Typography(
        bodyLarge = TextStyle(fontFamily = Mono, fontSize = bsp(15), color = Ink),
        bodyMedium = TextStyle(fontFamily = Mono, fontSize = bsp(13), color = Ink),
        bodySmall = TextStyle(fontFamily = Mono, fontSize = bsp(11), color = Ink),
        labelLarge = TextStyle(fontFamily = Archivo, fontSize = dsp(15)),
        titleLarge = TextStyle(fontFamily = Archivo, fontSize = dsp(22), color = Ink),
        titleMedium = TextStyle(fontFamily = Archivo, fontSize = dsp(17), color = Ink),
        headlineLarge = TextStyle(fontFamily = Archivo, fontSize = dsp(36), lineHeight = dsp(if (t == AppTheme.ARCADE) 46 else 36), color = Ink),
    )
    val colors = if (t.dark) darkColorScheme(
        primary = Ink, onPrimary = Yellow, secondary = Orange, onSecondary = Paper,
        background = Yellow, onBackground = Ink, surface = Paper, onSurface = Ink,
    ) else lightColorScheme(
        primary = Ink, onPrimary = Yellow, secondary = Orange, onSecondary = Paper,
        background = Yellow, onBackground = Ink, surface = Paper, onSurface = Ink,
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

/** Biała karta z grubą ramką i twardym cieniem. */
@Composable
fun BrutalCard(
    modifier: Modifier = Modifier,
    bg: Color = Paper,
    shadow: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.padding(end = shadow, bottom = shadow)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(shadow, shadow)
                .background(Shadow)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(bg)
                .border(2.5.dp, Ink)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

@Composable
fun BrutalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bg: Color = Ink,
    fg: Color = Yellow,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shadow = if (pressed || !enabled) 0.dp else if (small) 3.dp else 4.dp
    val push = if (small) 3.dp else 4.dp
    Box(modifier.padding(end = push, bottom = push)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(push, push)
                .background(if (enabled) Shadow else Color.Transparent)
        )
        Box(
            Modifier
                .offset(push - shadow, push - shadow)
                .fillMaxWidth()
                .background(if (enabled) bg else Grey)
                .border(2.5.dp, Ink)
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
                .padding(vertical = if (small) 8.dp else 13.dp, horizontal = 10.dp)
        ) {
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = if (small) dsp(12) else dsp(15)),
                color = if (enabled) fg else Ink.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun Sticker(text: String, bg: Color = Orange, fg: Color = Paper, angle: Float = -3f) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = dsp(12)),
        color = fg,
        modifier = Modifier
            .rotate(angle)
            .background(bg)
            .border(2.5.dp, Ink)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun BrutalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    number: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        shape = RectangleShape,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink,
            unfocusedBorderColor = Ink,
            focusedContainerColor = Paper,
            unfocusedContainerColor = Paper,
            focusedLabelColor = Ink,
            cursorColor = Ink,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.titleMedium)
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        color = if (selected) Yellow else Ink,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(if (selected) Ink else Paper)
            .border(2.dp, Ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

fun daysWord(n: Int): String = if (n == 1) "dzień" else "dni"

val SHIFT_NAMES = mapOf("R" to "rano", "P" to "popołudnie", "N" to "nocka", "-" to "wolne")

val SHIFT_COLORS: Map<String, Color> get() = mapOf("R" to Mint, "P" to Orange, "N" to Ink, "-" to Paper)
