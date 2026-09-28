package app.homely

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.homely.data.*
import app.homely.platform.*
import app.homely.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InteractiveElementDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun layer() =
        WidgetLayer(
            type = "number",
            text = "Inicial",
            tapAction = "toggle_state",
            animation = "none",
            states = listOf(ElementState(condition = "toggled", text = "Alternado")),
        )

    @Test
    fun previewTogglesLocallyAndDoesNotPersistDeviceState() {
        val l = layer()
        compose.setContent {
            MaterialTheme { WidgetLayerContent(l, emptyMap(), true, Modifier.size(180.dp)) }
        }
        compose.onNodeWithText("Inicial").assertIsDisplayed()
        compose.onNodeWithTag("element-${l.id}").performTouchInput { click() }
        compose.onNodeWithText("Alternado").assertIsDisplayed()
        assertFalse(ElementSystem.toggled(ctx, l.id))
        compose.onNodeWithTag("element-${l.id}").performTouchInput { click() }
        compose.onNodeWithText("Inicial").assertIsDisplayed()
    }

    @Test
    fun longPressDoesNotTriggerTapAndTouchZoneOverridesWholeElement() {
        val l =
            layer()
                .copy(
                    tapAction = "none",
                    longPressAction = "toggle_state",
                    touchZones = listOf(ElementTouchZone(width = .5f, action = "toggle_state")),
                )
        compose.setContent {
            MaterialTheme { WidgetLayerContent(l, emptyMap(), true, Modifier.size(180.dp)) }
        }
        compose.onNodeWithTag("element-${l.id}").performTouchInput { longClick() }
        compose.onNodeWithText("Alternado").assertIsDisplayed()
        compose.onNodeWithTag("element-${l.id}").performTouchInput {
            click(Offset(width * .8f, height * .5f))
        }
        compose.onNodeWithText("Alternado").assertIsDisplayed()
        compose.onNodeWithTag("element-${l.id}").performTouchInput {
            click(Offset(width * .2f, height * .5f))
        }
        compose.onNodeWithText("Inicial").assertIsDisplayed()
    }

    @Test
    fun nativeBitmapUsesConditionalBackgroundAndWholeElementOpacity() {
        val l =
            WidgetLayer(
                type = "number",
                text = "",
                showValue = false,
                elementRadius = 0,
                elementBackground = "#0000FF",
                opacity = .5f,
                states = listOf(ElementState(condition = "always", background = "#FF0000")),
            )
        val bitmap = ElementBitmap.render(ctx, l, emptyMap(), emptyMap(), 100, 100)
        val pixel = bitmap.getPixel(50, 50)
        assertEquals(255, Color.red(pixel))
        assertTrue(Color.alpha(pixel) in 126..128)
        assertEquals(0, Color.blue(pixel))
        bitmap.recycle()
    }

    @Test
    fun savedElementRetainsImagesAfterOriginalIsRemoved() {
        val path = File(ctx.cacheDir, "element-${System.nanoTime()}.png")
        val image =
            Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        path.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
        val l = WidgetLayer(states = listOf(ElementState(imageId = "state-image")))
        val token =
            StudioWidgets.save(
                ctx,
                WidgetSpec(layers = listOf(l), backgroundImageId = "bg"),
                mapOf("state-image" to path.path, "bg" to path.path),
            )
        assertTrue(path.delete())
        val saved = StudioWidgets.read(ctx, token)!!
        assertTrue(saved.spec.imageKeys().all { File(saved.images.getValue(it)).isFile })
        val views = StudioWidgets.views(ctx, saved, 300, 150)
        assertNotNull(views.apply(ctx, null))
    }

    @Test
    fun functionalTemplateAndNativeClockRender() {
        val widget = pinkControlWidget().validate()
        assertEquals(9, widget.layers.size)
        val view =
            StudioWidgets.views(ctx, SavedWidget(widget, emptyMap()), 320, 190).apply(ctx, null)
        assertNotNull(view.findViewById<android.widget.TextClock>(R.id.layer_clock))
        compose.setContent { MaterialTheme { ComposedWidget(widget, preview = true) } }
        compose.onNodeWithTag("element-${widget.layers.first().id}").assertIsDisplayed()
    }
}
