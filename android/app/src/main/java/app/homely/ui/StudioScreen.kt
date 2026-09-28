package app.homely.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import app.homely.data.*
import app.homely.platform.*
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StudioScreen(
    vm: HomelyViewModel,
    state: HomelyState,
    onEditorTitle: (String?) -> Unit = {},
    onBack: () -> Unit,
) {
    var iconEditor by remember { mutableStateOf(false) }
    var widgetEditor by remember { mutableStateOf(false) }
    LaunchedEffect(iconEditor, widgetEditor) {
        onEditorTitle(
            if (iconEditor) "Criador de ícones"
            else if (widgetEditor) "Criador de Widgets" else null
        )
    }
    if (iconEditor) {
        IconCreatorScreen(vm, state) { iconEditor = false }
        return
    }
    if (widgetEditor) {
        WidgetCreatorScreen(vm, state) { widgetEditor = false }
        return
    }
    var applyTheme by remember { mutableStateOf(false) }
    var privateOpen by remember { mutableStateOf(false) }
    if (privateOpen) {
        PrivateLibrary(vm, state) { privateOpen = false }
        return
    }
    val draft = state.draft
    val manifest = draft.manifest
    LaunchedEffect(manifest.palette) {
        val updated = vm.state.value.draft.manifest.widgets.map { it.withPalette(manifest.palette) }
        if (updated != vm.state.value.draft.manifest.widgets)
            vm.editDraft { it.copy(manifest = it.manifest.copy(widgets = updated)) }
    }
    val ctx = LocalContext.current
    var section by rememberSaveable { mutableIntStateOf(0) }
    var saveTheme by remember { mutableStateOf(false) }
    var chooseApp by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var publish by remember { mutableStateOf(false) }
    var customColor by remember { mutableStateOf(false) }
    val wallpaper =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let { vm.chooseMedia(it, "wallpaper") }
        }
    val screenshots =
        rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) {
            if (it.isNotEmpty()) vm.chooseScreenshots(it)
        }
    val video =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let { vm.chooseMedia(it, "live_wallpaper") }
        }
    val icon =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedApp?.let { a -> vm.chooseMedia(it, "icon", a.packageName, a.label) }
            }
        }
    val importTheme =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.action {
                    val imported =
                        withContext(Dispatchers.IO) {
                            ctx.contentResolver.openInputStream(uri)!!.use {
                                ThemeArchive.import(
                                    it,
                                    File(ctx.filesDir, "import-" + System.currentTimeMillis()),
                                    vm.repo.json,
                                )
                            }
                        }
                    vm.editDraft { imported }
                    vm.message("Tema importado para o estúdio.")
                }
        }
    val exportTheme =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null)
                vm.action {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
                            ThemeArchive.export(
                                LocalThemeStore(ctx).normalized(draft),
                                it,
                                vm.repo.json,
                            )
                        }
                    }
                    vm.message("Seu arquivo .homely está pronto.")
                }
        }
    fun edit(change: (Manifest) -> Manifest) = vm.editDraft {
        val updated = change(it.manifest)
        it.copy(
            manifest =
                updated.copy(widgets = updated.widgets.map { w -> w.withPalette(updated.palette) })
        )
    }
    var canvas by remember { mutableStateOf(false) }
    var componentLibrary by remember { mutableStateOf<String?>(null) }
    fun editLayout(value: HomeLayout) = edit { it.copy(layout = value) }
    ThemeBuilderFrame(
        draft = draft,
        section = section,
        onSection = { section = it },
        onBack = onBack,
        onImport = {
            importTheme.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        },
        onExport = { exportTheme.launch(manifest.title.ifBlank { "Meu tema" } + ".homely") },
        history = { DraftHistoryBar(vm, state) },
    ) {
        when (section) {
            0 -> {
                BuilderChoices(
                    if (draft.wallpaperPath == null && draft.videoPath == null) "color"
                    else "image",
                    listOf("color" to "Cor", "image" to "Imagem"),
                ) { value ->
                    if (value == "color")
                        vm.editDraft { it.copy(wallpaperPath = null, videoPath = null) }
                    else wallpaper.launch("image/*")
                }
                if (draft.wallpaperPath == null)
                    StudioColorPicker(
                        manifest.palette.background,
                        listOf("#171625", "#FFFFFF", "#E8B5C8"),
                    ) { value ->
                        edit { it.copy(palette = it.palette.copy(background = value)) }
                    }
                BuilderCard("Wallpaper", "O cenário para seus ícones, widgets e aplicativos.") {
                    draft.wallpaperPath?.let { path ->
                        AsyncImage(
                            File(path),
                            "Wallpaper selecionado",
                            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    MediaTile(
                        Icons.Outlined.AddPhotoAlternate,
                        if (draft.wallpaperPath == null) "Adicionar wallpaper"
                        else "Trocar wallpaper",
                        "JPG, PNG ou WebP",
                    ) {
                        wallpaper.launch("image/*")
                    }
                    StudioSection("Wallpaper em movimento") {
                        MediaTile(
                            Icons.Outlined.MotionPhotosOn,
                            if (draft.videoPath == null) "Adicionar vídeo" else "Trocar vídeo",
                            "MP4 sem áudio · wallpaper animado do Android",
                        ) {
                            video.launch("video/mp4")
                        }
                        if (draft.videoPath != null)
                            TextButton({ vm.editDraft { it.copy(videoPath = null) } }) {
                                Text("Remover vídeo")
                            }
                    }
                }
            }
            1 -> {
                BuilderCard("Paleta e tipografia", "Veja suas cores na tela inicial e na grade.") {
                    StudioColorPicker(
                        manifest.palette.accent,
                        listOf("#7965FF", "#C78CFF", "#79C9A4", "#FFA878", "#8EBEFA", "#EE8BB4"),
                    ) { value ->
                        edit { it.copy(palette = it.palette.copy(accent = value)) }
                    }
                    OutlinedButton({ customColor = true }, Modifier.fillMaxWidth()) {
                        Text("Editar todas as cores")
                    }
                    OutlinedButton(
                        vm::extractPalette,
                        Modifier.fillMaxWidth(),
                        enabled = draft.wallpaperPath != null && !state.busy,
                    ) {
                        Text("Extrair paleta do wallpaper")
                    }
                    BuilderChoices(
                        "",
                        listOf("light" to "Variante clara", "dark" to "Variante escura"),
                    ) { value ->
                        edit {
                            it.copy(
                                palette = PaletteExtractor.variant(it.palette, value == "light")
                            )
                        }
                    }
                    Text("Fonte dos nomes e menus", style = MaterialTheme.typography.labelLarge)
                    BuilderChoices(
                        manifest.fontFamily,
                        listOf(
                            "sans" to "Sem serifa",
                            "serif" to "Serifada",
                            "mono" to "Mono",
                            "display" to "Display",
                            "hand" to "Manuscrita",
                        ),
                    ) { value ->
                        edit { it.copy(fontFamily = value) }
                    }
                    Text(
                        "Aa  0123456789",
                        fontFamily = studioFont(manifest.fontFamily),
                        fontSize = 22.sp,
                    )
                }
            }
            2 -> {
                BuilderCard(
                    "Ícones",
                    "Edite imagem, formato, bordas, sombra, texto e camadas de cada aplicativo.",
                ) {
                    BuilderChoices(
                        manifest.iconStyle,
                        listOf(
                            "rounded" to "Suave",
                            "circle" to "Circular",
                            "squircle" to "Squircle",
                            "teardrop" to "Gota",
                            "hexagon" to "Hexagonal",
                        ),
                    ) { value ->
                        edit { it.copy(iconStyle = value) }
                    }
                    Button({ iconEditor = true }, Modifier.fillMaxWidth()) {
                        Text("Abrir editor de ícones · ${draft.icons.size}")
                    }
                    OutlinedButton({ componentLibrary = "icons" }, Modifier.fillMaxWidth()) {
                        Text("Usar meus pacotes de ícones")
                    }
                    OutlinedButton({ chooseApp = true }, Modifier.fillMaxWidth()) {
                        Text("Associar imagem a um app")
                    }
                    FilterRail(
                        "Estilo para o catálogo",
                        draft.catalogIconStyle,
                        CatalogTaxonomy.iconStyles,
                    ) { value ->
                        vm.editDraft { it.copy(catalogIconStyle = value) }
                    }
                }
            }
            3 -> {
                OutlinedButton({ componentLibrary = "elements" }, Modifier.fillMaxWidth()) {
                    Text("Adicionar elemento salvo")
                }
                BuilderCard(
                    "Widgets",
                    "Crie relógios, clima, calendário, bateria, atalhos e composições com suas próprias camadas.",
                ) {
                    Button({ widgetEditor = true }, Modifier.fillMaxWidth()) {
                        Text("Abrir editor de widgets")
                    }
                    OutlinedButton({ componentLibrary = "widgets" }, Modifier.fillMaxWidth()) {
                        Text("Usar meus widgets salvos")
                    }
                    if (manifest.widgets.isEmpty())
                        Text(
                            "Seu tema ainda não tem widgets. Adicione o primeiro no editor.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    manifest.widgets.forEach { widget ->
                        Text(widget.name, style = MaterialTheme.typography.titleSmall)
                        ComposedWidget(
                            widget,
                            draft.widgetImages[widget.id],
                            preview = true,
                            images = draft.widgetImages,
                            fonts = draft.fontFiles,
                        )
                    }
                    OutlinedButton(
                        {
                            edit { it.copy(layout = it.layout.copy(kit = defaultHomeKit(draft))) }
                            canvas = true
                        },
                        Modifier.fillMaxWidth(),
                        enabled = manifest.widgets.isNotEmpty(),
                    ) {
                        Text("Organizar widgets na tela inicial")
                    }
                }
                BuilderHome(
                    manifest.layout,
                    ::editLayout,
                    onArrange = {
                        edit { it.copy(layout = it.layout.copy(kit = defaultHomeKit(draft))) }
                    },
                    onCanvas = { canvas = true },
                )
                manifest.widgets.forEach { widget ->
                    BuilderSwitch(widget.name + " · usar paleta", "", widget.followThemePalette) {
                        enabled ->
                        edit { m ->
                            m.copy(
                                widgets =
                                    m.widgets.map {
                                        if (it.id == widget.id)
                                            it.copy(followThemePalette = enabled)
                                                .withPalette(m.palette)
                                        else it
                                    }
                            )
                        }
                    }
                    TextButton({
                        edit { m ->
                            m.copy(
                                widgets = m.widgets.filterNot { it.id == widget.id },
                                layout =
                                    m.layout.copy(
                                        kit = m.layout.kit.filterNot { it.reference == widget.id }
                                    ),
                            )
                        }
                    }) {
                        Text("Remover " + widget.name)
                    }
                }
            }
            4 -> {
                var advanced by remember { mutableStateOf(false) }
                TextButton({ advanced = !advanced }) {
                    Text(if (advanced) "Fechar configurações" else "Gestos, grade e comportamento")
                }
                if (advanced) {
                    var option by remember { mutableStateOf("drawer") }
                    BuilderChoices(
                        option,
                        listOf(
                            "drawer" to "Grade de apps",
                            "gestures" to "Gestos",
                            "animations" to "Animações",
                            "behavior" to "Comportamento",
                        ),
                    ) {
                        option = it
                    }
                    when (option) {
                        "drawer" -> BuilderDrawer(manifest.layout, ::editLayout)
                        "gestures" -> GestureSettings(vm, state)
                        "animations" -> BuilderAnimations(manifest.layout, ::editLayout)
                        else -> BuilderBehavior(manifest.layout, ::editLayout)
                    }
                }
                BuilderCard(
                    "Salvar e aplicar",
                    "A navegação personalizada funciona ao escolher o Homely Home como tela inicial.",
                ) {
                    Field(
                        manifest.title,
                        { value -> edit { it.copy(title = value.take(60)) } },
                        "Nome do tema",
                    )
                    Button({ applyTheme = true }, Modifier.fillMaxWidth(), enabled = !state.busy) {
                        Text("Aplicar tema")
                    }
                    OutlinedButton(
                        { saveTheme = true },
                        Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    ) {
                        Text("Salvar privado")
                    }
                    TextButton({ privateOpen = true }, Modifier.fillMaxWidth()) {
                        Text("Abrir meus temas salvos")
                    }
                }
                var showPublishing by remember { mutableStateOf(false) }
                TextButton({ showPublishing = !showPublishing }) {
                    Text("Compartilhar na comunidade")
                }
                if (showPublishing)
                    BuilderCard(
                        "Publicar na comunidade",
                        "Prepare a apresentação da sua criação.",
                    ) {
                        Field(
                            manifest.description,
                            { value -> edit { it.copy(description = value.take(1000)) } },
                            "Descrição e créditos",
                            singleLine = false,
                        )
                        Text("Categoria", style = MaterialTheme.typography.labelLarge)
                        BuilderChoices(
                            manifest.category,
                            listOf(
                                    "Minimal",
                                    "Natureza",
                                    "Abstrato",
                                    "Espaço",
                                    "Anime",
                                    "Tecnologia",
                                )
                                .map { it to it },
                        ) { value ->
                            edit { it.copy(category = value) }
                        }
                        var tags by
                            remember(manifest.tags) {
                                mutableStateOf(manifest.tags.joinToString(", "))
                            }
                        Field(
                            tags,
                            { value ->
                                tags = value
                                edit {
                                    it.copy(
                                        tags =
                                            value
                                                .split(',')
                                                .map { t -> t.trim().take(24) }
                                                .filter { t -> t.isNotEmpty() }
                                                .take(10)
                                    )
                                }
                            },
                            "Tags separadas por vírgula",
                        )
                        Text("Licença", style = MaterialTheme.typography.labelLarge)
                        BuilderChoices(
                            manifest.license,
                            listOf(
                                "CC-BY-4.0" to "Com crédito",
                                "CC0-1.0" to "Uso livre",
                                "personal-use" to "Uso pessoal",
                            ),
                        ) { value ->
                            edit { it.copy(license = value) }
                        }
                        OutlinedButton(
                            { screenshots.launch("image/*") },
                            Modifier.fillMaxWidth(),
                            enabled = draft.screenshotPaths.size < 8,
                        ) {
                            Text("Adicionar screenshots · ${draft.screenshotPaths.size}/8")
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(draft.screenshotPaths) { path ->
                                Box {
                                    AsyncImage(
                                        File(path),
                                        "Screenshot do tema",
                                        Modifier.width(72.dp)
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                    IconButton(
                                        {
                                            vm.editDraft {
                                                it.copy(screenshotPaths = it.screenshotPaths - path)
                                            }
                                        },
                                        Modifier.align(Alignment.TopEnd)
                                            .size(30.dp)
                                            .background(Color.Black.copy(alpha = .6f), CircleShape),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            "Remover screenshot",
                                            tint = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                        Button(
                            { if (vm.requireAuth()) publish = true },
                            Modifier.fillMaxWidth(),
                            enabled = !state.busy,
                        ) {
                            Text("Publicar na comunidade")
                        }
                    }
            }
        }
    }
    componentLibrary?.let { kind ->
        ThemeComponentLibrary(vm, state, kind) { componentLibrary = null }
    }
    if (canvas)
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { canvas = false },
            properties =
                androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = color(manifest.palette.background)) {
                Column(Modifier.safeDrawingPadding()) {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Arraste para posicionar", Modifier.weight(1f).padding(16.dp))
                        TextButton({ canvas = false }) { Text("Concluir") }
                    }
                    KitCanvas(
                        draft,
                        Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                        onMove = { item ->
                            edit { m ->
                                m.copy(
                                    layout =
                                        m.layout.copy(
                                            kit =
                                                m.layout.kit.map { x ->
                                                    if (x.id == item.id) item else x
                                                }
                                        )
                                )
                            }
                        },
                    )
                }
            }
        }
    if (saveTheme)
        SaveCreationDialog("theme", manifest.title, { saveTheme = false }) { folder, name ->
            saveTheme = false
            vm.action {
                val saved =
                    LocalThemeStore(ctx).save(draft.copy(manifest = manifest.copy(title = name)))
                CreationFolders.register(ctx, "theme", folder, name, saved.id)
                vm.message("Tema salvo em $folder.")
            }
        }
    if (applyTheme) ApplyThemeDialog(vm, draft = draft, onDismiss = { applyTheme = false })
    if (chooseApp) {
        var query by remember { mutableStateOf("") }
        val apps by
            produceState<List<InstalledApp>>(emptyList()) {
                value = withContext(Dispatchers.IO) { NativeActions.apps(ctx) }
            }
        AlertDialog(
            onDismissRequest = { chooseApp = false },
            title = { Text("Escolha um aplicativo") },
            text = {
                Column {
                    Field(query, { query = it }, "Buscar app")
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(apps.filter { it.label.contains(query, true) }) { a ->
                            TextButton(
                                {
                                    selectedApp = a
                                    chooseApp = false
                                    icon.launch("image/*")
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text(a.label, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ chooseApp = false }) { Text("Fechar") } },
        )
    }
    if (publish)
        AlertDialog(
            onDismissRequest = { publish = false },
            title = { Text("Compartilhar seu tema?") },
            text = {
                Text(
                    "Seu tema e seus recursos ficarão disponíveis para a comunidade conforme a licença escolhida. Confirme que você pode distribuir esses arquivos."
                )
            },
            confirmButton = {
                TextButton({
                    publish = false
                    vm.publish()
                }) {
                    Text("Publicar")
                }
            },
            dismissButton = { TextButton({ publish = false }) { Text("Revisar") } },
        )
    if (customColor) {
        var accent by remember { mutableStateOf(manifest.palette.accent) }
        var bg by remember { mutableStateOf(manifest.palette.background) }
        var surface by remember { mutableStateOf(manifest.palette.surface) }
        var secondary by remember { mutableStateOf(manifest.palette.secondary) }
        var textColor by remember { mutableStateOf(manifest.palette.text) }
        var activeTab by remember { mutableStateOf("accent") }

        AlertDialog(
            onDismissRequest = { customColor = false },
            title = { Text("Mesa de Cores") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Destaque", activeTab == "accent") { activeTab = "accent" }
                        Pill("Fundo", activeTab == "bg") { activeTab = "bg" }
                        Pill("Super", activeTab == "surface") { activeTab = "surface" }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Secundária", activeTab == "secondary") { activeTab = "secondary" }
                        Pill("Texto", activeTab == "text") { activeTab = "text" }
                    }
                    ColorWheel(
                        Modifier.fillMaxWidth().height(200.dp),
                        initialColor =
                            color(
                                when (activeTab) {
                                    "accent" -> accent
                                    "bg" -> bg
                                    "secondary" -> secondary
                                    "text" -> textColor
                                    else -> surface
                                }
                            ),
                    ) { newColor ->
                        val hex =
                            String.format(
                                "#%02x%02x%02x",
                                (newColor.red * 255).toInt(),
                                (newColor.green * 255).toInt(),
                                (newColor.blue * 255).toInt(),
                            )
                        when (activeTab) {
                            "accent" -> accent = hex
                            "bg" -> bg = hex
                            "secondary" -> secondary = hex
                            "text" -> textColor = hex
                            else -> surface = hex
                        }
                    }
                    var customHex by
                        remember(activeTab) {
                            mutableStateOf(
                                when (activeTab) {
                                    "accent" -> accent
                                    "bg" -> bg
                                    "secondary" -> secondary
                                    "text" -> textColor
                                    else -> surface
                                }
                            )
                        }
                    Field(
                        customHex,
                        {
                            customHex = it
                            if (Regex("#[0-9A-Fa-f]{6}").matches(it))
                                when (activeTab) {
                                    "accent" -> accent = it
                                    "bg" -> bg = it
                                    "secondary" -> secondary = it
                                    "text" -> textColor = it
                                    else -> surface = it
                                }
                        },
                        "Cor HEX",
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(24.dp).background(color(accent), CircleShape))
                            Text("Destaque", fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(24.dp).background(color(bg), CircleShape))
                            Text("Fundo", fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(24.dp).background(color(surface), CircleShape))
                            Text("Super", fontSize = 10.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    edit { it.copy(palette = Palette(accent, bg, surface, secondary, textColor)) }
                    customColor = false
                }) {
                    Text("Aplicar")
                }
            },
            dismissButton = { TextButton({ customColor = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun MediaTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick,
        shape = RoundedCornerShape(19.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Violet.copy(alpha = .15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.Outlined.Add,
                null,
                Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
