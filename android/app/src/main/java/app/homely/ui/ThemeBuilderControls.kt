package app.homely.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.homely.data.*
import kotlin.math.roundToInt

internal val builderSteps =
    listOf("Papel de parede", "Paleta", "Ícones", "Elementos e widgets", "Revisão")

@Composable
internal fun BuilderCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

@Composable
internal fun BuilderChoices(
    value: String,
    choices: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(choices) { (id, label) ->
            FilterChip(selected = value == id, onClick = { onSelect(id) }, label = { Text(label) })
        }
    }
}

@Composable
internal fun BuilderSlider(
    label: String,
    description: String,
    value: Int,
    range: IntRange,
    unit: String = "",
    onChange: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(
                "$value$unit",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        if (range.last > range.first)
            Slider(
                value.toFloat().coerceIn(range.first.toFloat(), range.last.toFloat()),
                { onChange(it.roundToInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = if (range.last - range.first <= 12) range.last - range.first - 1 else 0,
            )
    }
}

@Composable
internal fun BuilderSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
        Switch(checked, onChange)
    }
}

@Composable
internal fun BuilderVisualOption(
    id: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.width(172.dp),
        shape = RoundedCornerShape(18.dp),
        color =
            if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        border =
            BorderStroke(
                if (selected) 2.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ThemeDemonstration(id, Modifier.fillMaxWidth().height(98.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                if (selected)
                    Icon(
                        Icons.Outlined.CheckCircle,
                        null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
            }
        }
    }
}

@Composable
internal fun BuilderDrawer(layout: HomeLayout, edit: (HomeLayout) -> Unit) {
    BuilderCard(
        "Como organizar seus aplicativos?",
        "A mesma organização será usada na prévia e no Homely Home.",
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                listOf(
                    Triple("continuous", "Grade contínua", "Role para baixo para ver mais apps."),
                    Triple("pages", "Páginas", "Uma grade por página. Deslize para avançar."),
                    Triple("list", "Lista", "Uma linha por app, com ícone e nome."),
                    Triple("custom", "Personalizada", "Defina a densidade da sua grade contínua."),
                )
            ) { (id, title, description) ->
                BuilderVisualOption(id, title, description, layout.effectiveDrawerMode() == id) {
                    edit(
                        layout.copy(
                            drawerMode = id,
                            drawerLayout = if (id == "list") "list" else "grid",
                        )
                    )
                }
            }
        }
        if (layout.effectiveDrawerMode() != "list") {
            Text("Pontos de partida", style = MaterialTheme.typography.labelLarge)
            BuilderChoices(
                "",
                listOf(
                    "compact" to "Compacta",
                    "balanced" to "Equilibrada",
                    "spacious" to "Espaçada",
                ),
            ) {
                edit(
                    when (it) {
                        "compact" ->
                            layout.copy(drawerColumns = 5, drawerSpacing = 6, drawerIconSize = 42)
                        "spacious" ->
                            layout.copy(drawerColumns = 3, drawerSpacing = 22, drawerIconSize = 64)
                        else ->
                            layout.copy(drawerColumns = 4, drawerSpacing = 12, drawerIconSize = 54)
                    }
                )
            }
        }
    }
    BuilderCard(
        "Desenhe a grade",
        "Os valores mudam o resultado imediatamente. Ícones se ajustam ao espaço disponível.",
    ) {
        if (layout.effectiveDrawerMode() != "list")
            BuilderSlider("Colunas", "Aplicativos lado a lado.", layout.drawerColumns, 3..8) {
                edit(layout.copy(drawerColumns = it))
            }
        if (layout.effectiveDrawerMode() == "pages") {
            BuilderSlider(
                "Linhas por página",
                "${layout.drawerRows} × ${layout.drawerColumns} = ${layout.drawerRows * layout.drawerColumns} aplicativos por página.",
                layout.drawerRows,
                2..10,
            ) {
                edit(layout.copy(drawerRows = it))
            }
            Text("Direção das páginas", style = MaterialTheme.typography.labelLarge)
            BuilderChoices(
                layout.drawerDirection,
                listOf("horizontal" to "← Horizontal →", "vertical" to "↓ Vertical ↑"),
            ) {
                edit(layout.copy(drawerDirection = it))
            }
        }
        BuilderSlider(
            "Tamanho dos ícones",
            "Tamanho máximo de cada imagem.",
            layout.drawerIconSize,
            30..96,
            " dp",
        ) {
            edit(layout.copy(drawerIconSize = it))
        }
        BuilderSlider(
            "Espaçamento",
            "Respiro entre os aplicativos.",
            layout.drawerSpacing,
            0..32,
            " dp",
        ) {
            edit(layout.copy(drawerSpacing = it))
        }
        BuilderSlider(
            "Margens",
            "Distância entre a grade e as bordas da tela.",
            layout.drawerMargin,
            0..40,
            " dp",
        ) {
            edit(layout.copy(drawerMargin = it))
        }
    }
    BuilderCard("Superfície da grade", "Combine o painel de aplicativos com seu wallpaper.") {
        BuilderSlider(
            "Opacidade",
            "Quanto do fundo aparece através do painel.",
            (layout.drawerOpacity * 100).roundToInt(),
            20..100,
            "%",
        ) {
            edit(layout.copy(drawerOpacity = it / 100f))
        }
        BuilderSlider(
            "Cantos",
            "Arredondamento do painel de aplicativos.",
            layout.drawerRadius,
            0..48,
            " dp",
        ) {
            edit(layout.copy(drawerRadius = it))
        }
        if (android.os.Build.VERSION.SDK_INT >= 31)
            BuilderSlider(
                "Desfoque do fundo",
                "Suaviza o wallpaper enquanto a grade está aberta.",
                layout.drawerBlur,
                0..30,
                " dp",
            ) {
                edit(layout.copy(drawerBlur = it))
            }
        else
            Text(
                "Desfoque do wallpaper disponível no Android 12 ou superior.",
                style = MaterialTheme.typography.bodySmall,
            )
        BuilderSwitch(
            "Busca na grade",
            "Encontre aplicativos pelo nome.",
            layout.drawerShowSearch,
        ) {
            edit(layout.copy(drawerShowSearch = it))
        }
    }
}

@Composable
internal fun BuilderAnimations(layout: HomeLayout, edit: (HomeLayout) -> Unit) {
    BuilderCard(
        "Como a grade entra em cena?",
        "Abra e feche a grade na prévia para experimentar a transição escolhida.",
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                listOf(
                    Triple("slide", "Deslizar", "Um movimento contínuo entre as telas."),
                    Triple("fade", "Dissolver", "As telas aparecem suavemente."),
                    Triple("zoom", "Ampliar", "A grade cresce a partir do centro."),
                    Triple("none", "Instantânea", "Mudança imediata, sem movimento."),
                )
            ) { (id, title, description) ->
                BuilderVisualOption(id, title, description, layout.drawerAnimation == id) {
                    edit(layout.copy(drawerAnimation = id))
                }
            }
        }
        BuilderSlider(
            "Duração",
            "Tempo da transição e da mudança de página.",
            layout.animationDuration,
            0..800,
            " ms",
        ) {
            edit(layout.copy(animationDuration = it))
        }
        BuilderSwitch("Reduzir movimento", "Desativa as transições do tema.", layout.reduceMotion) {
            edit(layout.copy(reduceMotion = it))
        }
    }
    BuilderCard(
        "Ao abrir um aplicativo",
        "A animação de abertura usa o efeito escolhido no Homely Home.",
    ) {
        BuilderChoices(
            layout.appAnimation,
            listOf(
                "zoom" to "Ampliar",
                "fade" to "Dissolver",
                "slide" to "Deslizar",
                "none" to "Sem animação",
            ),
        ) {
            edit(layout.copy(appAnimation = it))
        }
        ThemeDemonstration(layout.appAnimation, Modifier.fillMaxWidth().height(105.dp))
    }
}

@Composable
internal fun BuilderHome(
    layout: HomeLayout,
    edit: (HomeLayout) -> Unit,
    onArrange: () -> Unit,
    onCanvas: () -> Unit,
) {
    BuilderCard(
        "Seu ponto de partida",
        "Defina a grade da tela inicial. O kit permite posicionar ícones e widgets livremente.",
    ) {
        BuilderSlider(
            "Colunas da tela inicial",
            "Quantidade de ícones lado a lado.",
            layout.columns,
            3..8,
        ) {
            edit(layout.copy(columns = it))
        }
        BuilderSlider(
            "Linhas da tela inicial",
            "Capacidade de cada página de ícones.",
            layout.rows,
            4..12,
        ) {
            edit(layout.copy(rows = it))
        }
        BuilderSlider(
            "Tamanho dos ícones",
            "Tamanho máximo na tela inicial.",
            layout.iconSize,
            30..96,
            " dp",
        ) {
            edit(layout.copy(iconSize = it))
        }
        BuilderSlider(
            "Espaçamento",
            "Distância entre os ícones da tela inicial.",
            layout.iconPadding,
            0..32,
            " dp",
        ) {
            edit(layout.copy(iconPadding = it))
        }
        BuilderSwitch(
            "Nomes dos aplicativos",
            "Exibe os nomes junto aos ícones.",
            layout.showLabels,
        ) {
            edit(layout.copy(showLabels = it))
        }
    }
    BuilderCard(
        "Composição livre",
        "Monte um kit com seus ícones personalizados e widgets. Arraste os elementos no canvas para posicionar.",
    ) {
        OutlinedButton(onArrange, Modifier.fillMaxWidth()) {
            Text(
                if (layout.kit.isEmpty()) "Montar kit automaticamente"
                else "Reorganizar kit automaticamente"
            )
        }
        Button(onCanvas, Modifier.fillMaxWidth(), enabled = layout.kit.isNotEmpty()) {
            Text("Posicionar elementos no canvas")
        }
        Text("Direção da transição entre páginas", style = MaterialTheme.typography.labelLarge)
        BuilderChoices(
            layout.scrollDirection,
            listOf("horizontal" to "Horizontal", "vertical" to "Vertical"),
        ) {
            edit(layout.copy(scrollDirection = it))
        }
    }
}

@Composable
internal fun BuilderBehavior(layout: HomeLayout, edit: (HomeLayout) -> Unit) {
    BuilderSwitch("Pesquisa na tela inicial", "", layout.showSearchBar) {
        edit(layout.copy(showSearchBar = it))
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    BuilderCard("Uma tela só sua", "Remova os controles que não usa e navegue pelos seus gestos.") {
        OutlinedButton(
            {
                edit(
                    layout.copy(
                        quickAction = "none",
                        showDrawerButton = false,
                        showDrawerHeader = false,
                        showDrawerCloseButton = false,
                        showPageIndicators = false,
                        showNavigationHandle = false,
                        drawerShowSearch = false,
                        showSearchBar = false,
                        statusBar = layout.statusBar.copy(show = false),
                    )
                )
            },
            Modifier.fillMaxWidth(),
        ) {
            Text("Aplicar visual limpo")
        }
        BuilderSwitch(
            "Barra de pesquisa",
            "Mostra a busca no topo da grade. O gesto Buscar ainda pode abri-la.",
            layout.drawerShowSearch,
        ) {
            edit(layout.copy(drawerShowSearch = it))
        }
        BuilderSwitch(
            "Botão Todos os aplicativos",
            "Atalho visível na tela inicial. Se ocultar, use um gesto para abrir a grade.",
            layout.showDrawerButton,
        ) {
            edit(layout.copy(showDrawerButton = it))
        }
        if (layout.showDrawerButton)
            BuilderChoices(
                layout.drawerStyle,
                listOf("button" to "Só ícone", "gesture" to "Ícone e texto"),
            ) {
                edit(layout.copy(drawerStyle = it))
            }
        BuilderSwitch(
            "Título da grade",
            "Nome e quantidade de aplicativos no topo.",
            layout.showDrawerHeader,
        ) {
            edit(layout.copy(showDrawerHeader = it))
        }
        BuilderSwitch(
            "Botão de fechar a grade",
            "Ao ocultar, volte pelo gesto configurado ou pelo Voltar do Android.",
            layout.showDrawerCloseButton,
        ) {
            edit(layout.copy(showDrawerCloseButton = it))
        }
        BuilderSwitch(
            "Controles de página",
            "Indicador e setas da tela inicial e da grade. Os gestos continuam funcionando.",
            layout.showPageIndicators,
        ) {
            edit(layout.copy(showPageIndicators = it))
        }
        BuilderSwitch(
            "Traço de navegação do tema",
            "Pequeno indicador decorativo na borda inferior.",
            layout.showNavigationHandle,
        ) {
            edit(layout.copy(showNavigationHandle = it))
        }
    }
    BuilderCard("Pequenos detalhes, sua experiência", "Preferências da navegação no Homely Home.") {
        BuilderSwitch(
            "Resposta tátil",
            "Uma vibração breve confirma gestos e ações.",
            layout.hapticFeedback,
        ) {
            edit(layout.copy(hapticFeedback = it))
        }
        Text("Botão Abrir Studio / ação rápida", style = MaterialTheme.typography.labelLarge)
        BuilderChoices(
            layout.quickAction,
            listOf("studio" to "Studio", "search" to "Busca", "none" to "Oculta"),
        ) {
            edit(layout.copy(quickAction = it))
        }
        BuilderSwitch(
            "Tela imersiva",
            "Oculta a barra de navegação do Android na home. Deslize da borda para revelá-la.",
            layout.immersiveHome,
        ) {
            edit(layout.copy(immersiveHome = it))
        }
        Text("Ícones das barras do Android", style = MaterialTheme.typography.labelLarge)
        BuilderChoices(
            layout.systemBarIcons,
            listOf("auto" to "Automáticos", "light" to "Claros", "dark" to "Escuros"),
        ) {
            edit(layout.copy(systemBarIcons = it))
        }
    }
    BuilderCard("Relógio e bateria", "Informações exibidas dentro da tela inicial do Homely.") {
        BuilderSwitch(
            "Mostrar informações",
            "Exibe a faixa superior do tema.",
            layout.statusBar.show,
        ) {
            edit(layout.copy(statusBar = layout.statusBar.copy(show = it)))
        }
        if (layout.statusBar.show) {
            Text("Relógio", style = MaterialTheme.typography.labelLarge)
            BuilderChoices(
                layout.statusBar.clockFormat,
                listOf("24h" to "24 horas", "12h" to "12 horas", "hidden" to "Oculto"),
            ) {
                edit(layout.copy(statusBar = layout.statusBar.copy(clockFormat = it)))
            }
            Text("Bateria", style = MaterialTheme.typography.labelLarge)
            BuilderChoices(
                layout.statusBar.batteryIcon,
                listOf("ios" to "Clássica", "android" to "Padrão", "hidden" to "Oculta"),
            ) {
                edit(layout.copy(statusBar = layout.statusBar.copy(batteryIcon = it)))
            }
            Text("Cor das informações", style = MaterialTheme.typography.labelLarge)
            BuilderChoices(
                layout.statusBar.tint,
                listOf("auto" to "Automática", "#FFFFFF" to "Branca", "#141421" to "Escura"),
            ) {
                edit(layout.copy(statusBar = layout.statusBar.copy(tint = it)))
            }
            StudioColorPicker(
                if (layout.statusBar.tint == "auto") "#FFFFFF" else layout.statusBar.tint,
                listOf("#FFFFFF", "#7965FF", "#141421"),
            ) {
                edit(layout.copy(statusBar = layout.statusBar.copy(tint = it)))
            }
            BuilderSwitch(
                "Fundo da faixa",
                "Uma cor própria atrás do relógio e da bateria.",
                layout.statusBar.background != "transparent",
            ) {
                edit(
                    layout.copy(
                        statusBar =
                            layout.statusBar.copy(background = if (it) "#171C27" else "transparent")
                    )
                )
            }
            if (layout.statusBar.background != "transparent") {
                StudioColorPicker(
                    layout.statusBar.background,
                    listOf("#171C27", "#7965FF", "#FFFFFF"),
                ) {
                    edit(layout.copy(statusBar = layout.statusBar.copy(background = it)))
                }
                BuilderSlider(
                    "Opacidade da faixa",
                    "Ajusta a transparência do fundo.",
                    (layout.statusBar.backgroundOpacity * 100).roundToInt(),
                    0..100,
                    "%",
                ) {
                    edit(
                        layout.copy(
                            statusBar = layout.statusBar.copy(backgroundOpacity = it / 100f)
                        )
                    )
                }
            }
        }
    }
    BuilderCard(
        "Cortina de notificações do Android",
        "As cores e os botões desse painel pertencem ao sistema e ao fabricante do aparelho.",
    ) {
        Text(
            "Em aparelhos compatíveis, as cores do sistema podem acompanhar o wallpaper. Escolha a paleta nas configurações do Android. As cores acima personalizam apenas a faixa do Homely.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                    )
                }
                    .onFailure {
                        android.widget.Toast.makeText(
                                context,
                                "Abra Tela ou Papel de parede nas configurações do Android.",
                                android.widget.Toast.LENGTH_LONG,
                            )
                            .show()
                    }
            },
            Modifier.fillMaxWidth(),
        ) {
            Text("Abrir configurações de tela do Android")
        }
    }
}
