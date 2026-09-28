package app.homely.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.homely.data.*

@Composable
fun ProfileScreen(vm: HomelyViewModel, state: HomelyState, onSettings: () -> Unit) {
    var privateOpen by remember { mutableStateOf(false) }
    if (privateOpen) {
        PrivateLibrary(vm, state) { privateOpen = false }
        return
    }
    val profile = state.profile
    var tab by rememberSaveable { mutableStateOf("Temas") }
    var edit by remember { mutableStateOf(false) }
    var newCollection by remember { mutableStateOf(false) }
    var collectionId by rememberSaveable { mutableStateOf<String?>(null) }
    val photo =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let { vm.avatar(it) }
        }
    LaunchedEffect(profile?.id, tab, collectionId) {
        if (profile != null) vm.loadLibrary(tab, collectionId)
    }
    if (profile == null) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                IconButton(onSettings) { Icon(Icons.Outlined.Settings, "Configurações") }
            }
            TextButton({ privateOpen = true }, Modifier.fillMaxWidth()) {
                Text("Meus salvos neste aparelho")
            }
            EmptyState(
                Icons.Outlined.PersonOutline,
                "Entrar no perfil",
                "Salve temas e acompanhe suas criações.",
                "Entrar ou criar conta",
            ) {
                vm.askAuth()
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onSettings) { Icon(Icons.Outlined.Settings, "Configurações") }
            }
        }
        item(span = { GridItemSpan(2) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Box {
                    Avatar(
                        profile.avatarUrl?.let { vm.repo.url(it) },
                        profile.displayName,
                        Modifier.size(88.dp).clickable { photo.launch("image/*") },
                    )
                    Box(
                        Modifier.align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Lilac)
                            .clickable { photo.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            "Trocar foto",
                            Modifier.size(14.dp),
                            tint = Color(0xFF231751),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(profile.displayName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "@" + profile.username,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                    )
                    Text(
                        profile.bio,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }
        if (profile.location.isNotBlank() || profile.website.isNotBlank())
            item(span = { GridItemSpan(2) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (profile.location.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                null,
                                Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                profile.location,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (profile.location.isNotBlank() && profile.website.isNotBlank()) {
                        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (profile.website.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Icon(
                                Icons.Outlined.Link,
                                null,
                                Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                profile.website,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        item(span = { GridItemSpan(2) }) { ProfileStats(profile) }
        item(span = { GridItemSpan(2) }) {
            TextButton({ privateOpen = true }, Modifier.fillMaxWidth()) {
                Text("Meus salvos neste aparelho")
            }
        }
        item(span = { GridItemSpan(2) }) {
            FilledTonalButton(
                { edit = true },
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            ) {
                Text("Editar perfil")
            }
        }
        item(span = { GridItemSpan(2) }) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                listOf("Temas", "Salvos", "Likes").forEach { label ->
                    Column(
                        Modifier.weight(1f).clickable {
                            tab = label
                            collectionId = null
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            label,
                            Modifier.padding(12.dp),
                            color =
                                if (tab == label) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight =
                                if (tab == label) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Box(
                            Modifier.fillMaxWidth()
                                .height(2.dp)
                                .background(if (tab == label) Violet else Color.Transparent)
                        )
                    }
                }
            }
        }
        if (tab == "Salvos")
            item(span = { GridItemSpan(2) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    item { Pill("Todas", collectionId == null) { collectionId = null } }
                    items(state.collections) { c ->
                        Pill(c.name + " · " + c.count, collectionId == c.id) { collectionId = c.id }
                    }
                    item { Pill("+ Coleção") { newCollection = true } }
                }
            }
        if (state.library.isEmpty())
            item(span = { GridItemSpan(2) }) {
                EmptyState(
                    if (tab == "Temas") Icons.Outlined.Palette else Icons.Outlined.BookmarkBorder,
                    if (tab == "Temas") "Sua próxima criação mora aqui."
                    else "Espaço para novas inspirações.",
                    if (tab == "Temas") "Abra Criar e publique seu primeiro tema."
                    else "Explore o feed e guarde os temas que combinam com você.",
                )
            }
        items(state.library, key = { it.id }) { ThemeCard(it, vm) }
    }
    if (edit) {
        EditProfileView(profile, vm, photo) { edit = false }
        return
    }
    if (newCollection) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newCollection = false },
            title = { Text("Uma nova coleção") },
            text = { Field(name, { name = it.take(40) }, "Nome da coleção") },
            confirmButton = {
                TextButton(
                    {
                        vm.collection(name)
                        newCollection = false
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Criar")
                }
            },
            dismissButton = { TextButton({ newCollection = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ProfileStats(profile: Profile) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf(
                profile.themeCount to "Temas",
                profile.followers to "Seguidores",
                profile.following to "Seguindo",
            )
            .forEach { (n, label) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(n.toString(), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}

@Composable
fun CreatorProfile(vm: HomelyViewModel, state: HomelyState) {
    val profile = state.creator ?: return
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ vm.closeCreator() }) { Icon(Icons.Outlined.ArrowBack, "Voltar") }
                Text("Criador", style = MaterialTheme.typography.titleLarge)
            }
        }
        item(span = { GridItemSpan(2) }) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(
                    profile.avatarUrl?.let { vm.repo.url(it) },
                    profile.displayName,
                    Modifier.size(84.dp),
                )
                Text(profile.displayName, style = MaterialTheme.typography.headlineMedium)
                Text("@" + profile.username, color = MaterialTheme.colorScheme.primary)
                if (profile.bio.isNotBlank()) Text(profile.bio)
                ProfileStats(profile)
                if (profile.id != state.profile?.id)
                    Button({ vm.follow(profile) }) {
                        Text(
                            if (profile.followed) "Seguindo · deixar de seguir"
                            else "Seguir criador"
                        )
                    }
            }
        }
        items(state.creatorThemes, key = { it.id }) { theme ->
            Box(
                Modifier.clickable {
                    vm.closeCreator()
                    vm.open(theme)
                }
            ) {
                ThemeCard(theme, vm)
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: HomelyViewModel, state: HomelyState, onBack: () -> Unit) {
    var modesOpen by remember { mutableStateOf(false) }
    if (modesOpen) {
        UsageModeSettings { modesOpen = false }
        return
    }
    var delete by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Outlined.ArrowBack, "Voltar") }
            Text("Configurações", style = MaterialTheme.typography.titleLarge)
        }
        SectionTitle("Sua experiência")
        OutlinedButton({ modesOpen = true }, Modifier.fillMaxWidth()) {
            Text("Modo de uso · ícones, widgets e temas")
        }
        Text("Aparência", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark" to "Escuro", "light" to "Claro", "system" to "Sistema").forEach {
                (id, label) ->
                Pill(label, state.appearance == id) { vm.appearance(id) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SectionTitle("Homely", "Seu celular. Seu universo.")
        Text(
            "Versão 0.5.0 · prévia de desenvolvimento",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Text(
            "Suas publicações e arquivos enviados são públicos. E-mail e senha não aparecem no perfil. Rascunhos ficam neste aparelho. Você pode excluir sua conta e publicações abaixo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Compatibilidade: wallpapers estáticos e animados, atalhos com ícones e Homely Clock. Ícones globais e a barra de notificações dependem de recursos do Android e do launcher.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.profile != null) {
            OutlinedButton(
                {
                    vm.logout()
                    onBack()
                },
                Modifier.fillMaxWidth(),
            ) {
                Text("Sair da conta")
            }
            TextButton({ delete = true }) {
                Text("Excluir minha conta", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (delete)
        AlertDialog(
            onDismissRequest = { delete = false },
            title = { Text("Excluir sua conta?") },
            text = {
                Text(
                    "Seu perfil, publicações, curtidas, coleções e arquivos enviados serão excluídos permanentemente. Arquivos que outras pessoas já baixaram não podem ser recolhidos."
                )
            },
            confirmButton = {
                TextButton({
                    delete = false
                    vm.deleteAccount()
                    onBack()
                }) {
                    Text("Excluir permanentemente", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ delete = false }) { Text("Cancelar") } },
        )
}

@Composable
fun EditProfileView(
    profile: Profile,
    vm: HomelyViewModel,
    photoLauncher:
        androidx.activity.compose.ManagedActivityResultLauncher<String, android.net.Uri?>,
    onClose: () -> Unit,
) {
    var p by remember { mutableStateOf(profile) }
    var usernameValid by remember { mutableStateOf(true) }

    val bannerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
            it?.let { vm.banner(it) }
        }

    val currentState by vm.state.collectAsState()
    val activeProfile = currentState.profile ?: profile

    androidx.activity.compose.BackHandler { onClose() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClose) { Icon(Icons.Outlined.ArrowBackIosNew, "Voltar") }
                Text(
                    "Editar perfil",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = {
                        vm.updateProfile(
                            p.copy(
                                avatarUrl = activeProfile.avatarUrl,
                                bannerUrl = activeProfile.bannerUrl,
                            )
                        )
                        onClose()
                    },
                    enabled = usernameValid && !currentState.busy,
                ) {
                    Text(
                        "Salvar",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Box(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box {
                    Avatar(
                        activeProfile.avatarUrl?.let { vm.repo.url(it) },
                        activeProfile.displayName,
                        Modifier.size(88.dp)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                    Box(
                        Modifier.align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB3A6FF))
                            .clickable { photoLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            "Trocar foto",
                            Modifier.size(16.dp),
                            tint = Color.White,
                        )
                    }
                }
            }

            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Field(
                    p.displayName,
                    { p = p.copy(displayName = it.take(50)) },
                    "Nome de exibição",
                )
                UsernameField(
                    p.username,
                    { p = p.copy(username = it) },
                    vm,
                    { usernameValid = it },
                )
                Field(
                    p.bio,
                    { p = p.copy(bio = it.take(150)) },
                    "Sobre mim",
                    singleLine = false,
                    helper = p.bio.length.toString() + "/150",
                )
                Field(
                    p.website,
                    { p = p.copy(website = it.take(200)) },
                    "Link do perfil",
                )
                Field(
                    p.location,
                    { p = p.copy(location = it.take(80)) },
                    "Localização",
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, null) },
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Imagem de capa",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        Modifier.fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { bannerLauncher.launch("image/*") }
                    ) {
                        if (activeProfile.bannerUrl != null) {
                            coil.compose.AsyncImage(
                                coil.request.ImageRequest.Builder(
                                        androidx.compose.ui.platform.LocalContext.current
                                    )
                                    .data(vm.repo.url(activeProfile.bannerUrl))
                                    .crossfade(true)
                                    .build(),
                                "Capa",
                                Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Image,
                                null,
                                Modifier.align(Alignment.Center).size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                "Editar capa",
                                Modifier.size(16.dp),
                                tint = Color.White,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}
