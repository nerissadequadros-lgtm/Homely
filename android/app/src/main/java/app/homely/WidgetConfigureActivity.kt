package app.homely

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.*

class WidgetConfigureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val drafts =
            listOf(HomelyRepository(this).draft()) + LocalThemeStore(this).list().map { it.draft }
        val options =
            drafts
                .flatMap { d -> d.manifest.widgets.map { Triple(it, d.widgetImages, d.fontFiles) } }
                .distinctBy { it.first.id }
        setContent {
            HomelyTheme(true) {
                var error by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                Surface {
                    Column(
                        Modifier.fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Seu widget Homely", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Escolha uma criação salva. Cada widget mantém sua própria configuração."
                        )
                        if (options.isEmpty())
                            Text(
                                "Crie e salve um widget no Studio antes de adicioná-lo à tela inicial."
                            )
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        options.forEach { (w, images, fonts) ->
                            ComposedWidget(
                                w,
                                images[w.id],
                                preview = true,
                                images = images,
                                fonts = fonts,
                            )
                            Button(
                                {
                                    busy = true
                                    lifecycleScope.launch {
                                        try {
                                            val token =
                                                withContext(Dispatchers.IO) {
                                                    StudioWidgets.save(
                                                        this@WidgetConfigureActivity,
                                                        w,
                                                        images,
                                                        fonts,
                                                    )
                                                }
                                            StudioWidgets.bind(
                                                this@WidgetConfigureActivity,
                                                id,
                                                token,
                                            )
                                            setResult(
                                                RESULT_OK,
                                                Intent()
                                                    .putExtra(
                                                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                                                        id,
                                                    ),
                                            )
                                            finish()
                                        } catch (e: Exception) {
                                            error = e.message
                                            busy = false
                                        }
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Usar ${w.name}")
                            }
                        }
                        TextButton({ finish() }) { Text("Cancelar") }
                    }
                }
            }
        }
    }
}
