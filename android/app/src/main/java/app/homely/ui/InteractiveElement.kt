package app.homely.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.homely.data.*
import app.homely.platform.ElementSystem
import kotlin.math.min
import kotlinx.coroutines.delay

@Composable
fun WidgetLayerContent(
    layer: WidgetLayer,
    images: Map<String, String>,
    preview: Boolean = false,
    modifier: Modifier = Modifier,
    fonts: Map<String, String> = emptyMap(),
) {
    if (!layer.interactive()) BaseWidgetLayerContent(layer, images, preview, modifier, fonts)
    else InteractiveElement(layer, images, preview, modifier, fonts)
}

@Composable
private fun InteractiveElement(
    layer: WidgetLayer,
    images: Map<String, String>,
    preview: Boolean,
    modifier: Modifier,
    fonts: Map<String, String>,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val revision by ElementSystem.revision.collectAsState()
    var reading by
        remember(layer.source()) { mutableStateOf(ElementSystem.read(ctx, layer.source())) }
    LaunchedEffect(layer.source(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                reading = ElementSystem.read(ctx, layer.source())
                delay(2000)
            }
        }
    }
    LaunchedEffect(revision) { reading = ElementSystem.read(ctx, layer.source()) }
    var touched by remember(layer.id) { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var simulatedToggle by remember(layer.id) { mutableStateOf(false) }
    var tapSequence by remember(layer.id) { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val toggled =
        if (preview) simulatedToggle else revision.let { ElementSystem.toggled(ctx, layer.id) }
    val state = layer.activeState(reading, pressed, toggled, touched)
    val effect = state?.animation?.takeUnless { it == "inherit" } ?: layer.animation
    val duration = layer.animationDuration
    val animatedColor by
        animateColorAsState(
            color(state?.color ?: layer.color),
            tween(duration),
            label = "element-color",
        )
    val alpha by
        animateFloatAsState(
            (state?.opacity ?: 1f) * layer.opacity,
            tween(duration),
            label = "element-opacity",
        )
    val scale by
        animateFloatAsState(
            (state?.scale ?: 1f) * (if (pressed && effect == "scale") .9f else 1f),
            tween(duration),
            label = "element-scale",
        )
    val rotation by
        animateFloatAsState(
            layer.rotation +
                (state?.rotation ?: 0f) +
                (if (effect == "rotate") tapSequence * 360f else 0f),
            tween(duration),
            label = "element-rotation",
        )
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(tapSequence) {
        if (tapSequence > 0) {
            touched = true
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(duration.coerceAtLeast(1)))
            delay(350)
            touched = false
        }
    }
    val font =
        remember(layer.fontId, fonts, layer.fontFamily) {
            layer.fontId
                ?.let { fonts[it] }
                ?.let {
                    runCatching {
                        androidx.compose.ui.text.font.FontFamily(
                            androidx.compose.ui.text.font.Font(java.io.File(it))
                        )
                    }
                        .getOrNull()
                } ?: studioFont(layer.fontFamily)
        }
    val currentLayer by rememberUpdatedState(layer)
    val currentState by rememberUpdatedState(state)
    fun perform(action: String) {
        tapSequence++
        if (action == "none") return
        if (preview) {
            if (action == "toggle_state") simulatedToggle = !simulatedToggle
            else if (action != "animate") feedback = "Ação: ${elementActionLabel(action)}"
        } else ElementSystem.execute(ctx, action, layer)
    }
    val dispatch by rememberUpdatedState<(String) -> Unit>(::perform)
    val shape = RoundedCornerShape(layer.elementRadius.dp)
    Box(
        modifier
            .testTag("element-${layer.id}")
            .graphicsLayer {
                this.alpha = alpha * (if (effect == "fade") 1f - pulse.value * .5f else 1f)
                scaleX = scale * (if (effect == "scale") 1f - pulse.value * .1f else 1f)
                scaleY = scaleX
                rotationZ = rotation
                translationX = if (effect == "slide") pulse.value * 12.dp.toPx() else 0f
            }
            .clip(shape)
            .background(
                state?.background?.let(::color)
                    ?: layer.elementBackground?.let(::color)
                    ?: Color.Transparent
            )
            .border(layer.elementBorderWidth.dp, color(layer.elementBorderColor), shape)
            .then(
                if (
                    layer.tapAction != "none" ||
                        layer.longPressAction != "none" ||
                        layer.touchZones.any { it.action != "none" } ||
                        layer.states.any { it.condition in listOf("pressed", "touched") }
                )
                    Modifier.pointerInput(layer.id) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    pressed = false
                                }
                            },
                            onLongPress = { dispatch(currentLayer.longPressAction) },
                            onTap = { point ->
                                val x = point.x / size.width
                                val y = point.y / size.height
                                val zone =
                                    currentLayer.touchZones.firstOrNull {
                                        x >= it.x &&
                                            y >= it.y &&
                                            x <= it.x + it.width &&
                                            y <= it.y + it.height
                                    }
                                dispatch(
                                    zone?.action
                                        ?: currentState?.action?.takeUnless { it == "inherit" }
                                        ?: currentLayer.tapAction
                                )
                            },
                        )
                    }
                else Modifier
            )
            .padding(layer.elementPadding.dp),
        contentAlignment =
            when (layer.alignment) {
                "start" -> Alignment.CenterStart
                "end" -> Alignment.CenterEnd
                else -> Alignment.Center
            },
    ) {
        AnimatedContent(
            targetState = state?.id,
            transitionSpec = {
                when (effect) {
                    "fade" -> fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                    "slide" ->
                        slideInHorizontally(tween(duration)) { it } togetherWith
                            slideOutHorizontally(tween(duration)) { -it }
                    "scale" -> scaleIn(tween(duration)) togetherWith scaleOut(tween(duration))
                    else -> EnterTransition.None togetherWith ExitTransition.None
                }
            },
            label = "element-state",
        ) { activeId ->
            val visual = layer.states.firstOrNull { it.id == activeId }
            val imageId = visual?.imageId
            val composition = visual?.components ?: layer.components
            if (composition.isNotEmpty() || layer.type == "element") {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val width = maxWidth
                    val height = maxHeight
                    composition
                        .filter { it.visible }
                        .sortedBy { it.zIndex }
                        .forEach { part ->
                            WidgetLayerContent(
                                part,
                                images,
                                preview,
                                Modifier.offset(width * part.x, height * part.y)
                                    .size(width * part.width, height * part.height),
                                fonts,
                            )
                        }
                }
            } else if (imageId != null)
                WidgetImage(
                    images[imageId],
                    visual.name,
                    Modifier.fillMaxSize(),
                    layer.imageScale,
                    layer.imageZoom,
                    layer.imageOffsetX,
                    layer.imageOffsetY,
                )
            else {
                val percentage =
                    reading.value
                        ?.toInt()
                        ?.toString()
                        ?.plus(if (layer.numberFormat == "percent") "%" else "") ?: "—"
                val numeric =
                    layer.type in
                        listOf("number", "battery", "ram", "storage", "volume", "signal", "cpu")
                val ring = layer.type in listOf("ring", "battery_ring")
                val bar = layer.type == "bar"
                val icon =
                    layer.type in
                        listOf("icon", "indicator", "wifi", "bluetooth", "lan", "flashlight")
                val text =
                    visual?.text
                        ?: if (numeric || ring || bar)
                            (if (layer.source() == "none")
                                layer.text.ifBlank {
                                    (layer.progress * 100).toInt().toString() + "%"
                                }
                            else percentage)
                        else layer.text
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment =
                        when (layer.alignment) {
                            "start" -> Alignment.CenterStart
                            "end" -> Alignment.CenterEnd
                            else -> Alignment.Center
                        },
                ) {
                    if (ring || bar)
                        Canvas(Modifier.fillMaxSize()) {
                            val progress =
                                (reading.value?.div(100)
                                        ?: if (layer.source() == "none") layer.progress else 0f)
                                    .coerceIn(0f, 1f)
                            val stroke =
                                layer.ringWidth.dp
                                    .toPx()
                                    .coerceAtMost(min(size.width, size.height) / 3)
                            if (ring) {
                                val side = (min(size.width, size.height) - stroke).coerceAtLeast(1f)
                                val start =
                                    Offset((size.width - side) / 2, (size.height - side) / 2)
                                drawArc(
                                    color(layer.trackColor).copy(alpha = layer.trackOpacity),
                                    -90f,
                                    360f,
                                    false,
                                    start,
                                    Size(side, side),
                                    style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(
                                            stroke,
                                            cap = StrokeCap.Round,
                                        ),
                                )
                                drawArc(
                                    animatedColor,
                                    -90f,
                                    360f * progress,
                                    false,
                                    start,
                                    Size(side, side),
                                    style =
                                        androidx.compose.ui.graphics.drawscope.Stroke(
                                            stroke,
                                            cap = StrokeCap.Round,
                                        ),
                                )
                            } else {
                                drawRoundRect(
                                    color(layer.trackColor).copy(alpha = layer.trackOpacity),
                                    cornerRadius =
                                        androidx.compose.ui.geometry.CornerRadius(
                                            layer.elementRadius.dp.toPx()
                                        ),
                                )
                                drawRoundRect(
                                    animatedColor,
                                    size = Size(size.width * progress, size.height),
                                    cornerRadius =
                                        androidx.compose.ui.geometry.CornerRadius(
                                            layer.elementRadius.dp.toPx()
                                        ),
                                )
                            }
                        }
                    if (icon) {
                        if (layer.iconName != "auto")
                            Text(
                                layer.iconName,
                                color = animatedColor,
                                fontSize = layer.fontSize.sp,
                            )
                        else
                            Icon(
                                when (layer.source()) {
                                    "wifi",
                                    "signal" -> Icons.Outlined.Wifi
                                    "bluetooth" -> Icons.Outlined.Bluetooth
                                    "lan" -> Icons.Outlined.Lan
                                    "flashlight" -> Icons.Outlined.FlashlightOn
                                    "battery" -> Icons.Outlined.BatteryFull
                                    "volume" -> Icons.Outlined.VolumeUp
                                    else -> Icons.Outlined.Circle
                                },
                                reading.label,
                                Modifier.size((layer.fontSize * 2).dp),
                                tint = animatedColor,
                            )
                    } else if (numeric || ring || bar) {
                        if (layer.showValue)
                            Text(
                                text,
                                color = animatedColor,
                                fontSize = layer.fontSize.sp,
                                fontWeight = FontWeight(layer.weight),
                                fontFamily = font,
                            )
                    } else
                        BaseWidgetLayerContent(
                            layer.copy(
                                color = String.format("#%06X", animatedColor.toArgb() and 0xFFFFFF),
                                text = text,
                                opacity = 1f,
                                tapAction = "none",
                            ),
                            images,
                            true,
                            Modifier.fillMaxSize(),
                            fonts,
                        )
                }
            }
        }
        feedback?.let { message ->
            LaunchedEffect(message, tapSequence) {
                delay(1300)
                feedback = null
            }
            Surface(
                Modifier.align(Alignment.BottomCenter),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .95f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(message, Modifier.padding(4.dp), fontSize = 10.sp)
            }
        }
    }
}

fun elementActionLabel(action: String) =
    when (action) {
        "none" -> "Nenhuma"
        "auto" -> "Ação do elemento"
        "app" -> "Abrir app"
        "toggle_state" -> "Alternar estado"
        "animate" -> "Animar"
        "wifi" -> "Painel Wi-Fi"
        "bluetooth" -> "Ajustes Bluetooth"
        "flashlight" -> "Alternar lanterna"
        "volume" -> "Painel de volume"
        "volume_up" -> "Aumentar volume"
        "volume_down" -> "Diminuir volume"
        "alarm" -> "Abrir relógio"
        "calendar" -> "Abrir calendário"
        "storage" -> "Armazenamento"
        else -> "Configurações"
    }
