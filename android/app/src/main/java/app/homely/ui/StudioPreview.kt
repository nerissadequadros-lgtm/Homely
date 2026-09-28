package app.homely.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import app.homely.data.Draft
import app.homely.data.HomeItem

/** Compact compatibility wrapper; interaction uses the very same launcher surface. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun StudioPreview(
    draft: Draft,
    onColumns: ((Int) -> Unit)? = null,
    onMove: ((HomeItem) -> Unit)? = null,
) {
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(
            Modifier.width(202.dp)
                .height(390.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(4.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
                .padding(4.dp)
        ) {
            CompositionLocalProvider(
                LocalDensity provides Density(density.density * .5f, density.fontScale)
            ) {
                if (onMove != null) KitCanvas(draft, Modifier.fillMaxSize().padding(12.dp), onMove)
                else ThemeExperience(draft, preview = true, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
