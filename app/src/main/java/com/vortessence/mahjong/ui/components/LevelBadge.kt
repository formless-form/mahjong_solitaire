package com.vortessence.mahjong.ui.components

import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class BadgeState { CURRENT, CLEARED, OPEN, LOCKED }

/**
 * The leading badge of a level or layout row, using Material 3 Expressive's shape set: a sunny
 * burst for the level you're up to, a soft cookie with a tick once cleared, a plain circle
 * otherwise.
 */
@Composable
fun LevelBadge(state: BadgeState, label: String, icon: ImageVector? = null, ribbon: Boolean = false) {
    // A level cleared cleanly wears a small gold ribbon pinned to its badge's lower right
    Box {
        Badge(state, label, icon)
        if (ribbon && state != BadgeState.LOCKED) {
            GoldRibbon(Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 4.dp).size(width = 16.dp, height = 21.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Badge(state: BadgeState, label: String, icon: ImageVector?) {
    val colors = MaterialTheme.colorScheme
    val (shape, container) = when (state) {
        BadgeState.CURRENT -> MaterialShapes.Sunny.toShape() to colors.primary
        BadgeState.CLEARED -> MaterialShapes.Cookie9Sided.toShape() to colors.primaryContainer
        BadgeState.OPEN -> CircleShape to colors.surfaceContainerHighest
        BadgeState.LOCKED -> CircleShape to colors.surfaceContainerHigh
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .background(container, shape),
    ) {
        when (state) {
            BadgeState.CLEARED -> Icon(Icons.Rounded.Check, contentDescription = "Cleared", tint = colors.onPrimaryContainer)
            BadgeState.LOCKED -> Icon(
                Icons.Rounded.Lock,
                contentDescription = "Locked",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).alpha(0.6f),
            )
            else -> if (icon != null) {
                Icon(icon, contentDescription = null, tint = if (state == BadgeState.CURRENT) colors.onPrimary else colors.primary)
            } else Text(
                label,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = if (state == BadgeState.CURRENT) colors.onPrimary else colors.primary,
            )
        }
    }
}

/** Difficulty as a short row of small amber stars. */
@Composable
fun DifficultyStars(stars: Int, dimmed: Boolean = false) {
    Row(Modifier.alpha(if (dimmed) 0.4f else 1f)) {
        repeat(stars) {
            Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
        }
    }
}

// A gold rosette on two tails, drawn to its box (about 16 x 21 dp): a domed medal lit from the
// upper left with a stamped inner ring, on shaded silk tails
@Composable
internal fun GoldRibbon(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "Cleared cleanly" }) {
        val w = size.width
        val h = size.height
        val r = w * 0.40f
        val c = Offset(w / 2f, r + w * 0.06f)
        fun tail(side: Float, light: Color, dark: Color) {
            val inner = c.x + side * w * 0.04f
            val outer = c.x + side * w * 0.46f
            val path = Path().apply {
                moveTo(c.x + side * w * 0.24f, c.y + r * 0.3f)
                lineTo(outer, h)
                lineTo((inner + outer) / 2f, h - w * 0.2f)
                lineTo(inner, h * 0.97f)
                lineTo(c.x + side * w * 0.02f, c.y + r * 0.5f)
                close()
            }
            drawPath(path, Brush.verticalGradient(listOf(light, dark), startY = c.y, endY = h))
            // Faint ribs fanning down the silk from under the medal
            val top = Offset(c.x + side * w * 0.12f, c.y + r * 0.4f)
            clipPath(path) {
                for (k in 0..3) {
                    val x = inner + (outer - inner) * (k + 0.5f) / 4f
                    drawLine(RibLine.copy(alpha = 0.22f), top, Offset(x, h), strokeWidth = w * 0.022f)
                    drawLine(GoldHighlight.copy(alpha = 0.16f), top + Offset(w * 0.02f, 0f), Offset(x + w * 0.02f, h), strokeWidth = w * 0.014f)
                }
            }
        }
        tail(-1f, TailLight, TailDark)
        tail(1f, TailMid, TailDeep)
        // A soft shadow under the medal, onto the tails
        drawCircle(Color.Black.copy(alpha = 0.28f), r * 1.02f, c + Offset(0f, w * 0.05f))
        drawCircle(Brush.radialGradient(listOf(GoldHighlight, Gold, GoldDeep), center = c + Offset(-r * 0.35f, -r * 0.4f), radius = r * 1.6f), r, c)
        // Stamped inner ring: a dark groove with a light lip below it
        drawCircle(GoldGroove.copy(alpha = 0.75f), r * 0.6f, c, style = Stroke(w * 0.045f))
        drawCircle(GoldHighlight.copy(alpha = 0.55f), r * 0.6f, c + Offset(0f, w * 0.03f), style = Stroke(w * 0.025f))
        // Bright rim, and a glint on the upper left
        drawCircle(Brush.linearGradient(listOf(GoldHighlight, GoldDeep), start = c - Offset(r, r), end = c + Offset(r, r)), r, c, style = Stroke(w * 0.055f))
        drawArc(Color.White.copy(alpha = 0.7f), 200f, 45f, false, topLeft = c - Offset(r * 0.78f, r * 0.78f), size = Size(r * 1.56f, r * 1.56f), style = Stroke(w * 0.04f, cap = StrokeCap.Round))
    }
}

private val GoldHighlight = Color(0xFFFFEBB0)
private val Gold = Color(0xFFE2B04A)
private val GoldDeep = Color(0xFF9A6A1C)
private val GoldGroove = Color(0xFF6E4A10)
private val TailLight = Color(0xFFC99A3A)
private val TailDark = Color(0xFF8A5F1A)
private val TailMid = Color(0xFFAE8130)
private val TailDeep = Color(0xFF6E4A12)
private val RibLine = Color(0xFF4A300A)
