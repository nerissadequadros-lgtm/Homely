package app.homely.ui

import app.homely.data.*

/** Editable composition inspired by the reference, using actual device readings. */
internal fun pinkControlWidget(): WidgetSpec {
    fun card(
        type: String,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        source: String = "auto",
        action: String = "auto",
    ) =
        WidgetLayer(
            type = type,
            x = x,
            y = y,
            width = w,
            height = h,
            dataSource = source,
            tapAction = action,
            color = "#9E526B",
            elementBackground = "#FCE5ED",
            elementBorderColor = "#B96C85",
            elementBorderWidth = 1f,
            elementRadius = 18,
            elementPadding = 6,
            fontSize = 18,
            weight = 600,
            animation = "scale",
        )
    fun connection(type: String, x: Float, action: String) =
        card(type, x, .63f, .15f, .32f, action = action)
            .copy(
                states =
                    listOf(
                        ElementState(
                            name = "Ativo",
                            condition = "enabled",
                            background = "#E995AF",
                            color = "#FFFFFF",
                        ),
                        ElementState(
                            name = "Indisponível",
                            condition = "unavailable",
                            opacity = .5f,
                        ),
                    )
            )
    return WidgetSpec(
        name = "Painel rosa",
        background = "#FFF5F8",
        height = 190,
        radius = 24,
        layers =
            listOf(
                card("ring", .025f, .045f, .28f, .55f, "battery")
                    .copy(
                        color = "#EBD862",
                        elementBackground = "#E995AF",
                        ringWidth = 5f,
                        fontSize = 17,
                        trackOpacity = .4f,
                        states =
                            listOf(
                                ElementState(
                                    name = "Bateria baixa",
                                    condition = "low",
                                    threshold = 20f,
                                    color = "#F46B71",
                                )
                            ),
                    ),
                card("clock", .325f, .045f, .47f, .55f, action = "alarm").copy(fontSize = 30),
                card("storage", .815f, .045f, .16f, .55f, action = "storage").copy(fontSize = 15),
                card("bar", .025f, .63f, .28f, .32f, "ram", "settings")
                    .copy(
                        color = "#E995AF",
                        showValue = false,
                        trackColor = "#FCE5ED",
                        trackOpacity = 1f,
                    ),
                WidgetLayer(
                    type = "ram",
                    x = .045f,
                    y = .68f,
                    width = .24f,
                    height = .21f,
                    fontSize = 13,
                    color = "#9E526B",
                    zIndex = 1,
                    tapAction = "settings",
                ),
                connection("lan", .325f, "settings"),
                connection("flashlight", .492f, "flashlight"),
                connection("bluetooth", .658f, "bluetooth"),
                connection("wifi", .825f, "wifi"),
            ),
    )
}
