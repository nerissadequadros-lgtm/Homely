package app.homely.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.homely.LauncherActivity
import app.homely.data.*
import app.homely.platform.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LauncherIconApplyDialog(items: List<CatalogIcon>, vm: HomelyViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val available =
        remember(items) {
            items.filter {
                ctx.packageManager.getLaunchIntentForPackage(it.asset.packageName.orEmpty()) != null
            }
        }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Aplicar ícones no Homely") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    available.size.toString() +
                        " ícones serão atualizados na sua tela inicial e na lista de aplicativos."
                )
                if (available.size < items.size)
                    Text(
                        (items.size - available.size).toString() +
                            " apps não estão instalados e serão ignorados."
                    )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    busy = true
                    vm.action {
                        try {
                            val store = LocalThemeStore(ctx)
                            val draft =
                                store.active()?.draft
                                    ?: Draft(manifest = Manifest(title = "Meus ícones"))
                            val icons = available.map { item ->
                                val downloaded = NativeActions.asset(vm.repo, item.asset)
                                val ext =
                                    when (item.asset.mime) {
                                        "image/png" -> "png"
                                        "image/webp" -> "webp"
                                        else -> "jpg"
                                    }
                                val file =
                                    withContext(Dispatchers.IO) {
                                        downloaded.copyTo(
                                            File(ctx.cacheDir, item.asset.id + "." + ext),
                                            overwrite = true,
                                        )
                                    }
                                DraftIcon(file.path, item.asset.packageName!!, item.asset.name)
                            }
                            val merged =
                                draft.icons.filterNot { old ->
                                    icons.any { it.packageName == old.packageName }
                                } + icons
                            val saved = store.save(draft.copy(icons = merged))
                            store.activate(saved)
                            vm.refreshPrivateThemes()
                            ctx.startActivity(Intent(ctx, LauncherActivity::class.java))
                            onDismiss()
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && available.isNotEmpty(),
            ) {
                Text("Aplicar seleção")
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}
