package app.homely.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.homely.data.*

@Composable
internal fun ThemeBuilderFrame(
    draft: Draft,
    section: Int,
    onSection: (Int) -> Unit,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    history: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expandedPreview by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(true) }
    var previewSession by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    LaunchedEffect(section) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyRow(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(builderSteps) { index, label ->
                FilterChip(
                    selected = section == index,
                    onClick = { onSection(index) },
                    label = { Text("${index + 1}  $label") },
                    modifier = Modifier.testTag("builder-step-$index"),
                )
            }
        }
        LinearProgressIndicator(
            progress = { (section + 1).toFloat() / builderSteps.size },
            Modifier.fillMaxWidth().padding(top = 4.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth >= 760.dp
            val phoneHeight = if (wide) (maxHeight - 130.dp).coerceIn(180.dp, 510.dp) else 218.dp
            val preview: @Composable (Modifier) -> Unit = { modifier ->
                Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(Color(0xFF39AA80), CircleShape))
                            Text(
                                "  PRÉVIA INTERATIVA",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton({ previewSession++ }, Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    "Reiniciar prévia",
                                    Modifier.size(18.dp),
                                )
                            }
                            IconButton({ expandedPreview = true }, Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Outlined.OpenInFull,
                                    "Ampliar prévia",
                                    Modifier.size(18.dp),
                                )
                            }
                            if (!wide)
                                IconButton({ showPreview = !showPreview }, Modifier.size(36.dp)) {
                                    Icon(
                                        if (showPreview) Icons.Outlined.ExpandLess
                                        else Icons.Outlined.ExpandMore,
                                        if (showPreview) "Recolher prévia" else "Mostrar prévia",
                                    )
                                }
                        }
                        if (showPreview || wide) {
                            key(previewSession) {
                                BuilderPhone(draft, Modifier.height(phoneHeight))
                            }
                        }
                    }
                }
            }
            val properties: @Composable (Modifier) -> Unit = { modifier ->
                Column(
                    modifier.testTag("builder-properties").verticalScroll(scroll).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (section == builderSteps.lastIndex) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onImport) { Text("Importar") }
                            OutlinedButton(onExport) { Text("Exportar pacote") }
                        }
                    }
                    history()
                    content()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (section > 0)
                            OutlinedButton({ onSection(section - 1) }, Modifier.weight(1f)) {
                                Text("Anterior")
                            }
                        if (section < builderSteps.lastIndex)
                            Button(
                                { onSection(section + 1) },
                                Modifier.weight(1f).testTag("builder-next"),
                            ) {
                                Text("Continuar")
                            }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (wide)
                Row(Modifier.fillMaxSize()) {
                    preview(Modifier.width(340.dp).fillMaxHeight())
                    properties(Modifier.weight(1f).fillMaxHeight())
                }
            else
                Column(Modifier.fillMaxSize()) {
                    preview(Modifier.fillMaxWidth())
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)
                    )
                    properties(Modifier.weight(1f).fillMaxWidth())
                }
        }
    }
    if (expandedPreview)
        Dialog(
            onDismissRequest = { expandedPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BackHandler { expandedPreview = false }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.safeDrawingPadding()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Teste seu tema",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        TextButton({ previewSession++ }) { Text("Reiniciar") }
                        IconButton({ expandedPreview = false }) {
                            Icon(Icons.Outlined.Close, "Fechar prévia ampliada")
                        }
                    }
                    key(previewSession) {
                        ThemeExperience(
                            draft,
                            preview = true,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                        )
                    }
                }
            }
        }
}

@Composable
private fun BuilderPhone(draft: Draft, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .aspectRatio(.49f)
            .clip(RoundedCornerShape(20.dp))
            .border(3.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .padding(3.dp)
    ) {
        val factor = maxWidth.value / 380f
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * factor, density.fontScale)
        ) {
            ThemeExperience(draft, preview = true, modifier = Modifier.fillMaxSize())
        }
    }
}
