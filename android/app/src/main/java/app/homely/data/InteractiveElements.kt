package app.homely.data

import java.util.UUID
import kotlinx.serialization.Serializable

val elementSources =
    listOf(
        "auto",
        "none",
        "battery",
        "ram",
        "storage",
        "wifi",
        "bluetooth",
        "lan",
        "flashlight",
        "volume",
        "signal",
        "cpu",
    )
val elementActions =
    listOf(
        "auto",
        "none",
        "app",
        "toggle_state",
        "animate",
        "wifi",
        "bluetooth",
        "flashlight",
        "volume",
        "volume_up",
        "volume_down",
        "settings",
        "alarm",
        "calendar",
        "storage",
    )
val elementAnimations = listOf("none", "fade", "scale", "slide", "rotate")

@Serializable
data class ElementState(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Novo estado",
    val condition: String = "always",
    val threshold: Float = 20f,
    val color: String? = null,
    val background: String? = null,
    val text: String? = null,
    val imageId: String? = null,
    val imageAssetId: String? = null,
    val opacity: Float = 1f,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val action: String = "inherit",
    val animation: String = "inherit",
    val components: List<WidgetLayer>? = null,
)

@Serializable
data class ElementTouchZone(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Área de toque",
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = .5f,
    val height: Float = 1f,
    val action: String = "none",
)

data class ElementReading(
    val value: Float? = null,
    val status: String = "unavailable",
    val label: String = "Indisponível",
)

fun WidgetLayer.source(): String =
    if (dataSource != "auto") dataSource
    else
        when (type) {
            "battery",
            "battery_ring" -> "battery"
            "ram",
            "storage",
            "wifi",
            "bluetooth",
            "lan",
            "flashlight",
            "volume",
            "signal",
            "cpu" -> type
            else -> "none"
        }

fun WidgetLayer.activeState(
    reading: ElementReading,
    pressed: Boolean,
    toggled: Boolean,
    touched: Boolean = false,
): ElementState? {
    fun matches(state: ElementState) =
        when (state.condition) {
            "pressed" -> pressed
            "touched" -> touched
            "toggled" -> toggled
            "low" -> reading.value?.let { it < state.threshold } == true
            "high" -> reading.value?.let { it >= state.threshold } == true
            "always" -> true
            "enabled" -> reading.status in listOf("enabled", "connected", "disconnected")
            else -> reading.status == state.condition
        }
    return states.firstOrNull { it.condition == "pressed" && matches(it) }
        ?: states.firstOrNull { it.condition == "touched" && touched }
        ?: states.firstOrNull { it.condition == "toggled" && matches(it) }
        ?: states.firstOrNull { it.condition in listOf("connected", "disconnected") && matches(it) }
        ?: states.firstOrNull {
            it.condition !in listOf("always", "pressed", "toggled", "touched") && matches(it)
        }
        ?: states.firstOrNull { it.condition == "always" }
}

fun WidgetLayer.elementImageKeys(): Set<String> =
    (if (type == "image") setOf(id) else emptySet()) +
        states.mapNotNull { it.imageId } +
        (components + states.flatMap { it.components.orEmpty() }).flatMap { it.elementImageKeys() }

fun WidgetLayer.interactive(): Boolean =
    components.isNotEmpty() ||
        type == "element" ||
        rotation != 0f ||
        states.isNotEmpty() ||
        touchZones.isNotEmpty() ||
        source() != "none" ||
        type in listOf("number", "icon", "ring", "bar", "indicator") ||
        longPressAction != "none" ||
        tapAction !in listOf("auto", "none", "app") ||
        elementBackground != null ||
        elementBorderWidth > 0 ||
        elementPadding > 0 ||
        animation != "none"

fun WidgetLayer.validateElement() {
    fun hex(v: String) = Regex("#[0-9a-fA-F]{6}").matches(v)
    require(
        dataSource in elementSources &&
            tapAction in elementActions &&
            longPressAction in elementActions
    )
    require(animation in elementAnimations && animationDuration in 0..1000)
    require(
        elementRadius in 0..100 &&
            elementPadding in 0..48 &&
            elementBorderWidth in 0f..12f &&
            ringWidth in 1f..32f &&
            trackOpacity in 0f..1f
    )
    require(elementBackground == null || hex(elementBackground))
    require(hex(elementBorderColor) && hex(trackColor))
    require(numberFormat in listOf("number", "percent") && iconName.length <= 16)
    require(states.size <= 12 && states.map { it.id }.distinct().size == states.size)
    states.forEach { s ->
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(s.id) && s.name.length in 1..60)
        require(
            s.condition in
                listOf(
                    "always",
                    "pressed",
                    "touched",
                    "toggled",
                    "low",
                    "high",
                    "enabled",
                    "disabled",
                    "connected",
                    "disconnected",
                    "unavailable",
                )
        )
        require(
            s.threshold in 0f..100f &&
                s.opacity in 0f..1f &&
                s.scale in .5f..1.5f &&
                s.rotation in -360f..360f
        )
        require(s.color == null || hex(s.color))
        require(s.background == null || hex(s.background))
        require(s.text == null || s.text.length <= 300)
        require(s.action == "inherit" || s.action in elementActions)
        require(s.animation == "inherit" || s.animation in elementAnimations)
        s.imageId?.let { require(Regex("[A-Za-z0-9_-]{1,80}").matches(it)) }
        require(s.imageAssetId == null || s.imageId != null)
    }
    require(touchZones.size <= 8 && touchZones.map { it.id }.distinct().size == touchZones.size)
    touchZones.forEach {
        require(
            Regex("[A-Za-z0-9_-]{1,80}").matches(it.id) &&
                it.action in elementActions &&
                it.name.length <= 60 &&
                it.x >= 0 &&
                it.y >= 0 &&
                it.width >= .05f &&
                it.height >= .05f &&
                it.x + it.width <= 1.001f &&
                it.y + it.height <= 1.001f
        )
    }
}
