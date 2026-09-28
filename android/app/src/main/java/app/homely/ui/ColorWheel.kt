package app.homely.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    initialColor: Color = Color.Red,
    onColorChanged: (Color) -> Unit,
) {
    val currentCallback by rememberUpdatedState(onColorChanged)
    var hsv by remember { mutableStateOf(floatArrayOf(0f, 1f, 1f)) }

    LaunchedEffect(initialColor) {
        val r = initialColor.red * 255
        val g = initialColor.green * 255
        val b = initialColor.blue * 255
        hsv =
            FloatArray(3).also {
                android.graphics.Color.RGBToHSV(r.toInt(), g.toInt(), b.toInt(), it)
            }
    }

    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val diameter = min(constraints.maxWidth, constraints.maxHeight).toFloat()
        val radius = diameter / 2f
        val center = Offset(radius, radius)

        Canvas(
            modifier =
                Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            updateColorFromOffset(offset, center, radius, hsv) { newHsv ->
                                hsv = newHsv
                                currentCallback(Color.hsv(newHsv[0], newHsv[1], newHsv[2]))
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            updateColorFromOffset(change.position, center, radius, hsv) { newHsv ->
                                hsv = newHsv
                                currentCallback(Color.hsv(newHsv[0], newHsv[1], newHsv[2]))
                            }
                        }
                    }
        ) {
            val sweepGradient =
                ShaderBrush(
                    SweepGradientShader(
                        center = center,
                        colors =
                            listOf(
                                Color.Red,
                                Color.Yellow,
                                Color.Green,
                                Color.Cyan,
                                Color.Blue,
                                Color.Magenta,
                                Color.Red,
                            ),
                        colorStops = null,
                    )
                )

            drawCircle(
                brush = sweepGradient,
                radius = radius,
                center = center,
            )

            drawCircle(
                color = Color.White,
                radius = radius,
                center = center,
                alpha = 1f - hsv[1], // Saturation (0 = white, 1 = color)
            )

            drawCircle(
                color = Color.Black,
                radius = radius,
                center = center,
                alpha = 1f - hsv[2], // Value (0 = black, 1 = color)
            )

            // Draw thumb
            val angleRad = Math.toRadians(hsv[0].toDouble())
            val thumbRadius = hsv[1] * radius
            val thumbX = center.x + (thumbRadius * cos(angleRad)).toFloat()
            val thumbY = center.y + (thumbRadius * sin(angleRad)).toFloat()

            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = Offset(thumbX, thumbY),
                style = Stroke(width = 3.dp.toPx()),
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = 14.dp.toPx(),
                center = Offset(thumbX, thumbY),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

private fun updateColorFromOffset(
    offset: Offset,
    center: Offset,
    radius: Float,
    currentHsv: FloatArray,
    onResult: (FloatArray) -> Unit,
) {
    val dx = offset.x - center.x
    val dy = offset.y - center.y
    val dist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

    val angle = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())) + 360) % 360
    val saturation = (dist / radius).coerceIn(0f, 1f)

    onResult(floatArrayOf(angle.toFloat(), saturation, currentHsv[2]))
}
