package app.homely.data

/** Opt-in palette binding is flattened into the saved spec for every renderer. */
fun WidgetSpec.withPalette(palette: Palette): WidgetSpec {
    if (!followThemePalette) return this
    return copy(
        background = palette.surface,
        foreground = palette.text,
        accent = palette.accent,
        layers =
            layers.map { root ->
                root.mapTree { layer ->
                    layer.copy(
                        color =
                            if (
                                layer.type in
                                    listOf(
                                        "bar",
                                        "ring",
                                        "progress",
                                        "battery_ring",
                                        "indicator",
                                        "shape",
                                    )
                            )
                                palette.accent
                            else palette.text,
                        elementBackground = layer.elementBackground?.let { palette.surface },
                        elementBorderColor = palette.secondary,
                        trackColor = palette.secondary,
                        states =
                            layer.states.map {
                                it.copy(
                                    color = it.color?.let { palette.accent },
                                    background = it.background?.let { palette.surface },
                                )
                            },
                    )
                }
            },
    )
}
