package app.homely

import android.app.role.RoleManager
import android.appwidget.*
import android.content.*
import android.os.*
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import app.homely.data.*
import app.homely.platform.*
import app.homely.ui.*
import kotlinx.coroutines.*

class LauncherActivity : ComponentActivity() {
    private lateinit var widgetHost: AppWidgetHost
    private var widgetIds by mutableStateOf<List<Int>>(emptyList())
    private var current by mutableStateOf<LocalTheme?>(null)
    private val widgetPrefs
        get() = getSharedPreferences("launcher-widgets", MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetHost = AppWidgetHost(this, 2030)
        widgetIds = widgetPrefs.getStringSet("ids", emptySet())!!.mapNotNull { it.toIntOrNull() }
        setContent {
            HomelyTheme(true) {
                val p = current?.draft?.manifest?.palette ?: Palette()
                val layout = current?.draft?.manifest?.layout ?: HomeLayout()
                SideEffect {
                    val controller =
                        androidx.core.view.WindowCompat.getInsetsController(
                            window,
                            window.decorView,
                        )
                    val darkIcons =
                        layout.systemBarIcons == "dark" ||
                            (layout.systemBarIcons == "auto" &&
                                color(p.background).luminance() > .5f)
                    controller.isAppearanceLightStatusBars = darkIcons
                    controller.isAppearanceLightNavigationBars = darkIcons
                    controller.systemBarsBehavior =
                        androidx.core.view.WindowInsetsControllerCompat
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    if (layout.immersiveHome)
                        controller.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                    else
                        controller.show(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                }
                MaterialTheme(
                    colorScheme =
                        MaterialTheme.colorScheme.copy(
                            primary = color(p.accent),
                            secondary = color(p.secondary),
                            background = color(p.background),
                            surface = color(p.surface),
                            onSurface = color(p.text),
                            onBackground = color(p.text),
                        )
                ) {
                    Home(current, widgetHost, widgetIds, { addWidget() }, { removeWidget(it) })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val controller =
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        lifecycleScope.launch {
            val store = LocalThemeStore(this@LauncherActivity)
            try {
                if (isDefault(this@LauncherActivity)) {
                    UsageModes(this@LauncherActivity).home = true
                    store.pending()?.let { store.activate(it) }
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                        this@LauncherActivity,
                        e.message,
                        android.widget.Toast.LENGTH_LONG,
                    )
                    .show()
            }
            current = withContext(Dispatchers.IO) { store.active() }
        }
    }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        widgetHost.stopListening()
        super.onStop()
    }

    private fun saveWidget(id: Int) {
        widgetIds = (widgetIds + id).distinct()
        widgetPrefs
            .edit()
            .putStringSet("ids", widgetIds.map { it.toString() }.toSet())
            .remove("pending")
            .apply()
    }

    private fun removeWidget(id: Int) {
        widgetHost.deleteAppWidgetId(id)
        widgetIds = widgetIds - id
        widgetPrefs.edit().putStringSet("ids", widgetIds.map { it.toString() }.toSet()).apply()
    }

    private fun addWidget() {
        val id = widgetHost.allocateAppWidgetId()
        widgetPrefs.edit().putInt("pending", id).apply()
        startActivityForResult(
            Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            301,
        )
    }

    @Deprecated("Platform widget configuration result")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in listOf(301, 302)) return
        val id =
            data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)?.takeIf { it != -1 }
                ?: widgetPrefs.getInt("pending", -1)
        if (id < 0) return
        if (resultCode != RESULT_OK) {
            widgetHost.deleteAppWidgetId(id)
            widgetPrefs.edit().remove("pending").apply()
            return
        }
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
        if (info == null) {
            widgetHost.deleteAppWidgetId(id)
            return
        }
        if (requestCode == 301 && info.configure != null) {
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(info.configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                302,
            )
        } else saveWidget(id)
    }

    companion object {
        fun isDefault(ctx: Context): Boolean {
            if (Build.VERSION.SDK_INT >= 29)
                return ctx.getSystemService(RoleManager::class.java)
                    .isRoleHeld(RoleManager.ROLE_HOME)
            return ctx.packageManager
                .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo
                ?.packageName == ctx.packageName
        }

        fun permissionIntent(ctx: Context): Intent =
            if (Build.VERSION.SDK_INT >= 29)
                ctx.getSystemService(RoleManager::class.java)
                    .createRequestRoleIntent(RoleManager.ROLE_HOME)
            else Intent(Settings.ACTION_HOME_SETTINGS)
    }
}

@Composable
private fun Home(
    theme: LocalTheme?,
    host: AppWidgetHost,
    ids: List<Int>,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
) {
    val ctx = LocalContext.current
    val draft = theme?.draft ?: Draft()
    val layout = draft.manifest.layout
    var settings by remember { mutableStateOf(false) }
    var contextPackage by remember { mutableStateOf<String?>(null) }
    val hiddenPrefs = remember { ctx.getSharedPreferences("home-hidden", 0) }
    var hidden by
        remember(theme?.id) {
            mutableStateOf(hiddenPrefs.getStringSet(theme?.id ?: "default", emptySet())!!.toSet())
        }
    fun hide(pkg: String) {
        hidden = hidden + pkg
        hiddenPrefs.edit().putStringSet(theme?.id ?: "default", hidden).apply()
    }
    fun openStudio(editor: String = "theme", pkg: String? = null) {
        HomelyRepository(ctx).saveDraft(draft)
        ctx.startActivity(
            Intent(ctx, MainActivity::class.java)
                .putExtra("editor", editor)
                .putExtra("package", pkg)
        )
    }
    fun appInfo(pkg: String) {
        ctx.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:$pkg"),
            )
        )
    }
    fun launch(pkg: String?) {
        if (pkg == null) return
        val intent = WidgetActions.appIntent(ctx, pkg) ?: return
        val animation = if (layout.reduceMotion) "none" else layout.appAnimation
        if (animation == "none") intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options =
            when (animation) {
                "zoom" ->
                    android.app.ActivityOptions.makeCustomAnimation(
                        ctx,
                        R.anim.theme_zoom_in,
                        R.anim.theme_zoom_out,
                    )
                "slide" ->
                    android.app.ActivityOptions.makeCustomAnimation(
                        ctx,
                        R.anim.theme_slide_in,
                        R.anim.theme_slide_out,
                    )
                "fade" ->
                    android.app.ActivityOptions.makeCustomAnimation(
                        ctx,
                        android.R.anim.fade_in,
                        android.R.anim.fade_out,
                    )
                else -> android.app.ActivityOptions.makeCustomAnimation(ctx, 0, 0)
            }
        runCatching { ctx.startActivity(intent, options.toBundle()) }
            .onFailure {
                android.widget.Toast.makeText(
                        ctx,
                        "Não foi possível abrir este aplicativo.",
                        android.widget.Toast.LENGTH_SHORT,
                    )
                    .show()
            }
    }
    fun action(value: String, pkg: String?) {
        when (value) {
            "launch",
            "app" -> launch(pkg ?: layout.gestures.quickPackage)
            "personalize" -> settings = true
            "studio" -> openStudio()
            "wallpaper" ->
                ctx.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Wallpaper")
                )
            "widgets" -> onAddWidget()
            "icon_long_press" ->
                if (pkg != null)
                    when (layout.gestures.iconLongPress) {
                        "edit" -> openStudio("icon", pkg)
                        "info" -> appInfo(pkg)
                        "remove" -> hide(pkg)
                        else -> contextPackage = pkg
                    }
            "lock" -> {
                val manager =
                    ctx.getSystemService(android.app.admin.DevicePolicyManager::class.java)
                val admin = ComponentName(ctx, ScreenLockAdmin::class.java)
                if (manager.isAdminActive(admin)) manager.lockNow()
                else
                    android.widget.Toast.makeText(
                            ctx,
                            "Autorize o bloqueio nas configurações de gestos do tema.",
                            android.widget.Toast.LENGTH_LONG,
                        )
                        .show()
            }
        }
    }
    BackHandler(settings || contextPackage != null) {
        settings = false
        contextPackage = null
    }
    ThemeExperience(
        draft,
        preview = false,
        modifier = Modifier.fillMaxSize(),
        hiddenPackages = hidden,
        onAction = ::action,
    ) {
        Column {
            ids.forEach { id ->
                val info = remember(id) { AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id) }
                if (info != null) {
                    AndroidView(
                        factory = {
                            host.createView(it, id, info).apply {
                                setAppWidget(id, info)
                                updateAppWidgetSize(Bundle(), 280, 120, 400, 220)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    TextButton({ onRemoveWidget(id) }) {
                        Text(
                            "Remover widget",
                            color = color(draft.manifest.palette.text),
                            fontSize = 10.sp,
                        )
                    }
                }
            }
            if (theme == null)
                Column(Modifier.padding(vertical = 24.dp)) {
                    Text("Sua tela. Seu universo.", fontSize = 28.sp, color = Color.White)
                    Text(
                        "Abra o Homely e aplique seu primeiro tema.",
                        color = Color.White.copy(alpha = .7f),
                    )
                }
        }
    }
    if (settings)
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text("Sua tela inicial") },
            text = {
                Column {
                    Text(theme?.title ?: "Homely Home")
                    if (layout.kit.isNotEmpty())
                        ids.forEach { id ->
                            val info = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id)
                            if (info != null) {
                                AndroidView(
                                    factory = {
                                        host.createView(it, id, info).apply {
                                            setAppWidget(id, info)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(140.dp),
                                )
                                TextButton({ onRemoveWidget(id) }) {
                                    Text("Remover widget externo")
                                }
                            }
                        }
                    TextButton({
                        settings = false
                        onAddWidget()
                    }) {
                        Text("Adicionar widget de outro aplicativo")
                    }
                    TextButton({
                        settings = false
                        openStudio()
                    }) {
                        Text("Editar este tema no Studio")
                    }
                    TextButton({
                        settings = false
                        ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    }) {
                        Text("Escolher outra tela inicial")
                    }
                }
            },
            confirmButton = { TextButton({ settings = false }) { Text("Concluir") } },
        )
    contextPackage?.let { pkg ->
        AlertDialog(
            onDismissRequest = { contextPackage = null },
            title = { Text(draft.icons.firstOrNull { it.packageName == pkg }?.label ?: pkg) },
            text = {
                Column {
                    TextButton({
                        contextPackage = null
                        openStudio("icon", pkg)
                    }) {
                        Text("Editar ícone no Studio")
                    }
                    TextButton({
                        contextPackage = null
                        appInfo(pkg)
                    }) {
                        Text("Informações do aplicativo")
                    }
                    TextButton({
                        contextPackage = null
                        hide(pkg)
                    }) {
                        Text("Remover da tela inicial")
                    }
                }
            },
            confirmButton = { TextButton({ contextPackage = null }) { Text("Fechar") } },
        )
    }
}

@Composable
fun CustomStatusBar(spec: StatusBarSpec, onHomeClick: () -> Unit) {
    if (!spec.show) return

    val ctx = LocalContext.current
    var batteryLevel by remember { mutableStateOf(100) }
    var timeStr by remember { mutableStateOf("") }

    DisposableEffect(ctx, spec.clockFormat) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                        if (level != -1 && scale != -1) {
                            batteryLevel = (level * 100 / scale.toFloat()).toInt()
                        }
                    }
                    if (
                        intent?.action == Intent.ACTION_TIME_TICK ||
                            intent?.action == Intent.ACTION_TIME_CHANGED
                    ) {
                        val sdf =
                            java.text.SimpleDateFormat(
                                if (spec.clockFormat == "24h") "HH:mm" else "hh:mm a",
                                java.util.Locale.getDefault(),
                            )
                        timeStr = sdf.format(java.util.Date())
                    }
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
            }
        ctx.registerReceiver(receiver, filter)

        val sdf =
            java.text.SimpleDateFormat(
                if (spec.clockFormat == "24h") "HH:mm" else "hh:mm a",
                java.util.Locale.getDefault(),
            )
        timeStr = sdf.format(java.util.Date())

        onDispose {
            ctx.unregisterReceiver(receiver)
        }
    }

    val backdrop =
        if (spec.background == "transparent") Color.Transparent
        else color(spec.background).copy(alpha = spec.backgroundOpacity)
    val tintColor =
        if (spec.tint == "auto") {
            if (backdrop.alpha > .5f && backdrop.luminance() > .5f) Color(0xFF141421)
            else Color.White
        } else color(spec.tint)

    Row(
        Modifier.fillMaxWidth()
            .background(backdrop)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onHomeClick() })
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spec.clockFormat != "hidden") {
            Text(timeStr, color = tintColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        if (false) { // Network indicators are not simulated as real connectivity.
            Icon(Icons.Outlined.Wifi, null, tint = tintColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.SignalCellular4Bar,
                null,
                tint = tintColor,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (spec.batteryIcon != "hidden") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$batteryLevel%",
                    color = tintColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (spec.batteryIcon == "android") Icons.Outlined.BatteryStd
                    else Icons.Outlined.BatteryFull,
                    null,
                    tint = tintColor,
                    modifier = Modifier.size(20.dp).offset(y = ((-1).dp)),
                )
            }
        }
    }
}
