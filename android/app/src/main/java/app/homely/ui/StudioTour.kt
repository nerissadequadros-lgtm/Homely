package app.homely.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun StudioTour() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("studio-onboarding", 0) }
    var open by remember { mutableStateOf(!app.homely.platform.UsageModes(ctx).chosen) }
    var page by remember { mutableIntStateOf(0) }
    fun done() {
        prefs.edit().putBoolean("seen", true).apply()
        open = false
    }
    val modes = remember { app.homely.platform.UsageModes(ctx) }
    if (open && !modes.chosen)
        AlertDialog(
            onDismissRequest = { done() },
            title = { Text("O que você quer criar?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Você pode mudar de ideia em Configurações → Modo de uso.")
                    listOf(
                            "Só quero mudar alguns ícones",
                            "Quero widgets sem trocar nada",
                            "Quero a experiência completa",
                            "Quero tudo",
                        )
                        .forEachIndexed { index, label ->
                            OutlinedButton(
                                {
                                    modes.icons = index == 0 || index == 3
                                    modes.floating = index == 1 || index == 3
                                    modes.home = index == 2
                                    modes.chosen = true
                                    done()
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text(label)
                            }
                        }
                    Text(
                        "A escolha da tela inicial será solicitada apenas quando você aplicar um tema completo.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton({
                    modes.chosen = true
                    done()
                }) {
                    Text("Explorar primeiro")
                }
            },
        )
}
