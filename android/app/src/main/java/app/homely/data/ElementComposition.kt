package app.homely.data

import java.util.UUID

fun WidgetLayer.allLayers(): List<WidgetLayer> =
    listOf(this) +
        (components + states.flatMap { it.components.orEmpty() }).flatMap { it.allLayers() }

fun WidgetLayer.mapTree(transform: (WidgetLayer) -> WidgetLayer): WidgetLayer =
    transform(
        copy(
            components = components.map { it.mapTree(transform) },
            states =
                states.map {
                    it.copy(components = it.components?.map { child -> child.mapTree(transform) })
                },
        )
    )

suspend fun WidgetLayer.mapTreeAsync(transform: suspend (WidgetLayer) -> WidgetLayer): WidgetLayer =
    transform(
        copy(
            components = components.map { it.mapTreeAsync(transform) },
            states =
                states.map {
                    it.copy(
                        components = it.components?.map { child -> child.mapTreeAsync(transform) }
                    )
                },
        )
    )

/** Copy media references to new keys so editing one reused composition cannot change another. */
fun cloneElement(
    layer: WidgetLayer,
    images: Map<String, String>,
): Pair<WidgetLayer, Map<String, String>> {
    val keys = layer.elementImageKeys().associateWith { UUID.randomUUID().toString() }
    val clone = layer.mapTree { l ->
        l.copy(
            id = keys[l.id] ?: UUID.randomUUID().toString(),
            imageAssetId = null,
            states =
                l.states.map { s ->
                    s.copy(
                        id = UUID.randomUUID().toString(),
                        imageId = s.imageId?.let(keys::getValue),
                        imageAssetId = null,
                    )
                },
            touchZones = l.touchZones.map { it.copy(id = UUID.randomUUID().toString()) },
        )
    }
    return clone to keys.mapNotNull { (old, new) -> images[old]?.let { new to it } }.toMap()
}
