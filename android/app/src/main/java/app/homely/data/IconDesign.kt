package app.homely.data

import kotlinx.serialization.Serializable

@Serializable
data class IconArtworkLayer(
    val kind: String = "shape",
    val shape: String = "circle",
    val imagePath: String? = null,
    val tintImage: Boolean = false,
    val color: String = "#FFFFFF",
    val gradientColor: String? = null,
    val gradientAngle: Float = 45f,
    val opacity: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = .58f,
    val rotation: Float = 0f,
)

@Serializable
data class IconAppearance(
    val background: IconArtworkLayer =
        IconArtworkLayer(shape = "square", color = "#A99CFF", scale = 1f),
    val foreground: IconArtworkLayer = IconArtworkLayer(),
)

@Serializable
data class IconDesign(
    val name: String = "Meu ícone",
    val packageName: String = "",
    val mask: String = "squircle",
    val gridColumns: Int = 6,
    val snapToGrid: Boolean = true,
    val states: Map<String, IconAppearance> = mapOf("default" to IconAppearance()),
) {
    fun appearance(state: String) = states[state] ?: states["default"] ?: IconAppearance()

    fun withAppearance(state: String, appearance: IconAppearance) =
        copy(states = states + (state to appearance))

    fun validate() {
        require(mask in setOf("circle", "square", "squircle"))
        require(
            gridColumns in 2..12 &&
                states.keys.all { it in setOf("default", "pressed", "disabled") }
        )
        require(states.containsKey("default"))
        require(name.length <= 80 && packageName.length <= 255)
        states.values
            .flatMap { listOf(it.background, it.foreground) }
            .forEach { layer ->
                require(layer.kind in setOf("shape", "image"))
                require(
                    layer.shape in
                        setOf("square", "circle", "rounded", "triangle", "star", "diamond", "heart")
                )
                require(layer.scale in .1f..1.5f && layer.opacity in 0f..1f)
                require(
                    layer.offsetX in -54f..54f &&
                        layer.offsetY in -54f..54f &&
                        layer.rotation in 0f..360f
                )
                require(layer.gradientAngle in 0f..360f)
                require(Regex("#[A-Fa-f0-9]{6}([A-Fa-f0-9]{2})?").matches(layer.color))
                require(
                    layer.gradientColor == null ||
                        Regex("#[A-Fa-f0-9]{6}([A-Fa-f0-9]{2})?").matches(layer.gradientColor)
                )
                require(layer.kind != "image" || !layer.imagePath.isNullOrBlank())
            }
    }
}
