package app.homely.platform

import app.homely.data.*
import java.io.*
import java.nio.file.Files
import java.util.zip.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ThemeArchiveTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun stateImagesAndBackgroundSurviveArchive() {
        val base = Files.createTempDirectory("homely-elements").toFile()
        try {
            val photo = File(base, "source.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val layer =
                WidgetLayer(
                    type = "ring",
                    dataSource = "battery",
                    ringWidth = 5f,
                    states =
                        listOf(
                            ElementState(
                                condition = "low",
                                imageId = "low-image",
                                color = "#FF0000",
                            )
                        ),
                )
            val widget =
                WidgetSpec(
                    backgroundImageId = "bg",
                    backgroundZoom = 2f,
                    backgroundOffsetX = .5f,
                    backgroundOpacity = .4f,
                    layers = listOf(layer),
                )
            val output = ByteArrayOutputStream()
            ThemeArchive.export(
                Draft(
                    Manifest(title = "States", widgets = listOf(widget)),
                    photo.path,
                    widgetImages = mapOf("bg" to photo.path, "low-image" to photo.path),
                ),
                output,
                json,
            )
            val restored =
                ThemeArchive.import(
                    ByteArrayInputStream(output.toByteArray()),
                    File(base, "import"),
                    json,
                )
            assertEquals(2f, restored.manifest.widgets.first().backgroundZoom, 0f)
            assertEquals(
                layer.states.first().color,
                restored.manifest.widgets.first().layers.first().states.first().color,
            )
            assertTrue(restored.widgetImages.keys.containsAll(setOf("bg", "low-image")))
            restored.widgetImages.values.forEach {
                assertArrayEquals(photo.readBytes(), File(it).readBytes())
            }
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun builderNavigationSurvivesPortableArchive() {
        val base = Files.createTempDirectory("homely-builder").toFile()
        try {
            val wall = File(base, "wall.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val layout =
                HomeLayout(
                    drawerMode = "pages",
                    drawerColumns = 5,
                    drawerRows = 4,
                    drawerSpacing = 6,
                    drawerMargin = 20,
                    drawerIconSize = 42,
                    drawerDirection = "horizontal",
                    drawerAnimation = "zoom",
                    animationDuration = 360,
                    reduceMotion = true,
                    drawerOpacity = .75f,
                    drawerBlur = 8,
                    drawerRadius = 18,
                    drawerShowSearch = false,
                    hapticFeedback = false,
                    gestures =
                        HomeGestures(
                            swipeUp = "drawer",
                            swipeDown = "wallpaper",
                            doubleTap = "app",
                            pinchIn = "widgets",
                            pinchOut = "personalize",
                            drawerSwipeDown = "close_drawer",
                            quickPackage = "com.android.settings",
                        ),
                )
            val output = ByteArrayOutputStream()
            ThemeArchive.export(
                Draft(Manifest(title = "Builder", layout = layout), wall.path),
                output,
                json,
            )
            val imported =
                ThemeArchive.import(
                    ByteArrayInputStream(output.toByteArray()),
                    File(base, "import"),
                    json,
                )
            assertEquals(layout, imported.manifest.layout)
            assertEquals("drawer", imported.manifest.layout.gestures.actionFor("swipe_up"))
            assertEquals(
                "close_drawer",
                imported.manifest.layout.gestures.actionFor("swipe_down", true),
            )
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun archiveRoundTripPreservesContentAndMetadata() {
        val base = Files.createTempDirectory("homely-test").toFile()
        try {
            val wall = File(base, "wall.png").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
            val draft =
                Draft(
                    Manifest(
                        title = "A theme",
                        widgets =
                            listOf(
                                WidgetSpec(id = "photo", kind = "image"),
                                WidgetSpec(
                                    id = "text",
                                    kind = "text",
                                    text = "Original widget",
                                    fontSize = 22,
                                ),
                            ),
                    ),
                    wall.path,
                    screenshotPaths = listOf(wall.path, wall.path),
                    widgetImages = mapOf("photo" to wall.path),
                )
            val output = ByteArrayOutputStream()
            ThemeArchive.export(draft, output, json)
            val imported =
                ThemeArchive.import(
                    ByteArrayInputStream(output.toByteArray()),
                    File(base, "import"),
                    json,
                )
            assertEquals("A theme", imported.manifest.title)
            assertEquals("1.5", imported.manifest.schemaVersion)
            assertEquals(2, imported.screenshotPaths.size)
            assertEquals("Original widget", imported.manifest.widgets[1].text)
            assertArrayEquals(
                wall.readBytes(),
                File(imported.widgetImages.getValue("photo")).readBytes(),
            )
            imported.screenshotPaths.forEach {
                assertArrayEquals(wall.readBytes(), File(it).readBytes())
            }
            assertArrayEquals(wall.readBytes(), File(imported.wallpaperPath!!).readBytes())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun traversalCannotWriteOutsideImportDirectory() {
        val base = Files.createTempDirectory("homely-zip").toFile()
        try {
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use {
                it.putNextEntry(ZipEntry("../escape"))
                it.write("unsafe".toByteArray())
                it.closeEntry()
            }
            try {
                ThemeArchive.import(
                    ByteArrayInputStream(bytes.toByteArray()),
                    File(base, "import"),
                    json,
                )
                fail("Expected rejection")
            } catch (_: IllegalArgumentException) {}
            assertFalse(File(base, "escape").exists())
            assertFalse(File(base, "import").exists())
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun tamperingIsRejected() {
        val base = Files.createTempDirectory("homely-hash").toFile()
        try {
            val wall = File(base, "wall.png").apply { writeText("original") }
            val bytes = ByteArrayOutputStream()
            ThemeArchive.export(Draft(Manifest(title = "Test"), wall.path), bytes, json)
            val corrupt = ByteArrayOutputStream()
            ZipOutputStream(corrupt).use { out ->
                ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { input ->
                    while (true) {
                        val e = input.nextEntry ?: break
                        out.putNextEntry(ZipEntry(e.name))
                        if (e.name.startsWith("assets/")) out.write("changed!".toByteArray())
                        else input.copyTo(out)
                        out.closeEntry()
                    }
                }
            }
            try {
                ThemeArchive.import(
                    ByteArrayInputStream(corrupt.toByteArray()),
                    File(base, "import"),
                    json,
                )
                fail("Expected checksum failure")
            } catch (_: IllegalArgumentException) {}
        } finally {
            base.deleteRecursively()
        }
    }
}
