package app.homely.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.homely.data.*
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class HomelyState(
    val discovery: DiscoveryInbox = DiscoveryInbox(),
    val discoveryThemes: List<ThemePost> = emptyList(),
    val discoveryLoading: Boolean = false,
    val discoveryError: Boolean = false,
    val privateThemes: List<LocalTheme> = emptyList(),
    val openStudio: Boolean = false,
    val feed: List<ThemePost> = emptyList(),
    val icons: List<CatalogIcon> = emptyList(),
    val iconsLoading: Boolean = false,
    val iconsError: Boolean = false,
    val iconsNextOffset: Int? = null,
    val iconCategory: String = "Todos",
    val iconStyle: String = "Todos",
    val selected: ThemePost? = null,
    val related: List<ThemePost> = emptyList(),
    val profile: Profile? = null,
    val collections: List<ThemeCollection> = emptyList(),
    val library: List<ThemePost> = emptyList(),
    val creator: Profile? = null,
    val creatorThemes: List<ThemePost> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val offline: Boolean = false,
    val message: String? = null,
    val authRequired: Boolean = false,
    val category: String = "Para você",
    val search: String = "",
    val nextOffset: Int? = null,
    val draft: Draft = Draft(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val savedAt: Long = 0,
    val appearance: String = "dark",
    val downloaded: Set<String> = emptySet(),
    val applied: String? = null,
)

class HomelyViewModel(application: Application) : AndroidViewModel(application) {
    val repo = HomelyRepository(application)
    private val _state =
        MutableStateFlow(
            HomelyState(
                feed = repo.cachedFeed(),
                privateThemes = app.homely.platform.LocalThemeStore(application).list(),
                draft = repo.draft(),
                appearance = repo.appearance(),
                downloaded = repo.downloaded(),
                applied = repo.applied(),
            )
        )
    val state: StateFlow<HomelyState> = _state.asStateFlow()
    private var feedJob: Job? = null
    private var iconsJob: Job? = null
    private var detailJob: Job? = null
    private var filterKind: String? = null

    init {
        app.homely.platform.DiscoveryNotifications.schedule(application)
        loadFeed()
        if (repo.token != null) action { refreshProfile() }
    }

    fun trackBehavior(
        kind: String,
        themeId: String? = null,
        context: String? = null,
        duration: Int = 0,
    ) {
        val client = DiscoveryClient(getApplication())
        client.enqueue(kind, themeId, context, duration)
        viewModelScope.launch { runCatching { client.flush() } }
    }

    fun refreshDiscovery() {
        viewModelScope.launch {
            _state.update { it.copy(discoveryLoading = true, discoveryError = false) }
            try {
                val inbox = DiscoveryClient(getApplication()).sync()
                _state.update { it.copy(discovery = inbox) }
                app.homely.platform.DiscoveryNotifications.deliver(getApplication(), inbox)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(discoveryError = true) }
            } finally {
                _state.update { it.copy(discoveryLoading = false) }
            }
        }
    }

    fun openDiscovery(notice: DiscoveryNotice) = action {
        val page = repo.get<ThemePage>("/v1/notifications/${notice.id}/themes")
        _state.update { it.copy(discoveryThemes = page.items) }
        refreshDiscovery()
    }

    fun refreshPrivateThemes() {
        _state.update {
            it.copy(privateThemes = app.homely.platform.LocalThemeStore(getApplication()).list())
        }
    }

    fun savePrivate() = action {
        app.homely.platform.LocalThemeStore(getApplication()).save(_state.value.draft)
        refreshPrivateThemes()
        message("Salvo somente neste aparelho.")
    }

    fun editPrivate(theme: LocalTheme) {
        editDraft { theme.draft }
        _state.update { it.copy(openStudio = true) }
    }

    fun consumedStudio() {
        _state.update { it.copy(openStudio = false) }
    }

    fun widgetImage(id: String, uri: Uri) = action {
        val file = copyUri(uri, "widget")
        editDraft { it.copy(widgetImages = it.widgetImages + (id to file.path)) }
    }

    fun message(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun requireAuth(): Boolean {
        if (_state.value.profile != null) return true
        _state.update { it.copy(authRequired = true) }
        return false
    }

    fun dismissAuth() {
        _state.update { it.copy(authRequired = false) }
    }

    fun askAuth() {
        _state.update { it.copy(authRequired = true) }
    }

    fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiException && e.status == 401) {
                    repo.setSession(null)
                    getApplication<Application>()
                        .getSystemService(android.app.NotificationManager::class.java)
                        .cancel(807)
                    _state.update {
                        it.copy(
                            profile = null,
                            authRequired = true,
                            discovery = DiscoveryInbox(),
                            discoveryThemes = emptyList(),
                        )
                    }
                }
                message(e.message ?: "Não foi possível concluir. Verifique sua conexão.")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    suspend fun refreshProfile() {
        val p = repo.get<Profile>("/v1/me")
        val c = repo.get<Collections>("/v1/collections")
        _state.update { it.copy(profile = p, collections = c.items) }
        refreshDiscovery()
    }

    fun authenticate(
        email: String,
        password: String,
        username: String,
        name: String,
        register: Boolean,
    ) = action {
        val body = buildJsonObject {
            put("email", email.trim())
            put("password", password)
            if (register) {
                put("username", username.trim().lowercase())
                put("displayName", name.trim())
            }
        }
        val result =
            repo.json.decodeFromString<AuthResponse>(
                repo.request("/v1/auth/" + if (register) "register" else "login", "POST", body)
            )
        repo.setSession(result.token)
        _state.update { it.copy(profile = result.user, authRequired = false) }
        refreshProfile()
        loadFeed()
        message("Bem-vindo ao Homely.")
    }

    fun logout() = action {
        repo.request("/v1/auth/logout", "POST")
        repo.setSession(null)
        _state.update {
            it.copy(
                profile = null,
                collections = emptyList(),
                library = emptyList(),
                discovery = DiscoveryInbox(),
                discoveryThemes = emptyList(),
            )
        }
        loadFeed()
    }

    fun deleteAccount() = action {
        repo.request("/v1/me", "DELETE")
        repo.setSession(null)
        _state.update {
            it.copy(
                profile = null,
                collections = emptyList(),
                library = emptyList(),
                authRequired = false,
            )
        }
        loadFeed()
        message("Sua conta e suas publicações foram excluídas.")
    }

    fun appearance(value: String) {
        repo.setAppearance(value)
        _state.update { it.copy(appearance = value) }
    }

    fun loadIcons(
        category: String = _state.value.iconCategory,
        style: String = _state.value.iconStyle,
        more: Boolean = false,
    ) {
        iconsJob?.cancel()
        iconsJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    iconsLoading = true,
                    iconsError = false,
                    iconCategory = category,
                    iconStyle = style,
                    icons = if (more) it.icons else emptyList(),
                )
            }
            try {
                val params = mutableListOf<String>()
                if (category != "Todos")
                    params += "category=" + URLEncoder.encode(category, "UTF-8")
                if (style != "Todos") params += "style=" + URLEncoder.encode(style, "UTF-8")
                if (more) params += "offset=" + (_state.value.iconsNextOffset ?: 0)
                val page = repo.get<IconPage>("/v1/icons?" + params.joinToString("&"))
                _state.update {
                    it.copy(
                        icons =
                            if (more) (it.icons + page.items).distinctBy { x -> x.asset.id }
                            else page.items,
                        iconsLoading = false,
                        iconsNextOffset = page.nextOffset,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(iconsLoading = false, iconsError = true) }
            }
        }
    }

    fun chooseScreenshots(uris: List<Uri>) = action {
        val capacity = 8 - _state.value.draft.screenshotPaths.size
        require(capacity > 0) { "Use até 8 imagens na galeria." }
        val paths = uris.take(capacity).map { copyUri(it, "screenshot").path }
        editDraft { it.copy(screenshotPaths = it.screenshotPaths + paths) }
        if (uris.size > capacity) message("A galeria comporta até 8 imagens.")
    }

    fun loadFeed(
        category: String = _state.value.category,
        search: String = _state.value.search,
        kind: String? = filterKind,
        more: Boolean = false,
    ) {
        if (!more && category !in listOf("Para você", "Explorar"))
            trackBehavior("category", context = category)
        feedJob?.cancel()
        filterKind = kind
        feedJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, category = category, search = search) }
            try {
                if (search.isNotBlank()) delay(300)
                val query = mutableListOf<String>()
                if (category !in listOf("Para você", "Explorar"))
                    query += "category=" + URLEncoder.encode(category, "UTF-8")
                if (search.isNotBlank()) query += "search=" + URLEncoder.encode(search, "UTF-8")
                kind?.let { query += "kind=$it" }
                if (more) query += "offset=" + (_state.value.nextOffset ?: 0)
                val personalized =
                    category == "Para você" &&
                        search.isBlank() &&
                        kind == null &&
                        repo.token != null
                val result =
                    if (personalized) {
                        try {
                            repo.get<ThemePage>(
                                "/v1/recommendations?offset=" +
                                    (if (more) _state.value.nextOffset ?: 0 else 0)
                            )
                        } catch (e: ApiException) {
                            if (e.status != 404) throw e
                            repo.get<ThemePage>("/v1/themes?" + query.joinToString("&"))
                        }
                    } else repo.get<ThemePage>("/v1/themes?" + query.joinToString("&"))
                _state.update {
                    it.copy(
                        feed =
                            if (more) (it.feed + result.items).distinctBy { t -> t.id }
                            else result.items,
                        nextOffset = result.nextOffset,
                        loading = false,
                        offline = false,
                    )
                }
                if (category == "Para você" && search.isBlank() && kind == null && !more)
                    repo.cacheFeed(result.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        offline = true,
                        message = "Sem conexão. Suas últimas inspirações continuam aqui.",
                    )
                }
            }
        }
    }

    fun open(theme: ThemePost) {
        trackBehavior("view", theme.id)
        detailJob?.cancel()
        _state.update { it.copy(selected = theme, related = emptyList(), creator = null) }
        detailJob = viewModelScope.launch {
            try {
                val d = repo.get<ThemePost>("/v1/themes/" + theme.id)
                val r = repo.get<ThemePage>("/v1/themes/" + theme.id + "/related")
                _state.update {
                    if (it.selected?.id == theme.id) it.copy(selected = d, related = r.items)
                    else it
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _state.update { it.copy(selected = null, related = emptyList()) }
    }

    private fun updateTheme(id: String, change: (ThemePost) -> ThemePost) {
        _state.update { s ->
            s.copy(
                feed = s.feed.map { if (it.id == id) change(it) else it },
                selected = s.selected?.let { if (it.id == id) change(it) else it },
                related = s.related.map { if (it.id == id) change(it) else it },
                library = s.library.map { if (it.id == id) change(it) else it },
            )
        }
    }

    fun like(theme: ThemePost) {
        if (!requireAuth()) return
        action {
            repo.request(
                "/v1/themes/" + theme.id + "/like",
                "PUT",
                buildJsonObject { put("active", !theme.liked) },
            )
            updateTheme(theme.id) {
                it.copy(
                    liked = !theme.liked,
                    likes = (it.likes + if (theme.liked) -1 else 1).coerceAtLeast(0),
                )
            }
        }
    }

    fun save(theme: ThemePost, collectionId: String? = null) {
        if (!requireAuth()) return
        action {
            repo.request(
                "/v1/themes/" + theme.id + "/save",
                "PUT",
                buildJsonObject {
                    put("active", if (collectionId != null) true else !theme.saved)
                    collectionId?.let { put("collectionId", it) }
                },
            )
            updateTheme(theme.id) {
                it.copy(saved = if (collectionId != null) true else !theme.saved)
            }
            refreshProfile()
            message(
                if (!theme.saved || collectionId != null) "Tema salvo na sua coleção."
                else "Tema removido dos salvos."
            )
        }
    }

    fun collection(name: String) = action {
        repo.request("/v1/collections", "POST", buildJsonObject { put("name", name) })
        refreshProfile()
    }

    fun loadLibrary(tab: String = "Temas", collection: String? = null) {
        if (collection != null) trackBehavior("collection", context = collection)
        if (!requireAuth()) return
        action {
            val q =
                when (tab) {
                    "Salvos" -> "saved=true"
                    "Likes" -> "liked=true"
                    else -> "author=" + _state.value.profile!!.id
                }
            val all = mutableListOf<ThemePost>()
            var offset: Int? = 0
            do {
                val result =
                    repo.get<ThemePage>(
                        "/v1/themes?limit=40&$q&offset=$offset" +
                            (collection?.let { "&collection=$it" } ?: "")
                    )
                all += result.items
                offset = result.nextOffset
            } while (offset != null)
            _state.update { it.copy(library = all) }
        }
    }

    fun creator(author: Author) = action {
        val p = repo.get<Profile>("/v1/users/" + author.id)
        val themes = repo.get<ThemePage>("/v1/themes?author=" + author.id)
        _state.update { it.copy(creator = p, creatorThemes = themes.items) }
    }

    fun closeCreator() {
        _state.update { it.copy(creator = null) }
    }

    fun follow(profile: Profile) {
        if (!requireAuth()) return
        action {
            val p =
                repo.json.decodeFromString<Profile>(
                    repo.request(
                        "/v1/users/" + profile.id + "/follow",
                        "PUT",
                        buildJsonObject { put("active", !profile.followed) },
                    )
                )
            _state.update { it.copy(creator = p) }
            refreshProfile()
        }
    }

    fun updateProfile(p: Profile) = action {
        repo.request(
            "/v1/me",
            "PUT",
            buildJsonObject {
                put("username", p.username)
                put("displayName", p.displayName)
                put("bio", p.bio)
                put("location", p.location)
                put("website", p.website)
                p.avatarUrl?.let { put("avatarUrl", it) }
                p.bannerUrl?.let { put("bannerUrl", it) }
            },
        )
        refreshProfile()
        message("Perfil atualizado.")
    }

    fun avatar(uri: Uri) = action {
        val file = copyUri(uri, "avatar")
        val upload = repo.upload(file, mime(file))
        val p = _state.value.profile!!
        repo.request(
            "/v1/me",
            "PUT",
            buildJsonObject {
                put("username", p.username)
                put("displayName", p.displayName)
                put("bio", p.bio)
                put("location", p.location)
                put("website", p.website)
                p.bannerUrl?.let { put("bannerUrl", it) }
                put("avatarUrl", upload.url)
            },
        )
        refreshProfile()
    }

    fun banner(uri: Uri) = action {
        val file = copyUri(uri, "banner")
        val upload = repo.upload(file, mime(file))
        val p = _state.value.profile!!
        repo.request(
            "/v1/me",
            "PUT",
            buildJsonObject {
                put("username", p.username)
                put("displayName", p.displayName)
                put("bio", p.bio)
                put("location", p.location)
                put("website", p.website)
                p.avatarUrl?.let { put("avatarUrl", it) }
                put("bannerUrl", upload.url)
            },
        )
        refreshProfile()
    }

    private val undo = ArrayDeque<Draft>()
    private val redo = ArrayDeque<Draft>()

    fun undoDraft() {
        if (undo.isNotEmpty()) {
            redo.addLast(_state.value.draft)
            restoreDraft(undo.removeLast())
        }
    }

    fun redoDraft() {
        if (redo.isNotEmpty()) {
            undo.addLast(_state.value.draft)
            restoreDraft(redo.removeLast())
        }
    }

    private fun restoreDraft(value: Draft) {
        repo.saveDraft(value)
        _state.update {
            it.copy(
                draft = value,
                canUndo = undo.isNotEmpty(),
                canRedo = redo.isNotEmpty(),
                savedAt = System.currentTimeMillis(),
            )
        }
    }

    fun editDraft(change: (Draft) -> Draft) {
        val value = change(_state.value.draft)
        if (value == _state.value.draft) return
        undo.addLast(_state.value.draft)
        if (undo.size > 50) undo.removeFirst()
        redo.clear()
        restoreDraft(value)
    }

    suspend fun importImage(uri: Uri): String = copyUri(uri, "studio").path

    private suspend fun copyUri(uri: Uri, prefix: String): File =
        withContext(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val type = ctx.contentResolver.getType(uri).orEmpty()
            val ext =
                when (type) {
                    "video/mp4" -> "mp4"
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
            val dest =
                File(ctx.filesDir, "$prefix-" + java.util.UUID.randomUUID().toString() + "." + ext)
            try {
                ctx.contentResolver.openInputStream(uri)!!.use { input ->
                    dest.outputStream().use { out ->
                        val buf = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > 50 * 1024 * 1024) error("O arquivo deve ter até 50 MB.")
                            out.write(buf, 0, n)
                        }
                    }
                }
            } catch (e: Exception) {
                dest.delete()
                throw e
            }
            dest
        }

    fun chooseMedia(uri: Uri, kind: String, packageName: String = "", label: String = "") = action {
        val f = copyUri(uri, kind)
        editDraft { d ->
            when (kind) {
                "wallpaper" -> d.copy(wallpaperPath = f.path)
                "live_wallpaper" -> d.copy(videoPath = f.path)
                else ->
                    d.copy(
                        icons =
                            d.icons.filterNot { it.packageName == packageName } +
                                DraftIcon(f.path, packageName, label)
                    )
            }
        }
    }

    fun saveStyledIcon(icon: DraftIcon, uri: Uri? = null) = action {
        val source = uri?.let { copyUri(it, "icon-source").path } ?: icon.sourcePath
        val result =
            withContext(Dispatchers.IO) {
                app.homely.platform.IconRenderer.materialize(
                    getApplication(),
                    icon.copy(sourcePath = source, rendered = false),
                    _state.value.draft.fontFiles,
                )
            }
        editDraft { d ->
            d.copy(icons = d.icons.filterNot { it.packageName == icon.packageName } + result)
        }
        val ctx = getApplication<Application>()
        val modes = app.homely.platform.UsageModes(ctx)
        if (modes.home && app.homely.LauncherActivity.isDefault(ctx)) {
            val store = app.homely.platform.LocalThemeStore(ctx)
            store.active()?.let { active ->
                val saved =
                    store.save(
                        active.draft.copy(
                            fontFiles = active.draft.fontFiles + _state.value.draft.fontFiles,
                            manifest =
                                active.draft.manifest.copy(
                                    fonts =
                                        (active.draft.manifest.fonts +
                                                _state.value.draft.manifest.fonts)
                                            .distinctBy { it.id }
                                ),
                            icons =
                                active.draft.icons.filterNot {
                                    it.packageName == result.packageName
                                } + result,
                        )
                    )
                store.activate(saved)
                refreshPrivateThemes()
            }
        }
        if (modes.icons) app.homely.platform.LauncherCapabilities.pin(ctx, result)
        message(
            if (modes.icons) "Ícone salvo. Confirme a adição no launcher."
            else "Ícone salvo com as camadas de edição."
        )
    }

    fun resetIcon(pkg: String) = editDraft {
        it.copy(icons = it.icons.filterNot { i -> i.packageName == pkg })
    }

    fun applyIconStyle(apps: List<app.homely.platform.InstalledApp>, style: DraftIcon) = action {
        val existing = _state.value.draft.icons.map { it.packageName }.toSet()
        val next =
            withContext(Dispatchers.IO) {
                apps
                    .filter { it.packageName !in existing }
                    .take(200 - existing.size)
                    .map { a ->
                        app.homely.platform.IconRenderer.materialize(
                            getApplication(),
                            style.copy(
                                path = "",
                                sourcePath = null,
                                packageName = a.packageName,
                                label = a.label,
                                rendered = false,
                            ),
                            _state.value.draft.fontFiles,
                        )
                    }
            }
        editDraft { it.copy(icons = it.icons + next) }
        message("Estilo aplicado a " + next.size + " aplicativos.")
    }

    fun extractPalette() = action {
        val path = _state.value.draft.wallpaperPath ?: error("Escolha um wallpaper primeiro.")
        val palette =
            withContext(Dispatchers.IO) { app.homely.platform.PaletteExtractor.extract(path) }
        editDraft { it.copy(manifest = it.manifest.copy(palette = palette)) }
        message("Paleta extraída do wallpaper.")
    }

    fun mime(file: File) =
        when (file.extension.lowercase()) {
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "mp4" -> "video/mp4"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

    fun publish() {
        if (!requireAuth()) return
        action {
            val d =
                app.homely.platform.LocalThemeStore(getApplication()).normalized(_state.value.draft)
            require(d.manifest.title.trim().length >= 3) {
                "Dê um nome ao seu tema (pelo menos 3 letras)."
            }
            require(d.wallpaperPath != null) { "Adicione uma imagem de wallpaper." }
            val assets = mutableListOf<Asset>()
            val wall = File(d.wallpaperPath!!)
            assets += repo.upload(wall, mime(wall)).asset("wallpaper", "Wallpaper")
            d.videoPath?.let {
                val f = File(it)
                assets += repo.upload(f, mime(f)).asset("live_wallpaper", "Wallpaper animado")
            }
            d.icons.forEach {
                val f = File(it.path)
                assets +=
                    repo
                        .upload(f, mime(f))
                        .asset("icon", it.label, it.packageName)
                        .copy(
                            category = CatalogTaxonomy.categoryForPackage(it.packageName),
                            style = d.catalogIconStyle,
                        )
            }
            d.icons
                .filter { it.sourcePath != null || it.statePaths.isNotEmpty() }
                .forEach { icon ->
                    val file = File(icon.sourcePath ?: icon.path)
                    val source =
                        repo
                            .upload(file, mime(file))
                            .asset("icon_source", icon.label, icon.packageName)
                    assets += source
                    val stateAssets =
                        icon.statePaths.mapValues { (state, path) ->
                            val stateFile = File(path)
                            val asset =
                                repo
                                    .upload(stateFile, mime(stateFile))
                                    .asset(
                                        "icon_source",
                                        "${icon.label} · $state",
                                        icon.packageName,
                                    )
                            assets += asset
                            asset.id
                        }
                    val index = assets.indexOfFirst {
                        it.kind == "icon" && it.packageName == icon.packageName
                    }
                    assets[index] =
                        assets[index].copy(
                            iconEditor =
                                IconEditor(
                                    source.id,
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
                                    stateAssetIds = stateAssets,
                                )
                        )
                }
            val widgets =
                d.manifest.widgets.map { w ->
                    w.validate()
                    val withBackground =
                        w.copy(
                            backgroundImageAssetId =
                                w.backgroundImageId?.let { id ->
                                    val f =
                                        File(
                                            d.widgetImages[id]
                                                ?: error("Selecione a imagem de fundo do widget.")
                                        )
                                    val asset =
                                        repo.upload(f, mime(f)).asset("widget_image", w.name)
                                    assets += asset
                                    asset.id
                                }
                        )
                    val path = d.widgetImages[w.id]
                    if (w.kind == "image") {
                        require(path != null) { "Adicione a imagem do widget." }
                        val f = File(path)
                        val asset = repo.upload(f, mime(f)).asset("widget_image", w.name)
                        assets += asset
                        withBackground.copy(imageAssetId = asset.id)
                    } else
                        withBackground.copy(
                            layers =
                                w.layers.map { root ->
                                    root.mapTreeAsync { original ->
                                        val l =
                                            original.copy(
                                                states =
                                                    original.states.map { state ->
                                                        state.copy(
                                                            imageAssetId =
                                                                state.imageId?.let { id ->
                                                                    val file =
                                                                        File(
                                                                            d.widgetImages[id]
                                                                                ?: error(
                                                                                    "Selecione a imagem do estado."
                                                                                )
                                                                        )
                                                                    val asset =
                                                                        repo
                                                                            .upload(
                                                                                file,
                                                                                mime(file),
                                                                            )
                                                                            .asset(
                                                                                "widget_image",
                                                                                state.name,
                                                                            )
                                                                    assets += asset
                                                                    asset.id
                                                                }
                                                        )
                                                    }
                                            )
                                        if (l.type == "image") {
                                            val f =
                                                File(
                                                    d.widgetImages[l.id]
                                                        ?: error("Selecione a imagem da camada.")
                                                )
                                            val asset =
                                                repo
                                                    .upload(f, mime(f))
                                                    .asset("widget_image", w.name)
                                            assets += asset
                                            l.copy(imageAssetId = asset.id)
                                        } else l
                                    }
                                }
                        )
                }
            d.screenshotPaths.forEachIndexed { index, path ->
                val f = File(path)
                assets += repo.upload(f, mime(f)).asset("screenshot", "Prévia " + (index + 1))
            }
            val fonts =
                d.manifest.fonts.map { font ->
                    val f = File(d.fontFiles[font.id] ?: error("Fonte ausente."))
                    val asset = repo.upload(f, mime(f)).asset("font", font.name)
                    assets += asset
                    font.copy(assetId = asset.id)
                }
            val m =
                d.manifest.copy(
                    fonts = fonts,
                    schemaVersion = "1.5",
                    widgets = widgets,
                    assets = assets,
                    title = d.manifest.title.trim(),
                )
            val theme =
                repo.json.decodeFromString<ThemePost>(
                    repo.request("/v1/themes", "POST", repo.json.encodeToJsonElement(m))
                )
            editDraft { Draft() }
            refreshProfile()
            loadFeed()
            open(theme)
            message("Seu tema já faz parte da comunidade.")
        }
    }

    fun report(theme: ThemePost, reason: String) {
        if (!requireAuth()) return
        action {
            repo.request(
                "/v1/themes/" + theme.id + "/report",
                "POST",
                buildJsonObject { put("reason", reason) },
            )
            message("Denúncia recebida para análise.")
        }
    }

    fun downloaded(id: String) {
        repo.markDownloaded(id)
        _state.update { it.copy(downloaded = repo.downloaded()) }
    }

    fun applied(id: String) {
        repo.markApplied(id)
        if (runCatching { java.util.UUID.fromString(id) }.isSuccess) trackBehavior("apply", id)
        _state.update { it.copy(applied = id) }
    }
}
