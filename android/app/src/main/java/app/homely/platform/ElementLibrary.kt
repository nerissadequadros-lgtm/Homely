package app.homely.platform

import android.content.Context
import app.homely.data.*

object ElementLibrary {
    fun list(ctx: Context): List<Pair<String, SavedWidget>> =
        ctx.getSharedPreferences("element-library", 0)
            .getStringSet("tokens", emptySet())
            .orEmpty()
            .mapNotNull { token -> StudioWidgets.read(ctx, token)?.let { token to it } }

    fun save(
        ctx: Context,
        name: String,
        layer: WidgetLayer,
        images: Map<String, String>,
        fonts: Map<String, String>,
    ): String {
        val token =
            StudioWidgets.save(
                ctx,
                WidgetSpec(name = name.take(60).ifBlank { "Meu elemento" }, layers = listOf(layer)),
                images,
                fonts.filterKeys { id -> layer.allLayers().any { it.fontId == id } },
            )
        val prefs = ctx.getSharedPreferences("element-library", 0)
        check(
            prefs
                .edit()
                .putStringSet("tokens", prefs.getStringSet("tokens", emptySet()).orEmpty() + token)
                .commit()
        )
        return token
    }
}
