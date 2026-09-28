package app.homely.platform

import android.content.Context
import android.graphics.*
import android.text.*
import app.homely.data.*
import kotlin.math.*

object LiveWidgetBitmap {
    fun render(
        ctx: Context,
        l: WidgetLayer,
        fonts: Map<String, String>,
        width: Int,
        height: Int,
    ): Bitmap {
        val bw = (width * 2).coerceIn(100, 768)
        val bh = (height.toFloat() / width.coerceAtLeast(1) * bw).toInt().coerceIn(64, 768)
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val scale = bw / width.coerceAtLeast(1).toFloat()
        val padding = 8 * scale
        val base = (l.fontSize * scale).coerceAtLeast(12f)
        val tint = Color.parseColor(l.color)
        val typeface =
            l.fontId?.let { fonts[it] }?.let { FontLibrary.typeface(it) }
                ?: Typeface.create(
                    when (l.fontFamily) {
                        "mono" -> "monospace"
                        "serif" -> "serif"
                        "hand" -> "cursive"
                        "display" -> "sans-serif-condensed"
                        else -> "sans-serif"
                    },
                    if (l.weight >= 600) Typeface.BOLD else Typeface.NORMAL,
                )
        var y = padding
        fun text(value: String, size: Float = base, alpha: Int = 255, family: Typeface = typeface) {
            val p =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = tint
                    this.alpha = alpha
                    textSize = size
                    this.typeface = family
                }
            val layout =
                StaticLayout.Builder.obtain(
                        value,
                        0,
                        value.length,
                        p,
                        (bw - 2 * padding).toInt().coerceAtLeast(1),
                    )
                    .setAlignment(
                        when (l.alignment) {
                            "center" -> Layout.Alignment.ALIGN_CENTER
                            "end" -> Layout.Alignment.ALIGN_OPPOSITE
                            else -> Layout.Alignment.ALIGN_NORMAL
                        }
                    )
                    .setMaxLines(9)
                    .build()
            c.save()
            c.translate(padding, y)
            layout.draw(c)
            c.restore()
            y += layout.height + 3 * scale
        }
        when (l.type) {
            "weather" -> {
                val w = l.weather
                val d = WeatherStore.cached(ctx, w)
                if (d == null)
                    text(
                        if (w.latitude == null) "Escolha uma cidade"
                        else "Sem previsão\nToque para atualizar",
                        base * .75f,
                    )
                else {
                    if (w.showCity) text(w.city, base * .7f, 185)
                    text(
                        (if (w.showIcon) WeatherStore.symbol(d.symbol) + " " else "") +
                            WeatherStore.temperature(d.temperature, w.unit),
                        base * (if (w.style == "compact") 1.2f else 1.8f),
                    )
                    if (w.showCondition) text(WeatherStore.description(d.symbol), base * .7f)
                    if (w.showRange)
                        text(
                            "↓ ${WeatherStore.temperature(d.minimum,w.unit)}   ↑ ${WeatherStore.temperature(d.maximum,w.unit)}",
                            base * .6f,
                            185,
                        )
                    if (w.showHumidity) text("Umidade ${d.humidity?.toInt()?:"—"}%", base * .55f)
                    if (w.showWind) text("Vento ${d.wind.toInt()} km/h", base * .55f)
                    if (w.style == "forecast")
                        text(
                            d.hourly.take(3).joinToString("   ") {
                                java.text
                                    .SimpleDateFormat("HH'h'", java.util.Locale.getDefault())
                                    .format(java.util.Date(it.time)) +
                                    " " +
                                    WeatherStore.temperature(it.temperature, w.unit)
                            },
                            base * .5f,
                        )
                    if (d.stale || System.currentTimeMillis() - d.fetchedAt > 7200000)
                        text("Dados anteriores", base * .5f)
                }
            }
            "calendar" -> {
                val zone =
                    if (l.clock.zone.isBlank()) java.time.ZoneId.systemDefault()
                    else java.time.ZoneId.of(l.clock.zone)
                val today = java.time.LocalDate.now(zone)
                val month = java.time.YearMonth.from(today).plusMonths(l.monthOffset.toLong())
                val first = month.atDay(1).dayOfWeek.value % 7
                val rows = (first + month.lengthOfMonth() + 6) / 7
                val size = min(base, (bw - padding * 2) / 14f)
                text(
                    month.format(
                        java.time.format.DateTimeFormatter.ofPattern(
                            "MMMM yyyy",
                            java.util.Locale("pt", "BR"),
                        )
                    ),
                    size,
                )
                val p =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = tint
                        textSize = size
                        this.typeface = typeface
                        textAlign = Paint.Align.CENTER
                    }
                val cellWidth = (bw - 2 * padding) / 7
                p.alpha = 150
                "DSTQQSS"
                    .forEachIndexed { index, ch ->
                        c.drawText(ch.toString(), padding + (index + .5f) * cellWidth, y + size, p)
                    }
                p.alpha = 255
                y += size * 1.8f
                val cellHeight = (bh - padding - y) / rows
                repeat(rows * 7) { index ->
                    val day = index - first + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val x = padding + (index % 7 + .5f) * cellWidth
                        val cy = y + (index / 7 + .5f) * cellHeight
                        if (java.time.YearMonth.from(today) == month && day == today.dayOfMonth) {
                            p.alpha = 40
                            c.drawCircle(x, cy, min(cellWidth, cellHeight) * .43f, p)
                            p.alpha = 255
                        }
                        c.drawText(day.toString(), x, cy - (p.ascent() + p.descent()) / 2, p)
                    }
                }
            }
            "phone" -> text("☎\n" + l.text.ifBlank { "Telefone" }, base)
        }
        return bitmap
    }
}
