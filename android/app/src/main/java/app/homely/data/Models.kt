package app.homely.data

import kotlinx.serialization.Serializable

@Serializable
data class Palette(
    val accent: String = "#7965FF",
    val background: String = "#090C12",
    val surface: String = "#171C27",
    val secondary: String = "#AEA2FF",
    val text: String = "#FFFFFF",
)

@Serializable
data class Compatibility(
    val minAndroid: Int = 26,
    val launcherRequiredForIconPack: Boolean = true,
    val systemBars: String = "preview-only",
)

@Serializable
data class Asset(
    val id: String,
    val kind: String = "wallpaper",
    val name: String = "Wallpaper",
    val url: String,
    val mime: String,
    val sha256: String,
    val size: Long,
    val packageName: String? = null,
    val category: String? = null,
    val style: String? = null,
    val iconEditor: IconEditor? = null,
)

@Serializable
data class Manifest(
    val schemaVersion: String = "1.5",
    val title: String = "",
    val description: String = "",
    val category: String = "Minimal",
    val tags: List<String> = emptyList(),
    val palette: Palette = Palette(),
    val iconStyle: String = "rounded",
    val clockStyle: String = "thin",
    val fontFamily: String = "sans",
    val fonts: List<ThemeFont> = emptyList(),
    val assets: List<Asset> = emptyList(),
    val compatibility: Compatibility = Compatibility(),
    val widgets: List<WidgetSpec> = emptyList(),
    val layout: HomeLayout = HomeLayout(),
    val license: String = "CC-BY-4.0",
)

@Serializable
data class Author(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
)

@Serializable
data class Profile(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val location: String = "",
    val website: String = "",
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val themeCount: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val followed: Boolean = false,
)

@Serializable
data class ThemePost(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String,
    val tags: List<String> = emptyList(),
    val coverUrl: String,
    val manifest: Manifest,
    val author: Author,
    val likes: Int = 0,
    val liked: Boolean = false,
    val saved: Boolean = false,
    val downloads: Int = 0,
)

@Serializable
data class ThemePage(val items: List<ThemePost> = emptyList(), val nextOffset: Int? = null)

@Serializable data class ThemeCollection(val id: String, val name: String, val count: Int = 0)

@Serializable data class Collections(val items: List<ThemeCollection> = emptyList())

@Serializable data class AuthResponse(val token: String, val user: Profile)

@Serializable
data class Upload(
    val id: String,
    val url: String,
    val mime: String,
    val size: Long,
    val sha256: String,
) {
    fun asset(kind: String, name: String, packageName: String? = null) =
        Asset(id, kind, name, url, mime, sha256, size, packageName)
}

@Serializable
data class Draft(
    val manifest: Manifest = Manifest(),
    val wallpaperPath: String? = null,
    val videoPath: String? = null,
    val icons: List<DraftIcon> = emptyList(),
    val screenshotPaths: List<String> = emptyList(),
    val catalogIconStyle: String = "Minimalista",
    val widgetImages: Map<String, String> = emptyMap(),
    val creationType: String = "Tema completo",
    val fontFiles: Map<String, String> = emptyMap(),
)

@Serializable
data class DraftIcon(
    val path: String,
    val packageName: String,
    val label: String,
    val backgroundColor: String? = null,
    val foregroundScale: Float = 1.0f,
    val shadowColor: String? = null,
    val shadowRadius: Float = 0f,
    val strokeColor: String? = null,
    val strokeWidth: Float = 0f,
    val mask: String = "squircle",
    val sourcePath: String? = null,
    val rotation: Float = 0f,
    val mirrored: Boolean = false,
    val rendered: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val placement: String = "free",
    val gridColumns: Int = 4,
    val text: String = "",
    val textColor: String = "#FFFFFF",
    val textSize: Int = 16,
    val fontId: String? = null,
    val fontFamily: String = "sans",
    val statePaths: Map<String, String> = emptyMap(),
)

@Serializable
data class IconEditor(
    val sourceAssetId: String,
    val background: String? = null,
    val scale: Float = 1f,
    val shadowColor: String? = null,
    val shadowRadius: Float = 0f,
    val strokeColor: String? = null,
    val strokeWidth: Float = 0f,
    val mask: String = "squircle",
    val rotation: Float = 0f,
    val mirrored: Boolean = false,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val placement: String = "free",
    val gridColumns: Int = 4,
    val text: String = "",
    val textColor: String = "#FFFFFF",
    val textSize: Int = 16,
    val fontId: String? = null,
    val fontFamily: String = "sans",
    val stateAssetIds: Map<String, String> = emptyMap(),
)
