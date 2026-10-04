package com.automatelinux.ben.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** The three bars at rest, as they are cut into the launcher icon. */
private val REST = floatArrayOf(0.57f, 1f, 0.71f)

/**
 * Ben's mark: the voice trace from the icon. Still, it stands beside something he said; moving,
 * it means he is working on an answer right now.
 */
@Composable
fun VoiceBars(color: Color, modifier: Modifier = Modifier, height: Dp = 16.dp, moving: Boolean = false) {
    val phase = if (moving) {
        val beat by rememberInfiniteTransition().animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        )
        beat
    } else {
        null
    }

    Canvas(modifier.size(width = height * 0.875f, height = height)) {
        val bar = size.width * 0.23f
        val gap = (size.width - 3 * bar) / 2
        for (i in 0..2) {
            val level = if (phase == null) {
                REST[i]
            } else {
                // Each bar a little behind the one before it, so the motion travels.
                0.62f + 0.38f * sin((phase - i * 0.21f) * 2 * PI).toFloat()
            }
            val h = size.height * level
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (bar + gap), (size.height - h) / 2),
                size = Size(bar, h),
                cornerRadius = CornerRadius(bar / 2),
            )
        }
    }
}
