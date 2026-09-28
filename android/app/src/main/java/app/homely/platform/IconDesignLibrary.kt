package app.homely.platform

import android.content.Context
import android.graphics.Bitmap
import app.homely.data.*
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class SavedIconDesign(
    val token: String,
    val name: String,
    val design: IconDesign,
    val renderedStates: Map<String, String>,
) {
    val previewPath: String
        get() = renderedStates.getValue("default")

    fun toDraftIcon(packageName: String = design.packageName, label: String = name) =
        DraftIcon(
            path = previewPath,
            packageName = packageName,
            label = label,
            sourcePath = previewPath,
            mask = "none",
            rendered = true,
            statePaths = renderedStates,
        )
}

object IconDesignLibrary {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("icon-design-library", 0)

    fun list(ctx: Context): List<SavedIconDesign> =
        prefs(ctx)
            .all
            .values
            .filterIsInstance<String>()
            .mapNotNull {
                runCatching { HomelyRepository(ctx).json.decodeFromString<SavedIconDesign>(it) }
                    .getOrNull()
            }
            .filter { File(it.previewPath).isFile }
            .sortedBy { it.name.lowercase() }

    fun save(ctx: Context, design: IconDesign, name: String): SavedIconDesign {
        design.validate()
        val token = UUID.randomUUID().toString()
        val directory = File(ctx.filesDir, "icon-library/$token").apply { check(mkdirs()) }
        val copies = mutableMapOf<String, String>()
        fun copy(layer: IconArtworkLayer): IconArtworkLayer =
            layer.copy(
                imagePath =
                    layer.imagePath?.let { path ->
                        copies.getOrPut(path) {
                            File(path).copyTo(File(directory, "source-${copies.size}.png")).path
                        }
                    }
            )
        val savedDesign =
            design.copy(
                name = name.take(80),
                states =
                    design.states.mapValues { (_, appearance) ->
                        appearance.copy(
                            background = copy(appearance.background),
                            foreground = copy(appearance.foreground),
                        )
                    },
            )
        val renders =
            listOf("default", "pressed", "disabled").associateWith { state ->
                val dest = File(directory, "$state.png")
                val bitmap = IconDesignRenderer.render(savedDesign, state, 512)
                dest.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                dest.path
            }
        val saved = SavedIconDesign(token, name.take(80), savedDesign, renders)
        check(
            prefs(ctx)
                .edit()
                .putString(token, HomelyRepository(ctx).json.encodeToString(saved))
                .commit()
        )
        return saved
    }
}
