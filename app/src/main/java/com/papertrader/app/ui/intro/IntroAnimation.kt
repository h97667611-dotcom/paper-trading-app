package com.papertrader.app.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val TOTAL_MS = 6200f
private const val WELCOME_TEXT = "Welcome to the best\nTrading simulator"

/**
 * Intro shown before the app starts: a white stick figure fades in, waves at the
 * viewer under the welcome text, then walks off the right side of the screen.
 * Tap anywhere to skip.
 */
@Composable
fun IntroAnimation(onFinished: () -> Unit) {
    val elapsed = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(Unit) {
        elapsed.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
        onFinished()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { onFinished() } }
    ) {
        val textLayout = remember(constraints.maxWidth) {
            textMeasurer.measure(
                text = WELCOME_TEXT,
                style = TextStyle(
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                constraints = Constraints(maxWidth = (constraints.maxWidth * 0.9f).toInt())
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val ms = elapsed.value
            val w = size.width
            val h = size.height
            val figureHeight = h * 0.22f
            val feetY = h * 0.62f

            val appear = (ms / 500f).coerceIn(0f, 1f)
            val textAlpha = ((ms - 300f) / 800f).coerceIn(0f, 1f)

            // Walking phase (starts at 3.6s, 2.4s long, smooth start/stop).
            val walkT = ((ms - 3600f) / 2400f).coerceIn(0f, 1f)
            val eased = walkT * walkT * (3f - 2f * walkT)
            val cx = w / 2f + eased * w * 1.05f
            val walkAmount = ((ms - 3500f) / 300f).coerceIn(0f, 1f)
            val walkPhase = walkT * 2f * PI.toFloat() * 4f

            // Arm raises at 0.4s, waves until 3.4s, lowers by 3.7s.
            val up = ((ms - 400f) / 400f).coerceIn(0f, 1f)
            val down = ((3700f - ms) / 300f).coerceIn(0f, 1f)
            val waveWeight = min(up, down)
            val waveSwing = sin(ms / 1000f * 2f * PI.toFloat() * 2.4f)

            // Ground line.
            drawLine(
                color = Color.White.copy(alpha = 0.15f * appear),
                start = Offset(0f, feetY),
                end = Offset(w, feetY),
                strokeWidth = 2f
            )

            drawStickman(
                cx = cx,
                feetY = feetY,
                height = figureHeight,
                alpha = appear,
                waveWeight = waveWeight,
                waveSwing = waveSwing,
                walkPhase = walkPhase,
                walkAmount = walkAmount
            )

            // Welcome text above the figure's head; it walks off together with him.
            val textTop = feetY - figureHeight - figureHeight * 0.18f - textLayout.size.height
            drawText(
                textLayout,
                topLeft = Offset(cx - textLayout.size.width / 2f, textTop),
                alpha = textAlpha
            )
        }
    }
}

private fun DrawScope.drawStickman(
    cx: Float,
    feetY: Float,
    height: Float,
    alpha: Float,
    waveWeight: Float,
    waveSwing: Float,
    walkPhase: Float,
    walkAmount: Float
) {
    val color = Color.White.copy(alpha = alpha)
    val stroke = height * 0.035f
    val r = height * 0.12f
    val bob = abs(sin(walkPhase)) * height * 0.02f * walkAmount

    val headCenter = Offset(cx, feetY - bob - height + r)
    val neck = Offset(cx, headCenter.y + r)
    val shoulder = Offset(cx, neck.y + height * 0.04f)
    val hip = Offset(cx, feetY - bob - height * 0.42f)
    val legLen = height * 0.42f
    val armSeg = height * 0.15f

    // Angles are in degrees measured from straight down, positive toward screen right.
    fun polar(from: Offset, len: Float, deg: Float): Offset {
        val rad = Math.toRadians(deg.toDouble())
        return Offset(from.x + len * sin(rad).toFloat(), from.y + len * cos(rad).toFloat())
    }

    fun limb(from: Offset, to: Offset) {
        drawLine(color, from, to, strokeWidth = stroke, cap = StrokeCap.Round)
    }

    // Head with a simple face.
    drawCircle(color, radius = r, center = headCenter, style = Stroke(width = stroke, cap = StrokeCap.Round))
    drawCircle(color, radius = r * 0.1f, center = Offset(cx - r * 0.38f, headCenter.y - r * 0.15f))
    drawCircle(color, radius = r * 0.1f, center = Offset(cx + r * 0.38f, headCenter.y - r * 0.15f))
    drawArc(
        color = color,
        startAngle = 25f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.5f, headCenter.y - r * 0.2f),
        size = Size(r, r * 0.8f),
        style = Stroke(width = stroke * 0.6f, cap = StrokeCap.Round)
    )

    // Body.
    limb(neck, hip)

    // Legs: standing apart, or swinging while walking.
    val swing = sin(walkPhase) * 30f * walkAmount
    val stance = 8f * (1f - walkAmount)
    limb(hip, polar(hip, legLen, -stance + swing))
    limb(hip, polar(hip, legLen, stance - swing))

    // Left arm (screen left): relaxed, swings opposite to the right side while walking.
    val leftArm = -12f * (1f - walkAmount) - swing * 0.8f
    limb(shoulder, polar(shoulder, armSeg * 2f, leftArm))

    // Right arm (screen right): raised and waving, blends into a walking swing.
    val walkArm = 12f * (1f - walkAmount) + swing * 0.8f
    val upperDeg = lerp(walkArm, 125f, waveWeight)
    val foreDeg = lerp(walkArm, 165f + waveSwing * 28f, waveWeight)
    val elbow = polar(shoulder, armSeg, upperDeg)
    val hand = polar(elbow, armSeg, foreDeg)
    limb(shoulder, elbow)
    limb(elbow, hand)
}
