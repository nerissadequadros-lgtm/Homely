package app.homely

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import app.homely.data.*
import app.homely.platform.*
import java.io.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StudioDeviceTest {
    @Test
    fun layeredIconsAndWidgetsSurviveSharingAndNativeRendering() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(ctx.cacheDir, "studio-source.png")
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
            source.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val icon =
            IconRenderer.materialize(
                ctx,
                DraftIcon(
                    "",
                    ctx.packageName,
                    "Homely",
                    backgroundColor = "#FF0000",
                    foregroundScale = .4f,
                    sourcePath = source.path,
                    mask = "circle",
                ),
            )
        val rendered = IconRenderer.decode(icon.path)
        assertEquals(Color.BLUE, rendered.getPixel(128, 128))
        assertEquals(Color.RED, rendered.getPixel(128, 32))
        assertEquals(0, Color.alpha(rendered.getPixel(0, 0)))
        rendered.recycle()
        val photo = WidgetLayer(type = "image", x = .5f, y = .5f, imageAssetId = null)
        val spec =
            WidgetSpec(
                layers =
                    listOf(
                        WidgetLayer(type = "clock", fontSize = 30),
                        photo,
                        WidgetLayer(
                            type = "shortcut",
                            x = .5f,
                            tapPackage = ctx.packageName,
                            text = "Homely",
                            zIndex = 2,
                        ),
                    ),
                columns = 4,
                rows = 2,
            )
        val draft =
            Draft(
                Manifest(title = "Studio QA", widgets = listOf(spec)),
                source.path,
                icons = listOf(icon),
                widgetImages = mapOf(photo.id to source.path),
            )
        val saved = LocalThemeStore(ctx).save(draft)
        val dir = File(ctx.cacheDir, "studio-roundtrip-${System.nanoTime()}")
        try {
            val out = ByteArrayOutputStream()
            ThemeArchive.export(saved.draft, out, HomelyRepository(ctx).json)
            val copy =
                ThemeArchive.import(
                    ByteArrayInputStream(out.toByteArray()),
                    dir,
                    HomelyRepository(ctx).json,
                )
            assertEquals("circle", copy.icons.single().mask)
            assertEquals(.4f, copy.icons.single().foregroundScale)
            assertArrayEquals(
                source.readBytes(),
                File(copy.icons.single().sourcePath!!).readBytes(),
            )
            assertArrayEquals(
                source.readBytes(),
                File(copy.widgetImages.getValue(photo.id)).readBytes(),
            )
            assertEquals(3, copy.manifest.widgets.single().layers.size)
            val token = StudioWidgets.save(ctx, spec, draft.widgetImages)
            val snapshot = StudioWidgets.read(ctx, token)!!
            source.writeText("changed source")
            assertTrue(IconRenderer.decode(snapshot.images.getValue(photo.id)).width > 0)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view = StudioWidgets.views(ctx, snapshot, 320, 160).apply(ctx, null)
                assertNotNull(view.findViewById<android.view.View>(R.id.layer_clock))
            }
            assertTrue(
                Regex("#[0-9A-F]{6}").matches(PaletteExtractor.extract(copy.wallpaperPath!!).accent)
            )
        } finally {
            LocalThemeStore(ctx).remove(saved.id)
            dir.deleteRecursively()
            source.delete()
        }
    }

    @Test
    fun rejectsOffCanvasOrExecutableWidgetLayers() {
        assertTrue(
            runCatching {
                WidgetSpec(layers = listOf(WidgetLayer(x = .75f, width = .5f))).validate()
            }
                .isFailure
        )
        assertTrue(
            runCatching { WidgetSpec(layers = listOf(WidgetLayer(type = "script"))).validate() }
                .isFailure
        )
        assertTrue(
            runCatching { WidgetSpec(layers = listOf(WidgetLayer(x = Float.NaN))).validate() }
                .isFailure
        )
    }
}
