package com.opentdi.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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

/**
 * False in ambient (the dimmed always-on screen) or with the screen off: then nothing may animate. The app targets SDK
 * 36, so Wear OS 6 keeps it visible all night, and its frames would otherwise keep running.
 */
@Composable
fun rememberInteractive(): Boolean {
    val context = LocalContext.current
    val power = remember { context.getSystemService(PowerManager::class.java) }
    var on by remember { mutableStateOf(power.isInteractive) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                on = power.isInteractive
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        on = power.isInteractive // the screen may have changed before the receiver was registered
        onDispose { context.unregisterReceiver(receiver) }
    }
    return on
}

/**
 * A faint ring at the edge of the round screen that slowly breathes in and out, one breath per [breathMs]. Compose it
 * only while the screen is on: it animates for as long as it is shown.
 */
@Composable
fun BreathingRing(color: Color, modifier: Modifier = Modifier, breathMs: Int = 10_000) {
    val breath = rememberInfiniteTransition(label = "breath").animateFloat(
        0f, 1f, infiniteRepeatable(tween(breathMs / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b",
    )
    Canvas(modifier.fillMaxSize()) {
        val b = breath.value // read here, so each frame only redraws
        drawCircle(
            color,
            radius = size.minDimension / 2 - 3.dp.toPx() - 4.dp.toPx() * b,
            alpha = .12f + .23f * b,
            style = Stroke(3.dp.toPx()),
        )
    }
}

/** A dim amber dawn rising from below the screen over 30 s, once: nothing moves after that. */
@Composable
fun Sunrise(modifier: Modifier = Modifier) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, tween(30_000, easing = LinearOutSlowInEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val v = rise.value
        drawRect(
            Brush.radialGradient(
                listOf(DAWN.copy(alpha = .5f * v), DUSK_ROSE.copy(alpha = .35f * v), Color.Transparent),
                center = Offset(center.x, size.height * (1.3f - .4f * v)),
                radius = size.minDimension * (.6f + .3f * v),
            ),
        )
    }
}

/** A still indigo glow fading to black, behind the early-wake (WBTB) alarm. */
@Composable
fun NightGlow(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(Brush.radialGradient(listOf(NIGHT_GLOW, Color.Black), center, radius = size.minDimension * .6f))
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
private val DAWN = Color(0xFFB0662C)
private val DUSK_ROSE = Color(0xFF4E2A38)
private val NIGHT_GLOW = Color(0xFF1A1838)
