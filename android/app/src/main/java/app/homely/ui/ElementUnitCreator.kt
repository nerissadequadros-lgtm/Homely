package app.homely.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.homely.data.*
import app.homely.platform.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

@Composable
fun ElementCreatorScreen(vm: HomelyViewModel, state: HomelyState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("unit-element-draft", 0) }
    val original = remember {
        runCatching { vm.repo.json.decodeFromString<SavedWidget>(prefs.getString("draft", "")!!) }
            .getOrNull()
    }
    var layer by remember {
        mutableStateOf(
            original?.spec?.layers?.singleOrNull()?.takeIf { it.components.isEmpty() }
                ?: unitElement("wifi")
        )
    }
    var name by remember { mutableStateOf(original?.spec?.name ?: "Meu elemento") }
    var images by remember { mutableStateOf(original?.images ?: emptyMap()) }
    val fonts = original?.fonts.orEmpty() + state.draft.fontFiles
    var step by rememberSaveable { mutableIntStateOf(0) }
    var appearance by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var crop by remember { mutableStateOf(false) }
    var appPicker by remember { mutableStateOf(false) }
    var imageKey by remember { mutableStateOf<String?>(null) }
    var previewState by remember { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val id = imageKey
            if (uri != null && id != null)
                vm.action {
                    images = images + (id to vm.importImage(uri))
                    if (id == layer.id) layer = layer.copy(type = "image", imageAssetId = null)
                }
            else if (id != null && id !in images) {
                layer =
                    layer.copy(
                        states =
                            layer.states.map {
                                if (it.imageId == id) it.copy(imageId = null) else it
                            }
                    )
            }
        }
    fun image(id: String) {
        imageKey = id
        picker.launch("image/*")
    }
    LaunchedEffect(layer, name, images, fonts) {
        prefs
            .edit()
            .putString(
                "draft",
                vm.repo.json.encodeToString(
                    SavedWidget(WidgetSpec(name = name, layers = listOf(layer)), images, fonts)
                ),
            )
            .apply()
    }
    val steps = listOf("Base", "Estados", "Ação", "Animação", "Salvar")
    Column(Modifier.fillMaxSize().testTag("element-unit-editor")) {
        BuilderChoices(
            step.toString(),
            steps.mapIndexed { i, label -> i.toString() to "${i + 1} $label" },
        ) {
            step = it.toInt()
            previewState = null
        }
        val shown =
            previewState
                ?.let { id -> layer.states.find { it.id == id } }
                ?.let { s -> layer.copy(states = listOf(s.copy(condition = "always"))) } ?: layer
        WidgetCanvas(
            WidgetSpec(
                name = name,
                id = layer.id,
                canvasWidth = 320,
                height = 220,
                layers = listOf(shown),
                background = "#171625",
            ),
            images,
            fonts,
            layer.id,
            {},
            { layer = it.copy(states = layer.states) },
            Modifier.fillMaxWidth().height(214.dp).testTag("element-unit-canvas"),
        )
        val scroll = rememberScrollState()
        LaunchedEffect(step) { scroll.scrollTo(0) }
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                0 -> {
                    BuilderChoices(
                        layer.source().takeUnless { it == "none" } ?: layer.type,
                        listOf(
                            "wifi" to "Wi-Fi",
                            "battery" to "Bateria",
                            "ram" to "RAM",
                            "cpu" to "CPU do Homely",
                            "clock" to "Hora",
                            "calendar" to "Calendário",
                            "bluetooth" to "Bluetooth",
                            "flashlight" to "Lanterna",
                            "storage" to "Armazenamento",
                            "icon" to "Símbolo",
                            "text" to "Texto",
                        ),
                    ) {
                        layer =
                            unitElement(it)
                                .copy(
                                    id = layer.id,
                                    x = layer.x,
                                    y = layer.y,
                                    width = layer.width,
                                    height = layer.height,
                                )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton({ image(layer.id) }, Modifier.weight(1f)) {
                            Text("Escolher imagem")
                        }
                        IconButton({ appearance = true }) {
                            Icon(Icons.Outlined.Edit, "Editar cor e aparência")
                        }
                    }
                    if (layer.type == "image")
                        TextButton({ crop = true }) { Text("Recortar imagem") }
                    BuilderSlider("Largura", "", (layer.width * 100).toInt(), 10..100, "%") {
                        layer =
                            layer.copy(width = it / 100f, x = layer.x.coerceAtMost(1 - it / 100f))
                    }
                    BuilderSlider("Altura", "", (layer.height * 100).toInt(), 10..100, "%") {
                        layer =
                            layer.copy(height = it / 100f, y = layer.y.coerceAtMost(1 - it / 100f))
                    }
                }
                1 -> {
                    BuilderChoices(
                        previewState ?: "base",
                        listOf("base" to "Padrão") + layer.states.map { it.id to it.name },
                    ) {
                        previewState = it.takeUnless { id -> id == "base" }
                    }
                    val selected = layer.states.find { it.id == previewState }
                    if (selected == null) {
                        StudioColorPicker(layer.color, listOf("#FFFFFF", "#7965FF", "#EA8DAB")) {
                            layer = layer.copy(color = it)
                        }
                    } else {
                        fun change(s: ElementState) {
                            layer =
                                layer.copy(
                                    states = layer.states.map { if (it.id == s.id) s else it }
                                )
                        }
                        Field(
                            selected.name,
                            { change(selected.copy(name = it.take(60))) },
                            "Nome do estado",
                        )
                        BuilderChoices(
                            selected.condition,
                            listOf(
                                "pressed" to "Pressionado",
                                "disabled" to "Desativado",
                                "enabled" to "Ativo",
                                "connected" to "Conectado",
                                "disconnected" to "Desconectado",
                                "low" to "Abaixo de",
                                "high" to "A partir de",
                                "touched" to "Tocado",
                                "toggled" to "Alternado",
                                "unavailable" to "Indisponível",
                            ),
                        ) {
                            change(selected.copy(condition = it))
                        }
                        if (selected.condition in listOf("low", "high"))
                            BuilderSlider("Limite", "", selected.threshold.toInt(), 0..100, "%") {
                                change(selected.copy(threshold = it.toFloat()))
                            }
                        StudioColorPicker(
                            selected.color ?: layer.color,
                            listOf("#FFFFFF", "#7965FF", "#EA8DAB"),
                        ) {
                            change(selected.copy(color = it))
                        }
                        BuilderSlider(
                            "Opacidade",
                            "",
                            (selected.opacity * 100).toInt(),
                            0..100,
                            "%",
                        ) {
                            change(selected.copy(opacity = it / 100f))
                        }
                        OutlinedButton({
                            val id = selected.imageId ?: UUID.randomUUID().toString()
                            change(selected.copy(imageId = id))
                            image(id)
                        }) {
                            Text("Imagem do estado")
                        }
                        if (selected.imageId != null)
                            TextButton({
                                change(selected.copy(imageId = null, imageAssetId = null))
                            }) {
                                Text("Remover imagem")
                            }
                        TextButton({
                            layer = layer.copy(states = layer.states - selected)
                            previewState = null
                        }) {
                            Text("Remover estado")
                        }
                    }
                    OutlinedButton(
                        {
                            val s =
                                ElementState(
                                    name = "Estado ${layer.states.size + 1}",
                                    condition = "pressed",
                                )
                            layer = layer.copy(states = layer.states + s)
                            previewState = s.id
                        },
                        enabled = layer.states.size < 12,
                    ) {
                        Text("Adicionar estado")
                    }
                }
                2 -> {
                    Text("Ao tocar", style = MaterialTheme.typography.labelLarge)
                    BuilderChoices(
                        layer.tapAction,
                        elementActions.map { it to elementActionLabel(it) },
                    ) {
                        layer = layer.copy(tapAction = it)
                    }
                    if (layer.tapAction == "app")
                        OutlinedButton({ appPicker = true }) { Text("Escolher aplicativo") }
                    Text("Ao manter pressionado", style = MaterialTheme.typography.labelLarge)
                    BuilderChoices(
                        layer.longPressAction,
                        elementActions.map { it to elementActionLabel(it) },
                    ) {
                        layer = layer.copy(longPressAction = it)
                    }
                    if (layer.longPressAction == "app" && layer.tapAction != "app")
                        OutlinedButton({ appPicker = true }) { Text("Escolher aplicativo") }
                }
                3 -> {
                    BuilderChoices(
                        layer.animation,
                        elementAnimations.map { it to animationLabel(it) },
                    ) {
                        layer = layer.copy(animation = it)
                    }
                    if (layer.animation != "none")
                        BuilderSlider("Duração", "", layer.animationDuration, 80..1000, " ms") {
                            layer = layer.copy(animationDuration = it)
                        }
                    Box(Modifier.fillMaxWidth().height(100.dp)) {
                        WidgetLayerContent(layer, images, true, Modifier.fillMaxSize(), fonts)
                    }
                }
                4 -> {
                    Field(name, { name = it.take(60) }, "Nome do elemento")
                    Button(
                        { saving = true },
                        Modifier.fillMaxWidth(),
                        enabled = name.isNotBlank() && !state.busy,
                    ) {
                        Text("Salvar elemento")
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step > 0)
                TextButton({
                    step--
                    previewState = null
                }) {
                    Text("Anterior")
                }
            Spacer(Modifier.weight(1f))
            if (step < steps.lastIndex)
                Button(
                    {
                        step++
                        previewState = null
                    },
                    Modifier.testTag("element-next"),
                ) {
                    Text("Continuar")
                }
        }
    }
    if (crop)
        WidgetImageCropDialog(
            WidgetSpec(canvasWidth = 320, height = 220),
            layer,
            images,
            { crop = false },
        ) { value ->
            layer =
                layer.copy(
                    imageScale = value.scale,
                    imageZoom = value.zoom,
                    imageOffsetX = value.x,
                    imageOffsetY = value.y,
                )
            crop = false
        }
    if (appearance)
        AlertDialog(
            onDismissRequest = { appearance = false },
            title = { Text("Aparência") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ElementProperties(
                        layer,
                        images,
                        { layer = it.copy(components = emptyList()) },
                        ::image,
                        vm,
                        showTabs = false,
                    )
                }
            },
            confirmButton = { TextButton({ appearance = false }) { Text("Concluir") } },
        )
    if (saving)
        SaveCreationDialog("element", name, { saving = false }) { folder, label ->
            saving = false
            vm.action {
                withContext(Dispatchers.IO) {
                    val token =
                        ElementLibrary.save(
                            ctx,
                            label,
                            layer.copy(components = emptyList()),
                            images,
                            fonts,
                        )
                    CreationFolders.register(ctx, "element", folder, label, token)
                }
                vm.message("Elemento salvo em $folder.")
                onBack()
            }
        }
    if (appPicker) {
        val apps by
            produceState<List<InstalledApp>>(emptyList()) {
                value = withContext(Dispatchers.IO) { NativeActions.apps(ctx) }
            }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { appPicker = false },
            title = { Text("Aplicativo") },
            text = {
                Column {
                    Field(query, { query = it }, "Buscar")
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(apps.filter { it.label.contains(query, true) }) { app ->
                            TextButton({
                                layer = layer.copy(tapPackage = app.packageName)
                                appPicker = false
                            }) {
                                Text(app.label)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ appPicker = false }) { Text("Cancelar") } },
        )
    }
}

internal fun unitElement(kind: String): WidgetLayer =
    WidgetLayer(
        type = kind,
        x = .2f,
        y = .2f,
        width = .6f,
        height = .6f,
        text = if (kind == "text") "Texto" else "",
        fontSize = 32,
        color = "#FFFFFF",
        animation = "scale",
        states =
            if (kind in listOf("wifi", "bluetooth", "flashlight"))
                listOf(
                    ElementState(name = "Ativo", condition = "enabled", color = "#AFA0FF"),
                    ElementState(name = "Desativado", condition = "disabled", opacity = .45f),
                )
            else emptyList(),
    )
