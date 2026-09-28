package app.homely.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.homely.data.*
import app.homely.platform.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun StandaloneComponentPicker(vm: HomelyViewModel, kind: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val singular =
        when (kind) {
            "icons" -> "icon"
            "elements" -> "element"
            else -> "widget"
        }
    val entries = remember { CreationFolders.entries(ctx, singular) }
    var folder by remember { mutableStateOf("Todas") }
    val icons = remember { if (kind == "icons") IconDesignLibrary.list(ctx) else emptyList() }
    val pieces = remember {
        if (kind == "elements") ElementLibrary.list(ctx)
        else entries.mapNotNull { e -> StudioWidgets.read(ctx, e.token)?.let { e.token to it } }
    }
    var choosingIcon by remember { mutableStateOf<SavedIconDesign?>(null) }
    fun includeIcon(icon: SavedIconDesign, app: InstalledApp? = null) {
        vm.action {
            val item =
                icon.toDraftIcon(
                    app?.packageName ?: icon.design.packageName,
                    app?.label ?: icon.name,
                )
            val current = vm.state.value.draft
            val copied =
                withContext(Dispatchers.IO) {
                    copyThemeComponents(
                        Draft(icons = listOf(item)),
                        current,
                        "icons",
                        File(ctx.filesDir, "component-packs"),
                    )
                }
            vm.editDraft { copied }
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (kind) {
                    "icons" -> "Meus ícones"
                    "elements" -> "Meus elementos"
                    else -> "Meus widgets"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BuilderChoices(
                    folder,
                    (listOf("Todas") + entries.map { it.folder }.distinct()).map { it to it },
                ) {
                    folder = it
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (kind == "icons") {
                        items(
                            icons.filter { i ->
                                folder == "Todas" ||
                                    entries.any { it.token == i.token && it.folder == folder }
                            }
                        ) { icon ->
                            TextButton(
                                {
                                    if (icon.design.packageName.isBlank()) choosingIcon = icon
                                    else includeIcon(icon)
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text(icon.name)
                            }
                        }
                        if (icons.isEmpty()) item { Text("Nenhum ícone salvo.") }
                    } else {
                        items(
                            pieces.filter { i ->
                                folder == "Todas" ||
                                    entries.any { it.token == i.first && it.folder == folder }
                            }
                        ) { (_, saved) ->
                            TextButton(
                                {
                                    vm.action {
                                        val current = vm.state.value.draft
                                        val source = standaloneWidgetDraft(saved)
                                        var copied =
                                            withContext(Dispatchers.IO) {
                                                copyThemeComponents(
                                                    source,
                                                    current,
                                                    "widgets",
                                                    File(ctx.filesDir, "component-packs"),
                                                )
                                            }
                                        val widget = copied.manifest.widgets.last()
                                        if (
                                            copied.manifest.layout.kit.none {
                                                it.reference == widget.id
                                            }
                                        ) {
                                            val page =
                                                (copied.manifest.layout.kit.maxOfOrNull { it.page }
                                                        ?: 0)
                                                    .coerceAtMost(20)
                                            copied =
                                                copied.copy(
                                                    manifest =
                                                        copied.manifest.copy(
                                                            layout =
                                                                copied.manifest.layout.copy(
                                                                    kit =
                                                                        copied.manifest.layout.kit +
                                                                            HomeItem(
                                                                                kind = "widget",
                                                                                reference =
                                                                                    widget.id,
                                                                                x = .05f,
                                                                                y = .08f,
                                                                                width = .9f,
                                                                                height = .32f,
                                                                                page = page,
                                                                            )
                                                                )
                                                        )
                                                )
                                        }
                                        vm.editDraft { copied }
                                        onDismiss()
                                    }
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text(saved.spec.name)
                            }
                        }
                        if (pieces.isEmpty()) item { Text("Nenhuma criação salva.") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Concluir") } },
    )
    if (choosingIcon != null) {
        val apps by
            produceState<List<InstalledApp>>(emptyList()) {
                value = withContext(Dispatchers.IO) { NativeActions.apps(ctx) }
            }
        var query by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { choosingIcon = null },
            title = { Text("Aplicar ao aplicativo") },
            text = {
                Column {
                    Field(query, { query = it }, "Buscar")
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(apps.filter { it.label.contains(query, true) }) { app ->
                            TextButton({
                                choosingIcon?.let { includeIcon(it, app) }
                                choosingIcon = null
                            }) {
                                Text(app.label)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ choosingIcon = null }) { Text("Cancelar") } },
        )
    }
}

internal fun standaloneWidgetDraft(saved: SavedWidget): Draft {
    val media = mutableMapOf<String, String>()
    val layers =
        saved.spec.displayLayers().map { l ->
            val (clone, images) = cloneElement(l, saved.images)
            media.putAll(images)
            clone
        }
    val backgroundId =
        saved.spec.backgroundImageId?.let { old ->
            UUID.randomUUID().toString().also { fresh ->
                saved.images[old]?.let { media[fresh] = it }
            }
        }
    val widget =
        saved.spec.copy(
            id = UUID.randomUUID().toString(),
            kind = "custom",
            layers = layers,
            backgroundImageId = backgroundId,
            backgroundImageAssetId = null,
            imageAssetId = null,
        )
    return Draft(
        manifest =
            Manifest(
                widgets = listOf(widget),
                fonts = saved.fonts.keys.map { ThemeFont(id = it, name = "Fonte importada") },
            ),
        widgetImages = media,
        fontFiles = saved.fonts,
    )
}
