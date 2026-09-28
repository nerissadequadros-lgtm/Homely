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
import app.homely.platform.LocalThemeStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reusing a private pack copies its files, so deleting the source theme never breaks the draft. */
@Composable
fun ThemeComponentLibrary(
    vm: HomelyViewModel,
    state: HomelyState,
    kind: String,
    onDismiss: () -> Unit,
) {
    var standalone by remember { mutableStateOf(kind == "elements") }
    if (standalone) {
        StandaloneComponentPicker(vm, kind, onDismiss)
        return
    }
    val context = LocalContext.current
    val themes by
        produceState<List<LocalTheme>?>(null) {
            value = withContext(Dispatchers.IO) { LocalThemeStore(context).list() }
        }
    val available =
        themes.orEmpty().filter {
            if (kind == "icons") it.draft.icons.isNotEmpty()
            else it.draft.manifest.widgets.isNotEmpty()
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (kind == "icons") "Meus pacotes de ícones" else "Meus widgets salvos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton({ standalone = true }, Modifier.fillMaxWidth()) {
                    Text("Criações independentes por pasta")
                }
                when {
                    themes == null -> CircularProgressIndicator()
                    available.isEmpty() ->
                        Text(
                            "Salve um tema com seus ícones ou widgets em Salvar privado para encontrá-lo aqui."
                        )
                    else ->
                        LazyColumn(Modifier.heightIn(max = 350.dp)) {
                            items(available, key = { it.id }) { saved ->
                                OutlinedButton(
                                    onClick = {
                                        vm.action {
                                            val current = vm.state.value.draft
                                            val imported =
                                                withContext(Dispatchers.IO) {
                                                    copyThemeComponents(
                                                        saved.draft,
                                                        current,
                                                        kind,
                                                        File(context.filesDir, "component-packs"),
                                                    )
                                                }
                                            vm.editDraft { imported }
                                            vm.message(
                                                if (kind == "icons") "Ícones adicionados ao tema."
                                                else
                                                    "Widgets adicionados ao tema. Organize-os na tela inicial."
                                            )
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.busy,
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(saved.title)
                                        Text(
                                            if (kind == "icons") "${saved.draft.icons.size} ícones"
                                            else "${saved.draft.manifest.widgets.size} widgets",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Concluir") } },
    )
}

internal fun copyThemeComponents(
    source: Draft,
    current: Draft,
    kind: String,
    directory: File,
): Draft {
    require(kind in listOf("icons", "widgets"))
    val fontsNeeded =
        if (kind == "icons") source.icons.mapNotNull { it.fontId }.toSet()
        else
            source.manifest.widgets
                .flatMap { it.displayLayers() }
                .flatMap { it.allLayers() }
                .mapNotNull { it.fontId }
                .toSet()
    val fontSpecs = source.manifest.fonts.filter { it.id in fontsNeeded }
    val mergedFonts = (current.manifest.fonts.filterNot { it.id in fontsNeeded } + fontSpecs)
    require(mergedFonts.size <= 20) {
        "O tema comporta até 20 fontes. Remova fontes antes de importar este pacote."
    }
    val importedWidgetIds = source.manifest.widgets.map { it.id }.toSet()
    val widgets =
        if (kind == "widgets")
            current.manifest.widgets.filterNot { it.id in importedWidgetIds } +
                source.manifest.widgets
        else current.manifest.widgets
    require(widgets.size <= 12) {
        "O tema comporta até 12 widgets. Remova alguns antes de importar este pacote."
    }
    val target = File(directory, UUID.randomUUID().toString())
    check(target.mkdirs()) { "Não foi possível preparar os arquivos do pacote." }
    val copies = mutableMapOf<String, String>()
    fun copy(path: String): String =
        copies.getOrPut(path) {
            val file = File(path)
            require(file.isFile) { "Um arquivo do pacote não está mais disponível." }
            file
                .copyTo(File(target, UUID.randomUUID().toString() + "." + file.extension.take(12)))
                .path
        }
    try {
        val fontFiles =
            source.fontFiles.filterKeys { it in fontsNeeded }.mapValues { copy(it.value) }
        require(fontFiles.keys.containsAll(fontsNeeded)) {
            "O pacote está sem um arquivo de fonte."
        }
        val icons =
            if (kind == "icons") {
                val imported =
                    source.icons.map {
                        it.copy(
                            path = copy(it.path),
                            sourcePath = it.sourcePath?.let(::copy),
                            statePaths = it.statePaths.mapValues { (_, path) -> copy(path) },
                        )
                    }
                current.icons.filterNot { icon ->
                    imported.any { it.packageName == icon.packageName }
                } + imported
            } else current.icons
        val images =
            if (kind == "widgets") {
                val needed = source.manifest.widgets.flatMap { it.imageKeys() }.toSet()
                require(source.widgetImages.keys.containsAll(needed)) {
                    "O pacote está sem uma imagem de widget."
                }
                current.widgetImages +
                    source.widgetImages.filterKeys { it in needed }.mapValues { copy(it.value) }
            } else current.widgetImages
        return current.copy(
            manifest = current.manifest.copy(widgets = widgets, fonts = mergedFonts),
            icons = icons,
            widgetImages = images,
            fontFiles = current.fontFiles + fontFiles,
        )
    } catch (error: Exception) {
        target.deleteRecursively()
        throw error
    }
}
