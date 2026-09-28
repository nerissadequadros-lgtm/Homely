package app.homely.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.homely.data.*
import app.homely.platform.InstalledApp
import app.homely.platform.NativeActions
import app.homely.platform.ScreenLockAdmin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val homeGestureChoices =
    listOf(
        "swipe_up" to "Deslizar para cima",
        "swipe_down" to "Deslizar para baixo",
        "swipe_left" to "Deslizar à esquerda",
        "swipe_right" to "Deslizar à direita",
        "tap" to "Um toque",
        "double_tap" to "Duplo toque",
        "long_press" to "Manter pressionado",
        "pinch_in" to "Juntar dois dedos",
        "pinch_out" to "Afastar dois dedos",
    )

private val homeActionChoices =
    listOf(
        "drawer" to "Abrir grade de apps",
        "close_drawer" to "Fechar grade de apps",
        "search" to "Buscar aplicativos",
        "next_page" to "Próxima página",
        "previous_page" to "Página anterior",
        "personalize" to "Menu de personalização",
        "wallpaper" to "Escolher wallpaper",
        "widgets" to "Adicionar widgets",
        "lock" to "Bloquear a tela",
        "app" to "Abrir aplicativo",
        "none" to "Sem ação",
    )

@Composable
fun GestureSettings(vm: HomelyViewModel, state: HomelyState) {
    val ctx = LocalContext.current
    val layout = state.draft.manifest.layout
    val gestures = layout.gestures
    var selectedGesture by rememberSaveable { mutableStateOf("swipe_up") }
    var drawerScope by rememberSaveable { mutableStateOf(false) }
    var showBindings by rememberSaveable { mutableStateOf(false) }
    var chooseApp by rememberSaveable { mutableStateOf(false) }
    fun editGestures(change: (HomeGestures) -> HomeGestures) = vm.editDraft { draft ->
        draft.copy(
            manifest =
                draft.manifest.copy(
                    layout =
                        draft.manifest.layout.copy(
                            gestures = change(draft.manifest.layout.gestures)
                        )
                )
        )
    }
    val mainEntry =
        if (layout.drawerStyle == "button") "button"
        else homeGestureChoices.firstOrNull { gestures.actionFor(it.first) == "drawer" }?.first
    val bindingGesture = if (drawerScope) "drawer_swipe_down" else selectedGesture
    val bindingAction = gestures.actionFor(bindingGesture)
    val actionChoices =
        if (drawerScope)
            listOf(
                "close_drawer" to "Fechar grade de apps",
                "search" to "Buscar aplicativos",
                "none" to "Sem ação",
            )
        else homeActionChoices
    val configured = homeGestureChoices.filter { gestures.actionFor(it.first) != "none" }
    val actionValues =
        homeGestureChoices.map { gestures.actionFor(it.first) } +
            gestures.actionFor("drawer_swipe_down")
    val accent = color(state.draft.manifest.palette.accent)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        GesturePanel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.TouchApp, null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(
                        "Como abrir os aplicativos?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Escolha uma entrada e veja o movimento.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(
                    listOf(homeGestureChoices.first(), "button" to "Tocar no botão") +
                        homeGestureChoices.drop(1),
                    key = { it.first },
                ) { (gesture, title) ->
                    val isSelected = mainEntry == gesture
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color =
                            if (isSelected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = .52f)
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                        border =
                            BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
                            ),
                        modifier =
                            Modifier.width(132.dp)
                                .semantics { selected = isSelected }
                                .clickable(role = Role.RadioButton) {
                                    vm.editDraft { draft ->
                                        val current = draft.manifest.layout
                                        val updated =
                                            if (gesture == "button")
                                                current.copy(
                                                    drawerStyle = "button",
                                                    showDrawerButton = true,
                                                )
                                            else {
                                                val cleared =
                                                    homeGestureChoices.fold(current.gestures) {
                                                        g,
                                                        (id, _) ->
                                                        if (g.actionFor(id) == "drawer")
                                                            g.withBinding(id, "none")
                                                        else g
                                                    }
                                                current.copy(
                                                    drawerStyle = "gesture",
                                                    gestures =
                                                        cleared.withBinding(gesture, "drawer"),
                                                )
                                            }
                                        draft.copy(manifest = draft.manifest.copy(layout = updated))
                                    }
                                    if (gesture != "button") selectedGesture = gesture
                                    drawerScope = false
                                },
                    ) {
                        Column(
                            Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            ThemeDemonstration(gesture, Modifier.height(116.dp), accent)
                            Text(title, style = MaterialTheme.typography.labelLarge, minLines = 2)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (isSelected)
                                    Icon(
                                        Icons.Outlined.CheckCircle,
                                        null,
                                        Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                Text(
                                    if (isSelected) "Selecionado" else "Usar este gesto",
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Text(
                "A escolha define a entrada principal. Combine outros atalhos nos vínculos abaixo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        GesturePanel {
            Text(
                "GESTO → AÇÃO",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Escolha o gesto e o que acontece depois.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !drawerScope,
                    onClick = { drawerScope = false },
                    label = { Text("Gestos gerais") },
                )
                FilterChip(
                    selected = drawerScope,
                    onClick = { drawerScope = true },
                    label = { Text("Grade aberta") },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GestureSelector(
                    label = "GESTO",
                    value = bindingGesture,
                    choices =
                        if (drawerScope) listOf("drawer_swipe_down" to "Deslizar para baixo")
                        else homeGestureChoices,
                    modifier = Modifier.weight(1f),
                    onSelect = { selectedGesture = it },
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    null,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                GestureSelector(
                    label = "AÇÃO",
                    value = bindingAction,
                    choices = actionChoices,
                    modifier = Modifier.weight(1f),
                    onSelect = { action ->
                        editGestures { it.withBinding(bindingGesture, action) }
                    },
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemeDemonstration(bindingGesture, Modifier.width(88.dp).height(130.dp), accent)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            actionChoices.firstOrNull { it.first == bindingAction }?.second
                                ?: "Escolha uma ação",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            when {
                                drawerScope ->
                                    "Deslize a partir do topo da grade. Na navegação vertical por páginas, volte à primeira página para executar este gesto."
                                bindingAction in listOf("next_page", "previous_page") ->
                                    "Navega entre páginas da tela inicial e da grade horizontal. Na grade vertical, deslizar para cima e para baixo muda de página."
                                selectedGesture in listOf("tap", "double_tap", "long_press") ->
                                    "Use uma área vazia da tela inicial. Ícones e widgets mantêm seus próprios toques."
                                selectedGesture.startsWith("pinch") ->
                                    "Faça o movimento com dois dedos sobre a tela inicial."
                                else ->
                                    "Gestos laterais também funcionam na grade. Na grade contínua, os movimentos verticais ficam reservados à rolagem."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "As alterações já estão no preview. Abra a etapa Preview para testar com os dedos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton({ showBindings = !showBindings }, contentPadding = PaddingValues(0.dp)) {
                Text(
                    if (showBindings) "Ocultar vínculos configurados"
                    else "Ver vínculos configurados (${configured.size})"
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            AnimatedVisibility(showBindings) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    configured.forEach { (id, title) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    selectedGesture = id
                                    drawerScope = false
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                title,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                null,
                                Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                homeActionChoices
                                    .firstOrNull { it.first == gestures.actionFor(id) }
                                    ?.second ?: gestures.actionFor(id),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (configured.isEmpty())
                        Text(
                            "Nenhum gesto ativo na tela inicial. O botão de aplicativos continua disponível.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
            }
        }

        if ("app" in actionValues || layout.quickAction == "app") {
            GesturePanel {
                Text("Aplicativo do atalho", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Usado pelos gestos e pelo botão com a ação “Abrir aplicativo”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton({ chooseApp = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Apps, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        gestures.quickPackage?.let { "Trocar aplicativo" } ?: "Escolher aplicativo"
                    )
                }
                Text(
                    gestures.quickPackage ?: "Selecione um app instalado para ativar o atalho.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if ("lock" in actionValues) {
            GesturePanel {
                Text("Bloqueio por gesto", style = MaterialTheme.typography.titleSmall)
                Text(
                    "O Android precisa autorizar o Homely a bloquear a tela. O preview apenas simula esta ação.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    {
                        runCatching {
                            ctx.startActivity(
                                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                    .putExtra(
                                        DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                                        ComponentName(ctx, ScreenLockAdmin::class.java),
                                    )
                                    .putExtra(
                                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                        "Permitir que os gestos configurados no Homely Home bloqueiem a tela. Você pode revogar este acesso nas configurações do Android.",
                                    )
                            )
                        }
                            .onFailure {
                                vm.message(
                                    "Não foi possível abrir a autorização de bloqueio neste aparelho."
                                )
                            }
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Permitir bloqueio da tela")
                }
            }
        }

        GesturePanel {
            Text("Atalhos nos elementos", style = MaterialTheme.typography.titleMedium)
            Text(
                "Defina o toque nos ícones e o botão adicional da tela inicial.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GestureSelector(
                label = "MANTER UM ÍCONE PRESSIONADO",
                value = gestures.iconLongPress,
                choices =
                    listOf(
                        "menu" to "Abrir menu do aplicativo",
                        "edit" to "Editar ícone",
                        "info" to "Informações do aplicativo",
                        "remove" to "Remover da tela inicial",
                    ),
                onSelect = { action -> editGestures { it.copy(iconLongPress = action) } },
            )
            GestureSelector(
                label = "BOTÃO DE AÇÃO RÁPIDA",
                value = layout.quickAction,
                choices =
                    listOf(
                        "studio" to "Abrir o editor",
                        "search" to "Buscar aplicativos",
                        "app" to "Abrir aplicativo",
                        "none" to "Ocultar botão",
                    ),
                onSelect = { action ->
                    vm.editDraft { draft ->
                        draft.copy(
                            manifest =
                                draft.manifest.copy(
                                    layout = draft.manifest.layout.copy(quickAction = action)
                                )
                        )
                    }
                },
            )
            Text(
                "O painel de notificações permanece disponível pelo gesto do Android na borda superior.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (chooseApp) {
        var query by rememberSaveable { mutableStateOf("") }
        val apps by
            produceState<List<InstalledApp>?>(null, ctx) {
                value = withContext(Dispatchers.IO) { NativeActions.apps(ctx) }
            }
        val matches =
            apps.orEmpty().filter {
                it.label.contains(query, true) || it.packageName.contains(query, true)
            }
        AlertDialog(
            onDismissRequest = { chooseApp = false },
            title = { Text("Aplicativo do atalho") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Field(query, { query = it }, "Buscar aplicativo")
                    when {
                        apps == null -> CircularProgressIndicator(Modifier.size(24.dp))
                        matches.isEmpty() ->
                            Text(
                                "Nenhum aplicativo encontrado.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        else ->
                            LazyColumn(
                                Modifier.heightIn(max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(matches, key = { it.packageName }) { app ->
                                    TextButton(
                                        {
                                            editGestures { it.copy(quickPackage = app.packageName) }
                                            chooseApp = false
                                        },
                                        Modifier.fillMaxWidth(),
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(app.label)
                                            Text(
                                                app.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
            },
            confirmButton = { TextButton({ chooseApp = false }) { Text("Fechar") } },
        )
    }
}

@Composable
private fun GesturePanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun GestureSelector(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border =
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .75f)),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        choices.firstOrNull { it.first == value }?.second ?: "Escolher ação",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        minLines = 2,
                    )
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                    trailingIcon =
                        if (value == id)
                            ({ Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) })
                        else null,
                )
            }
        }
    }
}
