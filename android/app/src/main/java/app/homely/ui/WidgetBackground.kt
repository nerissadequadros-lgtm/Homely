package app.homely.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.homely.data.WidgetSpec
import app.homely.data.widgetImageFrame
import app.homely.data.widgetImageTranslation
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

/** Only the background is faded; text, clocks and other functional layers stay legible. */
@Composable
fun WidgetBackground(spec: WidgetSpec, images: Map<String, String>, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(spec.radius.dp))
            .alpha(spec.backgroundOpacity)
            .background(color(spec.background))
    ) {
        spec.backgroundImageId
            ?.let { images[it] }
            ?.let { path ->
                WidgetImage(
                    path,
                    null,
                    Modifier.fillMaxSize(),
                    spec.backgroundScale,
                    spec.backgroundZoom,
                    spec.backgroundOffsetX,
                    spec.backgroundOffsetY,
                )
            }
        if (spec.backgroundOverlay > 0f)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = spec.backgroundOverlay)))
    }
}

@Composable
fun WidgetImage(
    path: String?,
    description: String?,
    modifier: Modifier = Modifier,
    scale: String = "crop",
    zoom: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
) {
    val contentScale =
        remember(scale, zoom) {
            object : ContentScale {
                override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
                    if (
                        srcSize.width <= 0 ||
                            srcSize.height <= 0 ||
                            dstSize.width <= 0 ||
                            dstSize.height <= 0
                    )
                        return ScaleFactor(1f, 1f)
                    val frame =
                        widgetImageFrame(
                            srcSize.width,
                            srcSize.height,
                            dstSize.width,
                            dstSize.height,
                            scale,
                            zoom,
                        )
                    return ScaleFactor(frame.width / srcSize.width, frame.height / srcSize.height)
                }
            }
        }
    val alignment =
        remember(offsetX, offsetY) {
            object : Alignment {
                override fun align(
                    size: IntSize,
                    space: IntSize,
                    layoutDirection: LayoutDirection,
                ): IntOffset =
                    IntOffset(
                        widgetImageTranslation(size.width.toFloat(), space.width.toFloat(), offsetX)
                            .roundToInt(),
                        widgetImageTranslation(
                                size.height.toFloat(),
                                space.height.toFloat(),
                                offsetY,
                            )
                            .roundToInt(),
                    )
            }
        }
    AsyncImage(
        path?.let(::File),
        description,
        modifier,
        contentScale = contentScale,
        alignment = alignment,
    )
}
