package app.homely.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.homely.data.*
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

@Composable
fun WidgetStudio(vm: HomelyViewModel, state: HomelyState) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<WidgetSpec?>(null) }
    val widgets = state.draft.manifest.widgets
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.action {
                    val spec =
                        withContext(Dispatchers.IO) {
                            val bytes =
                                ctx.contentResolver.openInputStream(uri)!!.use { input ->
                                    val buffer = ByteArray(65537)
                                    var size = 0
                                    while (size < buffer.size) {
                                        val n = input.read(buffer, size, buffer.size - size)
                                        if (n < 0) break
                                        size += n
                                    }
                                    buffer.copyOf(size)
                                }
                            require(bytes.size <= 65536) {
                                "O arquivo de configuração deve ter até 64 KB."
                            }
                            kotlinx.serialization.json.Json.decodeFromString<WidgetSpec>(
                                    bytes.decodeToString()
                                )
                                .validate()
                                .copy(id = UUID.randomUUID().toString(), imageAssetId = null)
                        }
                    require(widgets.size < 12) { "Use até 12 widgets." }
                    editing = spec
                }
        }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("Seu próprio widget", "Crie uma peça viva para compor seu tema.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ editing = WidgetSpec() }, enabled = widgets.size < 12) { Text("Criar widget") }
            OutlinedButton(
                { import.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                enabled = widgets.size < 12,
            ) {
                Text("Importar .hwidget")
            }
        }
        Text(
            "Relógio, data, bateria, texto ou imagem. Widgets de outros apps podem ser adicionados em Personalizar na tela inicial Homely.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        widgets.forEachIndexed { index, w ->
            ComposedWidget(w, state.draft.widgetImages[w.id], preview = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ editing = w }) { Text("Editar") }
                TextButton(
                    {
                        val next = widgets.toMutableList()
                        next[index] = widgets[index - 1]
                        next[index - 1] = w
                        vm.editDraft { it.copy(manifest = it.manifest.copy(widgets = next)) }
                    },
                    enabled = index > 0,
                ) {
                    Text("Subir")
                }
                TextButton({
                    vm.editDraft {
                        it.copy(
                            manifest = it.manifest.copy(widgets = widgets - w),
                            widgetImages = it.widgetImages - w.id,
                        )
                    }
                }) {
                    Text("Remover")
                }
            }
        }
    }
    editing?.let { value ->
        WidgetEditor(
            value,
            state.draft.widgetImages[value.id],
            vm,
            onDismiss = { editing = null },
        ) { result ->
            val next =
                if (widgets.any { it.id == result.id })
                    widgets.map { if (it.id == result.id) result else it }
                else widgets + result
            vm.editDraft { it.copy(manifest = it.manifest.copy(widgets = next)) }
            editing = null
        }
    }
}

@Composable
private fun WidgetEditor(
    initial: WidgetSpec,
    imagePath: String?,
    vm: HomelyViewModel,
    onDismiss: () -> Unit,
    onSave: (WidgetSpec) -> Unit,
) {
    val ctx = LocalContext.current
    var w by remember { mutableStateOf(initial) }
    val image =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) vm.widgetImage(w.id, uri)
        }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                vm.action {
                    withContext(Dispatchers.IO) {
                        ctx.contentResolver.openOutputStream(uri, "wt")!!.use {
                            it.write(vm.repo.json.encodeToString(w).toByteArray())
                        }
                    }
                    vm.message("Configuração exportada. Imagens são incluídas no pacote .homely.")
                }
        }
    val valid = runCatching { w.validate() }.isSuccess && (w.kind != "image" || imagePath != null)
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Desenhe seu widget", style = MaterialTheme.typography.titleLarge)
                if (runCatching { w.validate() }.isSuccess)
                    ComposedWidget(w, imagePath, preview = true)
                Field(w.name, { w = w.copy(name = it.take(60)) }, "Nome")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(
                        listOf(
                            "clock" to "Relógio",
                            "date" to "Data",
                            "battery" to "Bateria",
                            "text" to "Texto",
                            "image" to "Imagem",
                        )
                    ) { (kind, label) ->
                        Pill(label, w.kind == kind) {
                            w = w.copy(kind = kind, fontSize = if (kind == "text") 22 else 40)
                        }
                    }
                }
                if (w.kind == "text")
                    Field(
                        w.text,
                        { w = w.copy(text = it.take(300)) },
                        "Seu texto",
                        singleLine = false,
                    )
                if (w.kind == "image")
                    OutlinedButton({ image.launch("image/*") }) { Text("Enviar imagem do widget") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        w.foreground,
                        { w = w.copy(foreground = it) },
                        Modifier.weight(1f),
                        label = { Text("Texto HEX") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        w.background,
                        { w = w.copy(background = it) },
                        Modifier.weight(1f),
                        label = { Text("Fundo HEX") },
                        singleLine = true,
                    )
                }
                Field(w.accent, { w = w.copy(accent = it) }, "Destaque HEX")
                Text("Altura · " + w.height + " dp", fontSize = 12.sp)
                Slider(
                    w.height.toFloat(),
                    { w = w.copy(height = it.toInt()) },
                    valueRange = 64f..280f,
                )
                Text("Tamanho do texto · " + w.fontSize, fontSize = 12.sp)
                Slider(
                    w.fontSize.toFloat(),
                    { w = w.copy(fontSize = it.toInt()) },
                    valueRange = 12f..72f,
                )
                Text("Cantos · " + w.radius, fontSize = 12.sp)
                Slider(
                    w.radius.toFloat(),
                    { w = w.copy(radius = it.toInt()) },
                    valueRange = 0f..48f,
                )
                TextButton({ export.launch(w.name + ".hwidget") }, enabled = valid) {
                    Text("Exportar configuração")
                }
                Button({ onSave(w.validate()) }, Modifier.fillMaxWidth(), enabled = valid) {
                    Text("Usar no tema")
                }
                TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
        }
    }
}
