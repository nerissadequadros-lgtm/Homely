package app.homely.ui

import android.Manifest
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.homely.data.WidgetSpec
import app.homely.platform.*
import kotlinx.coroutines.*

@Composable
fun UsageModeSettings(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val modes = remember { UsageModes(ctx) }
    var revision by remember { mutableIntStateOf(0) }
    val support = remember(revision) { LauncherCapabilities.inspect(ctx) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onBack) { Text("Voltar") }
        Text("Seu Homely, do seu jeito", style = MaterialTheme.typography.headlineMedium)
        Text("Combine os modos sem perder as suas criações.")
        revision.let {
            listOf(
                    Triple("Só ícones", modes.icons, { v: Boolean -> modes.icons = v }),
                    Triple(
                        "Widgets flutuantes",
                        modes.floating,
                        { v: Boolean ->
                            modes.floating = v
                            if (!v) ctx.stopService(Intent(ctx, FloatingWidgetService::class.java))
                        },
                    ),
                    Triple("Tema completo", modes.home, { v: Boolean -> modes.home = v }),
                )
                .forEach { (label, checked, change) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label)
                        Switch(
                            checked,
                            {
                                change(it)
                                modes.chosen = true
                                revision++
                            },
                        )
                    }
                }
        }
        Text(
            "Tema completo pede o Homely como tela inicial somente ao aplicar. Desmarcar aqui não muda o launcher escolhido nas configurações do Android.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text("Launcher atual · ${support.name}", style = MaterialTheme.typography.titleMedium)
        Text(
            if (support.shortcuts) "Atalhos fixados disponíveis: o Android pedirá sua confirmação."
            else "Este launcher não aceita solicitações de atalho fixado."
        )
        Text(support.iconPacks)
        Text(
            "Atalhos não removem o ícone original. Você pode remover o antigo manualmente da tela inicial.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text("Widgets sem trocar o launcher", style = MaterialTheme.typography.titleMedium)
        Text(
            "No Studio de widgets, escolha Adicionar à tela inicial para um widget Android ou Flutuar na tela inicial para uma sobreposição."
        )
        Text(
            "Widgets flutuantes ficam ocultos durante o bloqueio e em outros apps. O acesso de uso serve apenas para identificar a tela inicial, processado no aparelho, sem envio ao servidor.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            {
                ctx.stopService(Intent(ctx, FloatingWidgetService::class.java))
                modes.floating = false
                revision++
            },
            Modifier.fillMaxWidth(),
        ) {
            Text("Desativar widgets flutuantes")
        }
        OutlinedButton(
            { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
            Modifier.fillMaxWidth(),
        ) {
            Text("Escolher launcher nas configurações")
        }
    }
}

@Composable
fun FloatingWidgetSetup(
    vm: HomelyViewModel,
    spec: WidgetSpec,
    images: Map<String, String>,
    fonts: Map<String, String> = emptyMap(),
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    var notice by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            revision++
        }
    val overlay = remember(revision) { Settings.canDrawOverlays(ctx) }
    val usage = remember(revision) { FloatingWidgetService.hasUsageAccess(ctx) }
    val notifications =
        Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget flutuante") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Mantém seu launcher atual. O widget aparece sobre a tela inicial e pode ser movido pela alça."
                )
                Text(
                    "Ele fica oculto em outros apps e durante o bloqueio. Para reconhecer a tela inicial, autorize o acesso de uso. Esses dados não saem do aparelho."
                )
                if (!overlay)
                    OutlinedButton({
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + ctx.packageName),
                            )
                        )
                    }) {
                        Text("1 · Permitir sobreposição")
                    }
                if (!usage)
                    OutlinedButton({
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_USAGE_ACCESS_SETTINGS,
                                Uri.parse("package:" + ctx.packageName),
                            )
                        )
                    }) {
                        Text("2 · Identificar a tela inicial")
                    }
                if (!notifications)
                    OutlinedButton({
                        if (Build.VERSION.SDK_INT >= 33)
                            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text("3 · Mostrar notificação e pausa")
                    }
                Text(
                    "Uma notificação permanece visível enquanto o serviço está ativo, com Pausar por hoje.",
                    style = MaterialTheme.typography.bodySmall,
                )
                notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    vm.action {
                        try {
                            val token =
                                withContext(Dispatchers.IO) {
                                    StudioWidgets.save(ctx, spec, images, fonts)
                                }
                            FloatingWidgetService.start(ctx, token)
                            onDismiss()
                            vm.message("Widget ativo. Volte à tela inicial para vê-lo.")
                        } catch (e: Exception) {
                            notice = e.message
                        }
                    }
                },
                enabled = overlay && usage && notifications,
            ) {
                Text("Ativar")
            }
        },
        dismissButton = { TextButton(onDismiss) { Text("Agora não") } },
    )
}
