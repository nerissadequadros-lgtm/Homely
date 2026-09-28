package app.homely

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.homely.data.*
import app.homely.platform.*
import app.homely.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CreatorFlowDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val ctx
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun unitEditorReplacesThePresetAndSavesOneElementInChosenFolder() {
        ctx.getSharedPreferences("unit-element-draft", 0).edit().clear().commit()
        val vm = HomelyViewModel(ctx.applicationContext as android.app.Application)
        val before = ElementLibrary.list(ctx).map { it.first }.toSet()
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        compose.setContent {
            val state by vm.state.collectAsState()
            MaterialTheme { ElementCreatorScreen(vm, state) { finished.set(true) } }
        }
        compose.onNodeWithText("Bateria", useUnmergedTree = true).performClick()
        repeat(4) { compose.onNodeWithTag("element-next").performClick() }
        compose.onNodeWithText("Salvar elemento").performClick()
        compose.onNodeWithText("+ Nova pasta").performClick()
        compose.onNodeWithText("Nome da pasta").performTextInput("Teste unitário")
        compose.onNodeWithTag("confirm-save-creation").performClick()
        compose.waitUntil(10000) { finished.get() }
        val item = ElementLibrary.list(ctx).first { it.first !in before }
        assertEquals(1, item.second.spec.layers.size)
        assertEquals("battery", item.second.spec.layers.single().type)
        assertTrue(item.second.spec.layers.single().components.isEmpty())
        assertTrue(
            CreationFolders.entries(ctx, "element").any {
                it.token == item.first && it.folder == "Teste unitário"
            }
        )
    }

    @Test
    fun iconStatesHaveDistinctPixelsAndOwnedMedia() {
        val input = File(ctx.cacheDir, "icon-${System.nanoTime()}.png")
        val bitmap =
            Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val base =
            IconAppearance(
                foreground =
                    IconArtworkLayer(
                        kind = "image",
                        imagePath = input.path,
                        tintImage = true,
                        color = "#FF0000",
                    )
            )
        val design =
            IconDesign(
                states =
                    mapOf(
                        "default" to base,
                        "pressed" to
                            base.copy(foreground = base.foreground.copy(color = "#00FF00")),
                        "disabled" to
                            base.copy(foreground = base.foreground.copy(color = "#0000FF")),
                    )
            )
        val saved = IconDesignLibrary.save(ctx, design, "Estados de teste")
        assertTrue(input.delete())
        listOf("default" to Color.RED, "pressed" to Color.GREEN, "disabled" to Color.BLUE)
            .forEach { (state, expected) ->
                val image = IconRenderer.decode(saved.renderedStates.getValue(state))
                assertEquals(expected, image.getPixel(image.width / 2, image.height / 2))
                image.recycle()
            }
        assertTrue(saved.design.states.values.all { File(it.foreground.imagePath!!).exists() })
        val rerendered = IconDesignRenderer.render(saved.design, "pressed", 48)
        assertEquals(Color.GREEN, rerendered.getPixel(24, 24))
        rerendered.recycle()
    }

    @Test
    fun importedSvgIsRasterizedAndRemainsUsableAfterSourceRemoval() {
        val svg =
            File(ctx.cacheDir, "vector-${System.nanoTime()}.svg").apply {
                writeText(
                    """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"><rect width="10" height="10" fill="#ff0000"/></svg>"""
                )
            }
        val path = IconDesignRenderer.importImage(ctx, android.net.Uri.fromFile(svg))
        assertTrue(svg.delete())
        val image = IconRenderer.decode(path)
        assertEquals(Color.RED, image.getPixel(image.width / 2, image.height / 2))
        image.recycle()
    }
}
