package app.homely.data

import kotlinx.serialization.Serializable

object CatalogTaxonomy {
    val iconCategories =
        listOf(
            "Comunicação",
            "Social",
            "Foto e vídeo",
            "Produtividade",
            "Música e áudio",
            "Navegação",
            "Sistema",
            "Jogos",
            "Compras",
            "Saúde",
        )
    val iconStyles =
        listOf(
            "Minimalista",
            "Colorido",
            "3D",
            "Contorno",
            "Monocromático",
            "Neon",
            "Pastel",
            "Retrô",
        )

    fun categoryForPackage(pkg: String): String =
        when {
            listOf("dialer", "messaging", "whatsapp", "telegram").any { pkg.contains(it, true) } ->
                "Comunicação"
            listOf("camera", "photo", "youtube").any { pkg.contains(it, true) } -> "Foto e vídeo"
            listOf("chrome", "browser", "maps").any { pkg.contains(it, true) } -> "Navegação"
            listOf("music", "spotify").any { pkg.contains(it, true) } -> "Música e áudio"
            else -> "Sistema"
        }
}

@Serializable
data class CatalogIcon(
    val asset: Asset,
    val themeId: String,
    val themeTitle: String,
    val author: Author,
    val category: String,
    val style: String,
)

@Serializable
data class IconPage(val items: List<CatalogIcon> = emptyList(), val nextOffset: Int? = null)

data class WidgetItem(
    val id: String,
    val name: String,
    val category: String,
    val style: String,
    val size: String,
    val subtitle: String,
)

val widgetCatalog =
    listOf(
        WidgetItem(
            "clock",
            "Essencial",
            "Relógios",
            "Minimalista",
            "3 × 2",
            "Hora e data, sem distrações.",
        ),
        WidgetItem(
            "editorial",
            "Editorial",
            "Relógios",
            "Editorial",
            "3 × 2",
            "O tempo com outra personalidade.",
        ),
        WidgetItem(
            "date",
            "Hoje",
            "Calendário",
            "Minimalista",
            "2 × 2",
            "Um olhar para o seu dia.",
        ),
        WidgetItem(
            "pastel",
            "Dia leve",
            "Calendário",
            "Pastel",
            "2 × 2",
            "Uma pausa de cor na sua tela.",
        ),
        WidgetItem(
            "battery",
            "Energia",
            "Bateria",
            "Minimalista",
            "2 × 1",
            "Bateria do aparelho. Toque para atualizar.",
        ),
        WidgetItem("neon", "Pulse", "Bateria", "Neon", "2 × 1", "Bateria com um toque de luz."),
    )

fun themeScreenshots(theme: ThemePost): List<String> =
    theme.manifest.assets
        .filter { it.kind == "screenshot" }
        .map { it.url }
        .ifEmpty { listOf(theme.coverUrl) }
