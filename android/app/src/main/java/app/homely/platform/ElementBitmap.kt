package app.homely.platform

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.homely.data.*
import kotlin.math.min

/** Static rendering for Android RemoteViews; live transitions run in Homely Home. */
object ElementBitmap {
    fun render(
        ctx: Context,
        layer: WidgetLayer,
        images: Map<String, String>,
        fonts: Map<String, String>,
        width: Int,
        height: Int,
        content: Boolean = true,
    ): Bitmap {
        val w = width.coerceIn(1, 1024)
        val h = height.coerceIn(1, 1024)
        val density = ctx.resources.displayMetrics.density
        val reading = ElementSystem.read(ctx, layer.source())
        val state = layer.activeState(reading, false, ElementSystem.toggled(ctx, layer.id))
        val l = layer.copy(color = state?.color ?: layer.color, text = state?.text ?: layer.text)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.scale(state?.scale ?: 1f, state?.scale ?: 1f, w / 2f, h / 2f)
        c.rotate(layer.rotation + (state?.rotation ?: 0f), w / 2f, h / 2f)
        val radius = l.elementRadius * density
        val bounds = RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.clipPath(Path().apply { addRoundRect(bounds, radius, radius, Path.Direction.CW) })
        (state?.background ?: l.elementBackground)?.let {
            p.color = Color.parseColor(it)
            c.drawRoundRect(bounds, radius, radius, p)
        }
        if (l.elementBorderWidth > 0) {
            p.color = Color.parseColor(l.elementBorderColor)
            p.style = Paint.Style.STROKE
            p.strokeWidth = l.elementBorderWidth * density
            val inset = p.strokeWidth / 2
            c.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radius, radius, p)
            p.style = Paint.Style.FILL
        }
        val padding =
            (l.elementPadding * density).coerceAtMost(min(w, h) / 2f - 0.5f).coerceAtLeast(0f)
        c.translate(padding, padding)
        val cw = (w - 2 * padding).coerceAtLeast(1f)
        val ch = (h - 2 * padding).coerceAtLeast(1f)
        val imageKey = state?.imageId ?: l.id.takeIf { l.type == "image" }
        val composition = state?.components ?: l.components
        if (content && (composition.isNotEmpty() || l.type == "element")) {
            composition
                .filter { it.visible }
                .sortedBy { it.zIndex }
                .forEach { part ->
                    val bmp =
                        render(
                            ctx,
                            part,
                            images,
                            fonts,
                            (cw * part.width).toInt(),
                            (ch * part.height).toInt(),
                        )
                    c.drawBitmap(
                        bmp,
                        null,
                        RectF(
                            cw * part.x,
                            ch * part.y,
                            cw * (part.x + part.width),
                            ch * (part.y + part.height),
                        ),
                        Paint(Paint.ANTI_ALIAS_FLAG),
                    )
                    bmp.recycle()
                }
        } else if (!content) {
            // Clock content is supplied by Android TextClock above this bitmap.
        } else if (imageKey != null) {
            images[imageKey]?.let { path ->
                c.drawBitmap(
                    WidgetImageBitmap.image(
                        path,
                        cw.toInt(),
                        ch.toInt(),
                        l.imageScale,
                        l.imageZoom,
                        l.imageOffsetX,
                        l.imageOffsetY,
                    ),
                    null,
                    RectF(0f, 0f, cw, ch),
                    p,
                )
            }
        } else {
            val progress =
                (reading.value?.div(100) ?: if (l.source() == "none") l.progress else 0f).coerceIn(
                    0f,
                    1f,
                )
            val ring = l.type in listOf("ring", "battery_ring")
            val bar = l.type == "bar"
            if (ring || bar) {
                fun track() {
                    p.color = Color.parseColor(l.trackColor)
                    p.alpha = (l.trackOpacity * 255).toInt()
                }
                if (ring) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = (l.ringWidth * density).coerceAtMost(min(cw, ch) / 3)
                    p.strokeCap = Paint.Cap.ROUND
                    val side = (min(cw, ch) - p.strokeWidth).coerceAtLeast(1f)
                    val rect =
                        RectF((cw - side) / 2, (ch - side) / 2, (cw + side) / 2, (ch + side) / 2)
                    track()
                    c.drawArc(rect, -90f, 360f, false, p)
                    p.color = Color.parseColor(l.color)
                    p.alpha = 255
                    c.drawArc(rect, -90f, 360 * progress, false, p)
                    p.style = Paint.Style.FILL
                } else {
                    track()
                    c.drawRoundRect(RectF(0f, 0f, cw, ch), radius, radius, p)
                    p.color = Color.parseColor(l.color)
                    p.alpha = 255
                    c.drawRoundRect(RectF(0f, 0f, cw * progress, ch), radius, radius, p)
                }
            }
            val numeric =
                l.type in listOf("number", "battery", "ram", "storage", "volume", "signal", "cpu")
            val icon =
                l.type in listOf("icon", "indicator", "wifi", "bluetooth", "lan", "flashlight")
            if (l.type in listOf("calendar", "weather", "phone")) {
                val content =
                    LiveWidgetBitmap.render(
                        ctx,
                        l,
                        fonts,
                        (cw / density).toInt(),
                        (ch / density).toInt(),
                    )
                c.drawBitmap(content, null, RectF(0f, 0f, cw, ch), p)
            } else if (l.showValue || (!numeric && !ring && !bar)) {
                val value =
                    state?.text
                        ?: when {
                            icon ->
                                if (l.iconName != "auto") l.iconName
                                else
                                    when (l.source()) {
                                        "wifi",
                                        "signal" -> "Wi-Fi"
                                        "bluetooth" -> "ᛒ"
                                        "lan" -> "LAN"
                                        "flashlight" -> "☀"
                                        "volume" -> "♪"
                                        else -> "●"
                                    }
                            numeric || ring || bar ->
                                if (l.source() == "none")
                                    l.text.ifBlank { "${(l.progress*100).toInt()}%" }
                                else
                                    reading.value
                                        ?.toInt()
                                        ?.toString()
                                        ?.plus(if (l.numberFormat == "percent") "%" else "") ?: "—"
                            l.type in listOf("clock", "date", "analog") ->
                                layerDate(l, System.currentTimeMillis())
                            l.type == "note" -> WidgetNotes.text(ctx, l)
                            else -> l.text
                        }
                val tp =
                    TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.parseColor(l.color)
                        textSize = l.fontSize * density
                        typeface =
                            l.fontId?.let { fonts[it] }?.let(FontLibrary::typeface)
                                ?: Typeface.create(
                                    when (l.fontFamily) {
                                        "mono" -> "monospace"
                                        "serif" -> "serif"
                                        "hand" -> "cursive"
                                        else -> "sans-serif"
                                    },
                                    if (l.weight >= 600) Typeface.BOLD else Typeface.NORMAL,
                                )
                    }
                val layout =
                    StaticLayout.Builder.obtain(value, 0, value.length, tp, cw.toInt())
                        .setAlignment(
                            when (l.alignment) {
                                "start" -> Layout.Alignment.ALIGN_NORMAL
                                "end" -> Layout.Alignment.ALIGN_OPPOSITE
                                else -> Layout.Alignment.ALIGN_CENTER
                            }
                        )
                        .setMaxLines(9)
                        .build()
                c.translate(0f, ((ch - layout.height) / 2).coerceAtLeast(0f))
                layout.draw(c)
            }
        }
        // Alpha is applied once to the entire element, including its background and border.
        val opacity = l.opacity * (state?.opacity ?: 1f)
        if (opacity >= 1f) return bitmap
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
            Canvas(it).drawBitmap(bitmap, 0f, 0f, Paint().apply { alpha = (opacity * 255).toInt() })
            bitmap.recycle()
        }
    }
}
