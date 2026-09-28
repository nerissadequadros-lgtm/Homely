package app.homely.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.homely.data.*
import app.homely.platform.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetCreatorScreen(vm: HomelyViewModel, state: HomelyState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var widgetId by remember { mutableStateOf(state.draft.manifest.widgets.firstOrNull()?.id) }
    var selected by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableIntStateOf(0) }
    var testing by remember { mutableStateOf(false) }
    var inspector by remember { mutableStateOf(false) }
    var inspectorTab by remember { mutableStateOf("Estilo") }
    var saving by remember { mutableStateOf(false) }
    var floating by remember { mutableStateOf(false) }
    var appPicker by remember { mutableStateOf(false) }
    var elementLibrary by remember { mutableStateOf(false) }
    var widgetLibrary by remember { mutableStateOf(false) }
    var iconLibrary by remember { mutableStateOf(false) }
    var cropTarget by remember { mutableStateOf<WidgetImageTarget?>(null) }
    var pendingImage by remember { mutableStateOf<WidgetImageTarget?>(null) }
    var elementImageKey by remember { mutableStateOf<String?>(null) }
    var pendingElementSave by remember { mutableStateOf<WidgetLayer?>(null) }
    val widgets = state.draft.manifest.widgets
    val w = widgets.firstOrNull { it.id == widgetId }
    val currentLayer = w?.layers?.firstOrNull { it.id == selected }
    val palette = state.draft.manifest.palette
    val colors =
        listOf(palette.accent, palette.secondary, palette.surface, palette.background, palette.text)

    fun save(value: WidgetSpec) {
        vm.editDraft { d ->
            val existing = d.manifest.widgets.any { it.id == value.id }
            if (!existing && d.manifest.widgets.size >= 12) d
            else
                d.copy(
                    manifest =
                        d.manifest.copy(
                            widgets =
                                if (existing)
                                    d.manifest.widgets.map { if (it.id == value.id) value else it }
                                else d.manifest.widgets + value
                        )
                )
        }
        widgetId = value.id
    }
    fun editLayer(value: WidgetLayer) {
        val current =
            vm.state.value.draft.manifest.widgets.firstOrNull { it.id == widgetId } ?: return
        save(current.copy(layers = current.layers.map { if (it.id == value.id) value else it }))
    }
    fun add(type: String) {
        if (w == null || w.layers.size >= 16) return
        val wide = type in listOf("calendar", "weather")
        val item =
            WidgetLayer(
                type = type,
                text = if (type in listOf("text", "title", "note")) "Texto" else "",
                x = .1f,
                y = .1f,
                width = if (wide) .8f else .5f,
                height = if (wide) .8f else .5f,
                color = w.foreground,
                zIndex = w.layers.size,
                fontSize = if (type in listOf("clock", "title")) 28 else 16,
                targetTime = if (type == "countdown") System.currentTimeMillis() + 3600000 else 0,
            )
        save(w.copy(kind = "custom", layers = w.layers + item))
        selected = item.id
        testing = false
        if (type == "shortcut") appPicker = true
    }
    LaunchedEffect(w?.id) {
        if (w != null && w.kind != "custom" && w.layers.isEmpty())
            save(w.copy(kind = "custom", layers = w.displayLayers()))
    }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val target = pendingImage
            pendingImage = null
            val widget =
                vm.state.value.draft.manifest.widgets.firstOrNull { it.id == target?.widgetId }
            if (uri != null && target != null && widget != null) {
                val key = target.layerId ?: widget.backgroundImageId ?: UUID.randomUUID().toString()
                save(
                    if (target.layerId == null)
                        widget.copy(backgroundImageId = key, backgroundImageAssetId = null)
                    else
                        widget.copy(
                            layers =
                                widget.layers.map {
                                    if (it.id == key) it.copy(imageAssetId = null) else it
                                }
                        )
                )
                vm.widgetImage(key, uri)
                cropTarget = target
            }
        }
    val elementImagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val key = elementImageKey
            elementImageKey = null
            if (uri != null && key != null) vm.widgetImage(key, uri)
            else if (
                key != null && key !in vm.state.value.draft.widgetImages && currentLayer != null
            )
                editLayer(
                    currentLayer.mapTree { l ->
                        l.copy(
                            states =
                                l.states.map {
                                    if (it.imageId == key)
                                        it.copy(imageId = null, imageAssetId = null)
                                    else it
                                }
                        )
                    }
                )
        }
    val importWidget =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.action {
                    require(vm.state.value.draft.manifest.widgets.size < 12) {
                        "Limite de 12 widgets por tema."
                    }
                    val spec =
                        withContext(Dispatchers.IO) {
                            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                                val bytes = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(4096)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    require(bytes.size() + count <= 65536) {
                                        "Arquivo acima de 64 KB."
                                    }
                                    bytes.write(buffer, 0, count)
                                }
                                vm.repo.json
                                    .decodeFromString<WidgetSpec>(
                                        bytes.toByteArray().decodeToString()
                                    )
                                    .validate()
                            }
                        }
                    save(
                        spec.copy(
                            id = UUID.randomUUID().toString(),
                            backgroundImageId = null,
                            backgroundImageAssetId = null,
                            imageAssetId = null,
                            layers =
                                spec.layers.map {
                                    it.mapTree { l ->
                                        l.copy(
                                            id = UUID.randomUUID().toString(),
                                            imageAssetId = null,
                                            fontId = null,
                                            states =
                                                l.states.map { s ->
                                                    s.copy(
                                                        id = UUID.randomUUID().toString(),
                                                        imageId = null,
                                                        imageAssetId = null,
                                                    )
                                                },
                                        )
                                    }
                                },
                        )
                    )
                    selected = null
                    step = 1
                    vm.message(
                        "Configuração importada. Imagens e fontes são incluídas apenas no arquivo .homely."
                    )
                }
        }
    val exportWidget =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null && w != null)
                vm.action {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
                            it.write(vm.repo.json.encodeToString(w.validate()).toByteArray())
                        }
                    }
                    vm.message("Configuração exportada. Use .homely para incluir imagens e fontes.")
                }
        }
    val apps by
        produceState<List<InstalledApp>>(emptyList()) {
            value = withContext(Dispatchers.IO) { NativeActions.apps(ctx) }
        }

    Column(Modifier.fillMaxSize().testTag("widget-creator")) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("Base", "Itens", "Fundo", "Revisão").withIndex().toList()) { (index, title)
                ->
                FilterChip(
                    selected = step == index,
                    onClick = { if (index == 0 || w != null) step = index },
                    label = { Text("${index + 1} $title") },
                    modifier = Modifier.testTag("widget-step-$index"),
                )
            }
        }
        BoxWithConstraints(Modifier.weight(.43f).fillMaxWidth()) {
            if (w == null)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OutlinedButton({ save(WidgetSpec()) }, enabled = widgets.size < 12) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Widget em branco")
                    }
                }
            else if (testing) {
                val ratio = w.canvasWidth.toFloat() / w.height
                val previewWidth = (maxWidth - 32.dp).coerceAtMost((maxHeight - 16.dp) * ratio)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(previewWidth, previewWidth / ratio)) {
                        ComposedWidget(
                            w,
                            preview = true,
                            images = state.draft.widgetImages,
                            fonts = state.draft.fontFiles,
                        )
                    }
                }
            } else
                WidgetCanvas(
                    w,
                    state.draft.widgetImages,
                    state.draft.fontFiles,
                    selected,
                    { selected = it },
                    ::editLayer,
                    Modifier.fillMaxSize().testTag("widget-canvas"),
                )
        }
        if (w != null)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${w.canvasWidth} × ${w.height}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(vm::undoDraft, Modifier.size(36.dp), enabled = state.canUndo) {
                    Icon(Icons.Outlined.Undo, "Desfazer")
                }
                IconButton(vm::redoDraft, Modifier.size(36.dp), enabled = state.canRedo) {
                    Icon(Icons.Outlined.Redo, "Refazer")
                }
                if (currentLayer != null && !testing) {
                    IconButton({ inspector = true }, Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Edit, "Editar item")
                    }
                    IconButton(
                        {
                            save(w.copy(layers = w.layers.filterNot { it.id == selected }))
                            selected = null
                        },
                        Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, "Remover item")
                    }
                }
                FilterChip(
                    testing,
                    { testing = !testing },
                    { Text(if (testing) "Editar" else "Testar") },
                )
            }
        HorizontalDivider()
        Column(
            Modifier.weight(.57f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                0 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            {
                                save(WidgetSpec(name = "Widget ${widgets.size + 1}"))
                                selected = null
                            },
                            enabled = widgets.size < 12,
                        ) {
                            Text("Em branco")
                        }
                        OutlinedButton({ widgetLibrary = true }) { Text("Meus widgets") }
                        IconButton({ importWidget.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Outlined.FileOpen, "Importar configuração")
                        }
                    }
                    if (w != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WidgetDimensionField(
                                "Largura",
                                w.canvasWidth,
                                { save(w.copy(canvasWidth = it)) },
                                Modifier.weight(1f),
                            )
                            WidgetDimensionField(
                                "Altura",
                                w.height,
                                { save(w.copy(height = it)) },
                                Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                w.placement == "free",
                                { save(w.copy(placement = "free")) },
                                { Text("Livre") },
                            )
                            FilterChip(
                                w.placement == "grid",
                                { save(w.copy(placement = "grid")) },
                                { Text("Grade") },
                            )
                        }
                        if (w.placement == "grid")
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StudioNumber(
                                    "Colunas",
                                    w.gridColumns.toFloat(),
                                    { save(w.copy(gridColumns = it.toInt())) },
                                    2f..12f,
                                    modifier = Modifier.weight(1f),
                                )
                                StudioNumber(
                                    "Linhas",
                                    w.gridRows.toFloat(),
                                    { save(w.copy(gridRows = it.toInt())) },
                                    2f..12f,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                    }
                    WidgetTemplateGallery(widgets.size < 12) {
                        save(it)
                        selected = null
                        step = 1
                    }
                }
                1 ->
                    if (w != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                { iconLibrary = true },
                                Modifier.weight(1f),
                                enabled = w.layers.size < 16,
                            ) {
                                Text("Meus ícones")
                            }
                            OutlinedButton(
                                { elementLibrary = true },
                                Modifier.weight(1f),
                                enabled = w.layers.size < 16,
                            ) {
                                Text("Meus elementos")
                            }
                        }
                        WidgetItemLibrary(w.layers.size < 16) { type ->
                            add(type)
                            if (type == "image") {
                                pendingImage =
                                    WidgetImageTarget(
                                        w.id,
                                        vm.state.value.draft.manifest.widgets
                                            .first { it.id == w.id }
                                            .layers
                                            .last()
                                            .id,
                                    )
                                imagePicker.launch("image/*")
                            }
                        }
                        if (w.layers.isNotEmpty())
                            StudioSection("Camadas") {
                                w.layers
                                    .sortedByDescending { it.zIndex }
                                    .forEach { item ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            TextButton(
                                                { selected = item.id },
                                                Modifier.weight(1f),
                                            ) {
                                                Text(
                                                    item.text.ifBlank {
                                                        widgetKindLabel(item.type)
                                                    },
                                                    maxLines = 1,
                                                )
                                            }
                                            IconButton({
                                                editLayer(item.copy(visible = !item.visible))
                                            }) {
                                                Icon(
                                                    if (item.visible) Icons.Outlined.Visibility
                                                    else Icons.Outlined.VisibilityOff,
                                                    "Visibilidade",
                                                )
                                            }
                                            IconButton({
                                                selected = item.id
                                                inspector = true
                                            }) {
                                                Icon(
                                                    Icons.Outlined.Edit,
                                                    "Editar ${widgetKindLabel(item.type)}",
                                                )
                                            }
                                        }
                                    }
                            }
                    }
                2 ->
                    if (w != null) {
                        StudioColorPicker(w.background, colors) { save(w.copy(background = it)) }
                        OutlinedButton(
                            {
                                pendingImage = WidgetImageTarget(w.id)
                                imagePicker.launch("image/*")
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (w.backgroundImageId == null) "Adicionar imagem"
                                else "Trocar imagem"
                            )
                        }
                        if (w.backgroundImageId != null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    { cropTarget = WidgetImageTarget(w.id) },
                                    Modifier.weight(1f),
                                ) {
                                    Text("Recortar")
                                }
                                TextButton({
                                    save(
                                        w.copy(
                                            backgroundImageId = null,
                                            backgroundImageAssetId = null,
                                        )
                                    )
                                }) {
                                    Text("Remover")
                                }
                            }
                            StudioNumber(
                                "Opacidade",
                                w.backgroundOpacity * 100,
                                { save(w.copy(backgroundOpacity = it / 100)) },
                                0f..100f,
                            )
                            StudioNumber(
                                "Escurecer",
                                w.backgroundOverlay * 100,
                                { save(w.copy(backgroundOverlay = it / 100)) },
                                0f..100f,
                            )
                        }
                        StudioNumber(
                            "Cantos",
                            w.radius.toFloat(),
                            { save(w.copy(radius = it.toInt())) },
                            0f..48f,
                        )
                    }
                3 ->
                    if (w != null) {
                        Field(w.name, { save(w.copy(name = it.take(60))) }, "Nome")
                        Text(
                            "${w.layers.size} itens · ${w.canvasWidth} × ${w.height}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        OutlinedButton({ testing = !testing }, Modifier.fillMaxWidth()) {
                            Text(if (testing) "Voltar à edição" else "Testar interações")
                        }
                        StudioSection("Usar e exportar") {
                            OutlinedButton(
                                {
                                    vm.action {
                                        StudioWidgets.pin(
                                            ctx,
                                            w,
                                            state.draft.widgetImages,
                                            state.draft.fontFiles,
                                        )
                                    }
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("Adicionar à tela inicial")
                            }
                            OutlinedButton({ floating = true }, Modifier.fillMaxWidth()) {
                                Text("Widget flutuante")
                            }
                            OutlinedButton(
                                { exportWidget.launch(w.name + ".hwidget") },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("Exportar configuração")
                            }
                        }
                    }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton({ if (step == 0) onBack() else step-- }, Modifier.weight(1f)) {
                Text("Voltar")
            }
            Button(
                {
                    if (step < 3) {
                        step++
                        testing = false
                    } else saving = true
                },
                Modifier.weight(1f),
                enabled = w != null,
            ) {
                Text(if (step == 3) "Salvar widget" else "Continuar")
            }
        }
    }

    if (inspector && w != null && currentLayer != null)
        ModalBottomSheet(
            onDismissRequest = { inspector = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxHeight(.9f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        widgetKindLabel(currentLayer.type),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton({ editLayer(currentLayer.copy(locked = !currentLayer.locked)) }) {
                        Icon(
                            if (currentLayer.locked) Icons.Outlined.Lock
                            else Icons.Outlined.LockOpen,
                            "Bloqueio",
                        )
                    }
                    TextButton({ inspector = false }) { Text("Concluir") }
                }
                if (!currentLayer.locked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            inspectorTab == "Estilo",
                            { inspectorTab = "Estilo" },
                            { Text("Estilo") },
                        )
                        FilterChip(
                            inspectorTab == "Posição",
                            { inspectorTab = "Posição" },
                            { Text("Posição") },
                        )
                    }
                    if (inspectorTab == "Posição") {
                        WidgetLayerPlacement(currentLayer, ::editLayer)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                {
                                    val ordered = w.layers.sortedBy { it.zIndex }.toMutableList()
                                    val index = ordered.indexOfFirst { it.id == currentLayer.id }
                                    if (index > 0) {
                                        java.util.Collections.swap(ordered, index, index - 1)
                                        save(
                                            w.copy(
                                                layers =
                                                    ordered.mapIndexed { i, l ->
                                                        l.copy(zIndex = i)
                                                    }
                                            )
                                        )
                                    }
                                },
                                Modifier.weight(1f),
                            ) {
                                Text("Para trás")
                            }
                            OutlinedButton(
                                {
                                    val ordered = w.layers.sortedBy { it.zIndex }.toMutableList()
                                    val index = ordered.indexOfFirst { it.id == currentLayer.id }
                                    if (index < ordered.lastIndex) {
                                        java.util.Collections.swap(ordered, index, index + 1)
                                        save(
                                            w.copy(
                                                layers =
                                                    ordered.mapIndexed { i, l ->
                                                        l.copy(zIndex = i)
                                                    }
                                            )
                                        )
                                    }
                                },
                                Modifier.weight(1f),
                            ) {
                                Text("Para frente")
                            }
                        }
                    } else {
                        if (
                            currentLayer.type == "image" &&
                                currentLayer.id in state.draft.widgetImages
                        )
                            OutlinedButton(
                                { cropTarget = WidgetImageTarget(w.id, currentLayer.id) },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("Recortar imagem")
                            }
                        if (currentLayer.type == "shortcut")
                            OutlinedButton({ appPicker = true }, Modifier.fillMaxWidth()) {
                                Text("Escolher aplicativo")
                            }
                        if (currentLayer.type == "weather")
                            WeatherProperties(vm, currentLayer, ::editLayer)
                        if (currentLayer.type == "calendar") {
                            ClockProperties(currentLayer, ::editLayer)
                            StudioNumber(
                                "Mês relativo",
                                currentLayer.monthOffset.toFloat(),
                                { editLayer(currentLayer.copy(monthOffset = it.toInt())) },
                                -12f..12f,
                            )
                        }
                        if (currentLayer.type == "phone")
                            Field(
                                currentLayer.phoneNumber,
                                { editLayer(currentLayer.copy(phoneNumber = it.take(30))) },
                                "Telefone",
                            )
                        if (currentLayer.type == "countdown")
                            WidgetCountdownButton(currentLayer, ::editLayer)
                        if (currentLayer.type == "shape")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                        "rounded" to "Retângulo",
                                        "circle" to "Círculo",
                                        "line" to "Linha",
                                    )
                                    .forEach { (value, label) ->
                                        FilterChip(
                                            currentLayer.shape == value,
                                            { editLayer(currentLayer.copy(shape = value)) },
                                            { Text(label) },
                                        )
                                    }
                            }
                        if (currentLayer.type == "progress")
                            StudioNumber(
                                "Progresso",
                                currentLayer.progress * 100,
                                { editLayer(currentLayer.copy(progress = it / 100)) },
                                0f..100f,
                            )
                        ElementProperties(
                            currentLayer,
                            state.draft.widgetImages,
                            ::editLayer,
                            { key ->
                                elementImageKey = key
                                elementImagePicker.launch("image/*")
                            },
                            vm,
                        )
                    }
                } else Text("Item bloqueado", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        {
                            val (copy, images) =
                                cloneElement(currentLayer, state.draft.widgetImages)
                            save(
                                w.copy(
                                    layers =
                                        w.layers + copy.copy(zIndex = w.layers.size, locked = false)
                                )
                            )
                            vm.editDraft { it.copy(widgetImages = it.widgetImages + images) }
                            selected = copy.id
                        },
                        Modifier.weight(1f),
                        enabled = w.layers.size < 16,
                    ) {
                        Text("Duplicar")
                    }
                    OutlinedButton(
                        { pendingElementSave = currentLayer },
                        Modifier.weight(1f),
                        enabled = currentLayer.type != "element",
                    ) {
                        Text("Salvar elemento")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    if (saving && w != null)
        SaveCreationDialog(kind = "widget", initialName = w.name, onDismiss = { saving = false }) {
            folder,
            name ->
            vm.action {
                val spec = w.copy(name = name).validate()
                val token =
                    withContext(Dispatchers.IO) {
                        StudioWidgets.save(
                            ctx,
                            spec,
                            state.draft.widgetImages,
                            state.draft.fontFiles,
                        )
                    }
                CreationFolders.register(ctx, "widget", folder, name, token)
                save(spec)
                saving = false
                vm.message("Widget salvo.")
                onBack()
            }
        }
    pendingElementSave?.let { item ->
        SaveCreationDialog(
            kind = "element",
            initialName = item.text.ifBlank { widgetKindLabel(item.type) },
            onDismiss = { pendingElementSave = null },
        ) { folder, name ->
            vm.action {
                val token =
                    withContext(Dispatchers.IO) {
                        ElementLibrary.save(
                            ctx,
                            name,
                            item,
                            state.draft.widgetImages,
                            state.draft.fontFiles,
                        )
                    }
                CreationFolders.register(ctx, "element", folder, name, token)
                pendingElementSave = null
                vm.message("Elemento salvo.")
            }
        }
    }
    if (elementLibrary && w != null) {
        val entries = remember { ElementLibrary.list(ctx) }
        AlertDialog(
            onDismissRequest = { elementLibrary = false },
            title = { Text("Meus elementos") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (entries.isEmpty()) Text("Nenhum elemento salvo.")
                    entries.forEach { (_, item) ->
                        TextButton(
                            {
                                vm.action {
                                    val (copy, images) =
                                        cloneElement(item.spec.layers.first(), item.images)
                                    val fonts =
                                        state.draft.manifest.fonts +
                                            item.fonts.keys
                                                .filter { id ->
                                                    state.draft.manifest.fonts.none { it.id == id }
                                                }
                                                .map {
                                                    ThemeFont(id = it, name = "Fonte do elemento")
                                                }
                                    require(fonts.size <= 20) { "Limite de 20 fontes por tema." }
                                    save(
                                        w.copy(
                                            layers =
                                                w.layers +
                                                    copy.copy(
                                                        x = .1f,
                                                        y = .1f,
                                                        width = .6f,
                                                        height = .6f,
                                                        zIndex = w.layers.size,
                                                    )
                                        )
                                    )
                                    vm.editDraft { d ->
                                        d.copy(
                                            widgetImages = d.widgetImages + images,
                                            fontFiles = d.fontFiles + item.fonts,
                                            manifest = d.manifest.copy(fonts = fonts),
                                        )
                                    }
                                    selected = copy.id
                                    testing = false
                                    elementLibrary = false
                                }
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            Text(item.spec.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ elementLibrary = false }) { Text("Concluir") } },
        )
    }
    if (iconLibrary && w != null) {
        val icons = remember { IconDesignLibrary.list(ctx) }
        AlertDialog(
            onDismissRequest = { iconLibrary = false },
            title = { Text("Meus ícones") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (icons.isEmpty()) Text("Nenhum ícone salvo.")
                    icons.forEach { item ->
                        TextButton(
                            {
                                val id = UUID.randomUUID().toString()
                                val images = mutableMapOf(id to item.previewPath)
                                val states =
                                    item.renderedStates
                                        .filterKeys { it != "default" }
                                        .map { (name, path) ->
                                            val key = UUID.randomUUID().toString()
                                            images[key] = path
                                            ElementState(
                                                name =
                                                    if (name == "pressed") "Pressionado"
                                                    else "Desativado",
                                                condition = name,
                                                imageId = key,
                                            )
                                        }
                                val layer =
                                    WidgetLayer(
                                        id = id,
                                        type = "image",
                                        imageScale = "fit",
                                        x = .1f,
                                        y = .1f,
                                        width = .3f,
                                        height =
                                            (.3f * w.canvasWidth / w.height).coerceIn(.05f, .8f),
                                        zIndex = w.layers.size,
                                        tapPackage = item.design.packageName.ifBlank { null },
                                        tapAction =
                                            if (item.design.packageName.isBlank()) "none"
                                            else "app",
                                        states = states,
                                    )
                                save(w.copy(layers = w.layers + layer))
                                vm.editDraft { it.copy(widgetImages = it.widgetImages + images) }
                                selected = layer.id
                                testing = false
                                iconLibrary = false
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            Text(item.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ iconLibrary = false }) { Text("Concluir") } },
        )
    }
    if (widgetLibrary) {
        val saved = remember {
            CreationFolders.entries(ctx, "widget").mapNotNull { entry ->
                StudioWidgets.read(ctx, entry.token)?.let { entry to it }
            }
        }
        AlertDialog(
            onDismissRequest = { widgetLibrary = false },
            title = { Text("Meus widgets") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (widgets.isEmpty() && saved.isEmpty()) Text("Nenhum widget salvo.")
                    widgets.forEach { item ->
                        TextButton(
                            {
                                widgetId = item.id
                                selected = null
                                widgetLibrary = false
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            Text(item.name)
                        }
                    }
                    saved
                        .groupBy { it.first.folder }
                        .forEach { (folder, entries) ->
                            HorizontalDivider()
                            Text(folder, style = MaterialTheme.typography.labelMedium)
                            entries.forEach { (entry, item) ->
                                TextButton(
                                    {
                                        vm.action {
                                            require(widgets.size < 12) {
                                                "Limite de 12 widgets por tema."
                                            }
                                            val clones =
                                                item.spec.layers.map {
                                                    cloneElement(it, item.images)
                                                }
                                            val background =
                                                item.spec.backgroundImageId?.let {
                                                    UUID.randomUUID().toString()
                                                }
                                            val images =
                                                clones
                                                    .flatMap { it.second.entries }
                                                    .associate { it.toPair() }
                                                    .toMutableMap()
                                            if (background != null)
                                                item.images[item.spec.backgroundImageId]?.let {
                                                    images[background] = it
                                                }
                                            val fonts =
                                                state.draft.manifest.fonts +
                                                    item.fonts.keys
                                                        .filter { id ->
                                                            state.draft.manifest.fonts.none {
                                                                it.id == id
                                                            }
                                                        }
                                                        .map {
                                                            ThemeFont(
                                                                id = it,
                                                                name = "Fonte do widget",
                                                            )
                                                        }
                                            require(fonts.size <= 20) {
                                                "Limite de 20 fontes por tema."
                                            }
                                            save(
                                                item.spec.copy(
                                                    id = UUID.randomUUID().toString(),
                                                    backgroundImageId = background,
                                                    backgroundImageAssetId = null,
                                                    layers = clones.map { it.first },
                                                )
                                            )
                                            vm.editDraft { d ->
                                                d.copy(
                                                    widgetImages = d.widgetImages + images,
                                                    fontFiles = d.fontFiles + item.fonts,
                                                    manifest = d.manifest.copy(fonts = fonts),
                                                )
                                            }
                                            selected = null
                                            widgetLibrary = false
                                        }
                                    },
                                    Modifier.fillMaxWidth(),
                                    enabled = widgets.size < 12,
                                ) {
                                    Text(entry.name)
                                }
                            }
                        }
                }
            },
            confirmButton = { TextButton({ widgetLibrary = false }) { Text("Concluir") } },
        )
    }
    cropTarget?.let { target ->
        val widget = widgets.firstOrNull { it.id == target.widgetId }
        val imageLayer = widget?.layers?.firstOrNull { it.id == target.layerId }
        if (widget != null && (target.layerId == null || imageLayer != null))
            WidgetImageCropDialog(
                widget,
                imageLayer,
                state.draft.widgetImages,
                { cropTarget = null },
            ) { crop ->
                save(
                    if (imageLayer == null)
                        widget.copy(
                            backgroundScale = crop.scale,
                            backgroundZoom = crop.zoom,
                            backgroundOffsetX = crop.x,
                            backgroundOffsetY = crop.y,
                        )
                    else
                        widget.copy(
                            layers =
                                widget.layers.map {
                                    if (it.id == imageLayer.id)
                                        it.copy(
                                            imageScale = crop.scale,
                                            imageZoom = crop.zoom,
                                            imageOffsetX = crop.x,
                                            imageOffsetY = crop.y,
                                        )
                                    else it
                                }
                        )
                )
                cropTarget = null
            }
    }
    if (appPicker && w != null)
        AlertDialog(
            onDismissRequest = { appPicker = false },
            title = { Text("Aplicativo") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    apps.forEach { app ->
                        TextButton(
                            {
                                w.layers
                                    .firstOrNull { it.id == selected }
                                    ?.let {
                                        editLayer(
                                            it.copy(
                                                tapPackage = app.packageName,
                                                text = app.label,
                                                tapAction = "app",
                                            )
                                        )
                                    }
                                appPicker = false
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            Text(app.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ appPicker = false }) { Text("Cancelar") } },
        )
    if (floating && w != null)
        FloatingWidgetSetup(vm, w, state.draft.widgetImages, state.draft.fontFiles) {
            floating = false
        }
}

private fun widgetKindLabel(type: String) =
    widgetKinds.firstOrNull { it.first == type }?.second ?: "Elemento"

@Composable
private fun WidgetLayerPlacement(layer: WidgetLayer, onChange: (WidgetLayer) -> Unit) {
    StudioNumber(
        "Largura (%)",
        layer.width * 100,
        { onChange(layer.copy(width = it / 100, x = layer.x.coerceAtMost(1 - it / 100))) },
        5f..100f,
    )
    StudioNumber(
        "Altura (%)",
        layer.height * 100,
        { onChange(layer.copy(height = it / 100, y = layer.y.coerceAtMost(1 - it / 100))) },
        5f..100f,
    )
    StudioNumber(
        "Horizontal (%)",
        layer.x * 100,
        { onChange(layer.copy(x = (it / 100).coerceAtMost(1 - layer.width))) },
        0f..100f,
    )
    StudioNumber(
        "Vertical (%)",
        layer.y * 100,
        { onChange(layer.copy(y = (it / 100).coerceAtMost(1 - layer.height))) },
        0f..100f,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ onChange(layer.copy(x = (1 - layer.width) / 2)) }) { Text("Centralizar X") }
        TextButton({ onChange(layer.copy(y = (1 - layer.height) / 2)) }) { Text("Centralizar Y") }
    }
}

@Composable
private fun WidgetCountdownButton(layer: WidgetLayer, onChange: (WidgetLayer) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(
        {
            val calendar = java.util.Calendar.getInstance()
            if (layer.targetTime > 0) calendar.timeInMillis = layer.targetTime
            android.app
                .DatePickerDialog(
                    ctx,
                    { _, year, month, day ->
                        android.app
                            .TimePickerDialog(
                                ctx,
                                { _, hour, minute ->
                                    calendar.set(year, month, day, hour, minute, 0)
                                    onChange(layer.copy(targetTime = calendar.timeInMillis))
                                },
                                calendar.get(java.util.Calendar.HOUR_OF_DAY),
                                calendar.get(java.util.Calendar.MINUTE),
                                true,
                            )
                            .show()
                    },
                    calendar.get(java.util.Calendar.YEAR),
                    calendar.get(java.util.Calendar.MONTH),
                    calendar.get(java.util.Calendar.DAY_OF_MONTH),
                )
                .show()
        },
        Modifier.fillMaxWidth(),
    ) {
        Text("Data e hora de destino")
    }
}
