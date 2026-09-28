package app.homely.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.homely.data.*
import app.homely.platform.LocalThemeStore
import coil.compose.AsyncImage
import java.io.File

@Composable
fun PrivateLibrary(vm: HomelyViewModel, state: HomelyState, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var applying by remember { mutableStateOf<LocalTheme?>(null) }
    var deleting by remember { mutableStateOf<LocalTheme?>(null) }
    LaunchedEffect(Unit) { vm.refreshPrivateThemes() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack) { Text("Voltar") }
            Text("Meus salvos", style = MaterialTheme.typography.headlineMedium)
        }
        Text(
            "Privados · neste aparelho · disponíveis offline",
            Modifier.padding(horizontal = 22.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.privateThemes.isEmpty())
                item {
                    Text(
                        "Salve uma criação ou aplique um tema para começar sua biblioteca.",
                        Modifier.padding(vertical = 30.dp),
                    )
                }
            items(state.privateThemes, key = { it.id }) { theme ->
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AsyncImage(
                            theme.draft.wallpaperPath?.let { File(it) },
                            theme.title,
                            Modifier.width(76.dp).height(130.dp).clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(theme.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                theme.draft.icons.size.toString() +
                                    " ícones · " +
                                    theme.draft.manifest.widgets.size +
                                    " widgets",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row {
                                TextButton({ applying = theme }) { Text("Aplicar") }
                                TextButton({
                                    vm.editPrivate(theme)
                                    onBack()
                                }) {
                                    Text("Editar")
                                }
                            }
                            TextButton({ deleting = theme }) {
                                Text("Excluir do aparelho", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    applying?.let { ApplyThemeDialog(vm, saved = it, onDismiss = { applying = null }) }
    deleting?.let { theme ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Excluir " + theme.title + "?") },
            text = { Text("A cópia privada será removida deste aparelho.") },
            confirmButton = {
                TextButton({
                    vm.action {
                        LocalThemeStore(ctx).remove(theme.id)
                        vm.refreshPrivateThemes()
                    }
                    deleting = null
                }) {
                    Text("Excluir")
                }
            },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancelar") } },
        )
    }
}
