package app.homely.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.homely.platform.FontLibrary
import kotlinx.coroutines.*

@Composable
fun StudioNumber(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, fontSize = 11.sp)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(
                { onChange((value - step).coerceIn(range)) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.width(30.dp),
            ) {
                Text("−")
            }
            var text by
                remember(value) {
                    mutableStateOf(
                        if (value % 1f == 0f) value.toInt().toString()
                        else "%.1f".format(java.util.Locale.US, value)
                    )
                }
            OutlinedTextField(
                text,
                {
                    text = it
                    it.toFloatOrNull()?.takeIf { it.isFinite() && it in range }?.let(onChange)
                },
                Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
            )
            TextButton(
                { onChange((value + step).coerceIn(range)) },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.width(30.dp),
            ) {
                Text("+")
            }
        }
    }
}

@Composable
fun StudioSection(
    title: String,
    initial: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initial) }
    Column {
        TextButton({ expanded = !expanded }, Modifier.fillMaxWidth()) {
            Text(title, Modifier.weight(1f))
            Text(if (expanded) "−" else "+")
        }
        androidx.compose.animation.AnimatedVisibility(expanded) {
            Column(
                Modifier.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
        HorizontalDivider()
    }
}

@Composable
fun StudioColorPicker(hex: String, palette: List<String>, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("studio-colors", 0) }
    var open by remember { mutableStateOf(false) }
    val recent =
        remember(open) {
            prefs.getString("recent", "")!!.split(',').filter {
                Regex("#[0-9a-fA-F]{6}").matches(it)
            }
        }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items((listOf(hex) + palette + recent).distinct().take(12)) { c ->
            Box(
                Modifier.size(32.dp)
                    .background(color(c), androidx.compose.foundation.shape.CircleShape)
                    .clickable { onChange(c) }
            )
        }
        item { TextButton({ open = true }) { Text("Cor personalizada") } }
    }
    if (open) {
        var value by remember { mutableStateOf(color(hex)) }
        fun asHex() =
            String.format(
                "#%02X%02X%02X",
                (value.red * 255).toInt(),
                (value.green * 255).toInt(),
                (value.blue * 255).toInt(),
            )
        var customHex by remember(value) { mutableStateOf(asHex()) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Cor personalizada") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColorWheel(Modifier.fillMaxWidth().height(190.dp), value) { value = it }
                    val hsv =
                        FloatArray(3).also {
                            android.graphics.Color.RGBToHSV(
                                (value.red * 255).toInt(),
                                (value.green * 255).toInt(),
                                (value.blue * 255).toInt(),
                                it,
                            )
                        }
                    Text("Luminosidade", fontSize = 12.sp)
                    Slider(hsv[2], { value = Color.hsv(hsv[0], hsv[1], it) }, valueRange = 0f..1f)
                    listOf("R", "G", "B").forEachIndexed { index, label ->
                        StudioNumber(
                            label,
                            listOf(value.red, value.green, value.blue)[index] * 255,
                            { v ->
                                value =
                                    when (index) {
                                        0 -> value.copy(red = v / 255)
                                        1 -> value.copy(green = v / 255)
                                        else -> value.copy(blue = v / 255)
                                    }
                            },
                            0f..255f,
                        )
                    }
                    Field(
                        customHex,
                        {
                            customHex = it
                            if (Regex("#[0-9a-fA-F]{6}").matches(it)) value = color(it)
                        },
                        "Hex",
                    )
                    Box(Modifier.fillMaxWidth().height(30.dp).background(value))
                }
            },
            confirmButton = {
                TextButton({
                    val chosen = asHex()
                    onChange(chosen)
                    prefs
                        .edit()
                        .putString(
                            "recent",
                            (listOf(chosen) + recent).distinct().take(8).joinToString(","),
                        )
                        .apply()
                    open = false
                }) {
                    Text("Usar cor")
                }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun StudioFontPicker(
    vm: HomelyViewModel,
    fontFamily: String,
    fontId: String?,
    onSelect: (String, String?) -> Unit,
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.action {
                    require(state.draft.fontFiles.size < 20) { "Use até 20 fontes por tema." }
                    val (font, path) = withContext(Dispatchers.IO) { FontLibrary.import(ctx, uri) }
                    vm.editDraft {
                        it.copy(
                            manifest = it.manifest.copy(fonts = it.manifest.fonts + font),
                            fontFiles = it.fontFiles + (font.id to path),
                        )
                    }
                    onSelect("sans", font.id)
                }
        }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(
            listOf(
                "sans" to "Sem serifa",
                "serif" to "Serifada",
                "display" to "Display",
                "mono" to "Mono",
                "hand" to "Manuscrita",
            )
        ) { (id, name) ->
            Pill(name, fontId == null && fontFamily == id) { onSelect(id, null) }
        }
        items(state.draft.manifest.fonts) { font ->
            Pill(font.name, font.id == fontId) { onSelect("sans", font.id) }
        }
    }
    OutlinedButton(
        {
            picker.launch(
                arrayOf(
                    "font/ttf",
                    "font/otf",
                    "application/x-font-ttf",
                    "application/octet-stream",
                    "*/*",
                )
            )
        },
        Modifier.fillMaxWidth(),
    ) {
        Text("Enviar fonte .ttf ou .otf")
    }
}
