package app.homely.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import app.homely.data.*

internal fun starterElement(source: String = "wifi"): WidgetLayer {
    val part =
        WidgetLayer(
            type = if (source == "clock") "clock" else if (source == "battery") "ring" else "icon",
            dataSource = if (source == "clock") "none" else source,
            x = .08f,
            y = .15f,
            width = .32f,
            height = .7f,
            fontSize = 28,
            tapAction = "none",
        )
    val label =
        WidgetLayer(
            type = "text",
            text =
                when (source) {
                    "clock" -> "Meu relógio"
                    "battery" -> "Bateria"
                    else -> "Wi-Fi"
                },
            x = .46f,
            y = .2f,
            width = .48f,
            height = .6f,
            fontSize = 20,
            tapAction = "none",
        )
    return WidgetLayer(
        type = "element",
        width = 1f,
        height = 1f,
        dataSource = if (source == "clock") "none" else source,
        components = listOf(part, label),
        elementBackground = "#252136",
        elementRadius = 22,
        animation = "fade",
        states =
            listOf(
                ElementState(name = "Ativo", condition = "enabled"),
                ElementState(name = "Desativado", condition = "disabled", opacity = .5f),
                ElementState(name = "Conectado", condition = "connected"),
                ElementState(name = "Sem conexão", condition = "disconnected"),
                ElementState(name = "Tocado", condition = "touched", scale = .94f),
                ElementState(name = "Pressionado", condition = "pressed", scale = .9f),
            ),
    )
}

@Composable
internal fun ElementCompositionEditor(
    root: WidgetLayer,
    name: String,
    images: Map<String, String>,
    fonts: Map<String, String>,
    vm: HomelyViewModel,
    onName: (String) -> Unit,
    onRoot: (WidgetLayer) -> Unit,
    onImage: (String) -> Unit,
    onMedia: (Map<String, String>) -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
) {
    var stateId by remember(root.id) { mutableStateOf<String?>(null) }
    var selected by remember(root.id) { mutableStateOf<String?>(null) }
    var panel by remember(root.id) { mutableStateOf("Camadas") }
    var testing by remember(root.id) { mutableStateOf(false) }
    val state = root.states.firstOrNull { it.id == stateId }
    val parts = state?.components ?: root.components
    LaunchedEffect(stateId) {
        if (state != null && state.components == null) {
            val copies = root.components.map { cloneElement(it, images) }
            onMedia(copies.flatMap { it.second.entries }.associate { it.toPair() })
            onRoot(
                root.copy(
                    states =
                        root.states.map {
                            if (it.id == state.id)
                                it.copy(components = copies.map { pair -> pair.first })
                            else it
                        }
                )
            )
        }
    }
    fun changeParts(value: List<WidgetLayer>) {
        if (state == null) onRoot(root.copy(components = value))
        else
            onRoot(
                root.copy(
                    states =
                        root.states.map {
                            if (it.id == state.id) it.copy(components = value) else it
                        }
                )
            )
    }
    fun changePart(value: WidgetLayer) =
        changeParts(parts.map { if (it.id == value.id) value else it })
    val preview =
        root.copy(states = state?.let { listOf(it.copy(condition = "always")) } ?: emptyList())
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (testing) "Simulação ao vivo" else "Editando: ${state?.name ?: "Normal"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            FilterChip(testing, { testing = !testing }, label = { Text("Testar") })
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    stateId == null,
                    {
                        stateId = null
                        selected = null
                    },
                    label = { Text("Normal") },
                )
            }
            items(root.states, key = { it.id }) { s ->
                FilterChip(
                    stateId == s.id,
                    {
                        stateId = s.id
                        selected = null
                        testing = false
                    },
                    label = { Text(s.name) },
                )
            }
            item {
                IconButton({
                    if (root.states.size < 12) {
                        val s = ElementState(name = "Novo estado", condition = "toggled")
                        onRoot(root.copy(states = root.states + s))
                        stateId = s.id
                        panel = "Estado"
                    }
                }) {
                    Icon(Icons.Outlined.Add, "Adicionar estado")
                }
            }
        }
        if (testing)
            Box(Modifier.fillMaxWidth().height(230.dp).padding(16.dp)) {
                WidgetLayerContent(root, images, true, Modifier.fillMaxSize(), fonts)
            }
        else
            WidgetCanvas(
                WidgetSpec(
                    name = name.ifBlank { "Elemento" },
                    background = state?.background ?: root.elementBackground ?: "#171C27",
                    height = 200,
                    radius = root.elementRadius.coerceAtMost(48),
                    layers = parts,
                ),
                images,
                fonts,
                selected,
                {
                    selected = it
                    panel = "Propriedades"
                },
                ::changePart,
                Modifier.fillMaxWidth().height(230.dp).testTag("element-canvas"),
            )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf("Camadas", "Assets", "Propriedades", "Estado", "Interações")) { item ->
                FilterChip(panel == item, { panel = item }, label = { Text(item) })
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (panel) {
                "Camadas" -> {
                    Field(name, onName, "Nome do elemento")
                    BuilderChoices(
                        "",
                        listOf(
                            "wifi" to "Base Wi-Fi",
                            "clock" to "Base relógio",
                            "battery" to "Base bateria",
                        ),
                    ) { source ->
                        onRoot(starterElement(source))
                        selected = null
                        stateId = null
                    }
                    parts
                        .sortedByDescending { it.zIndex }
                        .forEach { part ->
                            Surface(
                                onClick = {
                                    selected = part.id
                                    panel = "Propriedades"
                                },
                                color =
                                    if (selected == part.id)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(widgetItemIcon(part.type), null)
                                    Text(
                                        part.text.ifBlank {
                                            widgetKinds
                                                .firstOrNull { it.first == part.type }
                                                ?.second ?: part.type
                                        },
                                        Modifier.weight(1f).padding(10.dp),
                                        maxLines = 1,
                                    )
                                    IconButton({ changePart(part.copy(visible = !part.visible)) }) {
                                        Icon(
                                            if (part.visible) Icons.Outlined.Visibility
                                            else Icons.Outlined.VisibilityOff,
                                            "Mostrar ou ocultar",
                                        )
                                    }
                                    IconButton({ changeParts(parts - part) }) {
                                        Icon(Icons.Outlined.Delete, "Remover camada")
                                    }
                                }
                            }
                        }
                    if (parts.isEmpty()) Text("Adicione uma imagem, texto ou indicador em Assets.")
                    if (state != null)
                        Text(
                            "As alterações aqui pertencem apenas ao estado ${state.name}.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onDone) { Text("Concluir") }
                        Button(onSave) { Text("Salvar elemento") }
                    }
                }
                "Assets" -> {
                    Text("Adicionar camada", style = MaterialTheme.typography.titleMedium)
                    BuilderChoices("", elementKinds) { type ->
                        if (parts.size < 16) {
                            val l =
                                WidgetLayer(
                                    type = type,
                                    text = if (type == "text") "Seu texto" else "",
                                    x = .1f,
                                    y = .1f,
                                    width = .5f,
                                    height = .5f,
                                    zIndex = parts.size,
                                    tapAction = "none",
                                )
                            changeParts(parts + l)
                            selected = l.id
                            panel = "Propriedades"
                            if (type == "image") onImage(l.id)
                        }
                    }
                    Text(
                        "Use imagens próprias em qualquer camada ou estado. Até 16 camadas por composição.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                "Propriedades" -> {
                    val l = parts.firstOrNull { it.id == selected }
                    if (l == null) Text("Selecione uma camada no canvas ou na lista.")
                    else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({
                                onImage(l.id)
                                changePart(l.copy(type = "image", imageAssetId = null))
                            }) {
                                Text("Substituir imagem")
                            }
                            IconButton({
                                if (parts.size < 16) {
                                    val (clone, media) = cloneElement(l, images)
                                    val copy = clone.copy(zIndex = parts.size)
                                    onMedia(media)
                                    changeParts(parts + copy)
                                    selected = copy.id
                                }
                            }) {
                                Icon(Icons.Outlined.ContentCopy, "Duplicar")
                            }
                        }
                        BuilderSlider("X", "", (l.x * 100).toInt(), 0..95, "%") {
                            changePart(l.copy(x = (it / 100f).coerceAtMost(1 - l.width)))
                        }
                        BuilderSlider("Y", "", (l.y * 100).toInt(), 0..95, "%") {
                            changePart(l.copy(y = (it / 100f).coerceAtMost(1 - l.height)))
                        }
                        BuilderSlider("Largura", "", (l.width * 100).toInt(), 5..100, "%") {
                            changePart(
                                l.copy(width = it / 100f, x = l.x.coerceAtMost(1 - it / 100f))
                            )
                        }
                        BuilderSlider("Altura", "", (l.height * 100).toInt(), 5..100, "%") {
                            changePart(
                                l.copy(height = it / 100f, y = l.y.coerceAtMost(1 - it / 100f))
                            )
                        }
                        BuilderSlider("Rotação", "", l.rotation.toInt(), -180..180, "°") {
                            changePart(l.copy(rotation = it.toFloat()))
                        }
                        BuilderSlider("Profundidade", "", l.zIndex, 0..31) {
                            changePart(l.copy(zIndex = it))
                        }
                        ElementProperties(l, images, ::changePart, onImage, vm)
                    }
                }
                "Estado" -> {
                    if (state == null) {
                        Text("A composição Normal é usada quando nenhuma condição corresponde.")
                        Text("Fonte de dados", style = MaterialTheme.typography.titleSmall)
                        BuilderChoices(root.dataSource, elementSources.map { it to it }) {
                            onRoot(root.copy(dataSource = it))
                        }
                        StudioColorPicker(
                            root.elementBackground ?: "#171C27",
                            listOf("#171C27", "#FDE7ED", "#7965FF"),
                        ) {
                            onRoot(root.copy(elementBackground = it))
                        }
                    } else {
                        ElementProperties(
                            root.copy(states = listOf(state)),
                            images,
                            { changed ->
                                onRoot(
                                    changed.copy(
                                        states =
                                            root.states.mapNotNull {
                                                if (it.id == state.id) changed.states.firstOrNull()
                                                else it
                                            }
                                    )
                                )
                            },
                            onImage,
                            vm,
                            initialTab = "Estados",
                        )
                        TextButton({
                            onRoot(root.copy(states = root.states.filterNot { it.id == state.id }))
                            stateId = null
                        }) {
                            Text("Excluir estado")
                        }
                        TextButton({
                            onRoot(
                                root.copy(
                                    states =
                                        root.states.map {
                                            if (it.id == state.id) it.copy(components = null)
                                            else it
                                        }
                                )
                            )
                        }) {
                            Text("Restaurar camadas do estado Normal")
                        }
                    }
                }
                else ->
                    ElementProperties(root, images, onRoot, onImage, vm, initialTab = "Interações")
            }
        }
    }
}
