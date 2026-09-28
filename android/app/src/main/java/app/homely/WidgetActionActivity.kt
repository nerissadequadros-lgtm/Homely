package app.homely

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.homely.data.*
import app.homely.platform.*
import app.homely.ui.*
import kotlinx.coroutines.launch

class WidgetActionActivity : ComponentActivity() {
    private val torchPermission =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) runCatching { ElementSystem.toggleTorch(this) }
            else
                android.widget.Toast.makeText(
                        this,
                        "A permissão de câmera é necessária para usar a lanterna.",
                        android.widget.Toast.LENGTH_SHORT,
                    )
                    .show()
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val originalLayer =
            runCatching {
                HomelyRepository(this)
                    .json
                    .decodeFromString<WidgetLayer>(intent.getStringExtra("layer") ?: "")
            }
                .getOrNull()
                ?: run {
                    finish()
                    return
                }
        val active =
            originalLayer.activeState(
                ElementSystem.read(this, originalLayer.source()),
                false,
                ElementSystem.toggled(this, originalLayer.id),
            )
        val layer =
            originalLayer.copy(
                tapAction =
                    active?.action?.takeUnless { it == "inherit" } ?: originalLayer.tapAction
            )
        if (intent.getBooleanExtra("dispatch", false) && layer.tapAction == "none") {
            finish()
            return
        }
        if (intent.getBooleanExtra("requestTorch", false)) {
            torchPermission.launch(android.Manifest.permission.CAMERA)
            return
        }
        if (
            intent.getBooleanExtra("dispatch", false) &&
                (layer.tapAction !in listOf("auto", "app", "none") ||
                    (layer.tapAction == "auto" &&
                        layer.source() in
                            listOf(
                                "wifi",
                                "bluetooth",
                                "flashlight",
                                "volume",
                                "storage",
                                "ram",
                                "cpu",
                                "lan",
                                "signal",
                            )))
        ) {
            ElementSystem.execute(this, layer.tapAction, layer)
            finish()
            return
        }
        if (intent.getBooleanExtra("dispatch", false)) {
            val target = WidgetActions.intent(this, layer)
            if (
                target != null &&
                    target.component?.className != WidgetActionActivity::class.java.name
            ) {
                try {
                    startActivity(target)
                    finish()
                    return
                } catch (_: RuntimeException) {}
            }
        }
        enableEdgeToEdge()
        setContent {
            HomelyTheme(isSystemInDarkTheme()) {
                Surface {
                    Column(
                        Modifier.fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        TextButton({ finish() }) { Text("Voltar") }
                        Text(
                            when (layer.type) {
                                "weather" -> "Seu tempo, agora"
                                "calendar",
                                "date" -> "Seu calendário"
                                "note" -> "Nota rápida"
                                "phone" -> "Telefone"
                                "clock",
                                "analog" -> "Seu relógio"
                                else -> "Atalho"
                            },
                            style = MaterialTheme.typography.headlineLarge,
                        )
                        if (layer.type == "note") {
                            var note by remember {
                                mutableStateOf(WidgetNotes.text(this@WidgetActionActivity, layer))
                            }
                            OutlinedTextField(
                                note,
                                { note = it.take(300) },
                                Modifier.fillMaxWidth(),
                                label = { Text("Sua nota") },
                                minLines = 5,
                            )
                            Button(
                                {
                                    WidgetNotes.save(this@WidgetActionActivity, layer.id, note)
                                    StudioWidgets.updateAll(this@WidgetActionActivity)
                                    finish()
                                },
                                Modifier.fillMaxWidth(),
                            ) {
                                Text("Salvar nota")
                            }
                        } else {
                            WidgetLayerContent(
                                layer,
                                emptyMap(),
                                true,
                                Modifier.fillMaxWidth().height(300.dp),
                            )
                            if (layer.type == "weather") {
                                val revision by WeatherStore.revision.collectAsState()
                                val data =
                                    remember(revision) {
                                        WeatherStore.cached(
                                            this@WidgetActionActivity,
                                            layer.weather,
                                        )
                                    }
                                var refreshing by remember { mutableStateOf(false) }
                                Text(
                                    "Previsão para a cidade escolhida. Temperaturas mínima e máxima das próximas 24 horas."
                                )
                                data?.let {
                                    Text(
                                        "Atualizado em " +
                                            java.text
                                                .SimpleDateFormat(
                                                    "dd/MM HH:mm",
                                                    java.util.Locale.getDefault(),
                                                )
                                                .format(java.util.Date(it.fetchedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    it.hourly.forEach { p ->
                                        Text(
                                            java.text
                                                .SimpleDateFormat(
                                                    "HH:mm",
                                                    java.util.Locale.getDefault(),
                                                )
                                                .format(java.util.Date(p.time)) +
                                                "   " +
                                                WeatherStore.temperature(
                                                    p.temperature,
                                                    layer.weather.unit,
                                                ) +
                                                " · " +
                                                WeatherStore.description(p.symbol)
                                        )
                                    }
                                }
                                Button(
                                    {
                                        refreshing = true
                                        lifecycleScope.launch {
                                            WeatherStore.refresh(
                                                this@WidgetActionActivity,
                                                layer.weather,
                                                true,
                                            )
                                            StudioWidgets.updateAll(this@WidgetActionActivity)
                                            refreshing = false
                                        }
                                    },
                                    enabled = !refreshing && layer.weather.latitude != null,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (refreshing) "Atualizando…" else "Atualizar previsão")
                                }
                                Text(
                                    "Previsão: MET Norway. Cidades: GeoNames. Dados reorganizados pelo Homely; licença CC BY 4.0.",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                listOf(
                                        "MET Norway" to "https://api.met.no/",
                                        "GeoNames" to "https://www.geonames.org/",
                                        "Licença CC BY 4.0" to
                                            "https://creativecommons.org/licenses/by/4.0/",
                                    )
                                    .forEach { (name, url) ->
                                        TextButton({
                                            startActivity(
                                                Intent(
                                                    Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(url),
                                                )
                                            )
                                        }) {
                                            Text(name)
                                        }
                                    }
                            } else if (layer.type in listOf("date", "calendar"))
                                OutlinedButton({
                                    try {
                                        startActivity(
                                            Intent(
                                                Intent.ACTION_INSERT,
                                                android.provider.CalendarContract.Events
                                                    .CONTENT_URI,
                                            )
                                        )
                                    } catch (_: android.content.ActivityNotFoundException) {
                                        android.widget.Toast.makeText(
                                                this@WidgetActionActivity,
                                                "Instale ou selecione um aplicativo de calendário.",
                                                android.widget.Toast.LENGTH_LONG,
                                            )
                                            .show()
                                    }
                                }) {
                                    Text("Criar evento no calendário")
                                }
                            else
                                Text(
                                    "Para personalizar a ação, abra este item no Widget Studio e escolha um aplicativo instalado."
                                )
                        }
                    }
                }
            }
        }
    }
}
