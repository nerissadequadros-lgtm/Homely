package app.homely.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import app.homely.data.*
import app.homely.platform.*
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.*

@Composable
private fun CatalogHeader(
    title: @Composable () -> Unit,
    category: String,
    categories: List<String>,
    onCategory: (String) -> Unit,
) {
    Column(
        Modifier.padding(top = 10.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        FilterRail("Categoria", category, listOf("Todos") + categories, onCategory)
    }
}

@Composable
fun FilterRail(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            label.uppercase(),
            Modifier.padding(horizontal = 18.dp),
            fontSize = 8.sp,
            letterSpacing = 1.3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(options) { option -> Pill(option, option == value) { onSelect(option) } }
        }
    }
}

@Composable
fun IconsCatalogScreen(vm: HomelyViewModel, state: HomelyState) {
    val selected = remember { mutableStateMapOf<String, CatalogIcon>() }
    var applying by remember { mutableStateOf<List<CatalogIcon>?>(null) }
    val grid = rememberLazyGridState()
    LaunchedEffect(Unit) { if (state.icons.isEmpty()) vm.loadIcons() }
    LaunchedEffect(grid, state.iconsNextOffset, state.iconsLoading) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (
                    last >= state.icons.size - 5 &&
                        state.iconsNextOffset != null &&
                        !state.iconsLoading
                )
                    vm.loadIcons(more = true)
            }
    }
    Column(Modifier.fillMaxSize()) {
        CatalogHeader(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CleanIcon(
                        Glyph.Icons,
                        Modifier.size(24.dp),
                        MaterialTheme.colorScheme.primary,
                    )
                    Text("Ícones", style = MaterialTheme.typography.headlineMedium)
                }
            },
            state.iconCategory,
            CatalogTaxonomy.iconCategories,
            { vm.loadIcons(category = it) },
        )
        LazyVerticalGrid(
            GridCells.Fixed(3),
            state = grid,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.weight(1f).testTag("icons-grid"),
        ) {
            items(state.icons, key = { it.asset.id }) { item ->
                val active = selected.containsKey(item.asset.id)
                val border by
                    animateColorAsState(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                        tween(170),
                        label = "iconSelection",
                    )
                Surface(
                    onClick = {
                        if (active) selected.remove(item.asset.id)
                        else selected[item.asset.id] = item
                    },
                    shape = RoundedCornerShape(19.dp),
                    color =
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = .08f)
                        else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (active) 1.5.dp else .7.dp, border),
                    modifier =
                        Modifier.testTag("icon-" + item.asset.id).semantics {
                            this.selected = active
                            contentDescription = item.asset.name + ", " + item.style
                        },
                ) {
                    Column(
                        Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                vm.repo.url(item.asset.url),
                                item.asset.name,
                                Modifier.padding(vertical = 9.dp)
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(15.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            androidx.compose.animation.AnimatedVisibility(
                                active,
                                Modifier.align(Alignment.TopEnd),
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut(),
                            ) {
                                Box(
                                    Modifier.size(18.dp).background(Violet, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CleanIcon(Glyph.Check, Modifier.size(12.dp), Color.White)
                                }
                            }
                        }
                        Text(
                            item.asset.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                        Text(
                            item.style,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (state.iconsLoading)
                item(span = { GridItemSpan(3) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            if (state.icons.isEmpty() && !state.iconsLoading)
                item(span = { GridItemSpan(3) }) {
                    EmptyState(
                        Icons.Outlined.GridView,
                        if (state.iconsError) "Não foi possível carregar"
                        else "Nenhum ícone por aqui ainda",
                        if (state.iconsError) "Verifique sua conexão e tente novamente."
                        else "Escolha outra categoria ou outro estilo.",
                        if (state.iconsError) "Tentar novamente" else "Limpar filtros",
                    ) {
                        vm.loadIcons(category = "Todos", style = "Todos")
                    }
                }
        }
        AnimatedVisibility(
            selected.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton({ selected.clear() }) { Text("Limpar", fontSize = 12.sp) }
                    Button(
                        { applying = selected.values.toList() },
                        Modifier.weight(1f).height(47.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Violet,
                                contentColor = Color.White,
                            ),
                    ) {
                        Text(
                            "Aplicar " +
                                selected.size +
                                if (selected.size == 1) " ícone" else " ícones",
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
    applying?.let { items -> IconApplyDialog(items, vm) { applying = null } }
}

@Composable
private fun IconApplyDialog(items: List<CatalogIcon>, vm: HomelyViewModel, onDismiss: () -> Unit) {
    if (app.homely.LauncherActivity.isDefault(LocalContext.current)) {
        LauncherIconApplyDialog(items, vm, onDismiss)
        return
    }
    var step by remember { mutableIntStateOf(0) }
    var requested by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val item = items[step]
    val installed =
        remember(item.asset.packageName) {
            app.homely.platform.WidgetActions.appIntent(ctx, item.asset.packageName.orEmpty())
                ?.resolveActivity(ctx.packageManager) != null
        }
    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Sua seleção · " + (step + 1) + "/" + items.size) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AsyncImage(
                    vm.repo.url(item.asset.url),
                    item.asset.name,
                    Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                )
                Text(item.asset.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (!installed) "Este aplicativo não está instalado no aparelho."
                    else if (requested)
                        "Solicitação enviada. Confirme no launcher para adicionar o atalho."
                    else "O Android vai pedir sua confirmação para criar o atalho com este ícone.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "O ícone original do aplicativo não é substituído globalmente.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (loading) CircularProgressIndicator(Modifier.size(22.dp))
            }
        },
        confirmButton = {
            if (requested || !installed)
                TextButton({
                    if (step == items.lastIndex) onDismiss()
                    else {
                        step++
                        requested = false
                        error = null
                    }
                }) {
                    Text(if (step == items.lastIndex) "Concluir" else "Próximo ícone")
                }
            else
                TextButton(
                    {
                        loading = true
                        vm.action {
                            try {
                                NativeActions.shortcut(vm.repo, item.asset)
                                requested = true
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                loading = false
                            }
                        }
                    },
                    enabled = !loading,
                ) {
                    Text("Adicionar atalho")
                }
        },
        dismissButton = { TextButton(onDismiss, enabled = !loading) { Text("Fechar") } },
    )
}

@Composable
fun WidgetsCatalogScreen(vm: HomelyViewModel) {
    var category by rememberSaveable { mutableStateOf("Todos") }
    var selected by remember { mutableStateOf<WidgetItem?>(null) }
    val filtered = widgetCatalog.filter {
        (category == "Todos" || it.category == category)
    }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        CatalogHeader(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CleanIcon(
                        Glyph.Widgets,
                        Modifier.size(24.dp),
                        MaterialTheme.colorScheme.primary,
                    )
                    Text("Widgets", style = MaterialTheme.typography.headlineMedium)
                }
            },
            category,
            widgetCatalog.map { it.category }.distinct(),
            { category = it },
        )
        LazyVerticalGrid(
            GridCells.Fixed(2),
            contentPadding = PaddingValues(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().testTag("widgets-grid"),
        ) {
            items(filtered, key = { it.id }) { widget ->
                Surface(
                    onClick = { selected = widget },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border =
                        BorderStroke(.7.dp, MaterialTheme.colorScheme.outline.copy(alpha = .6f)),
                    modifier = Modifier.testTag("widget-" + widget.id),
                ) {
                    Column(
                        Modifier.padding(13.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        WidgetPreview(widget, Modifier.fillMaxWidth().height(108.dp))
                        Text(widget.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                widget.style,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                widget.size,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            if (filtered.isEmpty())
                item(span = { GridItemSpan(2) }) {
                    EmptyState(
                        Icons.Outlined.Widgets,
                        "Outra combinação?",
                        "Ainda não há widgets com estes filtros.",
                        "Limpar filtros",
                    ) {
                        category = "Todos"
                    }
                }
        }
    }
    selected?.let { widget ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(widget.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    WidgetPreview(widget, Modifier.fillMaxWidth().height(145.dp))
                    Text(widget.subtitle)
                    Text(
                        widget.size +
                            " · " +
                            widget.style +
                            ". O launcher confirma a posição na sua tela." +
                            if (widget.category == "Bateria")
                                " Atualização a cada 30 minutos ou ao tocar."
                            else "",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton({
                    val accepted = NativeWidgets.pin(ctx, widget.id)
                    selected = null
                    vm.message(
                        if (accepted) "Confirme a adição no launcher."
                        else
                            "Abra os widgets do seu launcher e escolha Homely · " +
                                widget.name +
                                "."
                    )
                }) {
                    Text("Adicionar widget")
                }
            },
            dismissButton = { TextButton({ selected = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun WidgetPreview(widget: WidgetItem, modifier: Modifier = Modifier) {
    val light = widget.id == "pastel"
    val accent =
        if (widget.id == "neon") Color(0xFFB7F67B) else if (light) Color(0xFF6D5286) else Lilac
    val background =
        if (light) Color(0xFFF0DDED)
        else if (widget.id == "neon") Color(0xFF1B2824) else Color(0xFF171625)
    val foreground = if (light) Color(0xFF422C57) else Color.White
    val now = remember { Date() }
    val battery =
        (LocalContext.current.getSystemService(android.content.Context.BATTERY_SERVICE)
                as android.os.BatteryManager)
            .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .coerceIn(0, 100)
    Column(
        modifier.clip(RoundedCornerShape(15.dp)).background(background).padding(12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (widget.category) {
            "Relógios" -> {
                Text(
                    "10:24",
                    color = foreground,
                    fontSize = if (widget.id == "editorial") 32.sp else 35.sp,
                    fontFamily =
                        if (widget.id == "editorial") FontFamily.Serif else FontFamily.SansSerif,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1).sp,
                )
                Text(
                    SimpleDateFormat("EEE, d MMM", Locale.forLanguageTag("pt-BR")).format(now),
                    color = accent,
                    fontSize = 9.sp,
                )
            }
            "Calendário" -> {
                Text(
                    SimpleDateFormat("EEEE", Locale.forLanguageTag("pt-BR"))
                        .format(now)
                        .uppercase(),
                    color = accent,
                    fontSize = 8.sp,
                    letterSpacing = 1.sp,
                )
                Text(
                    SimpleDateFormat("dd", Locale.getDefault()).format(now),
                    color = foreground,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    SimpleDateFormat("MMMM", Locale.forLanguageTag("pt-BR")).format(now),
                    color = accent,
                    fontSize = 10.sp,
                )
            }
            else -> {
                Text(
                    battery.toString() + "%",
                    color = foreground,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Light,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { battery / 100f },
                    modifier = Modifier.fillMaxWidth(.75f).height(5.dp).clip(CircleShape),
                    color = accent,
                    trackColor = foreground.copy(alpha = .1f),
                )
                Spacer(Modifier.height(5.dp))
                Text("ENERGIA", fontSize = 8.sp, letterSpacing = 1.sp, color = accent)
            }
        }
    }
}
