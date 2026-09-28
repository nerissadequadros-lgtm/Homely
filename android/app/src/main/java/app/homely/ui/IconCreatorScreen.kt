package app.homely.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.homely.data.*
import app.homely.platform.*
import coil.compose.AsyncImage
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

@Composable
fun DraftHistoryBar(vm: HomelyViewModel, state: HomelyState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (state.busy) "Salvando…" else "Rascunho salvo",
            Modifier.weight(1f),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(vm::undoDraft, enabled = state.canUndo) { Icon(Icons.Outlined.Undo, "Desfazer") }
        IconButton(vm::redoDraft, enabled = state.canRedo) { Icon(Icons.Outlined.Redo, "Refazer") }
    }
}

private val iconStateNames =
    listOf("default" to "Padrão", "pressed" to "Pressionado", "disabled" to "Desativado")
private val iconShapeNames =
    listOf(
        "circle" to "Círculo",
        "square" to "Quadrado",
        "rounded" to "Arredondado",
        "triangle" to "Triângulo",
        "star" to "Estrela",
        "diamond" to "Losango",
        "heart" to "Coração",
    )

@Composable
fun IconCreatorScreen(
    vm: HomelyViewModel,
    state: HomelyState,
    initialPackage: String? = null,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val repository = remember { HomelyRepository(ctx) }
    val prefs = remember { ctx.getSharedPreferences("icon-creator-draft", 0) }
    var design by
        remember(initialPackage) {
            mutableStateOf(
                runCatching {
                    repository.json.decodeFromString<IconDesign>(
                        prefs.getString(initialPackage ?: "independent", null) ?: error("new")
                    )
                }
                    .getOrElse {
                        IconDesign(
                            packageName = initialPackage.orEmpty(),
                            states =
                                mapOf(
                                    "default" to
                                        IconAppearance(
                                            background =
                                                IconArtworkLayer(
                                                    shape = "square",
                                                    scale = 1f,
                                                    color = state.draft.manifest.palette.accent,
                                                )
                                        )
                                ),
                        )
                    }
            )
        }
    var step by remember { mutableIntStateOf(0) }
    var editingState by remember { mutableStateOf("default") }
    var foreground by remember { mutableStateOf(true) }
    var drawing by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }
    var showLibrary by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var saveRevision by remember { mutableIntStateOf(0) }
    val undo = remember { mutableStateListOf<IconDesign>() }
    val redo = remember { mutableStateListOf<IconDesign>() }
    val selectedState = if (step == 2 || step == 3) editingState else "default"
    val appearance = design.appearance(selectedState)
    val layer = if (foreground) appearance.foreground else appearance.background
    fun edit(value: IconDesign) {
        if (value != design) {
            undo.add(design)
            if (undo.size > 50) undo.removeAt(0)
            redo.clear()
            design = value
        }
    }
    fun editLayer(value: IconArtworkLayer) {
        edit(
            design.withAppearance(
                selectedState,
                if (foreground) appearance.copy(foreground = value)
                else appearance.copy(background = value),
            )
        )
    }
    LaunchedEffect(design, initialPackage) {
        delay(350)
        prefs
            .edit()
            .putString(initialPackage ?: "independent", repository.json.encodeToString(design))
            .apply()
    }
    val apps by
        produceState<List<InstalledApp>>(emptyList()) {
            value = withContext(Dispatchers.IO) { NativeActions.apps(ctx) }
        }
    val library = remember(saveRevision, showLibrary) { IconDesignLibrary.list(ctx) }
    val folders =
        remember(saveRevision, showLibrary) {
            CreationFolders.entries(ctx, "icon").associateBy { it.token }
        }
    var importingForeground by remember { mutableStateOf(true) }
    var importingState by remember { mutableStateOf("default") }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.action {
                    val path =
                        withContext(Dispatchers.IO) { IconDesignRenderer.importImage(ctx, uri) }
                    val current = design.appearance(importingState)
                    val target =
                        (if (importingForeground) current.foreground else current.background).copy(
                            kind = "image",
                            imagePath = path,
                        )
                    edit(
                        design.withAppearance(
                            importingState,
                            if (importingForeground) current.copy(foreground = target)
                            else current.copy(background = target),
                        )
                    )
                }
        }
    fun importImage() {
        importingForeground = foreground
        importingState = selectedState
        picker.launch(arrayOf("image/*", "image/svg+xml"))
    }
    if (showSave)
        SaveCreationDialog("icon", design.name, { showSave = false }) { folder, name ->
            showSave = false
            vm.action {
                val saved =
                    withContext(Dispatchers.IO) { IconDesignLibrary.save(ctx, design, name) }
                CreationFolders.register(ctx, "icon", folder, name, saved.token)
                design = saved.design
                if (saved.design.packageName.isNotBlank())
                    vm.editDraft { draft ->
                        draft.copy(
                            icons =
                                draft.icons.filterNot {
                                    it.packageName == saved.design.packageName
                                } + saved.toDraftIcon()
                        )
                    }
                saveRevision++
                vm.message("Ícone salvo em $folder.")
            }
        }
    if (showLibrary)
        AlertDialog(
            onDismissRequest = { showLibrary = false },
            title = { Text("Meus ícones") },
            text = {
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (library.isEmpty()) Text("Nenhum ícone salvo.")
                    library.forEach { saved ->
                        TextButton({
                            edit(saved.design)
                            showLibrary = false
                            step = 1
                        }) {
                            AsyncImage(File(saved.previewPath), null, Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(saved.name)
                                Text(
                                    folders[saved.token]?.folder ?: "Minhas criações",
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showLibrary = false }) { Text("Fechar") } },
        )
    if (showApps) {
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showApps = false },
            title = { Text("Aplicativo") },
            text = {
                Column {
                    Field(query, { query = it }, "Buscar aplicativo")
                    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                        TextButton({
                            edit(design.copy(packageName = ""))
                            showApps = false
                        }) {
                            Text("Sem aplicativo")
                        }
                        apps
                            .filter { it.label.contains(query, true) }
                            .forEach { app ->
                                TextButton({
                                    edit(
                                        design.copy(
                                            packageName = app.packageName,
                                            name = app.label.take(60),
                                        )
                                    )
                                    showApps = false
                                }) {
                                    Text(app.label)
                                }
                            }
                    }
                }
            },
            confirmButton = { TextButton({ showApps = false }) { Text("Fechar") } },
        )
    }
    BackHandler {
        if (step > 0) {
            step--
            drawing = false
        } else onBack()
    }
    val palette = state.draft.manifest.palette
    val swatches =
        listOf(
            palette.accent,
            palette.secondary,
            palette.surface,
            palette.background,
            palette.text,
            "#FFFFFF",
            "#000000",
        )
    BoxWithConstraints(Modifier.fillMaxSize().testTag("icon-creator")) {
        val canvasHeight = (maxHeight * .32f).coerceIn(150.dp, 250.dp)
        Column(Modifier.fillMaxSize()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    listOf("Base", "Camadas", "Estados", "Revisão").mapIndexed { index, name ->
                        index to name
                    }
                ) { (index, name) ->
                    Pill("${index + 1} $name", step == index) {
                        step = index
                        drawing = false
                    }
                }
            }
            IconEditingCanvas(
                design,
                selectedState,
                foreground,
                drawing,
                canvasHeight,
                onEdit = { changed ->
                    val current = design.appearance(selectedState)
                    edit(
                        design.withAppearance(
                            selectedState,
                            if (foreground) current.copy(foreground = changed)
                            else current.copy(background = changed),
                        )
                    )
                },
                onDrawingFinished = { drawing = false },
            )
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (drawing) "Desenhar forma"
                    else if (foreground) "Primeiro plano" else "Fundo",
                    Modifier.weight(1f),
                    fontSize = 12.sp,
                )
                IconButton(
                    {
                        redo.add(design)
                        design = undo.removeAt(undo.lastIndex)
                    },
                    enabled = undo.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.Undo, "Desfazer")
                }
                IconButton(
                    {
                        undo.add(design)
                        design = redo.removeAt(redo.lastIndex)
                    },
                    enabled = redo.isNotEmpty(),
                ) {
                    Icon(Icons.Outlined.Redo, "Refazer")
                }
            }
            Column(
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> {
                        IconChoiceRow(layer.shape, iconShapeNames) {
                            editLayer(layer.copy(kind = "shape", shape = it))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ importImage() }, Modifier.weight(1f)) {
                                Text("Imagem ou SVG")
                            }
                            OutlinedButton({ showLibrary = true }, Modifier.weight(1f)) {
                                Text("Meus ícones")
                            }
                        }
                        OutlinedButton({ showApps = true }, Modifier.fillMaxWidth()) {
                            Text(
                                apps
                                    .firstOrNull { it.packageName == design.packageName }
                                    ?.let { "Aplicativo: ${it.label}" } ?: "Associar aplicativo"
                            )
                        }
                        if (design.packageName.isNotBlank())
                            TextButton({
                                vm.action {
                                    val path =
                                        withContext(Dispatchers.IO) {
                                            val file =
                                                File(
                                                    ctx.filesDir,
                                                    "icon-source-${UUID.randomUUID()}.png",
                                                )
                                            val bmp =
                                                ctx.packageManager
                                                    .getApplicationIcon(design.packageName)
                                                    .toBitmap(512, 512)
                                            file.outputStream().use {
                                                bmp.compress(
                                                    android.graphics.Bitmap.CompressFormat.PNG,
                                                    100,
                                                    it,
                                                )
                                            }
                                            bmp.recycle()
                                            file.path
                                        }
                                    editLayer(layer.copy(kind = "image", imagePath = path))
                                }
                            }) {
                                Text("Usar imagem do aplicativo")
                            }
                    }
                    1,
                    2 -> {
                        if (step == 2) {
                            IconChoiceRow(editingState, iconStateNames) { editingState = it }
                            if (selectedState != "default")
                                TextButton({
                                    edit(
                                        design.withAppearance(
                                            selectedState,
                                            design.appearance("default"),
                                        )
                                    )
                                }) {
                                    Text("Copiar estado padrão")
                                }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Fundo", !foreground) {
                                foreground = false
                                drawing = false
                            }
                            Pill("Primeiro plano", foreground) {
                                foreground = true
                                drawing = false
                            }
                        }
                        IconLayerControls(
                            layer,
                            swatches,
                            drawing,
                            onEdit = ::editLayer,
                            onImport = ::importImage,
                            onDrawing = { drawing = !drawing },
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Alinhar à grade", Modifier.weight(1f))
                            Switch(design.snapToGrid, { edit(design.copy(snapToGrid = it)) })
                        }
                        if (design.snapToGrid)
                            StudioNumber(
                                "Divisões",
                                design.gridColumns.toFloat(),
                                { edit(design.copy(gridColumns = it.toInt())) },
                                2f..12f,
                            )
                    }
                    3 -> {
                        IconChoiceRow(editingState, iconStateNames) { editingState = it }
                        IconChoiceRow(
                            design.mask,
                            listOf(
                                "circle" to "Círculo",
                                "square" to "Quadrado",
                                "squircle" to "Squircle",
                            ),
                        ) {
                            edit(design.copy(mask = it))
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf("circle", "square", "squircle").forEach { mask ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    IconDesignPreview(design, editingState, mask, 64)
                                    IconDesignPreview(design, editingState, mask, 32)
                                    Text(
                                        if (mask == "circle") "Círculo"
                                        else if (mask == "square") "Quadrado" else "Squircle",
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    {
                        if (step == 0) onBack()
                        else {
                            step--
                            drawing = false
                        }
                    },
                    Modifier.weight(1f),
                ) {
                    Text("Voltar")
                }
                Button(
                    {
                        if (step == 3) showSave = true
                        else {
                            step++
                            drawing = false
                        }
                    },
                    Modifier.weight(1f).testTag("icon-next"),
                    enabled = !state.busy,
                ) {
                    Text(if (step == 3) "Salvar" else "Continuar")
                }
            }
        }
    }
}

@Composable
private fun IconChoiceRow(
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { (id, label) -> Pill(label, value == id) { onChange(id) } }
    }
}

@Composable
private fun IconLayerControls(
    layer: IconArtworkLayer,
    swatches: List<String>,
    drawing: Boolean,
    onEdit: (IconArtworkLayer) -> Unit,
    onImport: () -> Unit,
    onDrawing: () -> Unit,
) {
    IconChoiceRow(layer.kind, listOf("shape" to "Forma", "image" to "Imagem")) {
        if (it == "image" && layer.imagePath == null) onImport() else onEdit(layer.copy(kind = it))
    }
    if (layer.kind == "shape") {
        IconChoiceRow(layer.shape, iconShapeNames) { onEdit(layer.copy(shape = it)) }
        OutlinedButton(onDrawing, Modifier.fillMaxWidth()) {
            Text(if (drawing) "Cancelar desenho" else "Desenhar no canvas")
        }
    } else {
        OutlinedButton(onImport, Modifier.fillMaxWidth()) { Text("Trocar imagem ou SVG") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Colorir imagem", Modifier.weight(1f))
            Switch(layer.tintImage, { onEdit(layer.copy(tintImage = it)) })
        }
    }
    if (layer.kind == "shape" || layer.tintImage) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Sólida", layer.gradientColor == null) { onEdit(layer.copy(gradientColor = null)) }
            Pill("Gradiente", layer.gradientColor != null) {
                onEdit(layer.copy(gradientColor = layer.gradientColor ?: "#FFFFFF"))
            }
        }
        StudioColorPicker(layer.color, swatches) { onEdit(layer.copy(color = it)) }
        layer.gradientColor?.let { end ->
            StudioColorPicker(end, swatches) { onEdit(layer.copy(gradientColor = it)) }
            StudioNumber(
                "Ângulo do gradiente",
                layer.gradientAngle,
                { onEdit(layer.copy(gradientAngle = it)) },
                0f..360f,
            )
        }
    }
    StudioNumber(
        "Opacidade · %",
        layer.opacity * 100,
        { onEdit(layer.copy(opacity = it / 100)) },
        0f..100f,
    )
    StudioSection("Posição") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StudioNumber(
                "X",
                layer.offsetX,
                { onEdit(layer.copy(offsetX = it)) },
                -54f..54f,
                modifier = Modifier.weight(1f),
            )
            StudioNumber(
                "Y",
                layer.offsetY,
                { onEdit(layer.copy(offsetY = it)) },
                -54f..54f,
                modifier = Modifier.weight(1f),
            )
        }
        StudioNumber(
            "Tamanho · %",
            layer.scale * 100,
            { onEdit(layer.copy(scale = it / 100)) },
            10f..150f,
        )
        StudioNumber("Rotação", layer.rotation, { onEdit(layer.copy(rotation = it)) }, 0f..360f)
        OutlinedButton({ onEdit(layer.copy(offsetX = 0f, offsetY = 0f)) }) { Text("Centralizar") }
    }
}

@Composable
private fun IconEditingCanvas(
    design: IconDesign,
    state: String,
    foreground: Boolean,
    drawing: Boolean,
    height: androidx.compose.ui.unit.Dp,
    onEdit: (IconArtworkLayer) -> Unit,
    onDrawingFinished: () -> Unit,
) {
    val image by
        produceState<ImageBitmap?>(null, design, state) {
            value =
                withContext(Dispatchers.Default) {
                    runCatching { IconDesignRenderer.render(design, state).asImageBitmap() }
                        .getOrNull()
                }
        }
    val latestDesign by rememberUpdatedState(design)
    val latestEdit by rememberUpdatedState(onEdit)
    val latestDone by rememberUpdatedState(onDrawingFinished)
    Box(
        Modifier.fillMaxWidth().height(height).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.padding(12.dp).aspectRatio(1f).testTag("icon-canvas").pointerInput(
                foreground,
                drawing,
                state,
            ) {
                var origin = Offset.Zero
                var start = IconArtworkLayer()
                var translation = Offset.Zero
                fun coordinate(value: Float) = (value / size.width * 108f).coerceIn(0f, 108f)
                detectDragGestures(
                    onDragStart = { point ->
                        origin = point
                        val appearance = latestDesign.appearance(state)
                        start = if (foreground) appearance.foreground else appearance.background
                        translation = Offset.Zero
                    },
                    onDragEnd = { if (drawing) latestDone() },
                    onDrag = { change, drag ->
                        change.consume()
                        val grid = latestDesign
                        val spacing = 108f / grid.gridColumns
                        fun snap(value: Float) =
                            (if (grid.snapToGrid) round(value / spacing) * spacing
                                else if (abs(value) < 2f) 0f else value)
                                .coerceIn(-54f, 54f)
                        if (drawing) {
                            val end = change.position
                            val x = coordinate((origin.x + end.x) / 2) - 54f
                            val y = coordinate((origin.y + end.y) / 2) - 54f
                            val extent =
                                (max(abs(origin.x - end.x), abs(origin.y - end.y)) / size.width)
                                    .coerceIn(.1f, 1.5f)
                            latestEdit(
                                start.copy(
                                    kind = "shape",
                                    offsetX = snap(x),
                                    offsetY = snap(y),
                                    scale = extent,
                                )
                            )
                        } else {
                            translation += drag
                            latestEdit(
                                start.copy(
                                    offsetX =
                                        snap(start.offsetX + translation.x / size.width * 108f),
                                    offsetY =
                                        snap(start.offsetY + translation.y / size.height * 108f),
                                )
                            )
                        }
                    },
                )
            }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val tile = size.width / 12
                repeat(12) { x ->
                    repeat(12) { y ->
                        drawRect(
                            if ((x + y) % 2 == 0) Color.Gray.copy(alpha = .1f)
                            else Color.Gray.copy(alpha = .03f),
                            Offset(x * tile, y * tile),
                            Size(tile, tile),
                        )
                    }
                }
            }
            image?.let { Image(it, "Prévia do ícone", Modifier.fillMaxSize()) }
            if (design.snapToGrid)
                Canvas(Modifier.fillMaxSize()) {
                    repeat(design.gridColumns + 1) { i ->
                        val at = i.toFloat() / design.gridColumns
                        drawLine(
                            Color.Gray.copy(alpha = .25f),
                            Offset(size.width * at, 0f),
                            Offset(size.width * at, size.height),
                        )
                        drawLine(
                            Color.Gray.copy(alpha = .25f),
                            Offset(0f, size.height * at),
                            Offset(size.width, size.height * at),
                        )
                    }
                }
        }
    }
}

@Composable
private fun IconDesignPreview(design: IconDesign, state: String, mask: String, dp: Int) {
    val image by
        produceState<ImageBitmap?>(null, design, state, mask) {
            value =
                withContext(Dispatchers.Default) {
                    runCatching {
                            IconDesignRenderer.render(design, state, 256, mask).asImageBitmap()
                        }
                        .getOrNull()
                }
        }
    image?.let { Image(it, "$mask · $dp dp", Modifier.size(dp.dp)) }
}
