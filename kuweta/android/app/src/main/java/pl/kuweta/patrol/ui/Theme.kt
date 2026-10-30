package pl.kuweta.patrol.ui

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
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.kuweta.patrol.R

val Yellow = Color(0xFFFFE14D)
val Ink = Color(0xFF111111)
val Paper = Color(0xFFFFFFFF)
val Orange = Color(0xFFFF5A36)
val Grey = Color(0xFFCFCFCF)
val Mint = Color(0xFF3EF08A)

val Archivo = FontFamily(Font(R.font.archivo_black))
val Mono = FontFamily(
    Font(R.font.space_mono, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

private val typography = Typography(
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 15.sp, color = Ink),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 13.sp, color = Ink),
    bodySmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, color = Ink),
    labelLarge = TextStyle(fontFamily = Archivo, fontSize = 15.sp),
    titleLarge = TextStyle(fontFamily = Archivo, fontSize = 22.sp, color = Ink),
    titleMedium = TextStyle(fontFamily = Archivo, fontSize = 17.sp, color = Ink),
    headlineLarge = TextStyle(fontFamily = Archivo, fontSize = 36.sp, lineHeight = 36.sp, color = Ink),
)

@Composable
fun KuwetaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Yellow,
            secondary = Orange, onSecondary = Paper,
            background = Yellow, onBackground = Ink,
            surface = Paper, onSurface = Ink,
        ),
        typography = typography,
        content = content,
    )
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
                .background(Ink)
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
                .background(if (enabled) Ink else Color.Transparent)
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
                style = MaterialTheme.typography.labelLarge.copy(fontSize = if (small) 12.sp else 15.sp),
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
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
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

val SHIFT_COLORS = mapOf("R" to Mint, "P" to Orange, "N" to Ink, "-" to Paper)
