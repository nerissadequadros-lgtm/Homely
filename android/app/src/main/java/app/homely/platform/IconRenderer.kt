package app.homely.platform

import android.content.Context
import android.graphics.*
import androidx.core.graphics.drawable.toBitmap
import app.homely.data.DraftIcon
import java.io.File
import java.util.UUID
import kotlin.math.*

/** One renderer for the editor, persisted themes and launcher. Source layers remain editable. */
object IconRenderer {
    fun decode(path: String, limit: Int = 1024): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Escolha uma imagem PNG, JPG ou WebP válida."
        }
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = 1
                while (
                    max(bounds.outWidth, bounds.outHeight) / inSampleSize > limit
                ) inSampleSize *= 2
            }
        return requireNotNull(BitmapFactory.decodeFile(path, options))
    }

    fun source(ctx: Context, icon: DraftIcon): Bitmap {
        val path = icon.sourcePath ?: icon.path.takeIf { it.isNotBlank() }
        if (path != null) return decode(path)
        val drawable = ctx.packageManager.getApplicationIcon(icon.packageName)
        return (if (drawable is android.graphics.drawable.AdaptiveIconDrawable) drawable.foreground
            else drawable)
            .toBitmap(256, 256)
    }

    fun render(
        ctx: Context,
        icon: DraftIcon,
        size: Int = 256,
        fonts: Map<String, String> = emptyMap(),
    ): Bitmap {
        require(
            icon.foregroundScale in .2f..1.5f &&
                icon.shadowRadius in 0f..20f &&
                icon.strokeWidth in 0f..10f
        )
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val unit = size / 108f
        val margin = max(4f, icon.shadowRadius / 2f) * unit
        val rect = RectF(margin, margin, size - margin, size - margin)
        val path = Path()
        when (icon.mask) {
            "circle" -> path.addOval(rect, Path.Direction.CW)
            "hexagon",
            "pentagon" -> {
                val sides = if (icon.mask == "hexagon") 6 else 5
                repeat(sides) { i ->
                    val angle = (i * 2 * PI / sides - PI / 2)
                    val x = rect.centerX() + rect.width() / 2 * cos(angle).toFloat()
                    val y = rect.centerY() + rect.height() / 2 * sin(angle).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            "none" -> path.addRect(rect, Path.Direction.CW)
            "teardrop" ->
                path.addRoundRect(
                    rect,
                    floatArrayOf(
                        40 * unit,
                        40 * unit,
                        40 * unit,
                        40 * unit,
                        0f,
                        0f,
                        40 * unit,
                        40 * unit,
                    ),
                    Path.Direction.CW,
                )
            else ->
                path.addRoundRect(
                    rect,
                    (if (icon.mask == "rounded") 16 else 28) * unit,
                    (if (icon.mask == "rounded") 16 else 28) * unit,
                    Path.Direction.CW,
                )
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = icon.backgroundColor?.let { Color.parseColor(it) } ?: Color.TRANSPARENT
        if (icon.shadowRadius > 0)
            paint.setShadowLayer(
                icon.shadowRadius * unit / 2,
                0f,
                2 * unit,
                Color.parseColor(icon.shadowColor ?: "#000000"),
            )
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
        canvas.save()
        canvas.clipPath(path)
        canvas.translate(icon.offsetX * unit, icon.offsetY * unit)
        canvas.rotate(icon.rotation, size / 2f, size / 2f)
        if (icon.mirrored) canvas.scale(-1f, 1f, size / 2f, size / 2f)
        val src = source(ctx, icon)
        val extent = rect.width() * icon.foregroundScale
        val scale = extent / max(src.width, src.height)
        val w = src.width * scale
        val h = src.height * scale
        canvas.drawBitmap(
            src,
            null,
            RectF((size - w) / 2, (size - h) / 2, (size + w) / 2, (size + h) / 2),
            paint,
        )
        src.recycle()
        canvas.restore()
        if (icon.text.isNotBlank()) {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor(icon.textColor)
            paint.textSize = icon.textSize * unit
            paint.textAlign = Paint.Align.CENTER
            paint.typeface =
                icon.fontId?.let { fonts[it] }?.let { FontLibrary.typeface(it) }
                    ?: Typeface.create(
                        when (icon.fontFamily) {
                            "serif" -> "serif"
                            "mono" -> "monospace"
                            "hand" -> "cursive"
                            "display" -> "sans-serif-condensed"
                            else -> "sans-serif"
                        },
                        Typeface.BOLD,
                    )
            canvas.drawText(icon.text.take(40), size / 2f, size - 16 * unit, paint)
        }
        if (icon.strokeWidth > 0) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = icon.strokeWidth * unit
            paint.color = Color.parseColor(icon.strokeColor ?: "#FFFFFF")
            canvas.drawPath(path, paint)
        }
        return bitmap
    }

    fun materialize(
        ctx: Context,
        icon: DraftIcon,
        fonts: Map<String, String> = emptyMap(),
    ): DraftIcon {
        if (icon.rendered && File(icon.path).isFile) return icon
        if (
            icon.path.isNotBlank() &&
                icon.sourcePath == null &&
                icon.backgroundColor == null &&
                icon.foregroundScale == 1f &&
                icon.shadowRadius == 0f &&
                icon.strokeWidth == 0f &&
                icon.rotation == 0f &&
                !icon.mirrored &&
                icon.offsetX == 0f &&
                icon.offsetY == 0f &&
                icon.text.isBlank()
        )
            return icon.copy(rendered = true)
        val source =
            icon.sourcePath
                ?: icon.path.takeIf { it.isNotBlank() }
                ?: run {
                    val file = File(ctx.filesDir, "source-${UUID.randomUUID()}.png")
                    val bmp = source(ctx, icon)
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    file.path
                }
        val result = icon.copy(sourcePath = source)
        val bitmap = render(ctx, result, fonts = fonts)
        val dest = File(ctx.filesDir, "icon-${UUID.randomUUID()}.png")
        dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return result.copy(path = dest.path, rendered = true)
    }
}
