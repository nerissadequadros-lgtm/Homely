package app.homely.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ThemeNavigationTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun oldThemesRetainTheirBindingsAndGainNavigationDefaults() {
        val manifest =
            json.decodeFromString<Manifest>(
                """{"schemaVersion":"1.0","layout":{"drawerLayout":"list","gestures":{"swipeDown":"notifications"}}}"""
            )
        manifest.validateComposition()
        assertEquals("list", manifest.layout.effectiveDrawerMode())
        assertEquals("horizontal", manifest.layout.drawerDirection)
        assertEquals(5, manifest.layout.drawerRows)
        assertEquals(54, manifest.layout.drawerIconSize)
        assertEquals("notifications", manifest.layout.gestures.actionFor("swipe_down"))
        assertEquals(
            "close_drawer",
            manifest.layout.gestures.actionFor("swipe_down", inDrawer = true),
        )
        assertEquals("next_page", manifest.layout.gestures.actionFor("swipe_left"))
        assertEquals("previous_page", manifest.layout.gestures.actionFor("swipe_right"))
        assertFalse(HomeGestureActions.contains("notifications"))
    }

    @Test
    fun bindingEachGestureChangesOnlyThatGestureAndRetainsTheSelectedApp() {
        val original = HomeGestures(quickPackage = "com.example.reader")
        HomeGestureIds.forEach { selected ->
            val changed = original.withBinding(selected, "app")
            assertEquals("com.example.reader", changed.quickPackage)
            HomeGestureIds.forEach { gesture ->
                assertEquals(
                    if (gesture == selected) "app" else original.actionFor(gesture),
                    changed.actionFor(gesture),
                )
            }
            HomeLayout(gestures = changed).validate()
        }
        assertEquals("none", original.actionFor("unrecognized"))
        assertThrows(IllegalArgumentException::class.java) { original.withBinding("bad", "drawer") }
        assertThrows(IllegalArgumentException::class.java) {
            original.withBinding("tap", "execute")
        }
    }

    @Test
    fun drawerDownBindingIsIndependentFromHomeDown() {
        val gestures =
            HomeGestures()
                .withBinding("swipe_down", "widgets")
                .withBinding("drawer_swipe_down", "none")
        assertEquals("widgets", gestures.actionFor("swipe_down"))
        assertEquals("none", gestures.actionFor("swipe_down", inDrawer = true))
        assertEquals("drawer", gestures.actionFor("swipe_up", inDrawer = true))
        assertEquals(
            "pages",
            HomeLayout(drawerLayout = "list", drawerMode = "pages").effectiveDrawerMode(),
        )
    }

    @Test
    fun serializedDraftPreservesTheCompleteBuilderConfiguration() {
        val layout =
            HomeLayout(
                drawerColumns = 5,
                drawerRows = 4,
                drawerSpacing = 8,
                drawerMargin = 20,
                drawerIconSize = 48,
                drawerMode = "pages",
                drawerDirection = "vertical",
                drawerAnimation = "fade",
                animationDuration = 420,
                reduceMotion = true,
                drawerOpacity = .65f,
                drawerBlur = 12,
                drawerRadius = 32,
                drawerShowSearch = false,
                hapticFeedback = false,
                gestures =
                    HomeGestures(
                        swipeUp = "drawer",
                        swipeDown = "close_drawer",
                        swipeLeft = "next_page",
                        swipeRight = "previous_page",
                        tap = "app",
                        doubleTap = "search",
                        pinchIn = "widgets",
                        pinchOut = "personalize",
                        drawerSwipeDown = "none",
                        quickPackage = "com.example.reader",
                    ),
            )
        val draft = Draft(manifest = Manifest(title = "Navigation", layout = layout))
        val restored = json.decodeFromString<Draft>(json.encodeToString(draft))
        restored.manifest.validateComposition()
        assertEquals(draft, restored)
    }

    @Test
    fun invalidDimensionsAppearanceActionsAndEnumsAreRejected() {
        val base = HomeLayout()
        val invalid =
            listOf(
                base.copy(drawerRows = 1),
                base.copy(drawerRows = 11),
                base.copy(drawerColumns = 9),
                base.copy(drawerSpacing = -1),
                base.copy(drawerMargin = 41),
                base.copy(drawerIconSize = 29),
                base.copy(drawerOpacity = Float.NaN),
                base.copy(drawerOpacity = .1f),
                base.copy(drawerBlur = 31),
                base.copy(drawerRadius = 49),
                base.copy(drawerMode = "carousel"),
                base.copy(drawerDirection = "diagonal"),
                base.copy(drawerAnimation = "spin"),
                base.copy(animationDuration = -1),
                base.copy(animationDuration = 801),
                base.copy(drawerStyle = "unknown"),
                base.copy(drawerLayout = "unknown"),
                base.copy(folderStyle = "unknown"),
                base.copy(appAnimation = "unknown"),
                base.copy(scrollDirection = "unknown"),
                base.copy(quickAction = "unknown"),
                base.copy(statusBar = StatusBarSpec(tint = "red")),
                base.copy(statusBar = StatusBarSpec(clockFormat = "seconds")),
                base.copy(statusBar = StatusBarSpec(batteryIcon = "custom")),
                base.copy(statusBar = StatusBarSpec(networkIcon = "custom")),
                base.copy(gestures = HomeGestures(pinchIn = "execute")),
                base.copy(gestures = HomeGestures(iconLongPress = "unknown")),
            )
        invalid.forEach { layout ->
            assertThrows(layout.toString(), IllegalArgumentException::class.java) {
                Manifest(layout = layout).validateComposition()
            }
        }
    }

    @Test
    fun appActionsNeedAValidPackageForEveryBindingAndQuickButton() {
        HomeGestureIds.forEach { gesture ->
            assertThrows(IllegalArgumentException::class.java) {
                HomeLayout(gestures = HomeGestures().withBinding(gesture, "app")).validate()
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            HomeLayout(quickAction = "app").validate()
        }
        assertThrows(IllegalArgumentException::class.java) {
            HomeLayout(gestures = HomeGestures(quickPackage = "invalid/package")).validate()
        }
        HomeLayout(
                quickAction = "app",
                gestures = HomeGestures(quickPackage = "com.example.reader"),
            )
            .validate()
    }
}
