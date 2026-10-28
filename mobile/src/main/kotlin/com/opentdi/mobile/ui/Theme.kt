package com.opentdi.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Night-first: near-black, a muted indigo-lavender accent, no bright whites; readable at the lowest brightness.
private val Colors = darkColorScheme(
    primary = Color(0xFF9D9AD6),
    onPrimary = Color(0xFF15142B),
    primaryContainer = Color(0xFF29274D),
    onPrimaryContainer = Color(0xFFC6C3EC),
    secondary = Color(0xFF9D9AD6),
    onSecondary = Color(0xFF15142B),
    secondaryContainer = Color(0xFF29274D),
    onSecondaryContainer = Color(0xFFC6C3EC),
    tertiary = Color(0xFFD4A35E),
    background = Color(0xFF060609),
    onBackground = Color(0xFFC9C7D6),
    surface = Color(0xFF060609),
    onSurface = Color(0xFFC9C7D6),
    surfaceVariant = Color(0xFF17161F),
    onSurfaceVariant = Color(0xFF9896AA),
    surfaceContainerLowest = Color(0xFF060609),
    surfaceContainerLow = Color(0xFF0D0C12),
    surfaceContainer = Color(0xFF111017),
    surfaceContainerHigh = Color(0xFF17161F),
    surfaceContainerHighest = Color(0xFF1D1C27),
    outline = Color(0xFF4B4960),
    outlineVariant = Color(0xFF2B2A39),
    error = Color(0xFFE08A8A),
    onError = Color(0xFF2B1010),
)

private val Type = Typography().let { base ->
    base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 16.sp, lineHeight = 23.sp),
        bodySmall = base.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontSize = 17.sp),
        titleLarge = base.titleLarge.copy(fontSize = 22.sp),
    )
}

/** Night-first theme; the rest of the UI uses its tokens, never raw colors. */
@Composable
fun OpenTdiTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, typography = Type) {
    // Text outside a Surface, or on the see-through cards (contentColorFor matches no token), otherwise falls back to black.
    CompositionLocalProvider(LocalContentColor provides Colors.onSurface, content = content)
}

val TABS = listOf("Tonight", "Watch", "Stats", "Sessions", "Settings")

@Composable
fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f)) {
        PrimaryTabRow(
            selectedTabIndex = selected,
            containerColor = Color.Transparent,
            divider = {},
            modifier = Modifier.navigationBarsPadding(),
        ) {
            TABS.forEachIndexed { i, name ->
                // The content form has no 16 dp side padding, so five one-line names fit a 360 dp wide phone.
                Tab(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    modifier = Modifier.height(48.dp),
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) { Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** A tab's scrolling page. */
@Composable
fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        content()
    }
}

/** A titled card: every group of the UI is one. */
@Composable
fun Section(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        // Slightly see-through, so the night sky shows faintly behind the cards.
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.86f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Every card grows and shrinks smoothly when a row appears or goes.
        Column(Modifier.animateContentSize(tween(250)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

/** The one-line explanation under a control. */
@Composable
fun Help(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** True while Settings shows its sliders (owner, 2026-10-28: sliders live behind "Show advanced settings"). */
val LocalAdvanced = staticCompositionLocalOf { false } // Settings provides it; the set-up guide shows no sliders

/** A slider setting; hidden unless [LocalAdvanced]. */
@Composable
fun IntSetting(
    label: String,
    help: String,
    value: Int,
    range: IntRange,
    step: Int,
    unit: String,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    AnimatedVisibility(
        LocalAdvanced.current,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        label = "advanced",
    ) {
        Column {
            Text("$label: $value $unit".trimEnd())
            Help(help)
            Slider(
                value = value.coerceIn(range).toFloat(),
                onValueChange = {
                    val snapped = (range.first + ((it - range.first) / step).roundToInt() * step).coerceIn(range)
                    if (snapped != value) onChange(snapped)
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = (range.last - range.first) / step - 1,
                enabled = enabled,
            )
        }
    }
}

// A state change (not a tap): fade in over 500 ms, out over 300 ms.
internal val Appear = fadeIn(tween(500)) + expandVertically()
internal val Disappear = fadeOut(tween(300)) + shrinkVertically()

/** A text button that shows and hides [content] (closed at first), with a chevron that turns as it opens. */
@Composable
fun ColumnScope.Fold(label: String, forceOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val shown = open || forceOpen
    TextButton(
        onClick = { open = !open },
        modifier = Modifier.semantics { stateDescription = if (shown) "Shown" else "Hidden" },
    ) {
        Text(label)
        Spacer(Modifier.width(6.dp))
        Chevron(shown)
    }
    AnimatedVisibility(shown, label = "fold") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

/** A small down-pointing chevron that turns up when [open]; drawn, as the app has no icon library. */
@Composable
fun Chevron(open: Boolean, modifier: Modifier = Modifier) {
    val turn by animateFloatAsState(if (open) 180f else 0f, tween(250), label = "chevron")
    val color = LocalContentColor.current
    Canvas(modifier.size(14.dp).graphicsLayer { rotationZ = turn }) {
        val v = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.35f)
            lineTo(size.width * 0.5f, size.height * 0.7f)
            lineTo(size.width * 0.85f, size.height * 0.35f)
        }
        drawPath(v, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}
