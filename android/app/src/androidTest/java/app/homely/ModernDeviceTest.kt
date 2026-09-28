package app.homely

import android.content.Intent
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import app.homely.data.*
import app.homely.platform.*
import java.io.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ModernDeviceTest {
    @Test
    fun privateThemeCopiesAssetsAndPreservesWidgetsOffline() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = HomelyRepository(ctx)
        val store = LocalThemeStore(ctx)
        val input = File(ctx.cacheDir, "modern-qa-" + System.nanoTime() + ".png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val image = WidgetSpec(kind = "image", name = "Minha foto")
        val clock = WidgetSpec(kind = "clock", name = "Meu relógio", foreground = "#FFEEDD")
        val draft =
            Draft(
                Manifest(
                    title = "Teste privado",
                    widgets = listOf(clock, image),
                    layout = HomeLayout(columns = 5, iconSize = 48),
                ),
                wallpaperPath = input.path,
                icons = listOf(DraftIcon(input.path, ctx.packageName, "Homely")),
                widgetImages = mapOf(image.id to input.path),
            )
        val saved = store.save(draft)
        val target = File(ctx.cacheDir, "modern-import-" + System.nanoTime())
        try {
            val original = ThemeArchive.sha(input)
            input.writeText("Source changed")
            assertEquals(original, ThemeArchive.sha(File(saved.draft.wallpaperPath!!)))
            assertEquals(original, ThemeArchive.sha(File(saved.draft.icons.first().path)))
            assertTrue(store.list().any { it.id == saved.id })
            store.stage(saved)
            assertEquals(saved.id, LocalThemeStore(ctx).pending()?.id)
            store.clearPending()
            val out = ByteArrayOutputStream()
            ThemeArchive.export(saved.draft, out, repo.json)
            val imported =
                ThemeArchive.import(ByteArrayInputStream(out.toByteArray()), target, repo.json)
            assertEquals("1.5", imported.manifest.schemaVersion)
            assertEquals(5, imported.manifest.layout.columns)
            assertEquals("#FFEEDD", imported.manifest.widgets.first().foreground)
            assertEquals(original, ThemeArchive.sha(File(imported.widgetImages.getValue(image.id))))
            assertTrue(
                ctx.packageManager
                    .queryIntentActivities(
                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                        0,
                    )
                    .any { it.activityInfo.name.endsWith("LauncherActivity") }
            )
        } finally {
            store.remove(saved.id)
            input.delete()
            target.deleteRecursively()
        }
    }

    @Test
    fun usernameEndpointHandlesOccupiedAndFreeHandles() = runBlocking {
        val repo = HomelyRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        assertFalse(
            repo
                .get<UsernameAvailability>("/v1/usernames/availability?username=homelystudio")
                .available
        )
        assertTrue(
            repo
                .get<UsernameAvailability>(
                    "/v1/usernames/availability?username=qa" + System.currentTimeMillis()
                )
                .available
        )
    }
}
