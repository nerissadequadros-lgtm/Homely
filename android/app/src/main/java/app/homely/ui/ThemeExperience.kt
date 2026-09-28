package app.homely.ui

import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import app.homely.CustomStatusBar
import app.homely.data.*
import app.homely.platform.AppShortcutInfo
import app.homely.platform.InstalledApp
import app.homely.platform.NativeActions
import app.homely.platform.WidgetActions
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val sampleAppNames =
    listOf(
        "Agenda",
        "Câmera",
        "Fotos",
        "Música",
        "Mapas",
        "Mensagens",
        "Telefone",
        "Relógio",
        "Notas",
        "Arquivos",
        "Clima",
        "Calculadora",
        "Contatos",
        "Navegador",
        "Livros",
        "Vídeos",
        "Podcasts",
        "E-mail",
        "Saúde",
        "Viagens",
        "Desenho",
        "Documentos",
        "Gravador",
        "Tarefas",
        "Receitas",
        "Tradução",
        "Notícias",
        "Rádio",
        "Jogos",
        "Compras",
        "Finanças",
        "Cinema",
        "Estudos",
        "Esportes",
        "Leitura",
        "Meditação",
        "Casa",
        "Trânsito",
        "Coleções",
        "Diário",
        "Projetos",
        "Atalhos",
        "Inspiração",
        "Planilhas",
        "Editor",
        "Favoritos",
        "Lembretes",
        "Galeria",
        "Explorar",
        "Scanner",
        "Treinos",
        "Idiomas",
        "Biblioteca",
        "Calendário",
        "Cronômetro",
        "Ajustes",
        "Conexões",
        "Downloads",
        "TV",
        "Comunidade",
        "Capturas",
        "Caderno",
        "Despertador",
        "Homely",
    )

/**
 * The editor and the installed launcher use this same state machine and renderer. External effects
 * are delegated only in launcher mode; previews remain inside this surface.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@Composable
fun ThemeExperience(
    draft: Draft,
    preview: Boolean,
    modifier: Modifier = Modifier,
    hiddenPackages: Set<String> = emptySet(),
    onAction: (String, String?) -> Unit = { _, _ -> },
    nativeWidgets: @Composable () -> Unit = {},
) {
    val ctx = LocalContext.current
    val layout = draft.manifest.layout
    val palette = draft.manifest.palette
    val haptic = LocalHapticFeedback.current
    var drawer by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var forceSearch by remember { mutableStateOf(false) }
    var homePage by remember { mutableIntStateOf(0) }
    var drawerPage by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var previewApp by remember { mutableStateOf<InstalledApp?>(null) }
    var previewMenu by remember { mutableStateOf<String?>(null) }
    var contextMenuApp by remember { mutableStateOf<InstalledApp?>(null) }
    var contextMenuWidget by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    val installed by
        produceState<List<InstalledApp>>(emptyList(), preview, refresh) {
            if (!preview) value = withContext(Dispatchers.IO) { NativeActions.apps(ctx) }
        }
    val customized =
        remember(draft.icons, ctx) {
            draft.icons.associateBy {
                WidgetActions.appIntent(ctx, it.packageName)
                    ?.resolveActivity(ctx.packageManager)
                    ?.packageName ?: it.packageName
            }
        }
    val apps =
        remember(installed, draft.icons, preview) {
            val source =
                if (preview) {
                    draft.icons.map { InstalledApp(it.packageName, it.label) } +
                        sampleAppNames.mapIndexed { i, label ->
                            InstalledApp("preview.app$i", label)
                        }
                } else installed
            source
                .distinctBy { it.packageName }
                .sortedWith(
                    compareBy<InstalledApp>(
                        {
                            draft.icons
                                .indexOfFirst { icon -> icon.packageName == it.packageName }
                                .let { index -> if (index < 0) Int.MAX_VALUE else index }
                        },
                        { it.label.lowercase() },
                    )
                )
        }
    val filtered = remember(apps, query) { apps.filter { it.label.contains(query, true) } }
    val homeApps =
        remember(apps, hiddenPackages) { apps.filterNot { it.packageName in hiddenPackages } }
    val pageSize = (layout.drawerColumns * layout.drawerRows).coerceAtLeast(1)
    val drawerPageCount = ((filtered.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val homePageSize = (layout.columns * layout.rows).coerceAtLeast(1)
    val homePageCount = (layout.kit.maxOfOrNull { it.page } ?: 0) + 1
    val mode = layout.effectiveDrawerMode()
    val paged = mode == "pages"
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val duration = if (layout.reduceMotion) 0 else layout.animationDuration
    val animation = if (layout.reduceMotion) "none" else layout.drawerAnimation
    LaunchedEffect(query, layout.drawerRows, layout.drawerColumns, mode) { drawerPage = 0 }
    LaunchedEffect(homePageCount) { homePage = homePage.coerceIn(0, homePageCount - 1) }

    fun perform(action: String, packageName: String? = null) {
        if (action == "none") return
        if (layout.hapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (action) {
            "drawer",
            "search" -> {
                drawer = true
                if (action == "search") forceSearch = true
                refresh++
            }
            "close_drawer" -> {
                drawer = false
                query = ""
                forceSearch = false
            }
            "next_page" ->
                if (drawer) drawerPage = (drawerPage + 1).coerceAtMost(drawerPageCount - 1)
                else homePage = (homePage + 1).coerceAtMost(homePageCount - 1)
            "previous_page" ->
                if (drawer) drawerPage = (drawerPage - 1).coerceAtLeast(0)
                else homePage = (homePage - 1).coerceAtLeast(0)
            "launch",
            "app" -> {
                val pkg = packageName ?: layout.gestures.quickPackage
                if (preview)
                    previewApp =
                        apps.firstOrNull { it.packageName == pkg }
                            ?: InstalledApp(
                                pkg ?: "preview.shortcut",
                                if (pkg == null) "Atalho de aplicativo"
                                else pkg.substringAfterLast('.'),
                            )
                else onAction("launch", pkg)
            }
            "icon_long_press" -> {
                if (packageName == "widget") {
                    contextMenuWidget = true
                    return
                }
                val app =
                    packageName?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
                        ?: packageName?.let { InstalledApp(it, it.substringAfterLast('.')) }
                if (app != null) {
                    contextMenuApp = app
                } else if (preview) {
                    previewMenu = "Menu do aplicativo"
                } else onAction(action, packageName)
            }
            "widget_long_press" -> {
                contextMenuWidget = true
            }
            "personalize",
            "studio" -> {
                if (preview) editMode = true else onAction(action, packageName)
            }
            else ->
                if (preview) {
                    previewMenu =
                        when (action) {
                            "wallpaper" -> "Escolher wallpaper"
                            "widgets" -> "Adicionar widgets"
                            "lock" -> "Tela bloqueada"
                            else -> action
                        }
                } else onAction(action, packageName)
        }
    }
    fun gestureAction(gesture: String): String {
        // Navigation within a paged drawer follows its own selected axis. On the first
        // vertical page, the downward binding remains available to return home.
        if (drawer && paged) {
            if (layout.drawerDirection == "vertical") {
                if (gesture == "swipe_up") return "next_page"
                if (gesture == "swipe_down" && drawerPage > 0) return "previous_page"
            } else {
                if (gesture == "swipe_left") return layout.gestures.swipeLeft
                if (gesture == "swipe_right") return layout.gestures.swipeRight
            }
        }
        return layout.gestures.actionFor(gesture, drawer)
    }
    fun canGesture(gesture: String): Boolean {
        if (previewApp != null || previewMenu != null) return false
        if (!drawer || paged || gesture.startsWith("pinch")) return gestureAction(gesture) != "none"
        if (gesture == "swipe_up") return false
        if (gesture == "swipe_down") {
            val atTop =
                if (mode == "list") !listState.canScrollBackward else !gridState.canScrollBackward
            return atTop && gestureAction(gesture) != "none"
        }
        return gestureAction(gesture) != "none"
    }
    // Pointer coroutines outlive recompositions; every gesture must resolve against
    // the live draft, including page counts, haptics and bindings edited mid-preview.
    // Local callable references compare equal even when their captured layout changes.
    // Lambdas let rememberUpdatedState replace the callback with the new capture.
    val currentCanGesture by rememberUpdatedState<(String) -> Boolean>({ canGesture(it) })
    val currentGesture by rememberUpdatedState<(String) -> Unit>({ perform(gestureAction(it)) })
    val currentPerform by
        rememberUpdatedState<(String, String?) -> Unit>({ action, packageName ->
            perform(action, packageName)
        })
    val currentGestures by rememberUpdatedState(layout.gestures)
    BackHandler(
        drawer ||
            previewApp != null ||
            previewMenu != null ||
            contextMenuApp != null ||
            editMode ||
            contextMenuWidget
    ) {
        when {
            contextMenuApp != null -> contextMenuApp = null
            contextMenuWidget -> contextMenuWidget = false
            editMode -> editMode = false
            previewApp != null -> previewApp = null
            previewMenu != null -> previewMenu = null
            else -> perform("close_drawer")
        }
    }
    val foreground = color(palette.text)
    Box(
        modifier
            .testTag("theme-experience")
            .semantics {
                stateDescription =
                    if (drawer)
                        "Grade, ${layout.drawerRows} linhas, ${layout.drawerColumns} colunas, página ${drawerPage + 1}"
                    else "Tela inicial, página ${homePage + 1}"
            }
            .background(color(palette.background))
            .themeSwipeInput(Unit, { currentCanGesture(it) }, { currentGesture(it) })
            .pointerInput(layout.gestures, drawer, previewApp, previewMenu) {
                detectTapGestures(
                    onTap = {
                        if (previewApp == null && previewMenu == null)
                            currentPerform(currentGestures.tap, null)
                    },
                    onDoubleTap = {
                        if (previewApp == null && previewMenu == null)
                            currentPerform(currentGestures.doubleTap, null)
                    },
                    onLongPress = {
                        if (previewApp == null && previewMenu == null)
                            currentPerform(currentGestures.longPress, null)
                    },
                )
            }
    ) {
        Box(Modifier.fillMaxSize().blur(if (drawer) layout.drawerBlur.dp else 0.dp)) {
            if (draft.videoPath != null) {
                AndroidView(
                    factory = { VideoView(it) },
                    update = { view ->
                        if (view.tag != draft.videoPath) {
                            view.tag = draft.videoPath
                            view.setVideoPath(draft.videoPath)
                            view.setOnPreparedListener { player ->
                                player.isLooping = true
                                player.setVolume(0f, 0f)
                                view.start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { it.stopPlayback() },
                )
            } else if (draft.wallpaperPath != null) {
                AsyncImage(
                    File(draft.wallpaperPath),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .12f)))
        }
        Column(
            Modifier.fillMaxSize()
                .then(if (!preview) Modifier.navigationBarsPadding() else Modifier)
        ) {
            CustomStatusBar(layout.statusBar) { perform("studio") }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    Modifier.fillMaxSize()
                        .testTag("theme-home")
                        .then(if (drawer) Modifier.clearAndSetSemantics {} else Modifier)
                ) {
                    AnimatedContent(
                        targetState = homePage,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        transitionSpec = {
                            experiencePageTransition(
                                animation,
                                duration,
                                layout.scrollDirection,
                                targetState > initialState,
                            )
                        },
                        label = "home-page",
                    ) { page ->
                        ExperienceKit(
                            draft,
                            page,
                            preview,
                            hiddenPackages,
                            onLaunch = { perform("launch", it) },
                            onLongPress = { perform("icon_long_press", it) },
                        )
                    }
                    if (layout.showPageIndicators && homePageCount > 1)
                        ExperiencePageControls(
                            homePage,
                            homePageCount,
                            foreground,
                            "home",
                            { perform("previous_page") },
                            { perform("next_page") },
                        )
                    if (layout.showSearchBar) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(color(palette.surface).copy(alpha = 0.5f))
                                    .combinedClickable(
                                        onClick = {
                                            perform(
                                                "launch",
                                                "com.google.android.googlequicksearchbox",
                                            )
                                        },
                                        onLongClick = { perform("widget_long_press") },
                                    )
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Search, null, tint = foreground)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Pesquisar",
                                    color = foreground.copy(alpha = 0.8f),
                                    fontSize = 16.sp,
                                )
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Outlined.Mic, null, tint = foreground)
                            }
                        }
                    }
                    val dockApps =
                        remember(layout.dock, apps) {
                            layout.dock
                                .mapNotNull { pkg ->
                                    apps.find { it.packageName == pkg }
                                        ?: apps.find {
                                            it.packageName.contains(
                                                pkg.split(".").last(),
                                                ignoreCase = true,
                                            )
                                        }
                                }
                                .distinctBy { it.packageName }
                                .take(layout.columns)
                        }
                    if (dockApps.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            dockApps.forEach { app ->
                                ExperienceApp(
                                    app = app,
                                    draft = draft,
                                    custom = customized[app.packageName],
                                    iconSize = layout.iconSize.dp,
                                    modifier =
                                        Modifier.weight(1f).testTag("dock-app-${app.packageName}"),
                                    showLabel = false,
                                    onClick = { perform("launch", app.packageName) },
                                    onLongClick = { perform("icon_long_press", app.packageName) },
                                )
                            }
                        }
                    }
                    if (layout.quickAction != "none")
                        TextButton(
                            { perform(layout.quickAction) },
                            Modifier.align(Alignment.CenterHorizontally).testTag("quick-action"),
                        ) {
                            Text(
                                when (layout.quickAction) {
                                    "search" -> "Buscar"
                                    "app" -> "Atalho rápido"
                                    else -> "Abrir Studio"
                                },
                                color = foreground,
                            )
                        }
                    if (layout.showDrawerButton && layout.drawerStyle == "button")
                        FilledTonalIconButton(
                            onClick = { perform("drawer") },
                            modifier =
                                Modifier.align(Alignment.CenterHorizontally)
                                    .padding(bottom = 10.dp)
                                    .testTag("drawer-open"),
                        ) {
                            Icon(Icons.Outlined.Apps, "Abrir grade de aplicativos")
                        }
                    else if (layout.showDrawerButton)
                        TextButton(
                            { perform("drawer") },
                            Modifier.fillMaxWidth().testTag("drawer-open"),
                        ) {
                            Icon(
                                Icons.Outlined.Apps,
                                null,
                                tint = foreground,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Todos os aplicativos", color = foreground)
                        }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = drawer,
                    enter = experienceEnter(animation, duration),
                    exit = experienceExit(animation, duration),
                ) {
                    Column(
                        Modifier.fillMaxSize()
                            .testTag("theme-drawer")
                            .clip(
                                RoundedCornerShape(
                                    topStart = layout.drawerRadius.dp,
                                    topEnd = layout.drawerRadius.dp,
                                )
                            )
                            .background(color(palette.surface).copy(alpha = layout.drawerOpacity))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { currentPerform(currentGestures.tap, null) },
                                    onDoubleTap = {
                                        currentPerform(currentGestures.doubleTap, null)
                                    },
                                    onLongPress = {
                                        currentPerform(currentGestures.longPress, null)
                                    },
                                )
                            }
                    ) {
                        if (layout.showDrawerHeader || layout.showDrawerCloseButton)
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(start = layout.drawerMargin.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (layout.showDrawerHeader)
                                    Column(Modifier.weight(1f).testTag("drawer-header")) {
                                        Text(
                                            "Aplicativos",
                                            color = foreground,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 19.sp,
                                        )
                                        Text(
                                            if (paged)
                                                "${layout.drawerRows} × ${layout.drawerColumns} · ${filtered.size} apps"
                                            else "${filtered.size} aplicativos",
                                            color = foreground.copy(alpha = .65f),
                                            fontSize = 11.sp,
                                        )
                                    }
                                else Spacer(Modifier.weight(1f))
                                if (layout.showDrawerCloseButton)
                                    IconButton(
                                        { perform("close_drawer") },
                                        Modifier.testTag("drawer-close"),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            "Voltar à tela inicial",
                                            tint = foreground,
                                        )
                                    }
                            }
                        if (layout.drawerShowSearch || forceSearch)
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = layout.drawerMargin.dp)
                                        .testTag("drawer-search"),
                                placeholder = { Text("Buscar aplicativo", fontSize = 13.sp) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                                shape = RoundedCornerShape(18.dp),
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = foreground,
                                        unfocusedTextColor = foreground,
                                    ),
                            )
                        if (filtered.isEmpty())
                            Box(
                                Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Nenhum aplicativo encontrado", color = foreground)
                            }
                        else if (paged) {
                            AnimatedContent(
                                targetState = drawerPage.coerceIn(0, drawerPageCount - 1),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                transitionSpec = {
                                    experiencePageTransition(
                                        animation,
                                        duration,
                                        layout.drawerDirection,
                                        targetState > initialState,
                                    )
                                },
                                label = "drawer-page",
                            ) { page ->
                                ExperienceAppPage(
                                    filtered.drop(page * pageSize).take(pageSize),
                                    draft,
                                    customized,
                                    Modifier.fillMaxSize().testTag("drawer-page-$page"),
                                    onClick = { perform("launch", it) },
                                    onLongClick = { perform("icon_long_press", it) },
                                )
                            }
                            if (layout.showPageIndicators)
                                ExperiencePageControls(
                                    drawerPage,
                                    drawerPageCount,
                                    foreground,
                                    "drawer",
                                    { perform("previous_page") },
                                    { perform("next_page") },
                                )
                        } else if (mode == "list") {
                            LazyColumn(
                                Modifier.weight(1f).fillMaxWidth(),
                                state = listState,
                                contentPadding = PaddingValues(layout.drawerMargin.dp),
                                verticalArrangement = Arrangement.spacedBy(layout.drawerSpacing.dp),
                            ) {
                                items(filtered, key = { it.packageName }) { app ->
                                    ExperienceApp(
                                        app,
                                        draft,
                                        customized[app.packageName],
                                        layout.drawerIconSize.dp,
                                        Modifier.testTag("drawer-app-${app.packageName}"),
                                        list = true,
                                        onClick = { perform("launch", app.packageName) },
                                        onLongClick = {
                                            perform("icon_long_press", app.packageName)
                                        },
                                    )
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                GridCells.Fixed(layout.drawerColumns),
                                Modifier.weight(1f).fillMaxWidth(),
                                state = gridState,
                                contentPadding = PaddingValues(layout.drawerMargin.dp),
                                verticalArrangement = Arrangement.spacedBy(layout.drawerSpacing.dp),
                                horizontalArrangement =
                                    Arrangement.spacedBy(layout.drawerSpacing.dp),
                            ) {
                                items(filtered, key = { it.packageName }) { app ->
                                    ExperienceApp(
                                        app,
                                        draft,
                                        customized[app.packageName],
                                        layout.drawerIconSize.dp,
                                        Modifier.testTag("drawer-app-${app.packageName}"),
                                        onClick = { perform("launch", app.packageName) },
                                        onLongClick = {
                                            perform("icon_long_press", app.packageName)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (layout.showNavigationHandle)
                Box(
                    Modifier.fillMaxWidth().height(14.dp).testTag("navigation-handle"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.width(72.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(foreground.copy(alpha = .5f))
                    )
                }
        }
        AnimatedContent(
            targetState = previewApp,
            transitionSpec = {
                val appAnimation = if (layout.reduceMotion) "none" else layout.appAnimation
                experienceEnter(appAnimation, duration) togetherWith
                    experienceExit(appAnimation, duration)
            },
            label = "preview-application",
        ) { app ->
            if (app != null)
                ExperiencePreviewOverlay(
                    "preview-app",
                    app.label,
                    "Aplicativo aberto no preview",
                    foreground,
                    color(palette.surface),
                    { previewApp = null },
                ) {
                    ExperienceAppImage(
                        app,
                        customized[app.packageName],
                        draft.manifest.iconStyle,
                        64.dp,
                    )
                    Text(
                        "Teste concluído. Volte para continuar navegando no seu tema.",
                        color = foreground.copy(alpha = .7f),
                        fontSize = 13.sp,
                    )
                }
        }
        previewMenu?.let { title ->
            ExperiencePreviewOverlay(
                "preview-menu",
                title,
                "Demonstração interativa",
                foreground,
                color(palette.surface),
                { previewMenu = null },
            ) {
                Icon(
                    if (title == "Tela bloqueada") Icons.Outlined.Lock else Icons.Outlined.Tune,
                    null,
                    Modifier.size(38.dp),
                    tint = color(palette.accent),
                )
                Text(
                    "Esta ação será executada pelo Android quando o tema estiver aplicado.",
                    color = foreground.copy(alpha = .7f),
                    fontSize = 13.sp,
                )
            }
        }
        // Samsung-style app context menu
        contextMenuApp?.let { app ->
            AppContextMenu(
                app = app,
                draft = draft,
                customized = customized,
                foreground = foreground,
                preview = preview,
                onDismiss = { contextMenuApp = null },
                onAction = { action, pkg ->
                    contextMenuApp = null
                    when (action) {
                        "launch" -> perform("launch", pkg)
                        "info" -> onAction("info", pkg)
                        "uninstall" -> onAction("uninstall", pkg)
                        else -> onAction(action, pkg)
                    }
                },
            )
        }
        // Samsung-style home edit mode
        if (editMode) {
            HomeEditModeOverlay(
                draft = draft,
                homePage = homePage,
                homePageCount = homePageCount,
                foreground = foreground,
                onDismiss = { editMode = false },
                onAction = { action ->
                    editMode = false
                    when (action) {
                        "wallpaper" -> onAction("wallpaper", null)
                        "themes" -> onAction("studio", null)
                        "widgets" -> onAction("widgets", null)
                        "settings" -> onAction("settings", null)
                        else -> {}
                    }
                },
                onPageSelect = { homePage = it },
            )
        }
        if (contextMenuWidget) {
            WidgetContextMenuOverlay(
                onDismiss = { contextMenuWidget = false },
                onAction = { action ->
                    contextMenuWidget = false
                    when (action) {
                        "widgets" -> onAction("widgets", null)
                        "studio" -> onAction("studio", null)
                        "settings" -> onAction("settings", null)
                    }
                },
            )
        }
    }
}

private fun experienceEnter(animation: String, duration: Int): EnterTransition =
    when (animation) {
        "fade" -> fadeIn(tween(duration))
        "zoom" -> scaleIn(tween(duration), initialScale = .86f) + fadeIn(tween(duration))
        "none" -> EnterTransition.None
        else -> slideInVertically(tween(duration)) { it } + fadeIn(tween(duration))
    }

private fun experienceExit(animation: String, duration: Int): ExitTransition =
    when (animation) {
        "fade" -> fadeOut(tween(duration))
        "zoom" -> scaleOut(tween(duration), targetScale = .86f) + fadeOut(tween(duration))
        "none" -> ExitTransition.None
        else -> slideOutVertically(tween(duration)) { it } + fadeOut(tween(duration))
    }

private fun experiencePageTransition(
    animation: String,
    duration: Int,
    direction: String,
    forward: Boolean,
): ContentTransform {
    if (animation != "slide")
        return experienceEnter(animation, duration) togetherWith experienceExit(animation, duration)
    val sign = if (forward) 1 else -1
    return if (direction == "vertical")
        (slideInVertically(tween(duration)) { it * sign } + fadeIn(tween(duration))) togetherWith
            (slideOutVertically(tween(duration)) { -it * sign } + fadeOut(tween(duration)))
    else
        (slideInHorizontally(tween(duration)) { it * sign } + fadeIn(tween(duration))) togetherWith
            (slideOutHorizontally(tween(duration)) { -it * sign } + fadeOut(tween(duration)))
}

@Composable
private fun ExperiencePageControls(
    page: Int,
    count: Int,
    foreground: Color,
    prefix: String,
    previous: () -> Unit,
    next: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(previous, Modifier.testTag("$prefix-previous"), enabled = page > 0) {
            Icon(
                Icons.Outlined.ChevronLeft,
                "Página anterior",
                tint = foreground.copy(alpha = if (page > 0) 1f else .3f),
            )
        }
        Text(
            "${page + 1} / $count",
            Modifier.testTag("$prefix-page-indicator"),
            color = foreground,
            fontSize = 12.sp,
        )
        IconButton(next, Modifier.testTag("$prefix-next"), enabled = page < count - 1) {
            Icon(
                Icons.Outlined.ChevronRight,
                "Próxima página",
                tint = foreground.copy(alpha = if (page < count - 1) 1f else .3f),
            )
        }
    }
}

@Composable
private fun ExperienceAppPage(
    apps: List<InstalledApp>,
    draft: Draft,
    customized: Map<String, DraftIcon>,
    modifier: Modifier,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
) {
    val layout = draft.manifest.layout
    BoxWithConstraints(modifier.padding(layout.drawerMargin.dp)) {
        val gap =
            layout.drawerSpacing.dp.coerceAtMost(
                (maxHeight / (layout.drawerRows * 3)).coerceAtLeast(0.dp)
            )
        val rowHeight =
            ((maxHeight - gap * (layout.drawerRows - 1)) / layout.drawerRows).coerceAtLeast(1.dp)
        val columnWidth =
            ((maxWidth - gap * (layout.drawerColumns - 1)) / layout.drawerColumns).coerceAtLeast(
                1.dp
            )
        val iconSize =
            layout.drawerIconSize.dp
                .coerceAtMost(columnWidth)
                .coerceAtMost(
                    (rowHeight - if (layout.showLabels) 20.dp else 0.dp).coerceAtLeast(8.dp)
                )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(layout.drawerRows) { row ->
                Row(
                    Modifier.fillMaxWidth().height(rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    repeat(layout.drawerColumns) { col ->
                        val app = apps.getOrNull(row * layout.drawerColumns + col)
                        if (app == null) Spacer(Modifier.weight(1f))
                        else
                            ExperienceApp(
                                app,
                                draft,
                                customized[app.packageName],
                                iconSize,
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .testTag("drawer-app-${app.packageName}"),
                                onClick = { onClick(app.packageName) },
                                onLongClick = { onLongClick(app.packageName) },
                            )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExperienceApp(
    app: InstalledApp,
    draft: Draft,
    custom: DraftIcon?,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    list: Boolean = false,
    showLabel: Boolean = draft.manifest.layout.showLabels,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val context = LocalContext.current
    val available =
        remember(app.packageName) {
            app.packageName.startsWith("preview.") ||
                runCatching {
                        context.packageManager.getApplicationInfo(app.packageName, 0).enabled
                    }
                    .getOrDefault(false)
        }
    val displayed = custom?.let { item ->
        item.copy(
            path =
                item.statePaths[
                        if (!available) "disabled" else if (pressed) "pressed" else "default"]
                    ?: item.path
        )
    }
    val clickable =
        modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    val foreground = color(draft.manifest.palette.text)
    if (list)
        Row(clickable.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ExperienceAppImage(app, displayed, draft.manifest.iconStyle, iconSize)
            Spacer(Modifier.width(14.dp))
            Text(
                app.label,
                color = foreground,
                fontFamily = studioFont(draft.manifest.fontFamily),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.ChevronRight,
                null,
                tint = foreground.copy(alpha = .4f),
                modifier = Modifier.size(18.dp),
            )
        }
    else
        Column(
            clickable.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ExperienceAppImage(app, displayed, draft.manifest.iconStyle, iconSize)
            if (showLabel) {
                Spacer(Modifier.height(4.dp))
                Text(
                    app.label,
                    color = foreground,
                    fontFamily = studioFont(draft.manifest.fontFamily),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
}

@Composable
private fun ExperienceAppImage(app: InstalledApp, custom: DraftIcon?, style: String, size: Dp) {
    val ctx = LocalContext.current
    val original by
        produceState<ImageBitmap?>(null, app.packageName, custom) {
            if (custom == null && !app.packageName.startsWith("preview."))
                value =
                    withContext(Dispatchers.IO) {
                        runCatching {
                            ctx.packageManager
                                .getApplicationIcon(app.packageName)
                                .toBitmap(144, 144)
                                .asImageBitmap()
                        }
                            .getOrNull()
                    }
        }
    val shape =
        when (style) {
            "circle" -> CircleShape
            "teardrop" -> TeardropShape
            "hexagon" -> HexagonShape
            "squircle" -> RoundedCornerShape(20.dp)
            else -> RoundedCornerShape(13.dp)
        }
    if (custom != null)
        AsyncImage(
            File(custom.path),
            app.label,
            Modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    else if (original != null) Image(original!!, app.label, Modifier.size(size).clip(shape))
    else {
        val colors =
            listOf(
                Color(0xFF7965FF),
                Color(0xFFEE9472),
                Color(0xFF62BFB3),
                Color(0xFF679CDD),
                Color(0xFFD481B9),
            )
        val index =
            (app.packageName.hashCode().toLong().let { kotlin.math.abs(it) } % colors.size).toInt()
        Box(
            Modifier.size(size).clip(shape).background(colors[index]),
            contentAlignment = Alignment.Center,
        ) {
            val symbol =
                when (index) {
                    0 -> Icons.Outlined.AutoAwesome
                    1 -> Icons.Outlined.FavoriteBorder
                    2 -> Icons.Outlined.GridView
                    3 -> Icons.Outlined.Explore
                    else -> Icons.Outlined.Bolt
                }
            Icon(symbol, app.label, Modifier.size(size * .53f), tint = Color.White)
        }
    }
}

@Composable
private fun ExperienceKit(
    draft: Draft,
    page: Int,
    preview: Boolean,
    hidden: Set<String>,
    onLaunch: (String) -> Unit,
    onLongPress: (String) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
        val width = maxWidth
        val height = maxHeight
        val density = LocalDensity.current
        draft.manifest.layout.kit
            .filter { it.page == page && !(it.kind == "icon" && it.reference in hidden) }
            .forEach { item ->
                Box(
                    Modifier.offset(width * item.x, height * item.y)
                        .size(width * item.width, height * item.height)
                        .then(
                            if (item.kind == "widget")
                                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                                Modifier.combinedClickable(
                                    onLongClick = { onLongPress("widget") },
                                    onClick = {},
                                )
                            else Modifier
                        )
                ) {
                    if (item.kind == "widget")
                        draft.manifest.widgets
                            .firstOrNull { it.id == item.reference }
                            ?.let { widget ->
                                CompositionLocalProvider(
                                    LocalDensity provides
                                        Density(
                                            density.density *
                                                (height.value * item.height / widget.height)
                                                    .coerceAtLeast(.1f),
                                            density.fontScale,
                                        )
                                ) {
                                    ComposedWidget(
                                        widget,
                                        draft.widgetImages[widget.id],
                                        preview = preview,
                                        images = draft.widgetImages,
                                        fonts = draft.fontFiles,
                                    )
                                }
                            }
                    else {
                        val custom = draft.icons.firstOrNull { it.packageName == item.reference }
                        ExperienceApp(
                            InstalledApp(
                                item.reference,
                                custom?.label ?: item.reference.substringAfterLast('.'),
                            ),
                            draft,
                            custom,
                            (width * item.width * .72f).coerceAtMost(
                                draft.manifest.layout.iconSize.dp
                            ),
                            Modifier.fillMaxSize().testTag("home-app-${item.reference}"),
                            onClick = { onLaunch(item.reference) },
                            onLongClick = { onLongPress(item.reference) },
                        )
                    }
                }
            }
    }
}

@Composable
private fun ExperiencePreviewOverlay(
    tag: String,
    title: String,
    subtitle: String,
    foreground: Color,
    background: Color,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .7f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.padding(24.dp).fillMaxWidth().testTag(tag).clickable {},
            shape = RoundedCornerShape(26.dp),
            color = background,
        ) {
            Column(
                Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(subtitle, color = foreground.copy(alpha = .6f), fontSize = 11.sp)
                Text(title, color = foreground, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                content()
                FilledTonalButton(onClose, Modifier.testTag("$tag-close")) {
                    Text("Voltar ao tema")
                }
            }
        }
    }
}

/**
 * Samsung One UI-style app context menu: shows the app name, shortcuts list, and quick actions
 * (Select, Add to Home, Uninstall, App Info) in a floating card with spring animation.
 */
@Composable
private fun AppContextMenu(
    app: InstalledApp,
    draft: Draft,
    customized: Map<String, DraftIcon>,
    foreground: Color,
    preview: Boolean,
    onDismiss: () -> Unit,
    onAction: (String, String) -> Unit,
) {
    val ctx = LocalContext.current
    val shortcuts by
        produceState<List<AppShortcutInfo>>(emptyList(), app.packageName) {
            if (!preview)
                value =
                    withContext(Dispatchers.IO) {
                        NativeActions.shortcuts(ctx, app.packageName)
                    }
        }
    val scale by
        animateFloatAsState(
            targetValue = 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "ctx-scale",
        )
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .45f))
            .clickable(onClick = onDismiss)
            .testTag("app-context-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(enabled = false) {}
                .testTag("app-context-menu"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // App icon + name header
            ExperienceAppImage(
                app,
                customized[app.packageName],
                draft.manifest.iconStyle,
                56.dp,
            )
            Text(
                app.label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            // App Info button
            IconButton(
                onClick = { onAction("info", app.packageName) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Outlined.Info,
                    "Informações",
                    tint = Color.White.copy(alpha = .7f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.height(2.dp))
            // Shortcuts card (Samsung-style rounded card)
            if (shortcuts.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF2A2A2A).copy(alpha = .92f),
                    shadowElevation = 12.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        shortcuts.forEachIndexed { index, shortcut ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { onAction("launch", app.packageName) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = .1f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val icon =
                                        when (index) {
                                            0 -> Icons.Outlined.PlayArrow
                                            1 -> Icons.Outlined.Search
                                            2 -> Icons.Outlined.Subscriptions
                                            else -> Icons.Outlined.Bookmark
                                        }
                                    Icon(icon, null, Modifier.size(16.dp), tint = Color.White)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    shortcut.label,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (index < shortcuts.lastIndex)
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp),
                                    color = Color.White.copy(alpha = .08f),
                                )
                        }
                    }
                }
            }
            // Quick actions row (Select, Add to Home, Uninstall, Widgets)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF2A2A2A).copy(alpha = .92f),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ContextQuickAction(Icons.Outlined.CheckCircle, "Selecionar") {}
                    ContextQuickAction(Icons.Outlined.AddCircleOutline, "Ad. à Tela\ninicial") {
                        onAction("add_home", app.packageName)
                    }
                    ContextQuickAction(Icons.Outlined.Delete, "Desinstalar") {
                        onAction("uninstall", app.packageName)
                    }
                    ContextQuickAction(Icons.Outlined.Widgets, "Widgets") {
                        onAction("widgets", app.packageName)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContextQuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, Modifier.size(20.dp), tint = Color.White)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White.copy(alpha = .8f),
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 12.sp,
        )
    }
}

/**
 * Samsung One UI-style home edit mode: zoomed-out pages with wallpaper visible, bottom toolbar with
 * Wallpaper, Themes, Widgets, Settings options.
 */
@Composable
private fun HomeEditModeOverlay(
    draft: Draft,
    homePage: Int,
    homePageCount: Int,
    foreground: Color,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
    onPageSelect: (Int) -> Unit,
) {
    val palette = draft.manifest.palette
    val scale by
        animateFloatAsState(
            targetValue = 0.72f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "edit-scale",
        )
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .55f))
            .clickable(onClick = onDismiss)
            .testTag("home-edit-mode")
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top bar with delete icon
            Row(
                Modifier.fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Outlined.Home, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Icon(
                    Icons.Outlined.Delete,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            // Zoomed-out pages
            Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(homePageCount.coerceAtMost(3)) { page ->
                        val selected = page == homePage
                        val pageSizeScale = if (selected) 0.58f else 0.46f
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = color(palette.background),
                            shadowElevation = if (selected) 16.dp else 4.dp,
                            border =
                                if (selected) BorderStroke(2.dp, Color.White.copy(alpha = .3f))
                                else null,
                            modifier =
                                Modifier.padding(horizontal = 6.dp)
                                    .fillMaxWidth(
                                        pageSizeScale / homePageCount.coerceAtMost(3).toFloat()
                                    )
                                    .aspectRatio(9f / 19.5f)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        alpha = if (selected) 1f else 0.7f
                                    }
                                    .clickable { onPageSelect(page) },
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (draft.wallpaperPath != null)
                                    AsyncImage(
                                        File(draft.wallpaperPath),
                                        null,
                                        Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                Box(
                                    Modifier.fillMaxSize()
                                        .background(Color.Black.copy(alpha = .15f))
                                )
                            }
                        }
                    }
                }
            }
            // Page indicators
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(homePageCount.coerceAtMost(5)) { page ->
                    val selected = page == homePage
                    Box(
                        Modifier.padding(horizontal = 3.dp)
                            .size(if (selected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Color.White else Color.White.copy(alpha = .4f)
                            )
                    )
                }
                // Add page button
                Icon(
                    Icons.Outlined.Add,
                    "Adicionar página",
                    tint = Color.White,
                    modifier = Modifier.padding(start = 4.dp).size(16.dp),
                )
            }
            // Bottom toolbar
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                color = color(palette.surface).copy(alpha = .85f),
                modifier = Modifier.fillMaxWidth().clickable(enabled = false) {},
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 16.dp).navigationBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    EditModeAction(Icons.Outlined.Wallpaper, "Papel de\nparede e est...") {
                        onAction("wallpaper")
                    }
                    EditModeAction(Icons.Outlined.Palette, "Temas") { onAction("themes") }
                    EditModeAction(Icons.Outlined.Widgets, "Widgets") { onAction("widgets") }
                    EditModeAction(Icons.Outlined.Settings, "Configurações") {
                        onAction("settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditModeAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, Modifier.size(24.dp), tint = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 14.sp,
        )
    }
}

/** Samsung-style Widget context menu. */
@Composable
private fun WidgetContextMenuOverlay(
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
) {
    val scale by
        animateFloatAsState(
            targetValue = 1f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            label = "widget-ctx-scale",
        )
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = .45f))
            .clickable(onClick = onDismiss)
            .testTag("widget-context-overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 32.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clickable(enabled = false) {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF2A2A2A).copy(alpha = .92f),
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    WidgetContextMenuItem(Icons.Outlined.Widgets, "Meus Widgets Homely") {
                        onAction("widgets")
                    }
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = Color.White.copy(alpha = .08f),
                    )
                    WidgetContextMenuItem(Icons.Outlined.Palette, "Personalizar Tema") {
                        onAction("studio")
                    }
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = Color.White.copy(alpha = .08f),
                    )
                    WidgetContextMenuItem(Icons.Outlined.Settings, "Configurações da Tela") {
                        onAction("settings")
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetContextMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = Color.White)
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
