package app.homely.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class WidgetImageFrame(val left: Float, val top: Float, val width: Float, val height: Float)

fun widgetImageTranslation(imageLength: Float, targetLength: Float, offset: Float): Float =
    (targetLength - imageLength) / 2f +
        abs(targetLength - imageLength) / 2f * offset.coerceIn(-1f, 1f)

/** Shared crop, fit and pan geometry for the editor, home and Android RemoteViews. */
fun widgetImageFrame(
    sourceWidth: Float,
    sourceHeight: Float,
    targetWidth: Float,
    targetHeight: Float,
    mode: String = "crop",
    zoom: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
): WidgetImageFrame {
    require(sourceWidth > 0 && sourceHeight > 0 && targetWidth > 0 && targetHeight > 0)
    val base =
        if (mode == "fit") min(targetWidth / sourceWidth, targetHeight / sourceHeight)
        else max(targetWidth / sourceWidth, targetHeight / sourceHeight)
    val width = sourceWidth * base * zoom.coerceIn(1f, 4f)
    val height = sourceHeight * base * zoom.coerceIn(1f, 4f)
    return WidgetImageFrame(
        widgetImageTranslation(width, targetWidth, offsetX),
        widgetImageTranslation(height, targetHeight, offsetY),
        width,
        height,
    )
}
