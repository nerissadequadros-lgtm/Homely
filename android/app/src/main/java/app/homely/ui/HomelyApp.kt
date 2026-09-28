package app.homely.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HomelyApp(
    vm: HomelyViewModel,
    initialEditor: String? = null,
    initialPackage: String? = null,
    initialNotifications: Boolean = false,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val dark =
        when (state.appearance) {
            "system" -> isSystemInDarkTheme()
            "light" -> false
            else -> true
        }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var notifications by rememberSaveable { mutableStateOf(initialNotifications) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var createMode by rememberSaveable { mutableStateOf<String?>(initialEditor) }
    LaunchedEffect(state.openStudio) {
        if (state.openStudio) {
            createMode = "theme"
            settings = false
            vm.consumedStudio()
        }
    }
    var nestedEditor by remember(createMode) { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    SideEffect {
        (ctx as? Activity)?.window?.let {
            WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !dark
        }
    }
    BackHandler(
        notifications ||
            state.selected != null ||
            state.creator != null ||
            settings ||
            createMode != null ||
            showCreateSheet ||
            tab != 0
    ) {
        when {
            notifications -> notifications = false
            state.creator != null -> vm.closeCreator()
            createMode != null -> createMode = null
            showCreateSheet -> showCreateSheet = false
            settings -> settings = false
            state.selected != null -> vm.closeDetail()
            else -> {
                tab = 0
                vm.loadFeed(category = "Para você", search = "", kind = null)
            }
        }
    }
    HomelyTheme(dark) {
        Scaffold(
            topBar = {
                HomelyHeader(
                    title =
                        when {
                            notifications -> "Notificações"
                            settings -> "Configurações"
                            nestedEditor != null -> nestedEditor!!
                            createMode == "icon" -> "Criador de ícones"
                            createMode == "widget" -> "Criador de Widgets"
                            createMode == "element" -> "Criador de elementos"
                            createMode == "theme" -> "Criador de temas"
                            tab == 4 || state.creator != null -> "Perfil"
                            else -> "Homely"
                        },
                    onHome = {
                        notifications = false
                        createMode = null
                        settings = false
                        tab = 0
                        vm.closeDetail()
                        vm.closeCreator()
                    },
                    onNotifications = {
                        notifications = true
                        vm.refreshDiscovery()
                    },
                    unread = state.discovery.unread,
                    onSearch =
                        if (
                            tab == 0 &&
                                createMode == null &&
                                !notifications &&
                                !settings &&
                                state.selected == null
                        ) {
                            { searchVisible = !searchVisible }
                        } else null,
                    onBack =
                        if (
                            createMode != null ||
                                settings ||
                                notifications ||
                                state.selected != null ||
                                state.creator != null
                        ) {
                            {
                                notifications = false
                                createMode = null
                                settings = false
                                vm.closeDetail()
                                vm.closeCreator()
                            }
                        } else null,
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                RefinedBottomNav(if (createMode != null) 2 else tab, state, vm) { destination ->
                    if (destination == 2) showCreateSheet = true
                    else {
                        notifications = false
                        createMode = null
                        settings = false
                        vm.closeDetail()
                        vm.closeCreator()
                        tab = destination
                        when (destination) {
                            0 -> vm.loadFeed(category = "Para você", search = "", kind = null)
                            1 -> vm.loadIcons()
                            4 -> if (state.profile != null) vm.loadLibrary()
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (state.busy)
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().height(2.dp),
                        color = Violet,
                        trackColor = Color.Transparent,
                    )
                val saveable = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
                val destination =
                    when {
                        notifications -> "notifications"
                        state.creator != null -> "creator"
                        createMode != null -> "create-$createMode"
                        settings -> "settings"
                        state.selected != null -> "detail"
                        else -> "tab-" + tab
                    }
                androidx.compose.animation.Crossfade(
                    targetState = destination,
                    animationSpec = androidx.compose.animation.core.tween(220),
                    label = "screenTransition",
                ) { screen ->
                    saveable.SaveableStateProvider(screen) {
                        when (screen) {
                            "notifications" -> DiscoveryCenter(vm, state) { notifications = false }
                            "creator" -> CreatorProfile(vm, state)
                            "create-theme" ->
                                StudioScreen(vm, state, onEditorTitle = { nestedEditor = it }) {
                                    createMode = null
                                }
                            "create-element" ->
                                ElementCreatorScreen(vm, state) { createMode = null }
                            "create-widget" -> WidgetCreatorScreen(vm, state) { createMode = null }
                            "create-icon" ->
                                IconCreatorScreen(vm, state, initialPackage = initialPackage) {
                                    createMode = null
                                }
                            "settings" -> SettingsScreen(vm, state) { settings = false }
                            "detail" -> DetailScreen(vm, state)
                            "tab-0" ->
                                Column {
                                    FeedHeader(vm, state, searchVisible = searchVisible)
                                    FeedGrid(vm, state)
                                }
                            "tab-1" -> IconsCatalogScreen(vm, state)
                            "tab-3" -> WidgetsCatalogScreen(vm)
                            else -> ProfileScreen(vm, state) { settings = true }
                        }
                    }
                }
            }
        }
        if (createMode != null) StudioTour()
        if (state.authRequired) AuthDialog(vm, state.busy)
        if (showCreateSheet) {
            CreateOptionsSheet(
                onDismiss = { showCreateSheet = false },
                onSelect = { mode ->
                    showCreateSheet = false
                    notifications = false
                    createMode = mode
                },
            )
        }
    }
}

@Composable
private fun AuthDialog(vm: HomelyViewModel, busy: Boolean) {
    var usernameValid by remember { mutableStateOf(false) }
    var register by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = { if (!busy) vm.dismissAuth() }) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrandMark()
                Text(
                    if (register) "Seu espaço começa aqui." else "Que bom ter você aqui.",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Crie, salve e compartilhe um celular com a sua cara.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (register) {
                    Field(name, { name = it }, "Seu nome")
                    UsernameField(username, { username = it }, vm, { usernameValid = it })
                }
                Field(email, { email = it }, "E-mail")
                OutlinedTextField(
                    pass,
                    { pass = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation =
                        androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    shape = RoundedCornerShape(16.dp),
                    supportingText = { if (register) Text("Pelo menos 10 caracteres") },
                )
                Button(
                    { vm.authenticate(email, pass, username, name, register) },
                    Modifier.fillMaxWidth().height(50.dp),
                    enabled =
                        !busy &&
                            (!register || usernameValid) &&
                            email.isNotBlank() &&
                            pass.isNotBlank(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Violet,
                            contentColor = Color.White,
                        ),
                ) {
                    Text(if (register) "Criar conta" else "Entrar")
                }
                TextButton({ register = !register }, Modifier.fillMaxWidth()) {
                    Text(if (register) "Já tenho uma conta" else "Ainda não tenho uma conta")
                }
                Text(
                    "Ao publicar, compartilhe apenas conteúdo que você tem autorização para usar.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
