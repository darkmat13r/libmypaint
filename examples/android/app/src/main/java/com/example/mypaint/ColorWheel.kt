package com.example.mypaint

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A reusable HSV color wheel composable that lets the user pick Hue and Saturation on the wheel,
 * and Value (brightness) with a slider. Emits Color through [onColorChange].
 */
@Composable
fun ColorWheel(
    modifier: Modifier = Modifier,
    initialColor: Color = Color(0f, 0.7f, 1f),
    showValueSlider: Boolean = true,
    onColorChange: (Color) -> Unit
) {
    // Convert initial RGB to HSV
    val (initH, initS, initV) = remember(initialColor) {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(
            (initialColor.red * 255f).toInt().coerceIn(0, 255),
            (initialColor.green * 255f).toInt().coerceIn(0, 255),
            (initialColor.blue * 255f).toInt().coerceIn(0, 255),
            hsv
        )
        Triple(hsv[0], hsv[1], hsv[2])
    }

    var hue by remember { mutableFloatStateOf(initH) }
    var sat by remember { mutableFloatStateOf(initS) }
    var value by remember { mutableFloatStateOf(initV) }

    var wheelSizePx by remember { mutableStateOf(0) }
    var wheelBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Regenerate wheel bitmap when size or value changes
    LaunchedEffect(wheelSizePx, value) {
        if (wheelSizePx <= 0) return@LaunchedEffect
        wheelBitmap = generateWheelBitmap(wheelSizePx, value).asImageBitmap()
    }

    // Notify external on any change
    LaunchedEffect(hue, sat, value) {
        val col = hsvToComposeColor(hue, sat, value)
        onColorChange(col)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .onSizeChanged { wheelSizePx = min(it.width, it.height) }
        ) {
            val bmp = wheelBitmap
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bmp,
                    contentDescription = "Color wheel",
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Gesture handling and selection indicator overlay
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(wheelSizePx) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                val minDim = min(size.width.toFloat(), size.height.toFloat())
                                val (h, s) = pointToHS(pos, minDim)
                                hue = h
                                sat = s
                            },
                            onDrag = { change, _ ->
                                val minDim = min(size.width.toFloat(), size.height.toFloat())
                                val (h, s) = pointToHS(change.position, minDim)
                                hue = h
                                sat = s
                            }
                        )
                    }
            ) {
                drawSelectionIndicator(hue, sat)
            }
        }

        if (showValueSlider) {
            Spacer(Modifier.height(8.dp))
            Text("Brightness")
            Slider(
                value = value,
                onValueChange = { v -> value = v.coerceIn(0f, 1f) },
                valueRange = 0f..1f
            )
        }
    }
}

private fun hsvToComposeColor(h: Float, s: Float, v: Float): Color {
    val argb = AndroidColor.HSVToColor(floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    return Color(argb)
}

private fun generateWheelBitmap(sizePx: Int, value: Float): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val cx = sizePx / 2f
    val cy = sizePx / 2f
    val radius = sizePx / 2f

    val radiusSq = radius * radius
    val hsv = FloatArray(3)

    for (y in 0 until sizePx) {
        val dy = y - cy
        for (x in 0 until sizePx) {
            val dx = x - cx
            val distSq = dx * dx + dy * dy
            if (distSq <= radiusSq) {
                val dist = sqrt(distSq)
                val s = (dist / radius).coerceIn(0f, 1f)
                val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                val h = ((angle + 360f) % 360f)
                hsv[0] = h
                hsv[1] = s
                hsv[2] = value
                bmp.setPixel(x, y, AndroidColor.HSVToColor(hsv))
            } else {
                bmp.setPixel(x, y, AndroidColor.TRANSPARENT)
            }
        }
    }
    return bmp
}

private fun DrawScope.drawSelectionIndicator(hue: Float, sat: Float) {
    val r = size.minDimension / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f

    val angleRad = Math.toRadians(hue.toDouble()).toFloat()
    val px = cx + cos(angleRad) * sat * r
    val py = cy + sin(angleRad) * sat * r

    // Outer ring
    drawCircle(
        color = Color.White,
        radius = 10.dp.toPx(),
        center = Offset(px, py),
        style = Stroke(width = 3.dp.toPx())
    )
    // Inner dot
    drawCircle(
        color = Color.Black,
        radius = 3.dp.toPx(),
        center = Offset(px, py)
    )
}

private fun pointToHS(pos: Offset, minDimen: Float): Pair<Float, Float> {
    val r = minDimen / 2f
    val cx = minDimen / 2f
    val cy = minDimen / 2f
    val dx = pos.x - cx
    val dy = pos.y - cy
    var angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    if (angle < 0f) angle += 360f
    val dist = sqrt(dx * dx + dy * dy)
    val s = (dist / r).coerceIn(0f, 1f)
    return angle to s
}
