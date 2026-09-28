package app.homely.platform

import app.homely.data.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CreatorArchiveTest {
    @Test
    fun iconStatesAndFreeWidgetDimensionsSurvivePortablePackage() {
        val root = Files.createTempDirectory("creator-archive").toFile()
        try {
            val files =
                listOf("default", "pressed", "disabled").associateWith { state ->
                    File(root, "$state.png").apply { writeBytes(state.toByteArray()) }.path
                }
            val widget = WidgetSpec(canvasWidth = 710, height = 600, followThemePalette = true)
            val draft =
                Draft(
                    Manifest(title = "Ícones com estados", widgets = listOf(widget)),
                    files.getValue("default"),
                    icons =
                        listOf(
                            DraftIcon(
                                files.getValue("default"),
                                "app.example",
                                "Ícone",
                                rendered = true,
                                statePaths = files,
                            )
                        ),
                )
            val json = Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
            }
            val output = ByteArrayOutputStream()
            ThemeArchive.export(draft, output, json)
            val imported =
                ThemeArchive.import(
                    output.toByteArray().inputStream(),
                    File(root, "restored"),
                    json,
                )
            assertEquals(widget, imported.manifest.widgets.single())
            assertEquals(files.keys, imported.icons.single().statePaths.keys)
            imported.icons.single().statePaths.forEach { (key, path) ->
                assertArrayEquals(key.toByteArray(), File(path).readBytes())
            }
            assertTrue(imported.icons.single().statePaths.values.none { it in files.values })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun paletteBindingOnlyChangesOptedInWidgets() {
        val original = WidgetSpec(layers = listOf(WidgetLayer(type = "ring", color = "#123456")))
        val palette = Palette(accent = "#FF0000", text = "#00FF00", surface = "#0000FF")
        assertEquals(original, original.withPalette(palette))
        val linked = original.copy(followThemePalette = true).withPalette(palette)
        assertEquals("#FF0000", linked.layers.single().color)
        assertEquals("#0000FF", linked.background)
    }
}
