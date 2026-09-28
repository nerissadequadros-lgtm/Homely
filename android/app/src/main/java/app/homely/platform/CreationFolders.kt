package app.homely.platform

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CreationEntry(val kind: String, val folder: String, val name: String, val token: String)

/** Folder membership is separate from owned media, so renaming folders cannot break assets. */
object CreationFolders {
    private val json = Json { ignoreUnknownKeys = true }

    fun entries(ctx: Context, kind: String): List<CreationEntry> = runCatching {
        json
            .decodeFromString<List<CreationEntry>>(
                ctx.getSharedPreferences("creation-folders", 0).getString("entries", "[]")!!
            )
            .filter { it.kind == kind }
    }
        .getOrDefault(emptyList())

    fun folders(ctx: Context, kind: String): List<String> =
        (listOf("Minhas criações") + entries(ctx, kind).map { it.folder }).distinct().sorted()

    @Synchronized
    fun register(ctx: Context, kind: String, folder: String, name: String, token: String) {
        require(kind in listOf("widget", "element", "icon", "theme"))
        require(folder.trim().length in 1..60 && name.trim().length in 1..60 && token.isNotBlank())
        val prefs = ctx.getSharedPreferences("creation-folders", 0)
        val current = runCatching {
            json.decodeFromString<List<CreationEntry>>(prefs.getString("entries", "[]")!!)
        }.getOrDefault(emptyList())
        val entry = CreationEntry(kind, folder.trim(), name.trim(), token)
        check(
            prefs
                .edit()
                .putString(
                    "entries",
                    json.encodeToString(
                        current.filterNot { it.kind == kind && it.token == token } + entry
                    ),
                )
                .commit()
        )
    }
}
