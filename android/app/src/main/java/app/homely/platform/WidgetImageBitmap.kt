package app.homely.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.homely.data.WidgetSpec
import app.homely.data.widgetImageFrame
import kotlin.math.max
import kotlin.math.roundToInt

/** Bounded bitmaps preserve the exact editor framing without oversized RemoteViews payloads. */
object WidgetImageBitmap {
    private fun bitmap(width: Int, height: Int): Bitmap {
        val reduction = (max(width, height) / 1024f).coerceAtLeast(1f)
        return Bitmap.createBitmap(
            (width / reduction).roundToInt().coerceAtLeast(1),
            (height / reduction).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
    }

    private fun draw(
        canvas: Canvas,
        path: String,
        width: Int,
        height: Int,
        scale: String,
        zoom: Float,
        offsetX: Float,
        offsetY: Float,
    ) {
        val source = IconRenderer.decode(path, max(width, height) * 2)
        try {
            val frame =
                widgetImageFrame(
                    source.width.toFloat(),
                    source.height.toFloat(),
                    width.toFloat(),
                    height.toFloat(),
                    scale,
                    zoom,
                    offsetX,
                    offsetY,
                )
            canvas.drawBitmap(
                source,
                null,
                RectF(frame.left, frame.top, frame.left + frame.width, frame.top + frame.height),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        } finally {
            source.recycle()
        }
    }

    fun image(
        path: String,
        width: Int,
        height: Int,
        scale: String = "crop",
        zoom: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
    ): Bitmap =
        bitmap(width, height).also { result ->
            draw(Canvas(result), path, result.width, result.height, scale, zoom, offsetX, offsetY)
        }

    fun background(
        spec: WidgetSpec,
        images: Map<String, String>,
        width: Int,
        height: Int,
        radius: Float = spec.radius.toFloat(),
    ): Bitmap =
        bitmap(width, height).also { result ->
            val canvas = Canvas(result)
            val bounds = RectF(0f, 0f, result.width.toFloat(), result.height.toFloat())
            val radiusPx = radius * result.width / width.coerceAtLeast(1)
            canvas.clipPath(
                Path().apply { addRoundRect(bounds, radiusPx, radiusPx, Path.Direction.CW) }
            )
            val layer = canvas.saveLayerAlpha(bounds, (spec.backgroundOpacity * 255).roundToInt())
            canvas.drawColor(Color.parseColor(spec.background))
            spec.backgroundImageId
                ?.let { images[it] }
                ?.let { path ->
                    runCatching {
                        draw(
                            canvas,
                            path,
                            result.width,
                            result.height,
                            spec.backgroundScale,
                            spec.backgroundZoom,
                            spec.backgroundOffsetX,
                            spec.backgroundOffsetY,
                        )
                    }
                }
            if (spec.backgroundOverlay > 0f)
                canvas.drawColor(Color.argb((spec.backgroundOverlay * 255).roundToInt(), 0, 0, 0))
            canvas.restoreToCount(layer)
        }
}
