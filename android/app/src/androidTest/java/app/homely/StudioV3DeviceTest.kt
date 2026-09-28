package app.homely

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import app.homely.data.*
import app.homely.platform.*
import java.io.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StudioV3DeviceTest {
    @Test
    fun freeformFontsGesturesAndKitSurviveArchive() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val font = ThemeFont(name = "Roboto QA")
        val source =
            File("/system/fonts/Roboto-Regular.ttf").takeIf { it.isFile }
                ?: File("/system/fonts/RobotoStatic-Regular.ttf")
        assertTrue(source.isFile)
        val photo = File(ctx.cacheDir, "v3-wall.png")
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            photo.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val layer =
            WidgetLayer(
                type = "title",
                text = "Homely",
                x = .17f,
                y = .23f,
                width = .7f,
                height = .4f,
                fontId = font.id,
                opacity = .75f,
                weight = 700,
                alignment = "start",
            )
        val widget = WidgetSpec(layers = listOf(layer), placement = "free", gridColumns = 7)
        val icon =
            IconRenderer.materialize(
                ctx,
                DraftIcon(
                    "",
                    ctx.packageName,
                    "Homely",
                    sourcePath = photo.path,
                    offsetX = 12f,
                    offsetY = -8f,
                    text = "H",
                    fontId = font.id,
                ),
                mapOf(font.id to source.path),
            )
        val draft =
            Draft(
                Manifest(
                    title = "Studio v3 QA",
                    widgets = listOf(widget),
                    fonts = listOf(font),
                    layout =
                        HomeLayout(
                            gestures = HomeGestures(doubleTap = "lock"),
                            kit =
                                listOf(
                                    HomeItem(
                                        kind = "widget",
                                        reference = widget.id,
                                        x = .08f,
                                        y = .2f,
                                        width = .8f,
                                        height = .3f,
                                    )
                                ),
                        ),
                ),
                photo.path,
                icons = listOf(icon),
                fontFiles = mapOf(font.id to source.path),
            )
        val store = LocalThemeStore(ctx)
        val saved = store.save(draft)
        val dir = File(ctx.cacheDir, "v3-roundtrip-${System.nanoTime()}")
        try {
            val out = ByteArrayOutputStream()
            val json = HomelyRepository(ctx).json
            ThemeArchive.export(saved.draft, out, json)
            val copy = ThemeArchive.import(ByteArrayInputStream(out.toByteArray()), dir, json)
            assertEquals("1.5", copy.manifest.schemaVersion)
            assertEquals(layer, copy.manifest.widgets.single().layers.single())
            assertEquals(draft.manifest.layout, copy.manifest.layout)
            assertEquals(12f, copy.icons.single().offsetX)
            assertEquals(-8f, copy.icons.single().offsetY)
            assertEquals(font.id, copy.icons.single().fontId)
            assertArrayEquals(
                source.readBytes(),
                File(copy.fontFiles.getValue(font.id)).readBytes(),
            )
            assertNotNull(FontLibrary.typeface(copy.fontFiles.getValue(font.id)))
        } finally {
            store.remove(saved.id)
            dir.deleteRecursively()
            photo.delete()
        }
    }

    @Test
    fun allFifteenWidgetItemsRenderInNativeHost() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val types =
            listOf(
                "text",
                "title",
                "clock",
                "analog",
                "date",
                "battery",
                "battery_ring",
                "image",
                "shortcut",
                "shape",
                "note",
                "spacer",
                "progress",
                "countdown",
                "weather",
            )
        val layers = types.mapIndexed { i, t ->
            WidgetLayer(
                type = t,
                text = "Teste",
                x = (i % 3) / 3f,
                y = (i / 3) / 5f,
                width = .3f,
                height = .19f,
                fontSize = 12,
                zIndex = i,
                targetTime = System.currentTimeMillis() + 120000,
                tapPackage = if (t == "shortcut") ctx.packageName else null,
            )
        }
        val widget = WidgetSpec(height = 300, layers = layers).validate()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view =
                StudioWidgets.views(ctx, SavedWidget(widget, emptyMap()), 320, 300).apply(ctx, null)
            assertNotNull(view.findViewById<android.view.View>(R.id.layer_analog))
            assertNotNull(view.findViewById<android.view.View>(R.id.layer_countdown))
        }
    }

    @Test
    fun rejectsEscapingKitAndMissingFontReferences() {
        assertTrue(
            runCatching {
                Manifest(
                        layout =
                            HomeLayout(
                                kit = listOf(HomeItem(reference = "app.test", x = .9f, width = .3f))
                            )
                    )
                    .validateComposition()
            }
                .isFailure
        )
        assertTrue(
            runCatching {
                Manifest(
                        widgets =
                            listOf(WidgetSpec(layers = listOf(WidgetLayer(fontId = "missing"))))
                    )
                    .validateComposition()
            }
                .isFailure
        )
        assertTrue(runCatching { WidgetSpec(id = "../escape").validate() }.isFailure)
    }
}
