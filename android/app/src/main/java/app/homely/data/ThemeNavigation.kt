package app.homely.data

/** The IDs used by both the visual builder and the launcher gesture dispatcher. */
val HomeGestureIds =
    listOf(
        "swipe_up",
        "swipe_down",
        "swipe_left",
        "swipe_right",
        "tap",
        "double_tap",
        "long_press",
        "pinch_in",
        "pinch_out",
        "drawer_swipe_down",
    )

/** Actions the editor can offer; the old notifications binding is read-only compatibility. */
val HomeGestureActions =
    listOf(
        "drawer",
        "close_drawer",
        "search",
        "next_page",
        "previous_page",
        "personalize",
        "wallpaper",
        "widgets",
        "lock",
        "app",
        "none",
    )

fun HomeGestures.actionFor(gesture: String, inDrawer: Boolean = false): String =
    when (gesture) {
        "swipe_up" -> swipeUp
        "swipe_down" -> if (inDrawer) drawerSwipeDown else swipeDown
        "swipe_left" -> swipeLeft
        "swipe_right" -> swipeRight
        "tap" -> tap
        "double_tap" -> doubleTap
        "long_press" -> longPress
        "pinch_in" -> pinchIn
        "pinch_out" -> pinchOut
        "drawer_swipe_down" -> drawerSwipeDown
        else -> "none"
    }

/** Changes only the requested binding, preserving all other gestures and the selected app. */
fun HomeGestures.withBinding(gesture: String, action: String): HomeGestures {
    require(
        action in HomeGestureActions || (gesture == "swipe_down" && action == "notifications")
    ) {
        "Ação de gesto inválida."
    }
    return when (gesture) {
        "swipe_up" -> copy(swipeUp = action)
        "swipe_down" -> copy(swipeDown = action)
        "swipe_left" -> copy(swipeLeft = action)
        "swipe_right" -> copy(swipeRight = action)
        "tap" -> copy(tap = action)
        "double_tap" -> copy(doubleTap = action)
        "long_press" -> copy(longPress = action)
        "pinch_in" -> copy(pinchIn = action)
        "pinch_out" -> copy(pinchOut = action)
        "drawer_swipe_down" -> copy(drawerSwipeDown = action)
        else -> throw IllegalArgumentException("Gesto inválido.")
    }
}

/** Older manifests only described a grid or a list. Keep their list after adding new defaults. */
fun HomeLayout.effectiveDrawerMode(): String =
    if (drawerMode == "continuous" && drawerLayout == "list") "list" else drawerMode

fun HomeLayout.validate() {
    require(
        dock.size <= 8 &&
            dock.distinct().size == dock.size &&
            dock.all { Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(it) }
    ) {
        "Aplicativos do dock inválidos."
    }
    require(columns in 3..8 && rows in 4..12 && iconSize in 30..96 && iconPadding in 0..32) {
        "Dimensões da tela inicial inválidas."
    }
    require(
        drawerColumns in 3..8 &&
            drawerRows in 2..10 &&
            drawerSpacing in 0..32 &&
            drawerMargin in 0..40 &&
            drawerIconSize in 30..96 &&
            drawerOpacity in .2f..1f &&
            drawerBlur in 0..30 &&
            drawerRadius in 0..48
    ) {
        "Dimensões ou aparência da grade inválidas."
    }
    require(drawerMode in listOf("continuous", "pages", "list", "custom")) {
        "Modo da grade inválido."
    }
    require(drawerDirection in listOf("horizontal", "vertical")) { "Direção da grade inválida." }
    require(
        drawerAnimation in listOf("slide", "fade", "zoom", "none") && animationDuration in 0..800
    ) {
        "Animação da grade inválida."
    }
    require(drawerStyle in listOf("gesture", "button") && drawerLayout in listOf("grid", "list"))
    require(folderStyle in listOf("window", "fullscreen"))
    require(appAnimation in listOf("zoom", "fade", "slide", "none"))
    require(scrollDirection in listOf("vertical", "horizontal"))
    require(quickAction in listOf("studio", "search", "app", "none"))
    require(statusBar.batteryIcon in listOf("ios", "android", "hidden"))
    require(statusBar.networkIcon in listOf("ios", "android", "hidden"))
    require(statusBar.clockFormat in listOf("24h", "12h", "hidden"))
    require(statusBar.tint == "auto" || Regex("#[0-9a-fA-F]{6}").matches(statusBar.tint))
    require(
        statusBar.background == "transparent" ||
            Regex("#[0-9a-fA-F]{6}").matches(statusBar.background)
    )
    require(statusBar.backgroundOpacity in 0f..1f)
    require(systemBarIcons in listOf("auto", "light", "dark"))
    require(gestures.iconLongPress in listOf("menu", "edit", "info", "remove"))
    HomeGestureIds.forEach { gesture ->
        val action = gestures.actionFor(gesture)
        require(
            action in HomeGestureActions || (gesture == "swipe_down" && action == "notifications")
        ) {
            "Ação de gesto inválida."
        }
    }
    gestures.quickPackage?.let {
        require(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(it)) {
            "Aplicativo do gesto inválido."
        }
    }
    require(
        (quickAction != "app" && HomeGestureIds.none { gestures.actionFor(it) == "app" }) ||
            gestures.quickPackage != null
    ) {
        "Escolha um aplicativo para a ação de abrir app."
    }
}
