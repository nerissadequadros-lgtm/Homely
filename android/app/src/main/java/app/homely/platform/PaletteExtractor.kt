package app.homely.platform

import android.graphics.Color
import app.homely.data.Palette

object PaletteExtractor {
    fun extract(path: String): Palette {
        val bitmap = IconRenderer.decode(path, 128)
        val bins = mutableMapOf<Int, Int>()
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val c = bitmap.getPixel(x, y)
            if (Color.alpha(c) > 128) {
                val key =
                    (Color.red(c) / 32 shl 6) or (Color.green(c) / 32 shl 3) or (Color.blue(c) / 32)
                bins[key] = (bins[key] ?: 0) + 1
            }
        }
        bitmap.recycle()
        require(bins.isNotEmpty()) { "A imagem não contém cores visíveis." }
        val candidates =
            bins.entries
                .sortedByDescending { it.value }
                .take(12)
                .map { (k, _) ->
                    Color.rgb(
                        ((k shr 6) and 7) * 32 + 16,
                        ((k shr 3) and 7) * 32 + 16,
                        (k and 7) * 32 + 16,
                    )
                }
        val hsv = FloatArray(3)
        val base = candidates.maxBy {
            Color.colorToHSV(it, hsv)
            hsv[1] * .7f + hsv[2] * .3f
        }
        Color.colorToHSV(base, hsv)
        hsv[1] = hsv[1].coerceIn(.3f, .8f)
        hsv[2] = .95f
        fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
        val accent = hex(Color.HSVToColor(hsv))
        val secondary = hex(Color.HSVToColor(floatArrayOf((hsv[0] + 35) % 360, .35f, .9f)))
        return Palette(
            accent,
            hex(Color.HSVToColor(floatArrayOf(hsv[0], .3f, .07f))),
            hex(Color.HSVToColor(floatArrayOf(hsv[0], .2f, .15f))),
            secondary,
            "#FFFFFF",
        )
    }

    fun variant(p: Palette, light: Boolean) =
        p.copy(
            background = if (light) "#F7F7FC" else "#090C12",
            surface = if (light) "#E8E8F2" else "#171C27",
            text = if (light) "#171625" else "#FFFFFF",
        )
}
