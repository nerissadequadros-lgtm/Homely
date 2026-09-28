package app.homely.platform

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import app.homely.data.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString

class LocalThemeStore(private val context: Context) {
    private val repo = HomelyRepository(context)
    private val prefs = context.getSharedPreferences("private-themes", Context.MODE_PRIVATE)

    fun list(): List<LocalTheme> = runCatching {
        repo.json.decodeFromString<List<LocalTheme>>(prefs.getString("items", "[]")!!)
    }
        .getOrDefault(emptyList())

    fun stage(theme: LocalTheme) {
        check(prefs.edit().putString("pending", theme.id).commit())
    }

    fun clearPending() {
        prefs.edit().remove("pending").apply()
    }

    fun pending(): LocalTheme? = list().firstOrNull { it.id == prefs.getString("pending", null) }

    fun active(): LocalTheme? = list().firstOrNull { it.id == prefs.getString("active", null) }

    fun normalized(draft: Draft): Draft {
        draft.manifest.validateComposition()
        draft.manifest.widgets
            .flatMap { it.imageKeys() }
            .forEach {
                require(draft.widgetImages[it]?.let { path -> File(path).isFile } == true) {
                    "Selecione as imagens das camadas e do fundo antes de salvar."
                }
            }
        draft.manifest.fonts.forEach {
            require(draft.fontFiles[it.id]?.let { p -> File(p).isFile } == true) {
                "Arquivo de fonte ausente."
            }
        }
        draft.icons.forEach { icon ->
            require(icon.statePaths.keys.all { it in listOf("default", "pressed", "disabled") }) {
                "Estado de ícone inválido."
            }
            require(icon.statePaths.values.all { File(it).isFile }) {
                "Imagem do estado do ícone ausente."
            }
        }
        val ready =
            draft.copy(
                icons = draft.icons.map { IconRenderer.materialize(context, it, draft.fontFiles) }
            )
        if (draft.wallpaperPath != null) return ready
        val file =
            File(
                context.filesDir,
                "solid-" + draft.manifest.palette.background.removePrefix("#") + ".png",
            )
        if (!file.exists()) {
            val bitmap = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.parseColor(draft.manifest.palette.background))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return ready.copy(wallpaperPath = file.path)
    }

    suspend fun save(input: Draft): LocalTheme =
        withContext(Dispatchers.IO) {
            val draft = normalized(input)
            require(draft.manifest.widgets.size <= 12) { "Use até 12 widgets." }
            draft.manifest.widgets.forEach { it.validate() }
            val id = UUID.randomUUID().toString()
            val dir = File(context.filesDir, "private-themes/" + id).apply { mkdirs() }
            try {
                fun copy(path: String): String {
                    val source = File(path)
                    require(source.isFile)
                    return source
                        .copyTo(File(dir, UUID.randomUUID().toString() + "." + source.extension))
                        .path
                }
                val stable =
                    draft.copy(
                        wallpaperPath = draft.wallpaperPath?.let { copy(it) },
                        videoPath = draft.videoPath?.let { copy(it) },
                        icons =
                            draft.icons.map {
                                it.copy(
                                    path = copy(it.path),
                                    sourcePath = it.sourcePath?.let { p -> copy(p) },
                                    statePaths =
                                        it.statePaths.mapValues { (_, path) -> copy(path) },
                                )
                            },
                        screenshotPaths = draft.screenshotPaths.map { copy(it) },
                        widgetImages = draft.widgetImages.mapValues { copy(it.value) },
                        fontFiles = draft.fontFiles.mapValues { copy(it.value) },
                    )
                val theme =
                    LocalTheme(
                        id,
                        draft.manifest.title.ifBlank { "Meu tema" },
                        System.currentTimeMillis(),
                        stable,
                    )
                check(
                    prefs
                        .edit()
                        .putString("items", repo.json.encodeToString(listOf(theme) + list()))
                        .commit()
                ) {
                    "Não foi possível salvar."
                }
                theme
            } catch (e: Exception) {
                dir.deleteRecursively()
                throw e
            }
        }

    suspend fun download(theme: ThemePost): LocalTheme =
        withContext(Dispatchers.IO) {
            val zip = File(context.cacheDir, "theme-" + UUID.randomUUID() + ".zip")
            val dir = File(context.cacheDir, "theme-" + UUID.randomUUID())
            try {
                repo.download("/v1/themes/" + theme.id + "/download", zip)
                val draft = zip.inputStream().use { ThemeArchive.import(it, dir, repo.json) }
                save(draft)
            } finally {
                zip.delete()
                dir.deleteRecursively()
            }
        }

    suspend fun activate(theme: LocalTheme) =
        withContext(Dispatchers.IO) {
            if (active()?.id == theme.id) {
                clearPending()
                return@withContext
            }
            theme.draft.wallpaperPath?.let { path ->
                File(path).inputStream().use {
                    WallpaperManager.getInstance(context)
                        .setStream(it, null, true, WallpaperManager.FLAG_SYSTEM)
                }
            }
            check(prefs.edit().putString("active", theme.id).remove("pending").commit())
        }

    fun remove(id: String) {
        require(id != prefs.getString("active", null)) {
            "Aplique outro tema antes de remover o atual."
        }
        val target = list().firstOrNull { it.id == id } ?: return
        check(
            prefs
                .edit()
                .putString("items", repo.json.encodeToString(list().filterNot { it.id == id }))
                .commit()
        )
        val dir = File(context.filesDir, "private-themes/" + target.id)
        if (
            dir.canonicalPath.startsWith(
                File(context.filesDir, "private-themes").canonicalPath + File.separator
            )
        )
            dir.deleteRecursively()
    }
}
