package app.homely.platform

import app.homely.data.*
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ThemeArchive {
    private const val MAX_FILE = 50L * 1024 * 1024
    private const val MAX_TOTAL = 250L * 1024 * 1024

    fun sha(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun export(draft: Draft, output: OutputStream, json: Json) {
        draft.manifest.validateComposition()
        require(draft.wallpaperPath != null) { "Adicione um wallpaper antes de exportar." }
        val files = mutableListOf<Pair<File, Asset>>()
        fun add(path: String, kind: String, name: String, pkg: String? = null): String {
            val f = File(path)
            require(f.length() in 1..MAX_FILE) { "Arquivo inválido." }
            val id = UUID.randomUUID().toString()
            val mime =
                when (f.extension) {
                    "ttf" -> "font/ttf"
                    "otf" -> "font/otf"
                    "mp4" -> "video/mp4"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    else -> "image/jpeg"
                }
            files +=
                f to
                    Asset(
                        id,
                        kind,
                        name,
                        "assets/" + id + "." + f.extension,
                        mime,
                        sha(f),
                        f.length(),
                        pkg,
                        if (kind == "icon") CatalogTaxonomy.categoryForPackage(pkg.orEmpty())
                        else null,
                        if (kind == "icon") draft.catalogIconStyle else null,
                    )
            return id
        }
        add(draft.wallpaperPath!!, "wallpaper", "Wallpaper")
        draft.videoPath?.let { add(it, "live_wallpaper", "Motion") }
        draft.icons.forEach { icon ->
            require(icon.statePaths.keys.all { it in listOf("default", "pressed", "disabled") }) {
                "Estado de ícone inválido."
            }
            val id = add(icon.path, "icon", icon.label, icon.packageName)
            if (icon.sourcePath != null || icon.statePaths.isNotEmpty()) {
                val source =
                    add(icon.sourcePath ?: icon.path, "icon_source", icon.label, icon.packageName)
                val states =
                    icon.statePaths.mapValues { (state, path) ->
                        add(path, "icon_source", "${icon.label} · $state", icon.packageName)
                    }
                val index = files.indexOfFirst { it.second.id == id }
                files[index] =
                    files[index].first to
                        files[index]
                            .second
                            .copy(
                                iconEditor =
                                    IconEditor(
                                        source,
                                        icon.backgroundColor,
                                        icon.foregroundScale,
                                        icon.shadowColor,
                                        icon.shadowRadius,
                                        icon.strokeColor,
                                        icon.strokeWidth,
                                        icon.mask,
                                        icon.rotation,
                                        icon.mirrored,
                                        icon.offsetX,
                                        icon.offsetY,
                                        icon.placement,
                                        icon.gridColumns,
                                        icon.text,
                                        icon.textColor,
                                        icon.textSize,
                                        icon.fontId,
                                        icon.fontFamily,
                                        stateAssetIds = states,
                                    )
                            )
            }
        }
        draft.screenshotPaths.forEachIndexed { index, path ->
            add(path, "screenshot", "Prévia " + (index + 1))
        }
        require(files.sumOf { it.first.length() } <= MAX_TOTAL) { "O pacote excede 250 MB." }
        val widgets =
            draft.manifest.widgets.map { w ->
                w.validate()
                val withBackground =
                    w.copy(
                        backgroundImageAssetId =
                            w.backgroundImageId?.let { id ->
                                add(
                                    draft.widgetImages[id]
                                        ?: error("Imagem de fundo do widget ausente."),
                                    "widget_image",
                                    w.name,
                                )
                            }
                    )
                if (w.kind == "image") {
                    val path = draft.widgetImages[w.id]
                    require(path != null) { "Adicione uma imagem ao widget." }
                    withBackground.copy(imageAssetId = add(path, "widget_image", w.name))
                } else
                    withBackground.copy(
                        layers =
                            w.layers.map { root ->
                                root.mapTree { original ->
                                    val l =
                                        original.copy(
                                            states =
                                                original.states.map { state ->
                                                    state.copy(
                                                        imageAssetId =
                                                            state.imageId?.let { id ->
                                                                add(
                                                                    draft.widgetImages[id]
                                                                        ?: error(
                                                                            "Imagem do estado ausente."
                                                                        ),
                                                                    "widget_image",
                                                                    state.name,
                                                                )
                                                            }
                                                    )
                                                }
                                        )
                                    if (l.type == "image")
                                        l.copy(
                                            imageAssetId =
                                                add(
                                                    draft.widgetImages[l.id]
                                                        ?: error("Imagem da camada ausente."),
                                                    "widget_image",
                                                    w.name,
                                                )
                                        )
                                    else l
                                }
                            }
                    )
            }
        val fonts =
            draft.manifest.fonts.map { font ->
                font.copy(
                    assetId =
                        add(
                            draft.fontFiles[font.id] ?: error("Arquivo de fonte ausente."),
                            "font",
                            font.name,
                        )
                )
            }
        val manifest =
            draft.manifest.copy(
                fonts = fonts,
                schemaVersion = "1.5",
                widgets = widgets,
                title = draft.manifest.title.ifBlank { "Meu tema" },
                assets = files.map { it.second },
            )
        require(files.size <= 512 && files.sumOf { it.first.length() } <= MAX_TOTAL) {
            "Tema excede os limites de recursos."
        }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(manifest).toByteArray())
            zip.closeEntry()
            files.forEach { (f, a) ->
                zip.putNextEntry(ZipEntry(a.url))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun import(input: InputStream, target: File, json: Json): Draft {
        require(!target.exists()) { "Diretório de importação já existe." }
        target.mkdirs()
        try {
            var total = 0L
            val seen = mutableSetOf<String>()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    require(seen.add(e.name) && seen.size <= 514) {
                        "Arquivo duplicado ou pacote muito grande."
                    }
                    require(
                        !e.isDirectory &&
                            (e.name == "manifest.json" ||
                                Regex("assets/[a-zA-Z0-9_.-]+").matches(e.name))
                    ) {
                        "Caminho não permitido no pacote."
                    }
                    val dest = File(target, e.name)
                    require(dest.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                        "Caminho inválido."
                    }
                    dest.parentFile!!.mkdirs()
                    var count = 0L
                    dest.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            count += n
                            total += n
                            require(count <= MAX_FILE && total <= MAX_TOTAL) {
                                "Pacote excede o limite de tamanho."
                            }
                            if (e.name == "manifest.json")
                                require(count <= 512 * 1024) { "Manifesto muito grande." }
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
            val manifest = json.decodeFromString<Manifest>(File(target, "manifest.json").readText())
            manifest.validateComposition()
            require(manifest.schemaVersion in listOf("1.0", "1.1", "1.2", "1.3", "1.4", "1.5")) {
                "Versão de tema não suportada."
            }
            require(
                manifest.assets.size in 1..512 &&
                    manifest.assets.map { it.id }.distinct().size == manifest.assets.size
            ) {
                "Recursos inválidos."
            }
            require(manifest.assets.count { it.kind == "screenshot" } <= 8) {
                "Use até 8 imagens na galeria."
            }
            manifest.assets.forEach { a ->
                require(Regex("assets/[a-zA-Z0-9_.-]+").matches(a.url) && seen.contains(a.url)) {
                    "Recurso ausente."
                }
                val f = File(target, a.url)
                require(f.length() == a.size && sha(f) == a.sha256) {
                    "A integridade do tema não pôde ser verificada."
                }
                require(
                    a.kind in
                        listOf(
                            "wallpaper",
                            "icon",
                            "icon_source",
                            "font",
                            "live_wallpaper",
                            "screenshot",
                            "widget_image",
                        )
                ) {
                    "Tipo de recurso não suportado."
                }
                require((a.kind == "font") == (a.mime in listOf("font/ttf", "font/otf"))) {
                    "Tipo de fonte incompatível."
                }
                require((a.kind == "live_wallpaper") == (a.mime == "video/mp4")) {
                    "Tipo de mídia incompatível."
                }
            }
            require(manifest.widgets.size <= 12) { "Use até 12 widgets." }
            manifest.widgets.forEach { w ->
                w.validate()
                if (w.backgroundImageId != null)
                    require(
                        manifest.assets.any {
                            it.id == w.backgroundImageAssetId && it.kind == "widget_image"
                        }
                    ) {
                        "Imagem de fundo do widget ausente."
                    }
                w.displayLayers()
                    .flatMap { it.allLayers() }
                    .filter { it.type == "image" }
                    .forEach { layer ->
                        require(
                            manifest.assets.any {
                                it.id == layer.imageAssetId && it.kind == "widget_image"
                            }
                        ) {
                            "Imagem da camada ausente."
                        }
                    }
                w.layers
                    .flatMap { it.allLayers() }
                    .flatMap { it.states }
                    .filter { it.imageId != null }
                    .forEach { state ->
                        require(
                            manifest.assets.any {
                                it.id == state.imageAssetId && it.kind == "widget_image"
                            }
                        ) {
                            "Imagem do estado ausente."
                        }
                    }
            }
            require(manifest.layout.columns in 3..8 && manifest.layout.iconSize in 30..96) {
                "Layout inválido."
            }
            manifest.fonts.forEach { font ->
                require(manifest.assets.any { it.id == font.assetId && it.kind == "font" }) {
                    "Fonte ausente."
                }
            }
            val wall =
                manifest.assets.firstOrNull { it.kind == "wallpaper" }
                    ?: error("Tema sem wallpaper.")
            return Draft(
                manifest.copy(assets = emptyList()),
                File(target, wall.url).path,
                manifest.assets
                    .firstOrNull { it.kind == "live_wallpaper" }
                    ?.let { File(target, it.url).path },
                manifest.assets
                    .filter { it.kind == "icon" }
                    .map {
                        require(!it.packageName.isNullOrBlank()) { "Ícone sem aplicativo." }
                        val e = it.iconEditor
                        val source = e?.let { editor ->
                            manifest.assets.firstOrNull { a ->
                                a.id == editor.sourceAssetId && a.kind == "icon_source"
                            } ?: error("Camada original do ícone ausente.")
                        }
                        if (e != null) {
                            e.validate()
                        }
                        val statePaths =
                            e?.stateAssetIds.orEmpty().mapValues { (state, id) ->
                                require(state in listOf("default", "pressed", "disabled")) {
                                    "Estado de ícone inválido."
                                }
                                val asset =
                                    manifest.assets.firstOrNull { a ->
                                        a.id == id && a.kind == "icon_source"
                                    } ?: error("Imagem do estado do ícone ausente.")
                                File(target, asset.url).path
                            }
                        if (e != null)
                            require(
                                e.scale in .2f..1.5f &&
                                    e.shadowRadius in 0f..20f &&
                                    e.strokeWidth in 0f..10f &&
                                    e.rotation.isFinite()
                            )
                        DraftIcon(
                            File(target, it.url).path,
                            it.packageName!!,
                            it.name,
                            backgroundColor = e?.background,
                            foregroundScale = e?.scale ?: 1f,
                            shadowColor = e?.shadowColor,
                            shadowRadius = e?.shadowRadius ?: 0f,
                            strokeColor = e?.strokeColor,
                            strokeWidth = e?.strokeWidth ?: 0f,
                            mask = e?.mask ?: "squircle",
                            sourcePath = source?.let { a -> File(target, a.url).path },
                            rotation = e?.rotation ?: 0f,
                            mirrored = e?.mirrored ?: false,
                            rendered = e != null,
                            offsetX = e?.offsetX ?: 0f,
                            offsetY = e?.offsetY ?: 0f,
                            placement = e?.placement ?: "free",
                            gridColumns = e?.gridColumns ?: 4,
                            text = e?.text ?: "",
                            textColor = e?.textColor ?: "#FFFFFF",
                            textSize = e?.textSize ?: 16,
                            fontId = e?.fontId,
                            fontFamily = e?.fontFamily ?: "sans",
                            statePaths = statePaths,
                        )
                    },
                fontFiles =
                    manifest.fonts.associate { font ->
                        font.id to
                            File(target, manifest.assets.first { it.id == font.assetId }.url).path
                    },
                widgetImages =
                    manifest.widgets
                        .flatMap { it.displayLayers() }
                        .flatMap { it.allLayers() }
                        .filter { it.type == "image" }
                        .associate { l ->
                            l.id to
                                File(target, manifest.assets.first { it.id == l.imageAssetId }.url)
                                    .path
                        } +
                        manifest.widgets
                            .flatMap { it.layers }
                            .flatMap { it.allLayers() }
                            .flatMap { it.states }
                            .mapNotNull { state ->
                                state.imageId?.let { id ->
                                    id to
                                        File(
                                                target,
                                                manifest.assets
                                                    .first { it.id == state.imageAssetId }
                                                    .url,
                                            )
                                            .path
                                }
                            } +
                        manifest.widgets.mapNotNull { widget ->
                            widget.backgroundImageId?.let { id ->
                                id to
                                    File(
                                            target,
                                            manifest.assets
                                                .first { it.id == widget.backgroundImageAssetId }
                                                .url,
                                        )
                                        .path
                            }
                        },
                screenshotPaths =
                    manifest.assets
                        .filter { it.kind == "screenshot" }
                        .map { File(target, it.url).path },
                catalogIconStyle =
                    manifest.assets.firstOrNull { it.kind == "icon" }?.style ?: "Minimalista",
            )
        } catch (e: Exception) {
            target.deleteRecursively()
            throw e
        }
    }
}
