package app.homely.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.homely.data.WidgetLayer
import app.homely.data.WidgetSpec

internal data class WidgetImageTarget(val widgetId: String, val layerId: String? = null)

internal data class WidgetImageCrop(
    val scale: String = "crop",
    val zoom: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
)

@Composable
internal fun WidgetImageCropDialog(
    widget: WidgetSpec,
    layer: WidgetLayer?,
    images: Map<String, String>,
    onDismiss: () -> Unit,
    onApply: (WidgetImageCrop) -> Unit,
) {
    var crop by
        remember(widget.id, layer?.id) {
            mutableStateOf(
                if (layer == null)
                    WidgetImageCrop(
                        widget.backgroundScale,
                        widget.backgroundZoom,
                        widget.backgroundOffsetX,
                        widget.backgroundOffsetY,
                    )
                else
                    WidgetImageCrop(
                        layer.imageScale,
                        layer.imageZoom,
                        layer.imageOffsetX,
                        layer.imageOffsetY,
                    )
            )
        }
    val imageReady = images.containsKey(layer?.id ?: widget.backgroundImageId)
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier =
                Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(.92f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (layer == null) "Enquadrar fundo" else "Enquadrar imagem",
                    style = MaterialTheme.typography.titleLarge,
                )
                BoxWithConstraints(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val ratio =
                        (widget.canvasWidth.toFloat() / widget.height) *
                            (layer?.let { it.width / it.height } ?: 1f)
                    val previewWidth = maxWidth.coerceAtMost(maxHeight * ratio)
                    Box(
                        Modifier.size(previewWidth, previewWidth / ratio)
                            .clip(RoundedCornerShape(if (layer == null) widget.radius.dp else 4.dp))
                    ) {
                        if (layer == null)
                            WidgetBackground(
                                widget.copy(
                                    backgroundScale = crop.scale,
                                    backgroundZoom = crop.zoom,
                                    backgroundOffsetX = crop.x,
                                    backgroundOffsetY = crop.y,
                                ),
                                images,
                                Modifier.fillMaxSize(),
                            )
                        else
                            WidgetLayerContent(
                                layer.copy(
                                    imageScale = crop.scale,
                                    imageZoom = crop.zoom,
                                    imageOffsetX = crop.x,
                                    imageOffsetY = crop.y,
                                ),
                                images,
                                true,
                                Modifier.fillMaxSize(),
                            )
                        if (!imageReady)
                            Text("Carregando imagem…", Modifier.align(Alignment.Center))
                    }
                }
                Column(
                    Modifier.weight(1.2f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Preencher", crop.scale == "crop") { crop = crop.copy(scale = "crop") }
                        Pill("Mostrar inteira", crop.scale == "fit") {
                            crop = crop.copy(scale = "fit")
                        }
                    }
                    Text(
                        "Zoom · ${(crop.zoom * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Slider(crop.zoom, { crop = crop.copy(zoom = it) }, valueRange = 1f..4f)
                    Text("Posição horizontal", style = MaterialTheme.typography.labelLarge)
                    Slider(crop.x, { crop = crop.copy(x = it) }, valueRange = -1f..1f)
                    Text("Posição vertical", style = MaterialTheme.typography.labelLarge)
                    Slider(crop.y, { crop = crop.copy(y = it) }, valueRange = -1f..1f)
                    TextButton({ crop = WidgetImageCrop() }) {
                        Icon(Icons.Outlined.RestartAlt, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Redefinir enquadramento")
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onDismiss) { Text("Cancelar") }
                    Button({ onApply(crop) }, enabled = imageReady) { Text("Aplicar recorte") }
                }
            }
        }
    }
}

@Composable
internal fun WidgetItemLibrary(enabled: Boolean, onAdd: (String) -> Unit) {
    val groups =
        listOf(
            "Relógios" to
                listOf(
                    "clock",
                    "analog",
                    "date",
                    "calendar",
                    "weather",
                    "battery",
                    "battery_ring",
                    "countdown",
                ),
            "Aparelho" to
                listOf(
                    "ram",
                    "storage",
                    "wifi",
                    "bluetooth",
                    "lan",
                    "flashlight",
                    "volume",
                    "signal",
                    "cpu",
                ),
            "Indicadores" to listOf("number", "icon", "ring", "bar", "indicator"),
            "Atalhos" to listOf("note", "shortcut", "phone", "progress"),
            "Visual" to listOf("image", "text", "title", "shape", "spacer"),
        )
    var category by remember { mutableStateOf(groups.first().first) }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(groups) { (title, _) ->
            FilterChip(category == title, { category = title }, { Text(title) })
        }
    }
    val types = groups.first { it.first == category }.second
    types.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { type ->
                Surface(
                    onClick = { onAdd(type) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f),
                ) {
                    Column(
                        Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            widgetItemIcon(type),
                            null,
                            Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            widgetKinds.first { it.first == type }.second,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    if (!enabled)
        Text(
            "Você chegou ao limite de 16 itens. Remova uma camada para adicionar outra.",
            style = MaterialTheme.typography.bodySmall,
        )
}

@Composable
internal fun WidgetTemplateGallery(enabled: Boolean, onChoose: (WidgetSpec) -> Unit) {
    val templates = remember { widgetTemplates() }
    templates.forEach { (_, template) ->
        Surface(
            onClick = { onChoose(freshWidgetTemplate(template)) },
            enabled = enabled,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    widgetItemIcon(template.layers.first().type),
                    null,
                    Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleSmall)
                }
                Icon(Icons.Outlined.Add, "Criar ${template.name}")
            }
        }
    }
    if (!enabled) Text("Limite de 12 widgets por tema.", style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun WidgetDimensionField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    LaunchedEffect(value, focused) { if (!focused) input = value.toString() }
    fun commit() {
        val next = input.toIntOrNull()?.coerceIn(64, 800) ?: value
        input = next.toString()
        if (next != value) onChange(next)
    }
    OutlinedTextField(
        input,
        { text ->
            input = text.filter(Char::isDigit).take(3)
            input.toIntOrNull()?.takeIf { it in 64..800 }?.let(onChange)
        },
        modifier.onFocusChanged {
            if (focused && !it.isFocused) commit()
            focused = it.isFocused
        },
        label = { Text(label) },
        suffix = { Text("dp") },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    commit()
                    focus.clearFocus()
                }
            ),
    )
}

private fun freshWidgetTemplate(template: WidgetSpec) =
    template.copy(
        id = java.util.UUID.randomUUID().toString(),
        layers = template.layers.map { it.copy(id = java.util.UUID.randomUUID().toString()) },
    )

internal fun widgetTemplates(): List<Pair<String, WidgetSpec>> =
    listOf(
        "Indicadores ao vivo e controles em cartões rosa" to pinkControlWidget(),
        "Hora, data e bateria em uma composição leve" to
            WidgetSpec(
                name = "Essencial",
                background = "#171625",
                height = 180,
                layers =
                    listOf(
                        WidgetLayer(
                            type = "clock",
                            x = .06f,
                            y = .04f,
                            width = .88f,
                            height = .51f,
                            fontSize = 44,
                            alignment = "start",
                        ),
                        WidgetLayer(
                            type = "date",
                            x = .06f,
                            y = .56f,
                            width = .88f,
                            height = .18f,
                            color = "#D7CDEB",
                            fontSize = 13,
                            alignment = "start",
                            zIndex = 1,
                        ),
                        WidgetLayer(
                            type = "battery",
                            x = .06f,
                            y = .78f,
                            width = .88f,
                            height = .16f,
                            color = "#B8A2FF",
                            fontSize = 12,
                            alignment = "start",
                            zIndex = 2,
                        ),
                    ),
            ),
        "Mês atual e data que acompanham o seu dia" to
            WidgetSpec(
                name = "Calendário",
                background = "#E9E2D5",
                foreground = "#322F29",
                columns = 4,
                rows = 4,
                height = 320,
                layers =
                    listOf(
                        WidgetLayer(
                            type = "calendar",
                            x = .04f,
                            y = .04f,
                            width = .92f,
                            height = .77f,
                            fontSize = 14,
                            color = "#322F29",
                        ),
                        WidgetLayer(
                            type = "date",
                            x = .04f,
                            y = .83f,
                            width = .92f,
                            height = .13f,
                            fontSize = 12,
                            color = "#625647",
                            zIndex = 1,
                        ),
                    ),
            ),
        "Um relógio analógico com data e carga" to
            WidgetSpec(
                name = "Tempo e energia",
                background = "#163C35",
                height = 200,
                layers =
                    listOf(
                        WidgetLayer(
                            type = "analog",
                            x = .03f,
                            y = .08f,
                            width = .42f,
                            height = .84f,
                            color = "#D7E9CF",
                        ),
                        WidgetLayer(
                            type = "date",
                            x = .5f,
                            y = .12f,
                            width = .45f,
                            height = .26f,
                            fontSize = 12,
                            color = "#D7E9CF",
                            zIndex = 1,
                        ),
                        WidgetLayer(
                            type = "battery_ring",
                            x = .58f,
                            y = .43f,
                            width = .28f,
                            height = .48f,
                            fontSize = 18,
                            color = "#A8D3B0",
                            zIndex = 2,
                        ),
                    ),
            ),
        "Anote uma intenção e acompanhe a hora" to
            WidgetSpec(
                name = "Meu foco",
                background = "#E2D8F7",
                foreground = "#332B46",
                height = 180,
                layers =
                    listOf(
                        WidgetLayer(
                            type = "clock",
                            x = .06f,
                            y = .05f,
                            width = .88f,
                            height = .36f,
                            fontSize = 28,
                            color = "#332B46",
                            alignment = "start",
                        ),
                        WidgetLayer(
                            type = "note",
                            text = "Uma coisa de cada vez.",
                            x = .06f,
                            y = .46f,
                            width = .88f,
                            height = .46f,
                            fontSize = 16,
                            color = "#514365",
                            alignment = "start",
                            zIndex = 1,
                        ),
                    ),
            ),
    )
