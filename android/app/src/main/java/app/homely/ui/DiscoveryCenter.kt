package app.homely.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.homely.data.DiscoveryClient

@Composable
fun DiscoveryCenter(vm: HomelyViewModel, state: HomelyState, onOpenTheme: () -> Unit) {
    val ctx = LocalContext.current
    val client = remember { DiscoveryClient(ctx) }
    var enabled by remember { mutableStateOf(client.enabled()) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.refreshDiscovery()
        }
    LaunchedEffect(Unit) { vm.refreshDiscovery() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Suas descobertas", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Seleções de temas que acompanham o seu gosto.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (state.profile == null)
            item { Button({ vm.askAuth() }) { Text("Entrar para receber recomendações") } }
        else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Descobertas diárias", Modifier.weight(1f))
                    Switch(
                        enabled,
                        {
                            enabled = it
                            client.enabled(it)
                            vm.refreshDiscovery()
                            if (it && Build.VERSION.SDK_INT >= 33)
                                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        },
                    )
                }
                if (
                    enabled &&
                        Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            ctx,
                            android.Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                )
                    OutlinedButton({
                        permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text("Permitir avisos no aparelho")
                    }
            }
            if (state.discoveryError)
                item {
                    TextButton({ vm.refreshDiscovery() }) {
                        Text("Não foi possível atualizar. Tentar novamente")
                    }
                }
            if (state.discoveryLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.discovery.items.isEmpty() && !state.discoveryLoading && !state.discoveryError)
                item {
                    Icon(Icons.Outlined.NotificationsNone, null)
                    Text(
                        "Suas próximas descobertas aparecerão aqui. Explore e salve temas para personalizar as sugestões."
                    )
                }
            items(state.discovery.items, key = { it.id }) { notice ->
                Card(onClick = { vm.openDiscovery(notice) }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        if (!notice.read)
                            Text(
                                "NOVA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        Text(notice.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Ver seleção · ${notice.themeIds.size} temas",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            items(state.discoveryThemes, key = { "theme-${it.id}" }) { theme ->
                Card(
                    onClick = {
                        vm.trackBehavior("recommendation_open", theme.id)
                        vm.open(theme)
                        onOpenTheme()
                    }
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        coil.compose.AsyncImage(
                            vm.repo.url(theme.coverUrl),
                            theme.title,
                            Modifier.size(64.dp),
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(theme.title)
                            Text(theme.category, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
