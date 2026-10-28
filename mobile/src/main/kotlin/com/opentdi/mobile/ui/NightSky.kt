package com.opentdi.mobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.opentdi.shared.sky.moonPhase
import com.opentdi.shared.sky.moonPhaseName
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A star at a fixed place (fractions of the screen) with its own size, brightness and twinkle phase. */
private class Star(val x: Float, val y: Float, val radius: Float, val bright: Float, val phase: Float)

/**
 * The night sky behind every tab (owner, 2026-10-28): a deep indigo gradient, slowly twinkling stars and now and then
 * a shooting star. Stars are seeded, so they stay put between screens. Nothing animates while the screen is off.
 */
@Composable
fun NightSky(modifier: Modifier = Modifier) {
    val stars = remember {
        val r = Random(20261028)
        List(70) {
            // More stars toward the top, like a real sky over a horizon.
            val y = r.nextFloat() * r.nextFloat()
            Star(r.nextFloat(), y, 0.6f + r.nextFloat() * 1.4f, 0.25f + r.nextFloat() * 0.5f, r.nextFloat())
        }
    }
    val transition = rememberInfiniteTransition(label = "sky")
    val twinkle by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "twinkle",
    )
    val shooting by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(23_000, easing = LinearEasing), RepeatMode.Restart), label = "shooting",
    )
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(SKY_TOP, SKY_BOTTOM)))
        for (s in stars) {
            val alpha = s.bright * (0.6f + 0.4f * sin(2 * PI.toFloat() * (twinkle + s.phase)))
            drawCircle(STAR, radius = s.radius * density, center = Offset(s.x * size.width, s.y * size.height), alpha = alpha)
        }
        // A shooting star during the first 4 % of each 23 s cycle, across the upper sky.
        if (shooting < 0.04f) {
            val p = shooting / 0.04f
            val start = Offset(size.width * 0.15f, size.height * 0.08f)
            val head = start + Offset(size.width * 0.45f * p, size.height * 0.12f * p)
            val tail = head - Offset(size.width * 0.09f, size.height * 0.024f)
            drawLine(
                Brush.linearGradient(listOf(Color.Transparent, STAR), tail, head),
                tail, head, strokeWidth = 1.5f * density, alpha = 1f - p,
            )
        }
    }
}

/** Tonight's moon, drawn at its real phase with a slow breathing glow, and the phase's name beside it. */
@Composable
fun MoonHeader(nowMs: Long) {
    val phase = moonPhase(nowMs)
    val glow by rememberInfiniteTransition(label = "moon").animateFloat(
        0.10f, 0.22f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "glow",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(56.dp)) { drawMoon(phase, glow) }
        Spacer(Modifier.width(16.dp))
        Text(moonPhaseName(phase), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DrawScope.drawMoon(phase: Double, glow: Float) {
    val r = size.minDimension * 0.36f
    val c = center
    drawCircle(MOON, radius = r * 1.35f, center = c, alpha = glow)
    drawCircle(MOON_SHADOW, radius = r, center = c)
    // The lit part: a half disk plus or minus the terminator's half ellipse; waning moons are mirrored.
    val waxing = phase < 0.5
    val k = cos(2 * PI * phase).toFloat() // 1 new, 0 quarter, -1 full
    val half = Path().apply { addArc(Rect(c, r), -90f, 180f); close() }
    val ellipse = Path().apply { addOval(Rect(c.x - r * abs(k), c.y - r, c.x + r * abs(k), c.y + r)) }
    val lit = Path().apply { op(half, ellipse, if (k > 0) PathOperation.Difference else PathOperation.Union) }
    scale(if (waxing) 1f else -1f, 1f, c) { drawPath(lit, MOON) }
}

private val SKY_TOP = Color(0xFF0E0C24)
private val SKY_BOTTOM = Color(0xFF040408)
private val STAR = Color(0xFFE6E4FF)
private val MOON = Color(0xFFE8E2C8)
private val MOON_SHADOW = Color(0xFF1B1A2A)
