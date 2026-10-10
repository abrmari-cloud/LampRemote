package com.maria.lampremote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Icons drawn after the physical remote's own symbols. */
enum class GlyphKind {
    SUN_FILLED, SUN_OUTLINE, MOON, BULB, KELVIN, HALF,
    BRIGHT_UP, BRIGHT_DOWN, K_UP, K_DOWN, POWER, NONE,
}

/** Maps the names offered when saving a button to the remote's symbols. Unknown names get initials. */
fun glyphFor(name: String): GlyphKind = when (name.trim().lowercase().replace('−', '-')) {
    "солнце тёмное", "солнце темное" -> GlyphKind.SUN_FILLED
    "солнце светлое" -> GlyphKind.SUN_OUTLINE
    "луна" -> GlyphKind.MOON
    "лампочка" -> GlyphKind.BULB
    "k" -> GlyphKind.KELVIN
    "◐" -> GlyphKind.HALF
    "яркость +" -> GlyphKind.BRIGHT_UP
    "яркость -" -> GlyphKind.BRIGHT_DOWN
    "k+" -> GlyphKind.K_UP
    "k-" -> GlyphKind.K_DOWN
    "on", "off" -> GlyphKind.POWER
    else -> GlyphKind.NONE
}

@Composable
fun Glyph(kind: GlyphKind, fallback: String, color: Color, size: Dp = 26.dp) {
    when (kind) {
        GlyphKind.KELVIN -> KBadge("", color, size)
        GlyphKind.K_UP -> KBadge("+", color, size)
        GlyphKind.K_DOWN -> KBadge("−", color, size)
        GlyphKind.BRIGHT_UP, GlyphKind.BRIGHT_DOWN -> Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(size * 0.85f)) { drawSun(color, filled = false) }
            Text(
                if (kind == GlyphKind.BRIGHT_UP) "+" else "−",
                color = color,
                fontSize = (size.value * 0.6f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        GlyphKind.NONE -> Text(
            fallback.trim().take(2),
            color = color,
            fontSize = (size.value * 0.55f).sp,
            fontWeight = FontWeight.SemiBold,
        )
        else -> Canvas(Modifier.size(size)) { drawGlyph(kind, color) }
    }
}

@Composable
private fun KBadge(sign: String, color: Color, size: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(size).border(1.6.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("K", color = color, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.Bold)
        }
        if (sign.isNotEmpty()) {
            Text(
                sign,
                color = color,
                fontSize = (size.value * 0.6f).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

private fun DrawScope.drawSun(color: Color, filled: Boolean) {
    val c = center
    val r = size.minDimension * 0.2f
    val w = size.minDimension * 0.08f
    if (filled) drawCircle(color, r, c) else drawCircle(color, r, c, style = Stroke(w))
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        drawLine(
            color,
            Offset(c.x + dx * r * 1.6f, c.y + dy * r * 1.6f),
            Offset(c.x + dx * r * 2.35f, c.y + dy * r * 2.35f),
            strokeWidth = w,
            cap = StrokeCap.Round,
        )
    }
}

fun DrawScope.drawGlyph(kind: GlyphKind, color: Color) {
    val s = size.minDimension
    val w = s * 0.08f
    val c = center
    when (kind) {
        GlyphKind.SUN_FILLED -> drawSun(color, filled = true)
        GlyphKind.SUN_OUTLINE -> drawSun(color, filled = false)
        GlyphKind.MOON -> {
            val disc = Path().apply { addOval(Rect(c, s * 0.38f)) }
            val bite = Path().apply { addOval(Rect(Offset(c.x + s * 0.18f, c.y - s * 0.14f), s * 0.32f)) }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), color)
        }
        GlyphKind.BULB -> {
            drawCircle(color, s * 0.24f, Offset(c.x, c.y - s * 0.1f), style = Stroke(w))
            drawLine(color, Offset(c.x - s * 0.12f, c.y + s * 0.26f), Offset(c.x + s * 0.12f, c.y + s * 0.26f), w, StrokeCap.Round)
            drawLine(color, Offset(c.x - s * 0.08f, c.y + s * 0.38f), Offset(c.x + s * 0.08f, c.y + s * 0.38f), w, StrokeCap.Round)
        }
        GlyphKind.HALF -> {
            val r = s * 0.36f
            drawCircle(color, r, c, style = Stroke(w))
            drawArc(color, 90f, 180f, true, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2))
        }
        GlyphKind.POWER -> {
            val r = s * 0.32f
            drawArc(
                color, -60f, 300f, false,
                topLeft = Offset(c.x - r, c.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(w, cap = StrokeCap.Round),
            )
            drawLine(color, Offset(c.x, c.y - r * 1.15f), Offset(c.x, c.y - r * 0.1f), w, StrokeCap.Round)
        }
        else -> Unit
    }
}
