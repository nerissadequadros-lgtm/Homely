package app.homely.platform

import android.content.Context
import android.graphics.*
import android.net.Uri
import app.homely.data.*
import com.caverock.androidsvg.SVG
import java.io.File
import java.util.UUID
import kotlin.math.*

/** Identical two-layer compositor for the editor and all exported icon states. */
object IconDesignRenderer {
    fun render(
        design: IconDesign,
        state: String = "default",
        size: Int = 384,
        mask: String = design.mask,
    ): Bitmap {
        design.validate()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(size / 108f, size / 108f)
        val bounds = RectF(0f, 0f, 108f, 108f)
        val clip =
            Path().apply {
                when (mask) {
                    "circle" -> addOval(bounds, Path.Direction.CW)
                    "square" -> addRect(bounds, Path.Direction.CW)
                    else -> addRoundRect(bounds, 28f, 28f, Path.Direction.CW)
                }
            }
        canvas.clipPath(clip)
        val appearance = design.appearance(state)
        drawLayer(canvas, appearance.background)
        drawLayer(canvas, appearance.foreground)
        return bitmap
    }

    private fun drawLayer(canvas: Canvas, layer: IconArtworkLayer) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.parseColor(layer.color)
        val extent = 108f * layer.scale
        val bounds = RectF(54f - extent / 2, 54f - extent / 2, 54f + extent / 2, 54f + extent / 2)
        layer.gradientColor?.let {
            val angle = Math.toRadians(layer.gradientAngle.toDouble())
            val dx = cos(angle).toFloat() * extent / 2
            val dy = sin(angle).toFloat() * extent / 2
            paint.shader =
                LinearGradient(
                    54f - dx,
                    54f - dy,
                    54f + dx,
                    54f + dy,
                    Color.parseColor(layer.color),
                    Color.parseColor(it),
                    Shader.TileMode.CLAMP,
                )
        }
        paint.alpha = (layer.opacity * 255).roundToInt()
        canvas.save()
        canvas.translate(layer.offsetX, layer.offsetY)
        canvas.rotate(layer.rotation, 54f, 54f)
        if (layer.kind == "image") {
            val image = IconRenderer.decode(requireNotNull(layer.imagePath))
            val factor = extent / max(image.width, image.height)
            val width = image.width * factor
            val height = image.height * factor
            // Isolate tint and opacity to this image, preserving the background layer.
            val imageSave =
                canvas.saveLayer(
                    bounds,
                    Paint().apply { alpha = (layer.opacity * 255).roundToInt() },
                )
            canvas.drawBitmap(
                image,
                null,
                RectF(54f - width / 2, 54f - height / 2, 54f + width / 2, 54f + height / 2),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
            if (layer.tintImage)
                canvas.drawRect(
                    bounds,
                    Paint(paint).apply {
                        alpha = 255
                        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                    },
                )
            canvas.restoreToCount(imageSave)
            image.recycle()
        } else {
            canvas.drawPath(shape(layer.shape, bounds), paint)
        }
        canvas.restore()
    }

    private fun shape(name: String, r: RectF) =
        Path().apply {
            when (name) {
                "circle" -> addOval(r, Path.Direction.CW)
                "rounded" -> addRoundRect(r, r.width() * .22f, r.height() * .22f, Path.Direction.CW)
                "triangle" -> {
                    moveTo(r.centerX(), r.top)
                    lineTo(r.right, r.bottom)
                    lineTo(r.left, r.bottom)
                    close()
                }
                "diamond" -> {
                    moveTo(r.centerX(), r.top)
                    lineTo(r.right, r.centerY())
                    lineTo(r.centerX(), r.bottom)
                    lineTo(r.left, r.centerY())
                    close()
                }
                "star" -> {
                    repeat(10) { i ->
                        val angle = i * PI / 5 - PI / 2
                        val radius = r.width() / 2 * if (i % 2 == 0) 1f else .45f
                        val x = r.centerX() + cos(angle).toFloat() * radius
                        val y = r.centerY() + sin(angle).toFloat() * radius
                        if (i == 0) moveTo(x, y) else lineTo(x, y)
                    }
                    close()
                }
                "heart" -> {
                    moveTo(r.centerX(), r.bottom)
                    cubicTo(
                        r.left - r.width() * .4f,
                        r.centerY(),
                        r.left,
                        r.top - r.height() * .25f,
                        r.centerX(),
                        r.top + r.height() * .2f,
                    )
                    cubicTo(
                        r.right,
                        r.top - r.height() * .25f,
                        r.right + r.width() * .4f,
                        r.centerY(),
                        r.centerX(),
                        r.bottom,
                    )
                    close()
                }
                else -> addRect(r, Path.Direction.CW)
            }
        }

    fun importImage(ctx: Context, uri: Uri): String {
        val bytes =
            requireNotNull(ctx.contentResolver.openInputStream(uri)).use { input ->
                val data = input.readBytesLimited(8 * 1024 * 1024)
                require(data.isNotEmpty()) { "O arquivo está vazio." }
                data
            }
        val prefix = bytes.take(4096).toByteArray().toString(Charsets.UTF_8)
        val isSvg =
            ctx.contentResolver.getType(uri) == "image/svg+xml" || prefix.contains("<svg", true)
        val bitmap =
            if (isSvg) {
                require(!prefix.contains("<!DOCTYPE", true) && !prefix.contains("<!ENTITY", true)) {
                    "SVG com entidades externas não é compatível."
                }
                val svg = SVG.getFromInputStream(bytes.inputStream())
                Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).also {
                    svg.documentWidth = 1024f
                    svg.documentHeight = 1024f
                    svg.renderToCanvas(Canvas(it))
                }
            } else {
                val temp = File(ctx.cacheDir, "icon-import-${UUID.randomUUID()}")
                try {
                    temp.writeBytes(bytes)
                    IconRenderer.decode(temp.path)
                } finally {
                    temp.delete()
                }
            }
        val file = File(ctx.filesDir, "icon-source-${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file.path
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "Escolha uma imagem de até 8 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
