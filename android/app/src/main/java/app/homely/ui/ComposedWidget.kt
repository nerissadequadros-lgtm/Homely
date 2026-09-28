package app.homely.ui

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import app.homely.data.*
import app.homely.platform.*
import java.io.File
import kotlin.math.*
import kotlinx.coroutines.*

fun studioFont(name: String) =
    when (name) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        "display" ->
            FontFamily(
                androidx.compose.ui.text.font.Typeface(
                    android.graphics.Typeface.create(
                        "sans-serif-condensed",
                        android.graphics.Typeface.NORMAL,
                    )
                )
            )
        "hand" ->
            FontFamily(
                androidx.compose.ui.text.font.Typeface(
                    android.graphics.Typeface.create("cursive", android.graphics.Typeface.NORMAL)
                )
            )
        else -> FontFamily.SansSerif
    }

@Composable
fun BaseWidgetLayerContent(
    layer: WidgetLayer,
    images: Map<String, String>,
    preview: Boolean = false,
    modifier: Modifier = Modifier,
    fonts: Map<String, String> = emptyMap(),
) {
    val ctx = LocalContext.current
    val notesRevision by WidgetNotes.revision.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(layer.type) {
        if (
            layer.type in
                listOf(
                    "clock",
                    "analog",
                    "date",
                    "battery",
                    "battery_ring",
                    "countdown",
                    "calendar",
                )
        )
            while (true) {
                delay(1000)
                now = System.currentTimeMillis()
            }
    }
    val battery =
        remember(now) {
            (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .coerceIn(0, 100)
        }
    val text =
        when (layer.type) {
            "clock" -> layerDate(layer, now)
            "date" -> layerDate(layer, now)
            "battery" -> "$battery%"
            "weather" -> WeatherStore.text(ctx, layer)
            "calendar" -> calendarText(layer, now)
            "note" ->
                if (preview) layer.text else notesRevision.let { WidgetNotes.text(ctx, layer) }
            "countdown" -> {
                val seconds = ((layer.targetTime - now) / 1000).coerceAtLeast(0)
                "${seconds/86400}d ${seconds/3600%24}h ${seconds/60%60}m ${seconds%60}s"
            }
            else -> layer.text
        }
    val font =
        remember(layer.fontId, fonts, layer.fontFamily) {
            layer.fontId
                ?.let { fonts[it] }
                ?.let { runCatching { FontFamily(Font(File(it))) }.getOrNull() }
                ?: studioFont(layer.fontFamily)
        }
    val alignment =
        when (layer.alignment) {
            "start" -> Alignment.CenterStart
            "end" -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    Box(
        modifier
            .alpha(layer.opacity)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (
                    !preview &&
                        layer.tapAction != "none" &&
                        (layer.tapPackage != null ||
                            layer.type in
                                listOf(
                                    "weather",
                                    "calendar",
                                    "date",
                                    "clock",
                                    "analog",
                                    "phone",
                                    "note",
                                    "shortcut",
                                ))
                )
                    Modifier.clickable { WidgetActions.launch(ctx, layer) }
                else Modifier
            ),
        contentAlignment = alignment,
    ) {
        when (layer.type) {
            "weather" -> WeatherContent(layer, Modifier.fillMaxSize(), fonts)
            "phone" ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Phone,
                        "Abrir telefone",
                        tint = color(layer.color),
                        modifier = Modifier.size((layer.fontSize * 2).dp),
                    )
                    Text(
                        layer.text.ifBlank { "Telefone" },
                        color = color(layer.color),
                        fontSize = layer.fontSize.sp,
                        fontFamily = font,
                        fontWeight = FontWeight(layer.weight),
                    )
                }
            "calendar" -> CalendarContent(layer, font, now)
            "image" ->
                WidgetImage(
                    images[layer.id],
                    layer.text.ifBlank { "Imagem" },
                    Modifier.fillMaxSize(),
                    scale = layer.imageScale,
                    zoom = layer.imageZoom,
                    offsetX = layer.imageOffsetX,
                    offsetY = layer.imageOffsetY,
                )
            "spacer" ->
                if (preview) Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = .2f)))
            "shape" ->
                Box(
                    Modifier.then(
                            if (layer.shape == "line") Modifier.fillMaxWidth().height(2.dp)
                            else Modifier.fillMaxSize()
                        )
                        .background(
                            color(layer.color),
                            if (layer.shape == "circle") CircleShape else RoundedCornerShape(12.dp),
                        )
                )
            "progress" ->
                LinearProgressIndicator(
                    progress = { layer.progress },
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    color = color(layer.color),
                )
            "battery_ring" -> {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    val side = min(size.width, size.height)
                    val origin =
                        androidx.compose.ui.geometry.Offset(
                            (size.width - side) / 2,
                            (size.height - side) / 2,
                        )
                    drawArc(
                        color(layer.color).copy(alpha = .15f),
                        -90f,
                        360f,
                        false,
                        origin,
                        androidx.compose.ui.geometry.Size(side, side),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(8f),
                    )
                    drawArc(
                        color(layer.color),
                        -90f,
                        battery * 3.6f,
                        false,
                        origin,
                        androidx.compose.ui.geometry.Size(side, side),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(8f),
                    )
                }
                Text("$battery%", color = color(layer.color), fontSize = layer.fontSize.sp)
            }
            "analog" ->
                Canvas(Modifier.fillMaxSize().padding(5.dp)) {
                    val radius = min(size.width, size.height) / 2
                    val c = center
                    val calendar =
                        java.util.Calendar.getInstance(
                                if (layer.clock.zone.isBlank()) java.util.TimeZone.getDefault()
                                else java.util.TimeZone.getTimeZone(layer.clock.zone)
                            )
                            .apply { timeInMillis = now }
                    drawCircle(color(layer.color).copy(alpha = .12f), radius, c)
                    fun hand(degrees: Float, length: Float, width: Float) {
                        val a = (degrees - 90) * PI.toFloat() / 180
                        drawLine(
                            color(layer.color),
                            c,
                            c +
                                androidx.compose.ui.geometry.Offset(
                                    cos(a) * radius * length,
                                    sin(a) * radius * length,
                                ),
                            width,
                            StrokeCap.Round,
                        )
                    }
                    if (layer.clock.marks)
                        repeat(12) { i ->
                            val a = i * PI.toFloat() / 6
                            drawCircle(
                                color(layer.color),
                                2f,
                                c +
                                    androidx.compose.ui.geometry.Offset(
                                        cos(a) * radius * .88f,
                                        sin(a) * radius * .88f,
                                    ),
                            )
                        }
                    hand(
                        calendar.get(java.util.Calendar.HOUR) * 30 +
                            calendar.get(java.util.Calendar.MINUTE) * .5f,
                        .5f,
                        5f,
                    )
                    hand(calendar.get(java.util.Calendar.MINUTE) * 6f, .75f, 3f)
                    if (layer.clock.seconds)
                        hand(calendar.get(java.util.Calendar.SECOND) * 6f, .8f, 1f)
                }
            "shortcut" -> {
                val bitmap by
                    produceState<ImageBitmap?>(null, layer.tapPackage) {
                        value =
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    ctx.packageManager
                                        .getApplicationIcon(layer.tapPackage!!)
                                        .toBitmap(128, 128)
                                        .asImageBitmap()
                                }
                                    .getOrNull()
                            }
                    }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bitmap == null)
                        Icon(
                            Icons.Outlined.Apps,
                            null,
                            tint = color(layer.color),
                            modifier = Modifier.size(32.dp),
                        )
                    bitmap?.let {
                        Image(it, layer.text, Modifier.weight(1f, false).aspectRatio(1f))
                    }
                    Text(
                        layer.text,
                        color = color(layer.color),
                        fontSize = layer.fontSize.sp,
                        maxLines = 1,
                        fontFamily = font,
                    )
                }
            }
            else ->
                Text(
                    text,
                    color = color(layer.color),
                    fontSize = layer.fontSize.sp,
                    fontFamily = font,
                    fontWeight = FontWeight(layer.weight),
                    maxLines = 6,
                    textAlign =
                        when (layer.alignment) {
                            "start" -> androidx.compose.ui.text.style.TextAlign.Start
                            "end" -> androidx.compose.ui.text.style.TextAlign.End
                            else -> androidx.compose.ui.text.style.TextAlign.Center
                        },
                    lineHeight = (layer.fontSize * 1.15f).sp,
                )
        }
    }
}

@Composable
fun ComposedWidget(
    widget: WidgetSpec,
    imagePath: String? = null,
    modifier: Modifier = Modifier,
    preview: Boolean = false,
    images: Map<String, String> = emptyMap(),
    fonts: Map<String, String> = emptyMap(),
) {
    val ctx = LocalContext.current
    val mapped =
        if (widget.kind == "image" && imagePath != null) images + (widget.id to imagePath)
        else images
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(widget.height.dp)
            .clip(RoundedCornerShape(widget.radius.dp))
            .then(
                if (!preview && widget.tapPackage != null)
                    Modifier.clickable { WidgetActions.launchPackage(ctx, widget.tapPackage) }
                else Modifier
            )
    ) {
        WidgetBackground(widget, mapped, Modifier.fillMaxSize())
        val width = maxWidth
        val height = maxHeight
        widget
            .displayLayers()
            .filter { it.visible }
            .sortedBy { it.zIndex }
            .forEach { l ->
                WidgetLayerContent(
                    l,
                    mapped,
                    preview,
                    Modifier.offset(width * l.x, height * l.y)
                        .size(width * l.width, height * l.height),
                    fonts,
                )
            }
    }
}
