package app.homely.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.*
import app.homely.data.*
import app.homely.platform.*
import java.util.UUID

internal val elementKinds =
    listOf(
        "text" to "Texto",
        "number" to "Número",
        "icon" to "Ícone",
        "image" to "Imagem",
        "ring" to "Anel",
        "bar" to "Barra",
        "indicator" to "Indicador",
        "clock" to "Relógio",
        "analog" to "Analógico",
        "calendar" to "Calendário",
        "battery" to "Bateria",
        "wifi" to "Wi-Fi",
        "bluetooth" to "Bluetooth",
        "flashlight" to "Lanterna",
        "ram" to "RAM",
        "storage" to "Armazenamento",
        "volume" to "Volume",
        "lan" to "LAN",
        "signal" to "Sinal Wi-Fi",
        "cpu" to "CPU do Homely",
    )

@Composable
fun ElementProperties(
    layer: WidgetLayer,
    images: Map<String, String>,
    onChange: (WidgetLayer) -> Unit,
    onImage: (String) -> Unit,
    vm: HomelyViewModel? = null,
    initialTab: String = "Aparência",
    showTabs: Boolean = true,
) {
    var tab by remember(layer.id, initialTab) { mutableStateOf(initialTab) }
    if (showTabs)
        BuilderChoices(tab, listOf("Aparência", "Estados", "Interações").map { it to it }) {
            tab = it
        }
    when (tab) {
        "Aparência" -> {
            BuilderCard(
                "Elemento independente",
                "Cada propriedade pertence apenas a este elemento.",
            ) {
                Field(
                    layer.text,
                    { onChange(layer.copy(text = it.take(300))) },
                    "Texto / número fixo",
                )
                Text("Fonte de dados", style = MaterialTheme.typography.labelLarge)
                BuilderChoices(layer.dataSource, elementSources.map { it to sourceLabel(it) }) {
                    onChange(layer.copy(dataSource = it))
                }
                if (layer.source() == "cpu")
                    Text(
                        "Uso de CPU do processo Homely, normalizado pelo número de núcleos. O Android não expõe o uso global a apps comuns.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                if (layer.source() == "bluetooth") BluetoothPermissionButton()
                if (layer.type == "image")
                    OutlinedButton({ onImage(layer.id) }, Modifier.fillMaxWidth()) {
                        Text("Escolher imagem")
                    }
                if (layer.type in listOf("icon", "indicator"))
                    Field(
                        layer.iconName,
                        { onChange(layer.copy(iconName = it.take(16))) },
                        "Símbolo ou auto",
                    )
                StudioColorPicker(layer.color, listOf("#FFFFFF", "#7965FF", "#EA8DAB", "#F5D76E")) {
                    onChange(layer.copy(color = it))
                }
                BuilderSlider(
                    "Tamanho do texto",
                    "Também dimensiona o ícone.",
                    layer.fontSize,
                    8..120,
                    " sp",
                ) {
                    onChange(layer.copy(fontSize = it))
                }
                BuilderSlider("Peso da fonte", "De leve a marcante.", layer.weight, 100..900) {
                    onChange(layer.copy(weight = it))
                }
                if (vm != null)
                    StudioFontPicker(vm, layer.fontFamily, layer.fontId) { family, id ->
                        onChange(layer.copy(fontFamily = family, fontId = id))
                    }
                BuilderChoices(
                    layer.fontFamily,
                    listOf(
                        "sans" to "Sem serifa",
                        "serif" to "Serifada",
                        "mono" to "Mono",
                        "display" to "Display",
                        "hand" to "Manuscrita",
                    ),
                ) {
                    onChange(layer.copy(fontFamily = it, fontId = null))
                }
                BuilderChoices(
                    layer.alignment,
                    listOf("start" to "Esquerda", "center" to "Centro", "end" to "Direita"),
                ) {
                    onChange(layer.copy(alignment = it))
                }
                BuilderSwitch(
                    "Mostrar valor",
                    "Número independente do anel ou da barra.",
                    layer.showValue,
                ) {
                    onChange(layer.copy(showValue = it))
                }
                BuilderChoices(
                    layer.numberFormat,
                    listOf("percent" to "Porcentagem", "number" to "Número"),
                ) {
                    onChange(layer.copy(numberFormat = it))
                }
                if (layer.type in listOf("clock", "analog", "date")) {
                    BuilderChoices(
                        layer.clock.format,
                        listOf("24" to "24 horas", "12" to "12 horas"),
                    ) {
                        onChange(layer.copy(clock = layer.clock.copy(format = it)))
                    }
                    BuilderSwitch(
                        "Segundos",
                        "Atualiza o relógio enquanto estiver visível.",
                        layer.clock.seconds,
                    ) {
                        onChange(layer.copy(clock = layer.clock.copy(seconds = it)))
                    }
                    BuilderChoices(
                        layer.clock.dateStyle,
                        listOf(
                            "full" to "Data completa",
                            "short" to "Data curta",
                            "weekday" to "Dia da semana",
                        ),
                    ) {
                        onChange(layer.copy(clock = layer.clock.copy(dateStyle = it)))
                    }
                }
                if (layer.type in listOf("ring", "bar", "battery_ring")) {
                    BuilderSlider(
                        "Espessura",
                        "Traço do anel.",
                        layer.ringWidth.toInt(),
                        1..32,
                        " dp",
                    ) {
                        onChange(layer.copy(ringWidth = it.toFloat()))
                    }
                    Text("Cor da trilha", style = MaterialTheme.typography.labelLarge)
                    StudioColorPicker(layer.trackColor, listOf("#FFFFFF", "#E7BCC8", "#171C27")) {
                        onChange(layer.copy(trackColor = it))
                    }
                    BuilderSlider(
                        "Opacidade da trilha",
                        "Fundo do indicador.",
                        (layer.trackOpacity * 100).toInt(),
                        0..100,
                        "%",
                    ) {
                        onChange(layer.copy(trackOpacity = it / 100f))
                    }
                    if (layer.source() == "none")
                        BuilderSlider(
                            "Progresso fixo",
                            "Use uma fonte para acompanhar um valor real.",
                            (layer.progress * 100).toInt(),
                            0..100,
                            "%",
                        ) {
                            onChange(layer.copy(progress = it / 100f))
                        }
                }
            }
            BuilderCard(
                "Fundo, borda e respiro",
                "Construa cartões e indicadores com aparência própria.",
            ) {
                BuilderSwitch(
                    "Cor de fundo",
                    "Aplica apenas dentro do elemento.",
                    layer.elementBackground != null,
                ) {
                    onChange(layer.copy(elementBackground = if (it) "#171C27" else null))
                }
                layer.elementBackground?.let { value ->
                    StudioColorPicker(value, listOf("#171C27", "#EA8DAB", "#FDE7ED")) {
                        onChange(layer.copy(elementBackground = it))
                    }
                }
                BuilderSlider("Raio", "Cantos do elemento.", layer.elementRadius, 0..100, " dp") {
                    onChange(layer.copy(elementRadius = it))
                }
                BuilderSlider(
                    "Borda",
                    "Espessura da borda.",
                    layer.elementBorderWidth.toInt(),
                    0..12,
                    " dp",
                ) {
                    onChange(layer.copy(elementBorderWidth = it.toFloat()))
                }
                if (layer.elementBorderWidth > 0)
                    StudioColorPicker(
                        layer.elementBorderColor,
                        listOf("#FFFFFF", "#AF6E83", "#7965FF"),
                    ) {
                        onChange(layer.copy(elementBorderColor = it))
                    }
                BuilderSlider(
                    "Espaçamento interno",
                    "Distância do conteúdo às bordas.",
                    layer.elementPadding,
                    0..48,
                    " dp",
                ) {
                    onChange(layer.copy(elementPadding = it))
                }
                BuilderSlider(
                    "Opacidade",
                    "Transparência do elemento completo.",
                    (layer.opacity * 100).toInt(),
                    0..100,
                    "%",
                ) {
                    onChange(layer.copy(opacity = it / 100f))
                }
                BuilderChoices(
                    layer.animation,
                    elementAnimations.map { it to animationLabel(it) },
                ) {
                    onChange(layer.copy(animation = it))
                }
                BuilderSlider(
                    "Duração",
                    "Transições entre estados e animações ao tocar.",
                    layer.animationDuration,
                    0..1000,
                    " ms",
                ) {
                    onChange(layer.copy(animationDuration = it))
                }
            }
        }
        "Estados" -> ElementStatesEditor(layer, images, onChange, onImage)
        else ->
            BuilderCard(
                "Interações",
                "Ações gerais ou áreas definidas por posição. A primeira área correspondente recebe o toque.",
            ) {
                Text("Ao tocar", style = MaterialTheme.typography.labelLarge)
                BuilderChoices(
                    layer.tapAction,
                    elementActions.map { it to elementActionLabel(it) },
                ) {
                    onChange(layer.copy(tapAction = it))
                }
                Text("Ao manter pressionado", style = MaterialTheme.typography.labelLarge)
                BuilderChoices(
                    layer.longPressAction,
                    elementActions.map { it to elementActionLabel(it) },
                ) {
                    onChange(layer.copy(longPressAction = it))
                }
                if (
                    layer.tapAction == "app" ||
                        layer.longPressAction == "app" ||
                        layer.states.any { it.action == "app" } ||
                        layer.touchZones.any { it.action == "app" }
                )
                    Field(
                        layer.tapPackage.orEmpty(),
                        { onChange(layer.copy(tapPackage = it.ifBlank { null })) },
                        "Pacote do app · ex. com.android.settings",
                    )
                layer.touchZones.forEach { zone ->
                    fun change(value: ElementTouchZone) =
                        onChange(
                            layer.copy(
                                touchZones =
                                    layer.touchZones.map { if (it.id == value.id) value else it }
                            )
                        )
                    HorizontalDivider()
                    Text(zone.name, style = MaterialTheme.typography.titleSmall)
                    BuilderSlider("X", "Posição horizontal.", (zone.x * 100).toInt(), 0..95, "%") {
                        change(
                            zone.copy(x = it / 100f, width = zone.width.coerceAtMost(1 - it / 100f))
                        )
                    }
                    BuilderSlider("Y", "Posição vertical.", (zone.y * 100).toInt(), 0..95, "%") {
                        change(
                            zone.copy(
                                y = it / 100f,
                                height = zone.height.coerceAtMost(1 - it / 100f),
                            )
                        )
                    }
                    BuilderSlider(
                        "Largura",
                        "Área ativa.",
                        (zone.width * 100).toInt(),
                        5..((1 - zone.x) * 100).toInt().coerceAtLeast(5),
                        "%",
                    ) {
                        change(zone.copy(width = it / 100f))
                    }
                    BuilderSlider(
                        "Altura",
                        "Área ativa.",
                        (zone.height * 100).toInt(),
                        5..((1 - zone.y) * 100).toInt().coerceAtLeast(5),
                        "%",
                    ) {
                        change(zone.copy(height = it / 100f))
                    }
                    BuilderChoices(
                        zone.action,
                        elementActions.map { it to elementActionLabel(it) },
                    ) {
                        change(zone.copy(action = it))
                    }
                    TextButton({ onChange(layer.copy(touchZones = layer.touchZones - zone)) }) {
                        Text("Remover área")
                    }
                }
                OutlinedButton(
                    {
                        onChange(
                            layer.copy(
                                touchZones =
                                    layer.touchZones +
                                        ElementTouchZone(name = "Área ${layer.touchZones.size+1}")
                            )
                        )
                    },
                    enabled = layer.touchZones.size < 8,
                ) {
                    Text("Adicionar área de toque")
                }
                Text(
                    "Animações, pressão longa e áreas de toque funcionam no Homely Home. Widgets em outros launchers usam a aparência no momento da atualização e a ação de toque da área inteira; a pressão longa pertence ao launcher.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
    }
}

@Composable
private fun ElementStatesEditor(
    layer: WidgetLayer,
    images: Map<String, String>,
    onChange: (WidgetLayer) -> Unit,
    onImage: (String) -> Unit,
) {
    var previewId by remember(layer.id) { mutableStateOf<String?>(null) }
    BuilderCard(
        "Estado → condição → aparência → ação",
        "O primeiro estado condicional correspondente vence. Pressionado e alternado têm prioridade; Padrão é a aparência de reserva.",
    ) {
        layer.states.forEach { state ->
            fun edit(value: ElementState) =
                onChange(
                    layer.copy(states = layer.states.map { if (it.id == value.id) value else it })
                )
            TextButton({ previewId = if (previewId == state.id) null else state.id }) {
                Text(
                    if (previewId == state.id) "Fechar prévia do estado"
                    else "Testar aparência deste estado"
                )
            }
            if (previewId == state.id)
                Box(Modifier.fillMaxWidth().height(120.dp)) {
                    WidgetLayerContent(
                        layer.copy(states = listOf(state.copy(condition = "always"))),
                        images,
                        true,
                        Modifier.fillMaxSize(),
                    )
                }
            Field(state.name, { edit(state.copy(name = it.take(60))) }, "Nome do estado")
            BuilderChoices(
                state.condition,
                listOf(
                    "always" to "Padrão",
                    "low" to "Abaixo de",
                    "high" to "A partir de",
                    "enabled" to "Ligado",
                    "disabled" to "Desligado",
                    "connected" to "Conectado",
                    "disconnected" to "Desconectado",
                    "unavailable" to "Indisponível",
                    "pressed" to "Pressionado",
                    "touched" to "Tocado (temporário)",
                    "toggled" to "Alternado",
                ),
            ) {
                edit(state.copy(condition = it))
            }
            if (state.condition in listOf("low", "high"))
                BuilderSlider(
                    "Limite",
                    "Valor da fonte de dados.",
                    state.threshold.toInt(),
                    0..100,
                    "%",
                ) {
                    edit(state.copy(threshold = it.toFloat()))
                }
            StudioColorPicker(
                state.color ?: layer.color,
                listOf("#EA8DAB", "#F5D76E", "#FFFFFF", "#7965FF"),
            ) {
                edit(state.copy(color = it))
            }
            BuilderSwitch(
                "Fundo deste estado",
                "Substitui a cor de fundo do elemento.",
                state.background != null,
            ) {
                edit(state.copy(background = if (it) "#FDE7ED" else null))
            }
            state.background?.let { bg ->
                StudioColorPicker(bg, listOf("#FDE7ED", "#EA8DAB", "#171C27")) {
                    edit(state.copy(background = it))
                }
            }
            Field(
                state.text.orEmpty(),
                { edit(state.copy(text = it.take(300).ifBlank { null })) },
                "Texto alternativo (vazio usa o valor ao vivo)",
            )
            OutlinedButton({
                val id = state.imageId ?: UUID.randomUUID().toString()
                edit(state.copy(imageId = id, imageAssetId = null))
                onImage(id)
            }) {
                Text(if (state.imageId == null) "Imagem deste estado" else "Trocar imagem")
            }
            if (state.imageId != null)
                TextButton({ edit(state.copy(imageId = null, imageAssetId = null)) }) {
                    Text("Usar conteúdo original")
                }
            BuilderSlider(
                "Opacidade",
                "Transparência neste estado.",
                (state.opacity * 100).toInt(),
                0..100,
                "%",
            ) {
                edit(state.copy(opacity = it / 100f))
            }
            BuilderSlider(
                "Escala",
                "Tamanho relativo.",
                (state.scale * 100).toInt(),
                50..150,
                "%",
            ) {
                edit(state.copy(scale = it / 100f))
            }
            BuilderSlider(
                "Rotação",
                "Ângulo ao entrar neste estado.",
                state.rotation.toInt(),
                -360..360,
                "°",
            ) {
                edit(state.copy(rotation = it.toFloat()))
            }
            BuilderChoices(
                state.animation,
                listOf("inherit" to "Animação do elemento") +
                    elementAnimations.map { it to animationLabel(it) },
            ) {
                edit(state.copy(animation = it))
            }
            BuilderChoices(
                state.action,
                listOf("inherit" to "Ação do elemento") +
                    elementActions.map { it to elementActionLabel(it) },
            ) {
                edit(state.copy(action = it))
            }
            TextButton({ onChange(layer.copy(states = layer.states - state)) }) {
                Text("Remover estado")
            }
            HorizontalDivider()
        }
        Button(
            {
                onChange(
                    layer.copy(
                        states =
                            layer.states +
                                ElementState(
                                    name = "Estado ${layer.states.size+1}",
                                    condition = if (layer.states.isEmpty()) "low" else "high",
                                )
                    )
                )
            },
            enabled = layer.states.size < 12,
        ) {
            Text("Adicionar estado")
        }
    }
}

@Composable
private fun BluetoothPermissionButton() {
    val ctx = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            ElementSystem.revision.value++
        }
    Text(
        "Conexão Bluetooth considera os perfis de áudio A2DP e headset. Outros acessórios podem não expor o estado.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (
        android.os.Build.VERSION.SDK_INT >= 31 &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                ctx,
                android.Manifest.permission.BLUETOOTH_CONNECT,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    )
        OutlinedButton({ launcher.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }) {
            Text("Autorizar leitura do Bluetooth")
        }
}

private fun sourceLabel(id: String) =
    when (id) {
        "auto" -> "Do elemento"
        "none" -> "Valor fixo"
        "battery" -> "Bateria"
        "ram" -> "RAM usada"
        "storage" -> "Armazenamento usado"
        "wifi" -> "Wi-Fi"
        "bluetooth" -> "Bluetooth"
        "lan" -> "LAN"
        "flashlight" -> "Lanterna"
        "volume" -> "Volume"
        "signal" -> "Sinal Wi-Fi"
        else -> "CPU do Homely"
    }

internal fun animationLabel(id: String) =
    when (id) {
        "fade" -> "Dissolver"
        "scale" -> "Escala"
        "slide" -> "Deslizar"
        "rotate" -> "Girar"
        else -> "Sem animação"
    }
