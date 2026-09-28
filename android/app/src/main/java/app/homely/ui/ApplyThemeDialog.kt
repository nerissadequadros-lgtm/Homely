package app.homely.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.homely.LauncherActivity
import app.homely.data.*
import app.homely.platform.LocalThemeStore

@Composable
fun ApplyThemeDialog(
    vm: HomelyViewModel,
    draft: Draft? = null,
    post: ThemePost? = null,
    saved: LocalTheme? = null,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { LocalThemeStore(ctx) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<LocalTheme?>(null) }
    fun activate(theme: LocalTheme) {
        vm.action {
            try {
                store.activate(theme)
                vm.applied(post?.id ?: theme.id)
                vm.refreshPrivateThemes()
                ctx.startActivity(Intent(ctx, LauncherActivity::class.java))
                onDismiss()
            } catch (e: Exception) {
                error = e.message
            } finally {
                working = false
            }
        }
    }
    val role =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val theme = pending
            if (LauncherActivity.isDefault(ctx) && theme != null) activate(theme)
            else {
                store.clearPending()
                working = false
                error =
                    "O tema ficou salvo no aparelho. Escolha Homely como tela inicial para aplicar ícones e widgets."
            }
        }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Aplicar tema completo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Wallpaper, ícones, widgets e organização passam a compor sua tela inicial Homely."
                )
                Text(
                    if (LauncherActivity.isDefault(ctx))
                        "A alteração será aplicada à sua tela inicial."
                    else
                        "Na próxima tela, escolha Homely como app de início. Você pode trocar novamente nas configurações do Android.",
                    fontSize = 13.sp,
                )
                Text(
                    "Uma cópia privada fica em Meus salvos, disponível sem internet.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    working = true
                    error = null
                    vm.action {
                        try {
                            val local =
                                saved
                                    ?: if (draft != null) store.save(draft)
                                    else store.download(requireNotNull(post))
                            pending = local
                            vm.refreshPrivateThemes()
                            if (LauncherActivity.isDefault(ctx)) activate(local)
                            else {
                                store.stage(local)
                                role.launch(LauncherActivity.permissionIntent(ctx))
                            }
                        } catch (e: Exception) {
                            working = false
                            error = e.message
                        }
                    }
                },
                enabled = !working,
            ) {
                Text(if (working) "Preparando…" else "Aplicar tema")
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !working) { Text("Agora não") } },
    )
}
