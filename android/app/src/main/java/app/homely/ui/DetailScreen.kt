package app.homely.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import app.homely.data.*
import app.homely.platform.NativeActions
import coil.compose.AsyncImage

@Composable
fun DetailScreen(vm: HomelyViewModel, state: HomelyState) {
    val theme = state.selected ?: return
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(theme.id, lifecycle) {
        var started =
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED))
                android.os.SystemClock.elapsedRealtime()
            else 0L
        fun record() {
            if (started > 0) {
                val seconds = ((android.os.SystemClock.elapsedRealtime() - started) / 1000).toInt()
                if (seconds >= 2) vm.trackBehavior("dwell", theme.id, duration = seconds)
                started = 0
            }
        }
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                    started = android.os.SystemClock.elapsedRealtime()
                else if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) record()
            }
        lifecycle.addObserver(observer)
        onDispose {
            record()
            lifecycle.removeObserver(observer)
        }
    }
    var apply by remember(theme.id) { mutableStateOf(false) }
    var save by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }
    val grid = rememberLazyGridState()
    LaunchedEffect(theme.id) { grid.scrollToItem(0) }
    val document =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri: Uri? ->
            if (uri != null) {
                pending?.let { (path, id) ->
                    vm.action {
                        NativeActions.saveToUri(vm.repo, path, uri)
                        vm.downloaded(id)
                        vm.message("Download concluído.")
                    }
                }
            }
        }
    val live =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            if (result.resultCode == Activity.RESULT_OK) vm.applied(theme.id)
        }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton({ vm.closeDetail() }) { Icon(Icons.Outlined.ArrowBack, "Voltar") }
            Text(
                theme.category,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Spacer(Modifier.weight(1f))
            IconButton({ vm.like(theme) }) {
                Icon(
                    if (theme.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    "Curtir",
                    tint =
                        if (theme.liked) Color(0xFFFF75A8) else MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton({ if (vm.requireAuth()) save = true }) {
                Icon(
                    if (theme.saved) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    "Salvar na coleção",
                )
            }
            IconButton({ if (vm.requireAuth()) report = true }) {
                Icon(Icons.Outlined.Flag, "Denunciar")
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item(span = { GridItemSpan(2) }) {
                ThemeImagePager(
                    themeScreenshots(theme),
                    theme.title,
                    { vm.repo.url(it) },
                    Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(24.dp)),
                )
            }
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            theme.title,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        if (state.downloaded.contains(theme.id))
                            Icon(
                                Icons.Outlined.DownloadDone,
                                "Baixado",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                    }
                    Row(
                        Modifier.clickable { vm.creator(theme.author) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Avatar(
                            theme.author.avatarUrl?.let { vm.repo.url(it) },
                            theme.author.displayName,
                            Modifier.size(34.dp),
                        )
                        Column {
                            Text(
                                theme.author.displayName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                            Text(
                                "@" + theme.author.username,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            theme.likes.toString() + " curtidas",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (theme.description.isNotBlank())
                        Text(
                            theme.description,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        theme.manifest.palette
                            .let { listOf(it.accent, it.background, it.surface) }
                            .forEach {
                                Box(
                                    Modifier.size(23.dp)
                                        .clip(CircleShape)
                                        .background(color(it))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline,
                                            CircleShape,
                                        )
                                )
                            }
                    }
                    Spacer(Modifier.height(5.dp))
                    SectionTitle(
                        "Dentro deste tema",
                        (theme.manifest.assets.count {
                                it.kind !in listOf("screenshot", "widget_image", "icon_source")
                            } + theme.manifest.widgets.size)
                            .toString() + " recursos · baixe juntos ou separados",
                    )
                }
            }
            items(
                theme.manifest.widgets,
                key = { "widget-" + it.id },
                span = { GridItemSpan(2) },
            ) { widget ->
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(widget.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Widget · incluído ao aplicar o tema",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            theme.manifest.assets
                .filter { it.kind !in listOf("screenshot", "widget_image", "icon_source") }
                .forEach { asset ->
                    item(span = { GridItemSpan(2) }) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (asset.kind != "live_wallpaper")
                                    AsyncImage(
                                        vm.repo.url(asset.url),
                                        null,
                                        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                else
                                    Box(
                                        Modifier.size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Violet.copy(alpha = .2f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Outlined.PlayCircleOutline, null, tint = Lilac)
                                    }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        asset.name,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        when (asset.kind) {
                                            "icon" -> "Ícone · " + asset.packageName
                                            "live_wallpaper" -> "Wallpaper animado"
                                            else -> "Wallpaper"
                                        } +
                                            " · " +
                                            String.format(
                                                java.util.Locale.getDefault(),
                                                "%.1f MB",
                                                asset.size / 1048576f,
                                            ),
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                    )
                                }
                                if (asset.kind == "icon")
                                    IconButton({
                                        vm.action {
                                            NativeActions.shortcut(vm.repo, asset)
                                            vm.message("Confirme o atalho no seu launcher.")
                                        }
                                    }) {
                                        Icon(Icons.Outlined.AddToHomeScreen, "Criar atalho")
                                    }
                                IconButton({
                                    pending = asset.url to ("asset:" + asset.id)
                                    document.launch(
                                        asset.name +
                                            "." +
                                            when (asset.mime) {
                                                "video/mp4" -> "mp4"
                                                "image/png" -> "png"
                                                "image/webp" -> "webp"
                                                else -> "jpg"
                                            }
                                    )
                                }) {
                                    Icon(
                                        Icons.Outlined.Download,
                                        "Baixar " + asset.name,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedButton(
                        {
                            pending = ("/v1/themes/" + theme.id + "/download") to theme.id
                            document.launch(theme.title + ".homely")
                        },
                        Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Outlined.Inventory2, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Baixar tema completo")
                    }
                    Text(
                        "Licença: " +
                            theme.manifest.license +
                            " · Ícones aplicados como atalhos com a confirmação do launcher. As barras do sistema não são alteradas.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SectionTitle("Continue se inspirando", "Mais temas que combinam com você")
                }
            }
            items(state.related, key = { it.id }) { ThemeCard(it, vm) }
        }
        Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.background) {
            Button(
                { apply = true },
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(17.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Violet,
                        contentColor = Color.White,
                    ),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    if (state.applied == theme.id) "Tema aplicado · alterar"
                    else "Aplicar tema completo",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    if (apply) ApplyThemeDialog(vm, post = theme, onDismiss = { apply = false })
    if (save)
        AlertDialog(
            onDismissRequest = { save = false },
            title = { Text("Salvar em uma coleção") },
            text = {
                Column {
                    state.collections.forEach { c ->
                        TextButton(
                            {
                                vm.save(theme, c.id)
                                save = false
                            },
                            Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Folder, null)
                            Spacer(Modifier.width(8.dp))
                            Text(c.name)
                        }
                    }
                    if (theme.saved)
                        TextButton({
                            vm.save(theme)
                            save = false
                        }) {
                            Text("Remover de todas as coleções")
                        }
                }
            },
            confirmButton = { TextButton({ save = false }) { Text("Fechar") } },
        )
    if (report) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { report = false },
            title = { Text("Denunciar tema") },
            text = { Field(reason, { reason = it }, "O que aconteceu?", singleLine = false) },
            confirmButton = {
                TextButton(
                    {
                        vm.report(theme, reason)
                        report = false
                    },
                    enabled = reason.trim().length >= 5,
                ) {
                    Text("Enviar")
                }
            },
            dismissButton = { TextButton({ report = false }) { Text("Cancelar") } },
        )
    }
}
