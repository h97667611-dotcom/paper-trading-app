package com.papertrader.app.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.papertrader.app.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// Timeline in milliseconds.
private const val TOTAL_MS = 8000f
private const val ENTER_START = 500f
private const val ENTER_END = 2700f
private const val TURN_START = 5500f
private const val TURN_END = 5800f
private const val EXIT_END = 7500f
private const val FADE_START = 7500f

// Sprite sheet (karzl_atlas.webp, 280 x 300 px): body, raised hand and lower hand are cut apart.
private const val ATLAS_W = 280f
private const val ATLAS_H = 300f
private val BODY_OFFSET = IntOffset(60, 0)
private val BODY_SIZE = IntSize(172, 300)
private val LEFT_HAND_OFFSET = IntOffset(2, 51)
private val LEFT_HAND_SIZE = IntSize(46, 74)
private val LEFT_HAND_PIVOT = Offset(24f, 112f)
private val RIGHT_HAND_OFFSET = IntOffset(233, 173)
private val RIGHT_HAND_SIZE = IntSize(46, 48)
private val RIGHT_HAND_PIVOT = Offset(246f, 178f)
private val FEET_PIVOT = Offset(146f, 296f)

/**
 * App start animation: "Ghosttrade" fades in, s'Karzl waddles in from the left, stops in
 * the middle and waves, turns around and walks off again. Tap anywhere to skip.
 *
 * The drawing is black-on-white, so the scene has a white background and the sprite is drawn
 * with a multiply blend (white parts stay invisible). At the end the screen fades to black
 * to hand over to the black app.
 */
@Composable
fun IntroAnimation(onFinished: () -> Unit) {
    val elapsed = remember { Animatable(0f) }
    val atlas = ImageBitmap.imageResource(R.drawable.karzl_atlas)
    val textMeasurer = rememberTextMeasurer()
    val titleLayout = remember {
        textMeasurer.measure(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("Ghost") }
                withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append("trade") }
            },
            TextStyle(color = Color.Black, fontSize = 46.sp, letterSpacing = 1.sp)
        )
    }

    LaunchedEffect(Unit) {
        elapsed.animateTo(TOTAL_MS, tween(TOTAL_MS.toInt(), easing = LinearEasing))
        onFinished()
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onFinished() } }
    ) {
        val t = elapsed.value
        val w = size.width
        val h = size.height
        val pi = PI.toFloat()
        val twoPi = 2f * pi

        val s = min(h * 0.34f / ATLAS_H, w * 0.6f / ATLAS_W)
        val charW = ATLAS_W * s
        val feetY = h * 0.68f
        val centerX = w / 2f

        val enterP = ((t - ENTER_START) / (ENTER_END - ENTER_START)).coerceIn(0f, 1f)
        val turnP = ((t - TURN_START) / (TURN_END - TURN_START)).coerceIn(0f, 1f)
        val exitP = ((t - TURN_END) / (EXIT_END - TURN_END)).coerceIn(0f, 1f)
        val fade = ((t - FADE_START) / (TOTAL_MS - FADE_START)).coerceIn(0f, 1f)

        // Where the character is and how strongly he waddles.
        val (x, walkAmp, walkPhase) = when {
            t < ENTER_END -> {
                val e = 1f - (1f - enterP) * (1f - enterP)
                Triple(lerp(-charW * 0.7f, centerX, e), sqrt(1f - enterP), enterP * twoPi * 5f)
            }
            t < TURN_END -> Triple(centerX, 0f, 0f)
            else -> {
                val e = exitP * exitP
                Triple(lerp(centerX, -charW * 0.9f, e), sqrt(exitP), exitP * twoPi * 4.5f)
            }
        }

        // Waving while he stands in the middle.
        val waveIn = ((t - (ENTER_END - 100f)) / 300f).coerceIn(0f, 1f)
        val waveOut = ((TURN_START - t) / 300f).coerceIn(0f, 1f)
        val waveW = min(waveIn, waveOut)
        val waveAngle = sin((t - ENTER_END) / 1000f * twoPi * 3f) * 28f * waveW
        val hopP = ((t - ENTER_END) / 350f).coerceIn(0f, 1f)
        val hop = sin(hopP * pi) * h * 0.03f

        val stride = sin(walkPhase)
        val sway = sin(t / 1000f * twoPi * 1.4f)
        val waddleDeg = stride * 5.5f * walkAmp + sway * 1.8f * waveW
        val bounce = abs(stride) * h * 0.012f * walkAmp + hop
        val leftHandDeg = waveAngle - stride * 12f * walkAmp
        val rightHandDeg = stride * 14f * walkAmp + sin(t / 1000f * twoPi * 1.4f + 1f) * 3f * waveW

        // Turning around: horizontal flip that squeezes through zero width.
        val facingRaw = cos(turnP * pi)
        val facing = if (abs(facingRaw) < 0.03f) (if (facingRaw < 0f) -0.03f else 0.03f) else facingRaw

        // Background (white -> black at the very end).
        drawRect(lerpColor(Color.White, Color.Black, fade))

        // "Ghosttrade" above the spot where he stands.
        val titleIn = ((t - 100f) / 700f).coerceIn(0f, 1f)
        val titleTop = feetY - ATLAS_H * s - titleLayout.size.height - h * 0.05f
        drawText(
            titleLayout,
            topLeft = Offset((w - titleLayout.size.width) / 2f, titleTop + (1f - titleIn) * 24f),
            alpha = titleIn * (1f - fade)
        )

        // Soft ground shadow that shrinks while he hops.
        val shadowShrink = 1f - (bounce / (h * 0.05f)).coerceIn(0f, 0.5f)
        val shadowW = charW * 0.30f * shadowShrink
        drawOval(
            Color.Black.copy(alpha = 0.10f * (1f - fade)),
            topLeft = Offset(x - shadowW, feetY - h * 0.004f),
            size = Size(shadowW * 2f, h * 0.014f)
        )

        drawKarzl(
            atlas = atlas,
            anchorX = x,
            feetY = feetY,
            scale = s,
            facing = facing,
            waddleDeg = waddleDeg,
            bounce = bounce,
            leftHandDeg = leftHandDeg,
            rightHandDeg = rightHandDeg
        )
    }
}

private fun DrawScope.drawKarzl(
    atlas: ImageBitmap,
    anchorX: Float,
    feetY: Float,
    scale: Float,
    facing: Float,
    waddleDeg: Float,
    bounce: Float,
    leftHandDeg: Float,
    rightHandDeg: Float
) {
    withTransform({
        // The feet pivot of the sprite sheet ends up at (anchorX, feetY - bounce).
        translate(anchorX, feetY - bounce)
        scale(facing, 1f, pivot = Offset.Zero)
        rotate(waddleDeg, pivot = Offset.Zero)
        scale(scale, scale, pivot = Offset.Zero)
        translate(-FEET_PIVOT.x, -FEET_PIVOT.y)
    }) {
        drawPart(atlas, BODY_OFFSET, BODY_SIZE)
        withTransform({ rotate(leftHandDeg, pivot = LEFT_HAND_PIVOT) }) {
            drawPart(atlas, LEFT_HAND_OFFSET, LEFT_HAND_SIZE)
        }
        withTransform({ rotate(rightHandDeg, pivot = RIGHT_HAND_PIVOT) }) {
            drawPart(atlas, RIGHT_HAND_OFFSET, RIGHT_HAND_SIZE)
        }
    }
}

private fun DrawScope.drawPart(atlas: ImageBitmap, offset: IntOffset, size: IntSize) {
    drawImage(
        image = atlas,
        srcOffset = offset,
        srcSize = size,
        dstOffset = offset,
        dstSize = size,
        blendMode = BlendMode.Multiply,
        filterQuality = FilterQuality.High
    )
}
