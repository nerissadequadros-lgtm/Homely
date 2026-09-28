package app.homely.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage

val Violet = Color(0xFF7965FF)
val Lilac = Color(0xFFAEA2FF)
private val Dark =
    darkColorScheme(
        primary = Lilac,
        onPrimary = Color(0xFF140F31),
        primaryContainer = Color(0xFF7965FF),
        onPrimaryContainer = Color.White,
        background = Color(0xFF090C12),
        surface = Color(0xFF10141D),
        surfaceVariant = Color(0xFF1B202C),
        onSurface = Color(0xFFF5F4FA),
        onSurfaceVariant = Color(0xFF9298A8),
        outline = Color(0xFF2B3140),
        secondary = Lilac,
    )
private val Light =
    lightColorScheme(
        primary = Color(0xFF6450EA),
        onPrimary = Color.White,
        primaryContainer = Color(0xFF7965FF),
        onPrimaryContainer = Color.White,
        background = Color(0xFFF8F8FC),
        surface = Color.White,
        surfaceVariant = Color(0xFFF0EFF8),
        onSurface = Color(0xFF161724),
        onSurfaceVariant = Color(0xFF73768A),
        outline = Color(0xFFE1E0EC),
        secondary = Violet,
    )

@Composable
fun HomelyTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography =
            Typography(
                headlineLarge =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                    ),
                headlineMedium =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.8).sp,
                    ),
                titleLarge =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                    ),
                titleMedium =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                bodyMedium =
                    androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                labelSmall =
                    androidx.compose.ui.text.TextStyle(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    ),
            ),
        content = content,
    )
}

fun color(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}
    .getOrDefault(Violet)

@Composable
fun BrandMark(modifier: Modifier = Modifier, tint: Color = Violet) {
    Canvas(modifier.size(27.dp)) {
        val w = size.width
        val h = size.height
        val p =
            Path().apply {
                moveTo(w * .58f, h * .86f)
                lineTo(w * .2f, h * .86f)
                quadraticTo(w * .12f, h * .86f, w * .12f, h * .76f)
                lineTo(w * .12f, h * .43f)
                quadraticTo(w * .12f, h * .39f, w * .18f, h * .35f)
                lineTo(w * .46f, h * .13f)
                quadraticTo(w * .50f, h * .1f, w * .55f, h * .14f)
                lineTo(w * .85f, h * .37f)
                quadraticTo(w * .9f, h * .41f, w * .9f, h * .46f)
                lineTo(w * .9f, h * .86f)
            }
        drawPath(
            p,
            Brush.linearGradient(listOf(Color(0xFF087DFF), Color(0xFF542AFF), Color(0xFF9D15FA))),
            style =
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = w * .14f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )
    }
}

@Composable
fun Avatar(url: String?, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFB3A6FF), Color(0xFF5A46C9)))),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null)
            AsyncImage(url, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else
            Text(
                name.take(1).uppercase().ifEmpty { "h" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Violet else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
fun SectionTitle(title: String, caption: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (caption != null)
                Text(
                    caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        action?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    button: String? = null,
    onClick: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (button != null) Button(onClick) { Text(button) }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    helper: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value,
        onChange,
        modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = leadingIcon,
        singleLine = singleLine,
        shape = RoundedCornerShape(16.dp),
        supportingText = helper?.let { { Text(it) } },
        colors =
            OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
    )
}

val TeardropShape =
    androidx.compose.foundation.shape.GenericShape { size, _ ->
        moveTo(size.width / 2f, 0f)
        cubicTo(size.width, 0f, size.width, size.height, size.width / 2f, size.height)
        cubicTo(0f, size.height, 0f, 0f, size.width / 2f, 0f)
    }

val HexagonShape =
    androidx.compose.foundation.shape.GenericShape { size, _ ->
        val width = size.width
        val height = size.height
        val w4 = width / 4f
        moveTo(w4, 0f)
        lineTo(width - w4, 0f)
        lineTo(width, height / 2f)
        lineTo(width - w4, height)
        lineTo(w4, height)
        lineTo(0f, height / 2f)
        close()
    }
