package app.homely.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

enum class Glyph {
    Home,
    Icons,
    Widgets,
    Plus,
    Search,
    Bookmark,
    Person,
    Close,
    Back,
    Check,
    More,
}

@Composable
fun CleanIcon(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    filled: Boolean = false,
) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val pen = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun path(block: Path.() -> Unit) {
                drawPath(Path().apply(block), tint, style = pen)
            }
            when (glyph) {
                Glyph.Home -> {
                    val p =
                        Path().apply {
                            moveTo(3.5f, 10.5f)
                            lineTo(12f, 3.5f)
                            lineTo(20.5f, 10.5f)
                            lineTo(20.5f, 20.3f)
                            lineTo(14.5f, 20.3f)
                            lineTo(14.5f, 14.4f)
                            lineTo(9.5f, 14.4f)
                            lineTo(9.5f, 20.3f)
                            lineTo(3.5f, 20.3f)
                            close()
                        }
                    drawPath(p, tint, style = if (filled) Fill else pen)
                }
                Glyph.Icons ->
                    listOf(4f to 4f, 14f to 4f, 4f to 14f, 14f to 14f).forEach { (x, y) ->
                        drawRoundRect(
                            tint,
                            Offset(x, y),
                            Size(6f, 6f),
                            CornerRadius(1.8f),
                            style = pen,
                        )
                    }
                Glyph.Widgets -> {
                    drawRoundRect(
                        tint,
                        Offset(5.5f, 3.5f),
                        Size(15f, 15f),
                        CornerRadius(2.4f),
                        style = pen,
                    )
                    path {
                        moveTo(3.2f, 7f)
                        lineTo(3.2f, 20.8f)
                        lineTo(17f, 20.8f)
                    }
                    drawLine(tint, Offset(15.5f, 6.4f), Offset(15.5f, 15.5f), 1.4f, StrokeCap.Round)
                }
                Glyph.Plus -> {
                    drawLine(tint, Offset(5f, 12f), Offset(19f, 12f), 1.65f, StrokeCap.Round)
                    drawLine(tint, Offset(12f, 5f), Offset(12f, 19f), 1.65f, StrokeCap.Round)
                }
                Glyph.Search -> {
                    drawCircle(tint, 6.6f, Offset(10.4f, 10.4f), style = pen)
                    drawLine(tint, Offset(15.4f, 15.4f), Offset(21f, 21f), 1.65f, StrokeCap.Round)
                }
                Glyph.Bookmark ->
                    path {
                        moveTo(6f, 4f)
                        lineTo(18f, 4f)
                        lineTo(18f, 21f)
                        lineTo(12f, 17f)
                        lineTo(6f, 21f)
                        close()
                    }
                Glyph.Person -> {
                    drawCircle(tint, 4f, Offset(12f, 7.5f), style = pen)
                    path {
                        moveTo(4.7f, 21f)
                        cubicTo(4.7f, 12.5f, 19.3f, 12.5f, 19.3f, 21f)
                    }
                }
                Glyph.Close -> {
                    drawLine(tint, Offset(6f, 6f), Offset(18f, 18f), 1.65f, StrokeCap.Round)
                    drawLine(tint, Offset(18f, 6f), Offset(6f, 18f), 1.65f, StrokeCap.Round)
                }
                Glyph.Back ->
                    path {
                        moveTo(14.5f, 4f)
                        lineTo(6.5f, 12f)
                        lineTo(14.5f, 20f)
                    }
                Glyph.Check ->
                    path {
                        moveTo(5f, 12f)
                        lineTo(10f, 17f)
                        lineTo(19f, 7f)
                    }
                Glyph.More ->
                    listOf(5f, 12f, 19f).forEach { drawCircle(tint, 1.5f, Offset(it, 12f)) }
            }
        }
    }
}

@Composable
fun RefinedBottomNav(
    selected: Int,
    state: HomelyState,
    vm: HomelyViewModel,
    onSelect: (Int) -> Unit,
) {
    val labels = listOf("Temas", "Ícones", "Criar", "Widgets", "Perfil")
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = .98f),
        shadowElevation = 8.dp,
    ) {
        Column {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = .35f),
                thickness = .5.dp,
            )
            Row(
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { i, label ->
                    val active = selected == i
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val scale by
                        animateFloatAsState(
                            if (pressed) .9f else if (active && i == 2) 1.05f else 1f,
                            spring(dampingRatio = .7f, stiffness = 500f),
                            label = "navScale",
                        )
                    val tint by
                        animateColorAsState(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            tween(200),
                            label = "navTint",
                        )
                    Column(
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(interactionSource = interaction, indication = null) {
                                onSelect(i)
                            }
                            .semantics {
                                contentDescription = label
                                this.selected = active
                                role = Role.Tab
                            }
                            .padding(vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Box(
                            Modifier.height(43.dp).graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            when (i) {
                                2 ->
                                    Box(
                                        Modifier.size(43.dp)
                                            .shadow(
                                                8.dp,
                                                CircleShape,
                                                ambientColor = Violet,
                                                spotColor = Violet,
                                            )
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(Color(0xFF8274FF), Color(0xFF5C43FA))
                                                ),
                                                CircleShape,
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CleanIcon(Glyph.Plus, Modifier.size(27.dp), Color.White)
                                    }
                                4 ->
                                    Box(
                                        Modifier.size(34.dp)
                                            .border(
                                                if (active) 1.7.dp else .7.dp,
                                                if (active) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline,
                                                CircleShape,
                                            )
                                            .padding(3.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (state.profile != null)
                                            Avatar(
                                                state.profile.avatarUrl?.let { vm.repo.url(it) },
                                                state.profile.displayName,
                                                Modifier.fillMaxSize(),
                                            )
                                        else
                                            Box(
                                                Modifier.fillMaxSize()
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        CircleShape,
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                CleanIcon(Glyph.Person, Modifier.size(19.dp), tint)
                                            }
                                    }
                                else ->
                                    CleanIcon(
                                        when (i) {
                                            0 -> Glyph.Home
                                            1 -> Glyph.Icons
                                            else -> Glyph.Widgets
                                        },
                                        Modifier.size(25.dp),
                                        tint,
                                        active && i == 0,
                                    )
                            }
                        }
                        Text(
                            label,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            color = tint,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
