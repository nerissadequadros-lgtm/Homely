package app.homely.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import app.homely.data.*
import kotlin.math.round

internal val widgetKinds =
    listOf(
        "text" to "Texto",
        "title" to "Título",
        "clock" to "Relógio",
        "analog" to "Analógico",
        "date" to "Data",
        "battery" to "Bateria",
        "battery_ring" to "Anel de bateria",
        "image" to "Imagem",
        "shortcut" to "Atalho",
        "shape" to "Forma",
        "note" to "Nota",
        "spacer" to "Respiro",
        "progress" to "Progresso",
        "countdown" to "Contagem",
        "weather" to "Clima",
        "calendar" to "Calendário",
        "phone" to "Telefone",
        "element" to "Composição",
        "number" to "Número",
        "icon" to "Ícone",
        "ring" to "Anel",
        "bar" to "Barra",
        "indicator" to "Indicador",
        "ram" to "RAM",
        "storage" to "Armazenamento",
        "wifi" to "Wi-Fi",
        "bluetooth" to "Bluetooth",
        "lan" to "LAN",
        "flashlight" to "Lanterna",
        "volume" to "Volume",
        "signal" to "Sinal Wi-Fi",
        "cpu" to "CPU do Homely",
    )

@Composable
internal fun WidgetCanvas(
    w: WidgetSpec,
    images: Map<String, String>,
    fonts: Map<String, String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onChange: (WidgetLayer) -> Unit,
    modifier: Modifier,
) {
    var zoom by remember(w.id) { mutableFloatStateOf(1f) }
    var pan by remember(w.id) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    BoxWithConstraints(
        modifier.clipToBounds().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val ratio = w.canvasWidth.toFloat() / w.height
        val width = (maxWidth - 32.dp).coerceAtMost((maxHeight - 48.dp).coerceAtLeast(1.dp) * ratio)
        val height = width / ratio
        val fit = width.value / w.canvasWidth
        BoxWithConstraints(
            Modifier.size(width, height)
                .graphicsLayer(
                    scaleX = zoom,
                    scaleY = zoom,
                    translationX = pan.x,
                    translationY = pan.y,
                )
                .clip(RoundedCornerShape((w.radius * fit).dp))
                .background(color(w.background))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) {
                                zoom = (zoom * event.calculateZoom()).coerceIn(.5f, 2f)
                                pan += event.calculatePan()
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
        ) {
            val cw = maxWidth
            val ch = maxHeight
            val pxW = with(density) { cw.toPx() }
            val pxH = with(density) { ch.toPx() }
            CompositionLocalProvider(
                LocalDensity provides Density(density.density * fit, density.fontScale)
            ) {
                WidgetBackground(w, images, Modifier.fillMaxSize())
            }
            if (w.placement == "grid")
                Canvas(Modifier.fillMaxSize()) {
                    repeat(w.gridColumns + 1) { i ->
                        val x = size.width * i / w.gridColumns
                        drawLine(
                            Color.White.copy(alpha = .2f),
                            androidx.compose.ui.geometry.Offset(x, 0f),
                            androidx.compose.ui.geometry.Offset(x, size.height),
                        )
                    }
                    repeat(w.gridRows + 1) { i ->
                        val y = size.height * i / w.gridRows
                        drawLine(
                            Color.White.copy(alpha = .2f),
                            androidx.compose.ui.geometry.Offset(0f, y),
                            androidx.compose.ui.geometry.Offset(size.width, y),
                        )
                    }
                }
            w.displayLayers()
                .filter { it.visible }
                .sortedBy { it.zIndex }
                .forEach { l ->
                    var dx by remember(l) { mutableFloatStateOf(0f) }
                    var dy by remember(l) { mutableFloatStateOf(0f) }
                    var dw by remember(l) { mutableFloatStateOf(0f) }
                    var dh by remember(l) { mutableFloatStateOf(0f) }
                    Box(
                        Modifier.offset(
                                cw * l.x + with(density) { dx.toDp() },
                                ch * l.y + with(density) { dy.toDp() },
                            )
                            .size(
                                cw * (l.width + dw / pxW).coerceIn(.05f, 1 - l.x),
                                ch * (l.height + dh / pxH).coerceIn(.05f, 1 - l.y),
                            )
                            .pointerInput(l, pxW, pxH, w.placement, w.gridColumns, w.gridRows) {
                                if (!l.locked)
                                    detectDragGestures(
                                        onDragStart = { onSelect(l.id) },
                                        onDragCancel = {
                                            dx = 0f
                                            dy = 0f
                                        },
                                        onDragEnd = {
                                            val x = l.x + dx / pxW
                                            val y = l.y + dy / pxH
                                            onChange(
                                                l.copy(
                                                    x =
                                                        (if (w.placement == "grid")
                                                                round(x * w.gridColumns) /
                                                                    w.gridColumns
                                                            else x)
                                                            .coerceIn(0f, 1 - l.width),
                                                    y =
                                                        (if (w.placement == "grid")
                                                                round(y * w.gridRows) / w.gridRows
                                                            else y)
                                                            .coerceIn(0f, 1 - l.height),
                                                )
                                            )
                                            dx = 0f
                                            dy = 0f
                                        },
                                    ) { change, amount ->
                                        change.consume()
                                        dx =
                                            (dx + amount.x).coerceIn(
                                                -l.x * pxW,
                                                (1 - l.x - l.width) * pxW,
                                            )
                                        dy =
                                            (dy + amount.y).coerceIn(
                                                -l.y * pxH,
                                                (1 - l.y - l.height) * pxH,
                                            )
                                    }
                            }
                            .clickable { onSelect(l.id) }
                            .border(
                                if (selected == l.id) 2.dp else 0.dp,
                                if (selected == l.id) Violet else Color.Transparent,
                            )
                    ) {
                        CompositionLocalProvider(
                            LocalDensity provides Density(density.density * fit, density.fontScale)
                        ) {
                            WidgetLayerContent(l, images, true, Modifier.fillMaxSize(), fonts)
                        }
                        // Intercept editor taps before the item's own interactive content.
                        Box(
                            Modifier.matchParentSize().pointerInput(l.id) {
                                detectTapGestures { onSelect(l.id) }
                            }
                        )
                        if (selected == l.id && !l.locked)
                            Box(
                                Modifier.align(Alignment.BottomEnd)
                                    .size(22.dp)
                                    .background(Violet, CircleShape)
                                    .pointerInput(l, pxW, pxH) {
                                        detectDragGestures(
                                            onDragCancel = {
                                                dw = 0f
                                                dh = 0f
                                            },
                                            onDragEnd = {
                                                onChange(
                                                    l.copy(
                                                        width =
                                                            (l.width + dw / pxW).coerceIn(
                                                                .05f,
                                                                1 - l.x,
                                                            ),
                                                        height =
                                                            (l.height + dh / pxH).coerceIn(
                                                                .05f,
                                                                1 - l.y,
                                                            ),
                                                    )
                                                )
                                                dw = 0f
                                                dh = 0f
                                            },
                                        ) { change, amount ->
                                            change.consume()
                                            dw += amount.x
                                            dh += amount.y
                                        }
                                    }
                            )
                    }
                }
        }
        Row(
            Modifier.align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .8f))
        ) {
            TextButton({ zoom = (zoom - .25f).coerceAtLeast(.5f) }) { Text("−") }
            TextButton({
                zoom = 1f
                pan = androidx.compose.ui.geometry.Offset.Zero
            }) {
                Text("${(zoom*100).toInt()}%")
            }
            TextButton({ zoom = (zoom + .25f).coerceAtMost(2f) }) { Text("+") }
        }
    }
}

internal fun widgetItemIcon(type: String) =
    when (type) {
        "clock",
        "analog" -> Icons.Outlined.Schedule
        "calendar",
        "date" -> Icons.Outlined.CalendarMonth
        "phone" -> Icons.Outlined.Phone
        "weather" -> Icons.Outlined.Cloud
        "battery",
        "battery_ring" -> Icons.Outlined.BatteryFull
        "image" -> Icons.Outlined.Image
        "shortcut" -> Icons.Outlined.Apps
        "note" -> Icons.Outlined.EditNote
        "countdown" -> Icons.Outlined.Timer
        "progress" -> Icons.Outlined.BarChart
        "shape" -> Icons.Outlined.Category
        "spacer" -> Icons.Outlined.SpaceBar
        "wifi",
        "signal" -> Icons.Outlined.Wifi
        "bluetooth" -> Icons.Outlined.Bluetooth
        "lan" -> Icons.Outlined.Lan
        "flashlight" -> Icons.Outlined.FlashlightOn
        "storage" -> Icons.Outlined.Storage
        "ram",
        "cpu" -> Icons.Outlined.Memory
        "volume" -> Icons.Outlined.VolumeUp
        "ring",
        "indicator" -> Icons.Outlined.RadioButtonChecked
        "icon" -> Icons.Outlined.StarOutline
        "bar" -> Icons.Outlined.BarChart
        else -> Icons.Outlined.Title
    }
