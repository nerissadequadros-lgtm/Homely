package app.homely

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.homely.data.*
import app.homely.platform.LocalThemeStore
import app.homely.ui.ThemeExperience
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeBuilderDeviceTest {
    @get:Rule val compose = createComposeRule()

    private fun layout() =
        HomeLayout(
            drawerRows = 4,
            drawerColumns = 5,
            drawerMode = "pages",
            drawerShowSearch = false,
            showDrawerButton = true,
            reduceMotion = true,
            hapticFeedback = false,
            quickAction = "none",
            statusBar = StatusBarSpec(show = false),
            // Keep a real app target and enough empty background to test surface gestures.
            kit = listOf(HomeItem(reference = "preview.app0", width = .2f, height = .15f)),
        )

    private fun render(layout: HomeLayout, onAction: (String, String?) -> Unit = { _, _ -> }) {
        compose.setContent {
            MaterialTheme {
                ThemeExperience(
                    draft = Draft(Manifest(title = "Builder device QA", layout = layout)),
                    preview = true,
                    modifier = Modifier.fillMaxSize(),
                    onAction = onAction,
                )
            }
        }
    }

    private val drawerApp =
        SemanticsMatcher("drawer application") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("drawer-app-") == true
        }

    private fun assertGrid(rows: Int, columns: Int) {
        compose.onAllNodes(drawerApp).assertCountEquals(rows * columns)
        val nodes = compose.onAllNodes(drawerApp).fetchSemanticsNodes()
        assertEquals(
            "Visible row count",
            rows,
            nodes.map { it.boundsInRoot.top.roundToInt() }.distinct().size,
        )
        assertEquals(
            "Visible column count",
            columns,
            nodes.map { it.boundsInRoot.left.roundToInt() }.distinct().size,
        )
    }

    @Test
    fun swipeOpensFourByFiveDrawerAndHorizontalSwipeChangesPage() {
        render(layout())
        compose.onNodeWithTag("theme-drawer").assertDoesNotExist()
        compose.onNodeWithTag("theme-home").performTouchInput { swipeUp() }
        compose.onNodeWithTag("theme-drawer").assertIsDisplayed()
        compose
            .onNodeWithTag("theme-experience")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Grade, 4 linhas, 5 colunas, página 1",
                )
            )
        assertGrid(4, 5)
        compose.onNodeWithTag("drawer-page-indicator").assertTextEquals("1 / 4")
        compose.onNodeWithTag("drawer-page-0").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("drawer-page-indicator").assertTextEquals("2 / 4")
        compose.onNodeWithTag("drawer-page-1").assertIsDisplayed()
        assertGrid(4, 5)
        compose.onNodeWithTag("drawer-page-1").performTouchInput { swipeRight() }
        compose.onNodeWithTag("drawer-page-indicator").assertTextEquals("1 / 4")
    }

    @Test
    fun alternateDoubleTapEntryAndDrawerSwipeDownUseIndependentBindings() {
        render(
            layout()
                .copy(
                    gestures =
                        HomeGestures(swipeUp = "none", doubleTap = "drawer", swipeDown = "widgets")
                )
        )
        compose.onNodeWithTag("theme-home").performTouchInput { swipeUp() }
        compose.onNodeWithTag("theme-drawer").assertDoesNotExist()
        compose.onNodeWithTag("theme-home").performTouchInput {
            doubleClick(Offset(width * .75f, height * .6f))
        }
        compose.onNodeWithTag("theme-drawer").assertIsDisplayed()
        compose.onNodeWithTag("drawer-page-0").performTouchInput { swipeDown() }
        compose.onNodeWithTag("theme-drawer").assertDoesNotExist()
        compose.onNodeWithTag("preview-menu").assertDoesNotExist()
        compose.onNodeWithTag("theme-home").performTouchInput { swipeDown() }
        compose.onNodeWithTag("preview-menu").assertIsDisplayed()
        compose.onNodeWithText("Adicionar widgets").assertIsDisplayed()
    }

    @Test
    fun verticalPageDirectionNavigatesAndReturnsHomeFromFirstPage() {
        render(layout().copy(drawerDirection = "vertical"))
        compose.onNodeWithTag("drawer-open").performClick()
        compose.onNodeWithTag("drawer-page-0").performTouchInput { swipeUp() }
        compose.onNodeWithTag("drawer-page-indicator").assertTextEquals("2 / 4")
        compose.onNodeWithTag("drawer-page-1").performTouchInput { swipeDown() }
        compose.onNodeWithTag("drawer-page-indicator").assertTextEquals("1 / 4")
        compose.onNodeWithTag("drawer-page-0").performTouchInput { swipeDown() }
        compose.onNodeWithTag("theme-drawer").assertDoesNotExist()
    }

    @Test
    fun minimalHomeKeepsGestureNavigationWithoutVisibleControls() {
        render(
            layout()
                .copy(
                    showDrawerButton = false,
                    showDrawerHeader = false,
                    showDrawerCloseButton = false,
                    showPageIndicators = false,
                    showNavigationHandle = false,
                    immersiveHome = true,
                    kit =
                        layout().kit +
                            HomeItem(
                                reference = "preview.app1",
                                page = 1,
                                width = .2f,
                                height = .15f,
                            ),
                )
        )
        compose.onNodeWithTag("drawer-open").assertDoesNotExist()
        compose.onNodeWithTag("quick-action").assertDoesNotExist()
        compose.onNodeWithText("Abrir Studio").assertDoesNotExist()
        compose.onNodeWithText("Todos os aplicativos").assertDoesNotExist()
        compose.onNodeWithTag("home-page-indicator").assertDoesNotExist()
        compose.onNodeWithTag("navigation-handle").assertDoesNotExist()

        compose.onNodeWithTag("theme-home").performTouchInput { swipeLeft() }
        compose
            .onNodeWithTag("theme-experience")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Tela inicial, página 2",
                )
            )
        compose.onNodeWithTag("theme-home").performTouchInput { swipeUp() }
        compose.onNodeWithTag("theme-drawer").assertIsDisplayed()
        compose.onNodeWithTag("drawer-search").assertDoesNotExist()
        compose.onNodeWithTag("drawer-close").assertDoesNotExist()
        compose.onNodeWithText("Aplicativos").assertDoesNotExist()
        compose.onNodeWithTag("drawer-page-indicator").assertDoesNotExist()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("theme-drawer").assertDoesNotExist()
        compose.onNodeWithTag("theme-home").performTouchInput { swipeUp() }
        compose.onNodeWithTag("drawer-page-0").performTouchInput { swipeLeft() }
        compose
            .onNodeWithTag("theme-experience")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Grade, 4 linhas, 5 colunas, página 2",
                )
            )
        compose.onNodeWithTag("drawer-page-1").performTouchInput { swipeDown() }
        compose.onNodeWithTag("theme-drawer").assertDoesNotExist()
    }

    @Test
    fun pinchBindingsOpenAndCloseDrawerWithoutLaunchingAnApplication() {
        render(
            layout().copy(gestures = HomeGestures(pinchIn = "drawer", pinchOut = "close_drawer"))
        )
        compose.onNodeWithTag("theme-home").performTouchInput {
            pinchHorizontally(inward = true)
        }
        compose.onNodeWithTag("theme-drawer").assertIsDisplayed()
        compose.onNodeWithTag("preview-app").assertDoesNotExist()
        compose.onNodeWithTag("theme-drawer").performTouchInput {
            pinchHorizontally(inward = false)
        }
        compose.onNodeWithTag("theme-drawer").assertDoesNotExist()
        compose.onNodeWithTag("preview-app").assertDoesNotExist()
        compose.onNodeWithTag("preview-menu").assertDoesNotExist()
    }

    private fun TouchInjectionScope.pinchHorizontally(inward: Boolean) {
        val start = if (inward) .3f else .1f
        val end = if (inward) .1f else .3f
        val y = height * .6f
        down(0, Offset(width * (.5f - start), y))
        down(1, Offset(width * (.5f + start), y))
        // Explicit pointer motion exercises the same two-finger recognizer as the launcher.
        moveTo(0, Offset(width * (.5f - end), y), delayMillis = 160)
        moveTo(1, Offset(width * (.5f + end), y), delayMillis = 160)
        up(0)
        up(1)
    }

    @Test
    fun lateralSwipeBindingsUpdateInsideAnAlreadyOpenDrawer() {
        val current = mutableStateOf(layout().copy(gestures = HomeGestures(swipeLeft = "none")))
        compose.setContent {
            MaterialTheme {
                ThemeExperience(
                    Draft(Manifest(layout = current.value)),
                    preview = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithTag("drawer-open").performClick()
        compose.onNodeWithTag("drawer-page-0").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("drawer-page-indicator").assertTextEquals("1 / 4")
        compose.onNodeWithTag("preview-app").assertDoesNotExist()
        compose.onNodeWithTag("preview-menu").assertDoesNotExist()
        compose.runOnIdle {
            current.value =
                current.value.copy(
                    gestures =
                        current.value.gestures.copy(
                            swipeLeft = "widgets",
                            swipeRight = "close_drawer",
                        )
                )
        }
        compose.onNodeWithTag("drawer-page-0").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("preview-menu").assertExists().assertIsDisplayed()
        compose.onNodeWithText("Adicionar widgets").assertIsDisplayed()
        compose.onNodeWithTag("preview-menu-close").performClick()
        compose.onNodeWithTag("drawer-page-indicator").assertTextEquals("1 / 4")
        compose.onNodeWithTag("drawer-page-0").performTouchInput { swipeRight() }
        compose.onNodeWithTag("theme-drawer").assertDoesNotExist()
    }

    @Test
    fun configuredAppHoldDoesNotTriggerTheEmptySpaceHoldAction() {
        render(
            layout().copy(gestures = HomeGestures(iconLongPress = "info", longPress = "wallpaper"))
        )
        compose.onNodeWithTag("home-app-preview.app0").performTouchInput { longClick() }
        compose.onNodeWithText("Informações do aplicativo").assertIsDisplayed()
        compose.onNodeWithText("Escolher wallpaper").assertDoesNotExist()
        compose.onNodeWithTag("preview-menu-close").performClick()
        compose.onNodeWithTag("theme-home").performTouchInput {
            longClick(Offset(width * .75f, height * .6f))
        }
        compose.onNodeWithText("Escolher wallpaper").assertIsDisplayed()
        compose.onNodeWithText("Informações do aplicativo").assertDoesNotExist()
    }

    @Test
    fun currentEditorConfigurationChangesAnAlreadyOpenPreview() {
        val current = mutableStateOf(layout())
        compose.setContent {
            MaterialTheme {
                ThemeExperience(
                    Draft(Manifest(layout = current.value)),
                    preview = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithTag("drawer-open").performClick()
        compose.onNodeWithTag("drawer-next").performClick()
        compose.onNodeWithTag("drawer-page-indicator").assertTextEquals("2 / 4")
        compose.runOnIdle {
            current.value =
                current.value.copy(drawerRows = 3, drawerColumns = 3, drawerShowSearch = true)
        }
        compose.onNodeWithTag("drawer-page-indicator").assertTextEquals("1 / 8")
        compose.onNodeWithTag("drawer-search").assertIsDisplayed()
        assertGrid(3, 3)
        compose.onNodeWithTag("drawer-search").performTextInput("Agenda")
        compose.onAllNodes(drawerApp).assertCountEquals(1)
        compose.onNodeWithTag("drawer-app-preview.app0").assertIsDisplayed()
    }

    @Test
    fun listScrollsWithoutReopeningOrClosingDrawer() {
        assertScrollableDrawer("list")
    }

    @Test
    fun continuousGridScrollsWithoutReopeningOrClosingDrawer() {
        assertScrollableDrawer("continuous")
    }

    private fun assertScrollableDrawer(mode: String) {
        render(layout().copy(drawerMode = mode, drawerColumns = 3))
        compose.onNodeWithTag("drawer-open").performClick()
        compose.onNodeWithTag("drawer-app-preview.app0").assertIsDisplayed()
        val scrollingDrawer = hasScrollAction() and hasAnyAncestor(hasTestTag("theme-drawer"))
        compose.onNode(scrollingDrawer).performTouchInput { swipeUp() }
        compose.onNodeWithTag("theme-drawer").assertIsDisplayed()
        compose.onNodeWithTag("drawer-app-preview.app0").assertIsNotDisplayed()
        compose.onNodeWithTag("drawer-page-indicator").assertDoesNotExist()
        compose.onNode(scrollingDrawer).performScrollToIndex(0)
        compose.onNodeWithTag("drawer-app-preview.app0").assertIsDisplayed()
        compose.onNode(scrollingDrawer).performTouchInput { swipeDown() }
        compose.onNodeWithTag("theme-drawer").assertDoesNotExist()
    }

    @Test
    fun appTapAndHoldStayInsideThePreview() {
        val externalActions = mutableListOf<Pair<String, String?>>()
        render(layout()) { action, pkg -> externalActions += action to pkg }
        compose.onNodeWithTag("home-app-preview.app0").performTouchInput { click() }
        compose.onNodeWithTag("preview-app").assertIsDisplayed()
        compose.onNodeWithTag("preview-app-close").performClick()
        compose.onNodeWithTag("drawer-open").performClick()
        compose.onNodeWithTag("drawer-app-preview.app0").performTouchInput { longClick() }
        compose.onNodeWithTag("preview-menu").assertIsDisplayed()
        compose.onNodeWithText("Menu do aplicativo").assertIsDisplayed()
        compose.onNodeWithTag("preview-menu-close").performClick()
        compose.onNodeWithTag("drawer-app-preview.app0").performTouchInput { click() }
        compose.onNodeWithTag("preview-app").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(
                "Preview must not execute external Android actions",
                externalActions.isEmpty(),
            )
        }
    }

    @Test
    fun localLibraryReloadsTheCompleteBuilderConfiguration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File(context.cacheDir, "builder-wall-${System.nanoTime()}.png")
        Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            image.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }
        val configuration =
            layout()
                .copy(
                    drawerOpacity = .67f,
                    drawerBlur = 14,
                    drawerRadius = 30,
                    drawerSpacing = 9,
                    drawerMargin = 21,
                    drawerIconSize = 48,
                    drawerAnimation = "fade",
                    animationDuration = 390,
                    showDrawerButton = false,
                    showPageIndicators = false,
                    showNavigationHandle = false,
                    immersiveHome = true,
                    showDrawerHeader = false,
                    showDrawerCloseButton = false,
                    gestures =
                        HomeGestures(
                            doubleTap = "app",
                            pinchIn = "widgets",
                            quickPackage = context.packageName,
                        ),
                )
        val store = LocalThemeStore(context)
        var savedId: String? = null
        try {
            val saved =
                store.save(
                    Draft(
                        Manifest(title = "Builder local QA", layout = configuration),
                        wallpaperPath = image.path,
                    )
                )
            savedId = saved.id
            val restored = LocalThemeStore(context).list().single { it.id == saved.id }
            assertEquals(configuration, restored.draft.manifest.layout)
            assertTrue(File(restored.draft.wallpaperPath!!).isFile)
        } finally {
            savedId?.let { store.remove(it) }
            image.delete()
        }
    }
}
