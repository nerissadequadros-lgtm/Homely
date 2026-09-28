package app.homely.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.sin

/** Small, asset-free motion illustrations shared by the theme builder's choice cards. */
@Composable
fun ThemeDemonstration(
    kind: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "Demonstração de $kind")
    val clock by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
            label = "Movimento ilustrativo",
        )
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    val outline = MaterialTheme.colorScheme.outlineVariant
    val foreground = MaterialTheme.colorScheme.onSurface
    val description =
        when (kind) {
            "swipe_up" -> "Demonstração: o dedo desliza para cima e revela a grade."
            "swipe_down",
            "drawer_swipe_down" -> "Demonstração: o dedo desliza para baixo."
            "swipe_left" -> "Demonstração: o dedo desliza para a esquerda."
            "swipe_right" -> "Demonstração: o dedo desliza para a direita."
            "tap",
            "button" -> "Demonstração: um toque abre a grade."
            "double_tap" -> "Demonstração: dois toques rápidos."
            "long_press" -> "Demonstração: segurar abre um menu."
            "pinch_in" -> "Demonstração: dois dedos se aproximam."
            "pinch_out" -> "Demonstração: dois dedos se afastam."
            "continuous" -> "Demonstração: a grade continua ao deslizar verticalmente."
            "pages" -> "Demonstração: uma página de aplicativos desliza para a próxima."
            "list" -> "Demonstração: aplicativos organizados em uma lista vertical."
            "custom" -> "Demonstração: grade com dimensões personalizadas."
            "fade" -> "Demonstração: a grade aparece suavemente."
            "zoom" -> "Demonstração: a grade aumenta até preencher a tela."
            "slide" -> "Demonstração: a grade entra de baixo para cima."
            else -> "Demonstração: grade sem transição."
        }
    Canvas(modifier.fillMaxWidth().height(138.dp).semantics { contentDescription = description }) {
        val h = min(size.height - 10.dp.toPx(), size.width * 1.7f)
        val w = h * .57f
        val left = (size.width - w) / 2f
        val top = (size.height - h) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val corner = CornerRadius(w * .17f)
        val movement = ((clock - .12f) / .45f).coerceIn(0f, 1f)
        val eased = movement * movement * (3f - 2f * movement)
        val response = ((clock - .44f) / .18f).coerceIn(0f, 1f)
        val cycleAlpha = if (clock > .88f) ((1f - clock) / .12f).coerceIn(0f, 1f) else 1f
        val innerLeft = left + w * .09f
        val innerTop = top + h * .11f
        val innerWidth = w * .82f
        val innerHeight = h * .78f

        drawCircle(accent.copy(alpha = .055f), h * .52f, center)
        drawRoundRect(background, Offset(left, top), Size(w, h), corner)
        drawRoundRect(
            outline.copy(alpha = .9f),
            Offset(left, top),
            Size(w, h),
            corner,
            style = Stroke(1.3.dp.toPx()),
        )
        drawRoundRect(
            foreground.copy(alpha = .2f),
            Offset(center.x - w * .11f, top + h * .045f),
            Size(w * .22f, h * .025f),
            CornerRadius(h * .02f),
        )

        fun grid(
            offsetX: Float = 0f,
            offsetY: Float = 0f,
            alpha: Float = 1f,
            columns: Int = 3,
            rows: Int = 4,
            alternating: Boolean = false,
        ) {
            val cellW = innerWidth / columns
            val cellH = innerHeight / 4.5f
            for (row in 0 until rows) {
                for (column in 0 until columns) {
                    val icon = min(cellW * .60f, cellH * .60f)
                    val x = innerLeft + column * cellW + (cellW - icon) / 2f + offsetX
                    val y = innerTop + row * cellH + cellH * .12f + offsetY
                    val tint =
                        if ((row + column) % 3 == 0 || alternating) accent
                        else foreground.copy(alpha = .30f)
                    drawRoundRect(
                        tint.copy(alpha = tint.alpha * alpha),
                        Offset(x, y),
                        Size(icon, icon),
                        CornerRadius(icon * .32f),
                    )
                    drawRoundRect(
                        foreground.copy(alpha = .13f * alpha),
                        Offset(x, y + icon + cellH * .07f),
                        Size(icon, h * .012f),
                        CornerRadius(h * .008f),
                    )
                }
            }
        }

        clipRect(innerLeft, innerTop, innerLeft + innerWidth, innerTop + innerHeight) {
            when (kind) {
                "continuous" -> grid(offsetY = -eased * innerHeight * .47f, rows = 7)
                "pages" -> {
                    grid(offsetX = -eased * innerWidth)
                    grid(offsetX = innerWidth * (1f - eased), alternating = true)
                }
                "list" -> {
                    for (row in 0..6) {
                        val y = innerTop + row * h * .15f - eased * h * .15f
                        drawRoundRect(
                            accent.copy(alpha = .75f),
                            Offset(innerLeft + w * .05f, y),
                            Size(w * .17f, w * .17f),
                            CornerRadius(w * .05f),
                        )
                        drawRoundRect(
                            foreground.copy(alpha = .3f),
                            Offset(innerLeft + w * .29f, y + w * .025f),
                            Size(w * .40f, h * .022f),
                            CornerRadius(h * .01f),
                        )
                        drawRoundRect(
                            foreground.copy(alpha = .12f),
                            Offset(innerLeft + w * .29f, y + w * .105f),
                            Size(w * .27f, h * .014f),
                            CornerRadius(h * .01f),
                        )
                    }
                }
                "custom" -> grid(columns = 4, rows = 5)
                "fade" -> grid(alpha = eased * cycleAlpha)
                "zoom" ->
                    scale(.58f + eased * .42f, pivot = center) { grid(alpha = eased * cycleAlpha) }
                "slide" -> grid(offsetY = (1f - eased) * innerHeight, alpha = cycleAlpha)
                "none" -> grid()
                else -> {
                    // The resting home has a clock, a widget and a dock. The gesture reveals its
                    // result.
                    drawRoundRect(
                        accent.copy(alpha = .18f),
                        Offset(innerLeft + w * .06f, innerTop + h * .035f),
                        Size(w * .53f, h * .065f),
                        CornerRadius(h * .015f),
                    )
                    drawRoundRect(
                        foreground.copy(alpha = .11f),
                        Offset(innerLeft + w * .06f, innerTop + h * .13f),
                        Size(w * .69f, h * .20f),
                        CornerRadius(h * .04f),
                    )
                    for (i in 0..2) {
                        drawRoundRect(
                            accent.copy(alpha = .45f),
                            Offset(innerLeft + w * (.06f + i * .26f), innerTop + h * .58f),
                            Size(w * .17f, w * .17f),
                            CornerRadius(w * .055f),
                        )
                    }
                    if (kind == "button") {
                        drawCircle(accent, w * .095f, Offset(center.x, innerTop + h * .48f))
                        for (r in -1..1) for (c in -1..1) {
                            drawCircle(
                                background,
                                w * .009f,
                                Offset(
                                    center.x + c * w * .035f,
                                    innerTop + h * .48f + r * w * .035f,
                                ),
                            )
                        }
                    }
                    if (kind == "long_press" && response > 0f) {
                        drawRoundRect(
                            accent.copy(alpha = .92f * response * cycleAlpha),
                            Offset(innerLeft + w * .16f, innerTop + h * .34f),
                            Size(w * .57f, h * .22f),
                            CornerRadius(w * .07f),
                        )
                        for (line in 0..2) drawLine(
                            background.copy(alpha = response * cycleAlpha),
                            Offset(innerLeft + w * .23f, innerTop + h * (.39f + line * .056f)),
                            Offset(innerLeft + w * .62f, innerTop + h * (.39f + line * .056f)),
                            2.dp.toPx(),
                            StrokeCap.Round,
                        )
                    } else if (kind !in listOf("pinch_in", "pinch_out", "long_press")) {
                        val sheetY = innerTop + innerHeight * (1f - response)
                        drawRoundRect(
                            background.copy(alpha = cycleAlpha),
                            Offset(innerLeft, sheetY),
                            Size(innerWidth, innerHeight),
                            CornerRadius(w * .08f),
                        )
                        grid(offsetY = innerHeight * (1f - response), alpha = response * cycleAlpha)
                    }
                }
            }
        }

        fun finger(x: Float, y: Float, pressed: Boolean = false) {
            val halo = if (pressed) .15f + .05f * sin(clock * 60f).toFloat() else .14f
            drawCircle(accent.copy(alpha = .14f * cycleAlpha), w * halo, Offset(x, y))
            drawCircle(
                accent.copy(alpha = .52f * cycleAlpha),
                w * .079f,
                Offset(x, y),
                style = Stroke(1.1.dp.toPx()),
            )
            drawRoundRect(
                foreground.copy(alpha = .95f * cycleAlpha),
                Offset(x - w * .042f, y - w * .025f),
                Size(w * .084f, w * .20f),
                CornerRadius(w * .045f),
            )
            drawRoundRect(
                background.copy(alpha = .9f * cycleAlpha),
                Offset(x - w * .022f, y - w * .006f),
                Size(w * .044f, w * .075f),
                CornerRadius(w * .022f),
            )
        }
        val cx = center.x
        val cy = top + h * .63f
        when (kind) {
            "swipe_up" -> {
                drawLine(
                    accent.copy(alpha = .22f),
                    Offset(cx, cy + h * .12f),
                    Offset(cx, cy - h * .20f),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
                finger(cx, cy + h * .12f - eased * h * .32f)
            }
            "swipe_down",
            "drawer_swipe_down" -> {
                drawLine(
                    accent.copy(alpha = .22f),
                    Offset(cx, cy - h * .22f),
                    Offset(cx, cy + h * .08f),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
                finger(cx, cy - h * .22f + eased * h * .30f)
            }
            "swipe_left",
            "swipe_right" -> {
                val direction = if (kind == "swipe_left") -1 else 1
                drawLine(
                    accent.copy(alpha = .22f),
                    Offset(cx - w * .27f, cy),
                    Offset(cx + w * .27f, cy),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
                finger(cx + direction * (eased - .5f) * w * .54f, cy)
            }
            "pinch_in",
            "pinch_out" -> {
                val spread = if (kind == "pinch_in") .28f - eased * .17f else .11f + eased * .17f
                finger(cx - w * spread, cy - h * .12f)
                finger(cx + w * spread, cy + h * .05f)
            }
            "tap",
            "double_tap",
            "long_press",
            "button" -> {
                val y = if (kind == "button") innerTop + h * .48f else cy - h * .03f
                val pressed =
                    if (kind == "double_tap") clock in .18f..0.26f || clock in .33f..0.41f
                    else clock in .18f..0.43f
                finger(cx, y, pressed)
                if (pressed)
                    drawCircle(
                        accent.copy(alpha = .5f),
                        w * .17f,
                        Offset(cx, y),
                        style = Stroke(1.5.dp.toPx()),
                    )
            }
        }
        if (kind == "pages") {
            for (i in 0..1) drawCircle(
                if ((eased > .5f) == (i == 1)) accent else outline,
                h * .014f,
                Offset(center.x + (i - .5f) * h * .06f, top + h * .86f),
            )
        }
        drawLine(
            foreground.copy(alpha = .22f),
            Offset(center.x - w * .14f, top + h * .948f),
            Offset(center.x + w * .14f, top + h * .948f),
            2.dp.toPx(),
            StrokeCap.Round,
        )
    }
}
