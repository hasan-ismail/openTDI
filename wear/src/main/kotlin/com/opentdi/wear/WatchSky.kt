package com.opentdi.wear

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** A few dim stars twinkling slowly behind the watch's main screen; few and faint, for the pillow-side screen. */
@Composable
fun WatchStars(modifier: Modifier = Modifier) {
    val stars = remember {
        val r = Random(7)
        List(24) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.5f + r.nextFloat(), r.nextFloat()) } // x, y, size, phase
    }
    val t by rememberInfiniteTransition(label = "stars").animateFloat(
        0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "twinkle",
    )
    Canvas(modifier.fillMaxSize()) {
        for ((x, y, radius, phase) in stars.map { it.toList() }) {
            val alpha = 0.18f + 0.14f * sin(2 * PI.toFloat() * (t + phase))
            drawCircle(STAR, radius = radius * density, center = Offset(x * size.width, y * size.height), alpha = alpha)
        }
    }
}

/** Tonight's moon at its real [phase] (0 new, 0.5 full), as on the phone. */
fun DrawScope.drawMoon(phase: Double) {
    val r = size.minDimension * 0.45f
    val c = center
    drawCircle(MOON_SHADOW, radius = r, center = c)
    val k = cos(2 * PI * phase).toFloat()
    val half = Path().apply { addArc(Rect(c, r), -90f, 180f); close() }
    val ellipse = Path().apply { addOval(Rect(c.x - r * abs(k), c.y - r, c.x + r * abs(k), c.y + r)) }
    val lit = Path().apply { op(half, ellipse, if (k > 0) PathOperation.Difference else PathOperation.Union) }
    scale(if (phase < 0.5) 1f else -1f, 1f, c) { drawPath(lit, MOON) }
}

private val STAR = Color(0xFFE6E4FF)
private val MOON = Color(0xFFD9D3BA)
private val MOON_SHADOW = Color(0xFF1B1A2A)
