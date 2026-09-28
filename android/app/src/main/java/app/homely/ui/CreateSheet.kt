package app.homely.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable
fun CreateOptionsSheet(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 24.dp,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "O que vamos criar?",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Text(
                        "Escolha e dê vida à sua ideia.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CreateOptionCard(
                    icon = Glyph.Home,
                    title = "Criar Tema",
                    description =
                        "Aparência, aplicativos, gestos e widgets em um editor visual interativo.",
                    gradient = listOf(Color(0xFF8274FF), Color(0xFF5C43FA)),
                    delay = 0,
                ) {
                    onSelect("theme")
                }
                CreateOptionCard(
                    icon = Glyph.Widgets,
                    title = "Criar Widget",
                    description = "Relógio, data, bateria, texto ou imagem personalizada.",
                    gradient = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8)),
                    delay = 60,
                ) {
                    onSelect("widget")
                }
                CreateOptionCard(
                    icon = Glyph.Icons,
                    title = "Criar Ícone",
                    description = "Personalize os ícones dos seus aplicativos favoritos.",
                    gradient = listOf(Color(0xFF10B981), Color(0xFF059669)),
                    delay = 120,
                ) {
                    onSelect("icon")
                }
                CreateOptionCard(
                    icon = Glyph.Plus,
                    title = "Criar Elemento",
                    description = "Um componente com aparência, estados e interações próprios.",
                    gradient = listOf(Color(0xFFA36FC4), Color(0xFF7965FF)),
                    delay = 180,
                ) {
                    onSelect("element")
                }
                TextButton(onDismiss, Modifier.fillMaxWidth()) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
private fun CreateOptionCard(
    icon: Glyph,
    title: String,
    description: String,
    gradient: List<Color>,
    delay: Int,
    onClick: () -> Unit,
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        enter.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier =
            Modifier.fillMaxWidth().graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 30f
            },
    ) {
        Box(
            Modifier.background(
                    Brush.linearGradient(
                        gradient,
                        start = Offset.Zero,
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    ),
                    RoundedCornerShape(20.dp),
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier.size(48.dp)
                        .background(Color.White.copy(alpha = .18f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CleanIcon(icon, Modifier.size(26.dp), Color.White)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        description,
                        color = Color.White.copy(alpha = .82f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
                CleanIcon(
                    Glyph.Back,
                    Modifier.size(20.dp).graphicsLayer { rotationZ = 180f },
                    Color.White.copy(alpha = .6f),
                )
            }
        }
    }
}
