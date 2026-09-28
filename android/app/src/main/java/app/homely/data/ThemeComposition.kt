package app.homely.data

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class WidgetLayer(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "text", // "text", "clock", "date", "battery", "image"
    val x: Float = 0f,
    val y: Float = 0f,
    val text: String = "",
    val color: String = "#FFFFFF",
    val fontSize: Int = 14,
    val zIndex: Int = 0,
    val width: Float = 0.5f,
    val height: Float = 0.5f,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val imageAssetId: String? = null,
    val tapPackage: String? = null,
    val fontFamily: String = "sans",
    val fontId: String? = null,
    val opacity: Float = 1f,
    val weight: Int = 400,
    val alignment: String = "center",
    val progress: Float = .5f,
    val targetTime: Long = 0L,
    val shape: String = "rounded",
    val weather: WeatherOptions = WeatherOptions(),
    val clock: ClockOptions = ClockOptions(),
    val phoneNumber: String = "",
    val tapAction: String = "auto",
    val monthOffset: Int = 0,
    val imageScale: String = "crop",
    val imageZoom: Float = 1f,
    val imageOffsetX: Float = 0f,
    val imageOffsetY: Float = 0f,
    val dataSource: String = "auto",
    val numberFormat: String = "percent",
    val showValue: Boolean = true,
    val iconName: String = "auto",
    val elementBackground: String? = null,
    val elementRadius: Int = 8,
    val elementBorderColor: String = "#FFFFFF",
    val elementBorderWidth: Float = 0f,
    val elementPadding: Int = 0,
    val ringWidth: Float = 8f,
    val trackColor: String = "#FFFFFF",
    val trackOpacity: Float = .15f,
    val animation: String = "none",
    val animationDuration: Int = 220,
    val longPressAction: String = "none",
    val states: List<ElementState> = emptyList(),
    val touchZones: List<ElementTouchZone> = emptyList(),
    val components: List<WidgetLayer> = emptyList(),
    val rotation: Float = 0f,
)

@Serializable
data class WidgetSpec(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Meu widget",
    val background: String = "#171625",
    val radius: Int = 24,
    val height: Int = 150,
    val canvasWidth: Int = 320,
    val followThemePalette: Boolean = false,
    val layers: List<WidgetLayer> = emptyList(),
    val tapPackage: String? = null,

    // Legacy fields
    val kind: String = "custom",
    val text: String = "",
    val foreground: String = "#FFFFFF",
    val accent: String = "#AEA2FF",
    val fontSize: Int = 40,
    val imageAssetId: String? = null,
    val columns: Int = 4,
    val rows: Int = 2,
    val placement: String = "free",
    val gridColumns: Int = 4,
    val gridRows: Int = 4,
    val backgroundImageId: String? = null,
    val backgroundImageAssetId: String? = null,
    val backgroundScale: String = "crop",
    val backgroundZoom: Float = 1f,
    val backgroundOffsetX: Float = 0f,
    val backgroundOffsetY: Float = 0f,
    val backgroundOpacity: Float = 1f,
    val backgroundOverlay: Float = 0f,
) {
    fun validate(elementDepth: Int = 0): WidgetSpec {
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(id)) { "Identificador de widget inválido." }
        require(name.length in 1..60) { "Nome do widget muito longo." }
        require(Regex("#[0-9a-fA-F]{6}").matches(background)) { "Fundo inválido." }
        backgroundImageId?.let {
            require(Regex("[A-Za-z0-9_-]{1,80}").matches(it)) { "Imagem de fundo inválida." }
        }
        require(backgroundImageAssetId == null || backgroundImageId != null) {
            "Identificador da imagem de fundo ausente."
        }
        validateImageTransform(
            backgroundScale,
            backgroundZoom,
            backgroundOffsetX,
            backgroundOffsetY,
        )
        require(backgroundOpacity in 0f..1f && backgroundOverlay in 0f..1f) {
            "Opacidade do fundo inválida."
        }
        require(radius in 0..48 && height in 64..800 && canvasWidth in 64..800) {
            "Dimensões inválidas."
        }
        require(kind in listOf("custom", "clock", "date", "battery", "text", "image")) {
            "Tipo de widget inválido."
        }
        require(columns in listOf(2, 4) && rows in listOf(2, 4) && (columns == 4 || rows == 2))
        require(layers.size <= 16 && layers.map { it.id }.distinct().size == layers.size) {
            "Use até 16 camadas distintas."
        }
        require(
            Regex("#[0-9a-fA-F]{6}").matches(foreground) && Regex("#[0-9a-fA-F]{6}").matches(accent)
        )
        require(placement in listOf("grid", "free") && gridColumns in 2..12 && gridRows in 2..12)
        require(fontSize in 8..120 && text.length <= 300)
        layers.forEach { l ->
            require(Regex("[A-Za-z0-9_-]{1,80}").matches(l.id)) {
                "Identificador de camada inválido."
            }
            require(
                l.type in
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
                        "calendar",
                        "phone",
                        "element",
                        "number",
                        "icon",
                        "ring",
                        "bar",
                        "indicator",
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
            )
            require(l.x.isFinite() && l.y.isFinite() && l.width.isFinite() && l.height.isFinite())
            require(
                l.x >= 0 &&
                    l.y >= 0 &&
                    l.width in 0.05f..1f &&
                    l.height in 0.05f..1f &&
                    l.x + l.width <= 1.001f &&
                    l.y + l.height <= 1.001f
            ) {
                "Camada fora do canvas."
            }
            require(
                l.opacity in 0f..1f &&
                    l.progress in 0f..1f &&
                    l.weight in 100..900 &&
                    l.targetTime >= 0
            )
            require(
                l.tapAction in elementActions &&
                    l.phoneNumber.length <= 30 &&
                    l.monthOffset in -12..12
            )
            l.weather.validate()
            l.validateElement()
            val groups = listOf(l.components) + l.states.mapNotNull { it.components }
            require(l.rotation in -360f..360f)
            require(elementDepth == 0 || groups.all { it.isEmpty() }) {
                "Uma composição aceita camadas internas, sem composições aninhadas."
            }
            groups
                .filter { it.isNotEmpty() }
                .forEach { WidgetSpec(layers = it).validate(elementDepth + 1) }
            l.clock.validate()
            validateImageTransform(l.imageScale, l.imageZoom, l.imageOffsetX, l.imageOffsetY)
            require(l.alignment in listOf("start", "center", "end"))
            require(l.fontSize in 8..120 && l.text.length <= 300 && l.zIndex in 0..31)
            require(Regex("#[0-9a-fA-F]{6}").matches(l.color)) { "Use cor HEX com 6 dígitos." }
            require(l.fontFamily in listOf("sans", "serif", "mono", "display", "hand"))
            l.tapPackage?.let {
                require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(it))
            }
        }
        return this
    }
}

private fun validateImageTransform(scale: String, zoom: Float, offsetX: Float, offsetY: Float) {
    require(
        scale in listOf("crop", "fit") && zoom in 1f..4f && offsetX in -1f..1f && offsetY in -1f..1f
    ) {
        "Enquadramento de imagem inválido."
    }
}

/** Media keys retained when a widget is saved independently from its theme. */
fun WidgetSpec.imageKeys(): Set<String> =
    displayLayers().flatMap { it.elementImageKeys() }.toSet() + listOfNotNull(backgroundImageId)

@Serializable
data class StatusBarSpec(
    val show: Boolean = true,
    val batteryIcon: String = "ios",
    val networkIcon: String = "ios",
    val clockFormat: String = "24h",
    val tint: String = "auto",
    val background: String = "transparent",
    val backgroundOpacity: Float = 1f,
)

@Serializable
data class HomeLayout(
    val columns: Int = 4,
    val rows: Int = 5,
    val iconSize: Int = 54,
    val iconPadding: Int = 12,
    val showLabels: Boolean = true,
    val drawerStyle: String = "gesture",
    val drawerLayout: String = "grid",
    val folderStyle: String = "window",
    val appAnimation: String = "zoom",
    val scrollDirection: String = "horizontal",
    val statusBar: StatusBarSpec = StatusBarSpec(),
    val gestures: HomeGestures = HomeGestures(),
    val kit: List<HomeItem> = emptyList(),
    val dock: List<String> =
        listOf(
            "com.samsung.android.dialer",
            "com.samsung.android.messaging",
            "com.android.chrome",
            "com.sec.android.app.camera",
        ),
    val showSearchBar: Boolean = true,
    val drawerColumns: Int = 4,
    val quickAction: String = "none",
    val drawerRows: Int = 5,
    val drawerSpacing: Int = 12,
    val drawerMargin: Int = 16,
    val drawerIconSize: Int = 54,
    val drawerMode: String = "continuous",
    val drawerDirection: String = "horizontal",
    val drawerAnimation: String = "slide",
    val animationDuration: Int = 280,
    val reduceMotion: Boolean = false,
    val drawerOpacity: Float = .94f,
    val drawerBlur: Int = 0,
    val drawerRadius: Int = 24,
    val drawerShowSearch: Boolean = true,
    val hapticFeedback: Boolean = true,
    val showDrawerButton: Boolean = false,
    val showDrawerHeader: Boolean = false,
    val showDrawerCloseButton: Boolean = false,
    val showPageIndicators: Boolean = true,
    val showNavigationHandle: Boolean = true,
    val immersiveHome: Boolean = false,
    val systemBarIcons: String = "light",
)

@Serializable
data class LocalTheme(val id: String, val title: String, val savedAt: Long, val draft: Draft)

@Serializable data class UsernameAvailability(val username: String, val available: Boolean)

/** Legacy single-component widgets are adapted to the same renderer. */
fun WidgetSpec.displayLayers(): List<WidgetLayer> =
    if (layers.isNotEmpty() || kind == "custom") layers
    else
        listOf(
            WidgetLayer(
                id = id,
                type = if (kind == "custom") "text" else kind,
                x = 0f,
                y = 0f,
                width = 1f,
                height = 1f,
                text = text,
                color = foreground,
                fontSize = fontSize,
                imageAssetId = imageAssetId,
                tapPackage = tapPackage,
            )
        )

fun IconEditor.validate() {
    require(stateAssetIds.keys.all { it in listOf("default", "pressed", "disabled") })
    require(stateAssetIds.values.all { runCatching { java.util.UUID.fromString(it) }.isSuccess })
    require(fontFamily in listOf("sans", "serif", "mono", "display", "hand"))
    require(
        scale in .2f..1.5f &&
            shadowRadius in 0f..20f &&
            strokeWidth in 0f..10f &&
            rotation in 0f..360f
    )
    require(
        mask in listOf("circle", "rounded", "squircle", "teardrop", "hexagon", "pentagon", "none")
    )
    require(
        offsetX in -54f..54f &&
            offsetY in -54f..54f &&
            gridColumns in 2..12 &&
            placement in listOf("grid", "free") &&
            text.length <= 40 &&
            textSize in 8..40
    )
    listOfNotNull(background, shadowColor, strokeColor, textColor).forEach {
        require(Regex("#[0-9a-fA-F]{6}").matches(it))
    }
}

fun Manifest.validateComposition() {
    listOf(palette.accent, palette.secondary, palette.background, palette.surface, palette.text)
        .forEach { require(Regex("#[0-9a-fA-F]{6}").matches(it)) { "Cor inválida." } }
    require(fontFamily in listOf("sans", "serif", "mono", "display", "hand"))
    layout.validate()
    require(widgets.size <= 12 && widgets.map { it.id }.distinct().size == widgets.size)
    widgets.forEach { it.validate() }
    require(fonts.size <= 20 && fonts.map { it.id }.distinct().size == fonts.size)
    fonts.forEach { require(Regex("[A-Za-z0-9_-]{1,80}").matches(it.id)) }
    require(layout.drawerColumns in 3..8 && layout.kit.size <= 120)
    require(layout.kit.map { it.id }.distinct().size == layout.kit.size)
    layout.kit.forEach {
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(it.id))
        require(
            it.kind in listOf("icon", "widget") &&
                it.page in 0..20 &&
                it.x in 0f..1f &&
                it.y in 0f..1f &&
                it.width in .05f..1f &&
                it.height in .05f..1f &&
                it.x + it.width <= 1.001f &&
                it.y + it.height <= 1.001f
        )
        if (it.kind == "widget") require(widgets.any { w -> w.id == it.reference })
        else
            require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(it.reference))
    }
    widgets
        .flatMap { it.layers }
        .flatMap { it.allLayers() }
        .mapNotNull { it.fontId }
        .forEach { require(fonts.any { f -> f.id == it }) }
    assets.mapNotNull { it.iconEditor?.fontId }.forEach { require(fonts.any { f -> f.id == it }) }
    fonts.forEach { require(it.name.length in 1..60) }
    assets
        .filter { it.kind == "font" }
        .forEach {
            require(it.mime in listOf("font/ttf", "font/otf") && it.size <= 5 * 1024 * 1024)
        }

    assets.mapNotNull { it.iconEditor }.forEach { it.validate() }
    assets.forEach { a ->
        a.iconEditor?.stateAssetIds?.values?.forEach { id ->
            require(
                assets.any {
                    it.id == id && it.kind == "icon_source" && it.packageName == a.packageName
                }
            ) {
                "Imagem do estado do ícone ausente."
            }
        }
    }
}

@Serializable
data class ThemeFont(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Minha fonte",
    val assetId: String? = null,
)

@Serializable
data class HomeGestures(
    val longPress: String = "personalize",
    val iconLongPress: String = "menu",
    val swipeUp: String = "drawer",
    val swipeDown: String = "search",
    val doubleTap: String = "none",
    val quickPackage: String? = null,
    val swipeLeft: String = "next_page",
    val swipeRight: String = "previous_page",
    val tap: String = "none",
    val pinchIn: String = "none",
    val pinchOut: String = "none",
    val drawerSwipeDown: String = "close_drawer",
)

@Serializable
data class HomeItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: String = "icon",
    val reference: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val page: Int = 0,
    val width: Float = .2f,
    val height: Float = .13f,
)

@Serializable
data class WeatherOptions(
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val unit: String = "c",
    val style: String = "editorial",
    val showCity: Boolean = true,
    val showCondition: Boolean = true,
    val showRange: Boolean = true,
    val showHumidity: Boolean = false,
    val showWind: Boolean = false,
    val showIcon: Boolean = true,
) {
    fun validate() {
        require(
            city.length <= 120 &&
                unit in listOf("c", "f") &&
                style in listOf("editorial", "compact", "forecast")
        )
        require((latitude == null) == (longitude == null))
        latitude?.let { require(it in -90.0..90.0) }
        longitude?.let { require(it in -180.0..180.0) }
    }
}

@Serializable
data class ClockOptions(
    val format: String = "24",
    val seconds: Boolean = false,
    val zone: String = "",
    val dateStyle: String = "full",
    val marks: Boolean = true,
) {
    fun validate() {
        require(
            format in listOf("24", "12") &&
                dateStyle in listOf("full", "short", "weekday") &&
                (zone.isEmpty() || zone in java.util.TimeZone.getAvailableIDs())
        )
    }
}
