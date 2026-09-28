package app.homely.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import app.homely.data.*
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.*

fun defaultHomeKit(draft: Draft): List<HomeItem> {
    val result = mutableListOf<HomeItem>()
    var page = 0
    var y = .02f
    draft.manifest.widgets.forEach { w ->
        val height = (w.height / 780f).coerceIn(.12f, .5f)
        if (y + height > .92f) {
            page++
            y = .02f
        }
        result +=
            HomeItem(
                kind = "widget",
                reference = w.id,
                x = .025f,
                y = y,
                page = page,
                width = if (w.columns == 2) .46f else .95f,
                height = height,
            )
        y += height + .025f
    }
    val cols = draft.manifest.layout.columns
    var col = 0
    draft.icons.take(108).forEach { icon ->
        if (y + .13f > .98f) {
            page++
            y = .02f
            col = 0
        }
        result +=
            HomeItem(
                kind = "icon",
                reference = icon.packageName,
                x = col.toFloat() / cols,
                y = y,
                page = page,
                width = 1f / cols,
                height = .13f,
            )
        col++
        if (col == cols) {
            col = 0
            y += .15f
        }
    }
    return result.take(120)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KitCanvas(
    draft: Draft,
    modifier: Modifier = Modifier,
    onMove: ((HomeItem) -> Unit)? = null,
    onIconLongPress: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val items = draft.manifest.layout.kit
    val pager = rememberPagerState(pageCount = { (items.maxOfOrNull { it.page } ?: 0) + 1 })
    val pageContent: @Composable PagerScope.(Int) -> Unit = { page ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = maxWidth
            val height = maxHeight
            val density = LocalDensity.current
            val pxW = with(density) { width.toPx() }
            val pxH = with(density) { height.toPx() }
            items
                .filter { it.page == page }
                .forEach { item ->
                    var dx by remember(item) { mutableFloatStateOf(0f) }
                    var dy by remember(item) { mutableFloatStateOf(0f) }
                    Box(
                        Modifier.offset(
                                width * item.x + with(density) { dx.toDp() },
                                height * item.y + with(density) { dy.toDp() },
                            )
                            .size(width * item.width, height * item.height)
                            .then(
                                if (onMove != null)
                                    Modifier.border(
                                            1.dp,
                                            Violet.copy(alpha = .4f),
                                            RoundedCornerShape(12.dp),
                                        )
                                        .pointerInput(item) {
                                            detectDragGestures(
                                                onDragEnd = {
                                                    onMove(
                                                        item.copy(
                                                            x =
                                                                (item.x + dx / pxW).coerceIn(
                                                                    0f,
                                                                    1 - item.width,
                                                                ),
                                                            y =
                                                                (item.y + dy / pxH).coerceIn(
                                                                    0f,
                                                                    1 - item.height,
                                                                ),
                                                        )
                                                    )
                                                    dx = 0f
                                                    dy = 0f
                                                },
                                                onDragCancel = {
                                                    dx = 0f
                                                    dy = 0f
                                                },
                                            ) { change, amount ->
                                                change.consume()
                                                dx += amount.x
                                                dy += amount.y
                                            }
                                        }
                                else Modifier
                            )
                    ) {
                        if (item.kind == "widget")
                            draft.manifest.widgets
                                .firstOrNull { it.id == item.reference }
                                ?.let { w ->
                                    CompositionLocalProvider(
                                        LocalDensity provides
                                            Density(
                                                density.density *
                                                    (height.value * item.height / w.height),
                                                density.fontScale,
                                            )
                                    ) {
                                        ComposedWidget(
                                            w,
                                            draft.widgetImages[w.id],
                                            preview = onMove != null,
                                            images = draft.widgetImages,
                                            fonts = draft.fontFiles,
                                        )
                                    }
                                }
                        else {
                            val custom =
                                draft.icons.firstOrNull { it.packageName == item.reference }
                            val original by
                                produceState<ImageBitmap?>(null, item.reference) {
                                    if (custom == null)
                                        value =
                                            withContext(Dispatchers.IO) {
                                                runCatching {
                                                    ctx.packageManager
                                                        .getApplicationIcon(item.reference)
                                                        .toBitmap(144, 144)
                                                        .asImageBitmap()
                                                }
                                                    .getOrNull()
                                            }
                                }
                            Column(
                                Modifier.fillMaxSize()
                                    .then(
                                        if (onMove == null)
                                            Modifier.combinedClickable(
                                                onClick = {
                                                    app.homely.platform.WidgetActions.launchPackage(
                                                        ctx,
                                                        item.reference,
                                                    )
                                                },
                                                onLongClick = {
                                                    onIconLongPress?.invoke(item.reference)
                                                },
                                            )
                                        else Modifier
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                val size =
                                    (width * item.width * .72f).coerceAtMost(
                                        draft.manifest.layout.iconSize.dp
                                    )
                                if (custom != null)
                                    AsyncImage(File(custom.path), custom.label, Modifier.size(size))
                                else
                                    original?.let { Image(it, item.reference, Modifier.size(size)) }
                                if (draft.manifest.layout.showLabels)
                                    Text(
                                        custom?.label ?: item.reference.substringAfterLast('.'),
                                        color = color(draft.manifest.palette.text),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                    )
                            }
                        }
                    }
                }
        }
    }
    Column(modifier) {
        if (draft.manifest.layout.scrollDirection == "horizontal")
            HorizontalPager(pager, Modifier.weight(1f), pageContent = pageContent)
        else VerticalPager(pager, Modifier.weight(1f), pageContent = pageContent)
        if (pager.pageCount > 1)
            Text(
                "${pager.currentPage+1} / ${pager.pageCount}",
                Modifier.align(Alignment.CenterHorizontally),
                color = color(draft.manifest.palette.text),
                fontSize = 10.sp,
            )
    }
}
