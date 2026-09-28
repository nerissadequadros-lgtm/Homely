package app.homely.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class InteractiveElementsTest {
    @Test
    fun statePriorityAndThresholdBoundaries() {
        val layer =
            WidgetLayer(
                states =
                    listOf(
                        ElementState(id = "base", condition = "always"),
                        ElementState(id = "low", condition = "low", threshold = 20f),
                        ElementState(id = "high", condition = "high", threshold = 20f),
                        ElementState(id = "toggle", condition = "toggled"),
                        ElementState(id = "press", condition = "pressed"),
                    )
            )
        assertEquals("low", layer.activeState(ElementReading(19f), false, false)?.id)
        assertEquals("high", layer.activeState(ElementReading(20f), false, false)?.id)
        assertEquals("base", layer.activeState(ElementReading(), false, false)?.id)
        assertEquals("toggle", layer.activeState(ElementReading(10f), false, true)?.id)
        assertEquals("press", layer.activeState(ElementReading(10f), true, true)?.id)
    }

    @Test
    fun enabledIncludesConnectedDevicesButNeverUnavailable() {
        val layer = WidgetLayer(type = "wifi", states = listOf(ElementState(condition = "enabled")))
        assertNotNull(layer.activeState(ElementReading(status = "connected"), false, false))
        assertNotNull(layer.activeState(ElementReading(status = "disconnected"), false, false))
        assertNull(layer.activeState(ElementReading(status = "disabled"), false, false))
        assertNull(layer.activeState(ElementReading(), false, false))
    }

    @Test
    fun rejectsOutOfBoundsZonesAndDuplicateStates() {
        listOf(
                WidgetLayer(touchZones = listOf(ElementTouchZone(x = .8f, width = .5f))),
                WidgetLayer(states = listOf(ElementState(id = "same"), ElementState(id = "same"))),
                WidgetLayer(ringWidth = 0f),
                WidgetLayer(elementPadding = 49),
                WidgetLayer(states = listOf(ElementState(scale = Float.NaN))),
            )
            .forEach {
                assertTrue(runCatching { WidgetSpec(layers = listOf(it)).validate() }.isFailure)
            }
    }

    @Test
    fun configurationSurvivesSerializationWithoutSharingMutableState() {
        val layer =
            WidgetLayer(
                type = "ring",
                dataSource = "battery",
                ringWidth = 4f,
                elementPadding = 3,
                tapAction = "toggle_state",
                states =
                    listOf(
                        ElementState(
                            condition = "low",
                            threshold = 25f,
                            imageId = "low-image",
                            color = "#FF0000",
                        )
                    ),
                touchZones = listOf(ElementTouchZone(action = "settings")),
            )
        val original =
            WidgetSpec(
                backgroundImageId = "background",
                backgroundZoom = 2f,
                layers = listOf(layer),
            )
        val restored = Json.decodeFromString<WidgetSpec>(Json.encodeToString(original)).validate()
        assertEquals(original, restored)
        assertEquals(setOf("low-image", "background"), restored.imageKeys())
        assertEquals("#FF0000", layer.states.first().color)
    }

    @Test
    fun cropAndFitKeepEdgesAndZoomConsistent() {
        val crop = widgetImageFrame(400f, 200f, 100f, 100f)
        assertEquals(WidgetImageFrame(-50f, 0f, 200f, 100f), crop)
        assertEquals(0f, widgetImageFrame(400f, 200f, 100f, 100f, offsetX = 1f).left, .001f)
        assertEquals(-100f, widgetImageFrame(400f, 200f, 100f, 100f, offsetX = -1f).left, .001f)
        assertEquals(
            WidgetImageFrame(0f, 25f, 100f, 50f),
            widgetImageFrame(400f, 200f, 100f, 100f, "fit"),
        )
        assertEquals(400f, widgetImageFrame(400f, 200f, 100f, 100f, zoom = 2f).width, .001f)
    }
}
