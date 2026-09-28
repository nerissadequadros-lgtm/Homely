package app.homely

import android.appwidget.AppWidgetManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.homely.data.*
import app.homely.platform.NativeWidgets
import app.homely.ui.ThemeImagePager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RefinementDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun swipeChangesScreenshotWithoutOpeningTheme() {
        var opened = false
        compose.setContent {
            MaterialTheme {
                ThemeImagePager(
                    listOf("file:///missing-a.png", "file:///missing-b.png"),
                    "Tema",
                    { it },
                    Modifier.size(250.dp, 300.dp).testTag("gallery"),
                    onOpen = { opened = true },
                )
            }
        }
        compose
            .onNodeWithTag("gallery")
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Imagem 1 de 2")
            )
        compose.onNodeWithTag("gallery").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose
            .onNodeWithTag("gallery")
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Imagem 2 de 2")
            )
        assertFalse(opened)
        compose.onNodeWithTag("gallery").performTouchInput { click(center) }
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test
    fun allCatalogWidgetsAreRegisteredAndInflate() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val providers =
            AppWidgetManager.getInstance(ctx).installedProviders.map { it.provider }.toSet()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            widgetCatalog.forEach { widget ->
                assertTrue(widget.name, providers.contains(NativeWidgets.component(ctx, widget.id)))
                assertNotNull(NativeWidgets.views(ctx, widget.id).apply(ctx, FrameLayout(ctx)))
            }
        }
    }

    @Test
    fun liveCatalogContainsOnlyAppIconsAndRealGalleries() = runBlocking {
        val repo = HomelyRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val icons = repo.get<IconPage>("/v1/icons")
        assertTrue(icons.items.size >= 12)
        assertTrue(
            icons.items.all { it.asset.kind == "icon" && !it.asset.packageName.isNullOrBlank() }
        )
        val filtered = repo.get<IconPage>("/v1/icons?style=3D")
        assertEquals(4, filtered.items.size)
        assertTrue(filtered.items.all { it.style == "3D" })
        val themes = repo.get<ThemePage>("/v1/themes")
        val editorial = themes.items.filter { it.author.username == "homelystudio" }
        assertTrue(editorial.isNotEmpty())
        assertTrue(editorial.all { themeScreenshots(it).distinct().size == 2 })
    }
}
