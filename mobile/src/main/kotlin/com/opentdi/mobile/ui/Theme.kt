package com.opentdi.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
fun OpenTdiTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = Colors, typography = Type, content = content)

val TABS = listOf("Tonight", "Watch", "Sessions", "Settings")

@Composable
fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        PrimaryTabRow(
            selectedTabIndex = selected,
            containerColor = Color.Transparent,
            divider = {},
            modifier = Modifier.navigationBarsPadding(),
        ) {
            TABS.forEachIndexed { i, name ->
                Tab(
                    selected = i == selected,
                    onClick = { onSelect(i) },
                    text = { Text(name, style = MaterialTheme.typography.titleSmall) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
