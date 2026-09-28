package app.homely.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import app.homely.data.*
import coil.compose.AsyncImage

@Composable
fun FeedHeader(
    vm: HomelyViewModel,
    state: HomelyState,
    icons: Boolean = false,
    searchVisible: Boolean = false,
) {
    val search = searchVisible
    Column(
        Modifier.padding(top = 5.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        AnimatedVisibility(
            search,
            enter = expandVertically(tween(220)) + fadeIn(),
            exit = shrinkVertically(tween(180)) + fadeOut(),
        ) {
            OutlinedTextField(
                state.search,
                { vm.loadFeed(search = it) },
                Modifier.fillMaxWidth().padding(horizontal = 15.dp),
                placeholder = { Text("Encontre o seu próximo estilo", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { CleanIcon(Glyph.Search, Modifier.size(19.dp)) },
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                listOf(
                    "Para você",
                    "Explorar",
                    "Minimal",
                    "Anime",
                    "Natureza",
                    "Abstrato",
                    "Espaço",
                    "Tecnologia",
                )
            ) { c ->
                Pill(c, state.category == c) {
                    vm.loadFeed(category = c)
                }
            }
        }
    }
}

@Composable
fun FeedGrid(vm: HomelyViewModel, state: HomelyState) {
    val grid = rememberLazyGridState()
    LaunchedEffect(grid, state.nextOffset, state.loading) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (last >= state.feed.size - 5 && state.nextOffset != null && !state.loading)
                    vm.loadFeed(more = true)
            }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = grid,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
        modifier = Modifier.fillMaxSize().testTag("themes-grid"),
    ) {
        if (state.offline)
            item(span = { GridItemSpan(2) }) {
                TextButton({ vm.loadFeed() }) {
                    Text("Sem conexão · tentar novamente", fontSize = 12.sp)
                }
            }
        items(state.feed, key = { it.id }) { ThemeCard(it, vm) }
        if (state.loading)
            item(span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        if (state.feed.isEmpty() && !state.loading)
            item(span = { GridItemSpan(2) }) {
                EmptyState(
                    Icons.Outlined.Search,
                    if (state.offline) "Vamos nos conectar?" else "Um novo ponto de partida",
                    if (state.offline) "Verifique sua conexão para carregar os temas."
                    else "Experimente outro estilo para encontrar novas inspirações.",
                    "Ver todos",
                ) {
                    vm.loadFeed(category = "Para você", search = "")
                }
            }
    }
}

@Composable
fun ThemeCard(theme: ThemePost, vm: HomelyViewModel, aspect: Float = .57f) {
    val screenshots = remember(theme) { themeScreenshots(theme) }
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth()
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(16.dp))
            .background(color(theme.manifest.palette.background))
            .border(
                .7.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = .7f),
                RoundedCornerShape(16.dp),
            )
    ) {
        ThemeImagePager(
            screenshots,
            theme.title,
            { vm.repo.url(it) },
            Modifier.fillMaxSize().testTag("theme-pager-" + theme.id),
            onOpen = { vm.open(theme) },
        ) {
            Column(
                Modifier.align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    theme.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                val count =
                    theme.manifest.assets.count {
                        it.kind !in listOf("screenshot", "widget_image", "icon_source")
                    } + theme.manifest.widgets.size
                Text(
                    count.toString() + if (count == 1) " recurso" else " recursos",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = .85f),
                )
            }
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton({ menu = true }, Modifier.size(38.dp)) {
                CleanIcon(Glyph.More, Modifier.size(19.dp), Color.White)
            }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Ver composição") },
                    onClick = {
                        menu = false
                        vm.open(theme)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (theme.saved) "Remover dos salvos" else "Salvar tema") },
                    onClick = {
                        menu = false
                        vm.save(theme)
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (theme.liked) "Remover curtida" else "Curtir tema") },
                    onClick = {
                        menu = false
                        vm.like(theme)
                    },
                )
            }
        }
        if (theme.saved)
            CleanIcon(
                Glyph.Bookmark,
                Modifier.align(Alignment.TopStart).padding(10.dp).size(16.dp),
                Color.White,
            )
    }
}

@Composable
fun ThemeImagePager(
    images: List<String>,
    title: String,
    resolve: (String) -> String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val pager = rememberPagerState(pageCount = { images.size })
    Box(
        modifier.semantics {
            stateDescription = "Imagem " + (pager.currentPage + 1) + " de " + images.size
        }
    ) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            AsyncImage(
                resolve(images[page]),
                title + " · imagem " + (page + 1) + " de " + images.size,
                Modifier.fillMaxSize().clickable(onClick = onOpen),
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
            )
        }
        Box(
            Modifier.fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = .1f),
                            Color.Transparent,
                            Color.Black.copy(alpha = .68f),
                        )
                    )
                )
        )
        overlay()
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 11.dp).semantics {
                contentDescription = "Imagens de " + title
            },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(images.size) { index ->
                val size by
                    animateFloatAsState(
                        if (pager.currentPage == index) 5.5f else 4.5f,
                        tween(180),
                        label = "dot",
                    )
                Box(
                    Modifier.size(size.dp)
                        .background(
                            if (pager.currentPage == index) Color.White
                            else Color.White.copy(alpha = .35f),
                            CircleShape,
                        )
                )
            }
        }
    }
}
