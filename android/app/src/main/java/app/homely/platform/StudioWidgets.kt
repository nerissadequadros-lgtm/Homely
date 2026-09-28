package app.homely.platform

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.drawable.toBitmap
import app.homely.R
import app.homely.data.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class SavedWidget(
    val spec: WidgetSpec,
    val images: Map<String, String>,
    val fonts: Map<String, String> = emptyMap(),
)

object StudioWidgets {
    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("studio-widgets", Context.MODE_PRIVATE)

    fun save(
        ctx: Context,
        spec: WidgetSpec,
        images: Map<String, String>,
        fonts: Map<String, String> = emptyMap(),
    ): String {
        spec.validate()
        require(fonts.keys.all { Regex("[A-Za-z0-9_-]{1,80}").matches(it) })
        val token = UUID.randomUUID().toString()
        val directory = File(ctx.filesDir, "studio-widgets/$token").apply { mkdirs() }
        val keys = spec.imageKeys()
        require(keys.all { images[it]?.let { path -> File(path).isFile } == true }) {
            "Selecione as imagens das camadas e do fundo do widget."
        }
        val copied =
            images
                .filterKeys { it in keys }
                .mapValues { (id, path) ->
                    File(path).copyTo(File(directory, "$id.${File(path).extension}")).path
                }
        require(keys.all { it in copied }) { "Selecione uma imagem para cada camada de imagem." }
        check(
            prefs(ctx)
                .edit()
                .putString(
                    token,
                    HomelyRepository(ctx)
                        .json
                        .encodeToString(
                            SavedWidget(
                                spec,
                                copied,
                                fonts.mapValues { (id, path) ->
                                    File(path)
                                        .copyTo(File(directory, "font-$id.${File(path).extension}"))
                                        .path
                                },
                            )
                        ),
                )
                .commit()
        )
        return token
    }

    fun read(ctx: Context, token: String): SavedWidget? = runCatching {
        HomelyRepository(ctx)
            .json
            .decodeFromString<SavedWidget>(prefs(ctx).getString(token, null) ?: return null)
    }
        .getOrNull()

    fun bind(ctx: Context, id: Int, token: String) {
        check(read(ctx, token) != null)
        prefs(ctx).edit().putString("id-$id", token).commit()
        update(ctx, id)
        ctx.sendBroadcast(
            Intent(ctx, StudioWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
        )
    }

    fun pin(
        ctx: Context,
        spec: WidgetSpec,
        images: Map<String, String>,
        fonts: Map<String, String> = emptyMap(),
    ) {
        val manager = AppWidgetManager.getInstance(ctx)
        check(manager.isRequestPinAppWidgetSupported) {
            "Este launcher não aceita pedidos automáticos. Adicione Homely Studio pelo seletor de widgets da sua tela inicial."
        }
        val token = save(ctx, spec, images, fonts)
        val callback =
            Intent(ctx, StudioWidgetProvider::class.java)
                .setAction("app.homely.PIN_STUDIO_WIDGET")
                .setData(android.net.Uri.parse("homely://widget/$token"))
                .putExtra("token", token)
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        manager.requestPinAppWidget(
            ComponentName(ctx, StudioWidgetProvider::class.java),
            Bundle().apply {
                putParcelable(
                    AppWidgetManager.EXTRA_APPWIDGET_PREVIEW,
                    views(ctx, read(ctx, token)!!, 300, spec.height),
                )
            },
            PendingIntent.getBroadcast(ctx, token.hashCode(), callback, flags),
        )
    }

    fun update(ctx: Context, id: Int) {
        val token = prefs(ctx).getString("id-$id", null) ?: return
        val saved = read(ctx, token) ?: return
        val manager = AppWidgetManager.getInstance(ctx)
        val options = manager.getAppWidgetOptions(id)
        manager.updateAppWidget(
            id,
            views(
                ctx,
                saved,
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 300).coerceIn(80, 600),
                options
                    .getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, saved.spec.height)
                    .coerceIn(64, 600),
            ),
        )
    }

    fun updateAll(ctx: Context) {
        val manager = AppWidgetManager.getInstance(ctx)
        manager.getAppWidgetIds(ComponentName(ctx, StudioWidgetProvider::class.java)).forEach {
            update(ctx, it)
        }
    }

    suspend fun refreshWeather(ctx: Context) {
        val ids =
            AppWidgetManager.getInstance(ctx)
                .getAppWidgetIds(ComponentName(ctx, StudioWidgetProvider::class.java))
        val options =
            ids.toList()
                .mapNotNull { prefs(ctx).getString("id-$it", null) }
                .mapNotNull { read(ctx, it) }
                .flatMap { it.spec.layers }
                .filter { it.type == "weather" }
                .map { it.weather }
                .distinct()
        kotlinx.coroutines.coroutineScope {
            options.map { w -> async { WeatherStore.refresh(ctx, w) } }.forEach { it.await() }
        }
        updateAll(ctx)
    }

    fun forget(ctx: Context, id: Int) {
        prefs(ctx).edit().remove("id-$id").apply()
    }

    private fun layerColor(l: WidgetLayer): Int =
        (android.graphics.Color.parseColor(l.color) and 0xFFFFFF) or
            ((l.opacity * 255).toInt() shl 24)

    private fun renderLayer(ctx: Context, l: WidgetLayer, fonts: Map<String, String>): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor(l.color)
                strokeWidth = 7f
            }
        val percent =
            if (l.type == "battery_ring")
                (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    .coerceIn(0, 100) / 100f
            else l.progress
        when (l.type) {
            "shape" ->
                when (l.shape) {
                    "circle" -> canvas.drawCircle(128f, 64f, 60f, paint)
                    "line" -> canvas.drawRect(0f, 62f, 256f, 66f, paint)
                    else -> canvas.drawRoundRect(RectF(0f, 0f, 256f, 128f), 16f, 16f, paint)
                }
            "progress" -> {
                paint.alpha = 50
                canvas.drawRoundRect(RectF(0f, 52f, 256f, 76f), 12f, 12f, paint)
                paint.alpha = 255
                canvas.drawRoundRect(RectF(0f, 52f, 256f * percent, 76f), 12f, 12f, paint)
            }
            "battery_ring" -> {
                paint.style = Paint.Style.STROKE
                canvas.drawArc(RectF(72f, 8f, 184f, 120f), -90f, 360 * percent, false, paint)
                paint.style = Paint.Style.FILL
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 24f
                canvas.drawText((percent * 100).toInt().toString() + "%", 128f, 72f, paint)
            }
            else -> {
                val textPaint =
                    android.text.TextPaint(paint).apply {
                        textSize =
                            if (l.type in listOf("calendar", "weather"))
                                l.fontSize.toFloat().coerceAtMost(22f)
                            else l.fontSize.toFloat().coerceAtMost(60f)
                        typeface =
                            l.fontId?.let { fonts[it] }?.let { FontLibrary.typeface(it) }
                                ?: Typeface.create(
                                    if (l.type == "calendar") "monospace"
                                    else if (l.fontFamily == "serif") "serif" else "sans-serif",
                                    if (l.weight >= 600) Typeface.BOLD else Typeface.NORMAL,
                                )
                    }
                val text =
                    when (l.type) {
                        "weather" -> WeatherStore.text(ctx, l)
                        "calendar" -> calendarText(l)
                        "phone" -> "☎  " + l.text.ifBlank { "Telefone" }
                        "note" -> WidgetNotes.text(ctx, l)
                        "battery" ->
                            ctx.getSystemService(BatteryManager::class.java)
                                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                                .coerceIn(0, 100)
                                .toString() + "%"
                        else -> l.text
                    }
                val layout =
                    android.text.StaticLayout.Builder.obtain(text, 0, text.length, textPaint, 248)
                        .setAlignment(
                            when (l.alignment) {
                                "start" -> android.text.Layout.Alignment.ALIGN_NORMAL
                                "end" -> android.text.Layout.Alignment.ALIGN_OPPOSITE
                                else -> android.text.Layout.Alignment.ALIGN_CENTER
                            }
                        )
                        .setMaxLines(9)
                        .build()
                canvas.translate(4f, ((128 - layout.height) / 2f).coerceAtLeast(0f))
                layout.draw(canvas)
            }
        }
        return bitmap
    }

    fun views(ctx: Context, saved: SavedWidget, width: Int, height: Int): RemoteViews {
        val w = saved.spec
        val density = ctx.resources.displayMetrics.density
        val pxW = (width * density).toInt()
        val pxH = (height * density).toInt()
        val root = RemoteViews(ctx.packageName, R.layout.widget_studio)
        val bg = WidgetImageBitmap.background(w, saved.images, pxW, pxH, w.radius * density)
        root.setImageViewBitmap(R.id.studio_background, bg)
        w.tapPackage?.let {
            WidgetActions.pendingIntent(
                    ctx,
                    WidgetLayer(id = w.id, tapAction = "app", tapPackage = it),
                )
                ?.let { action -> root.setOnClickPendingIntent(R.id.studio_background, action) }
        }
        root.removeAllViews(R.id.studio_layers)
        w.displayLayers()
            .filter { it.visible }
            .sortedBy { it.zIndex }
            .forEach { l ->
                val item =
                    RemoteViews(
                        ctx.packageName,
                        when (l.fontFamily) {
                            "serif" -> R.layout.widget_studio_layer_serif
                            "mono" -> R.layout.widget_studio_layer_mono
                            else -> R.layout.widget_studio_layer
                        },
                    )
                item.setViewPadding(
                    R.id.layer_root,
                    (l.x * pxW).toInt(),
                    (l.y * pxH).toInt(),
                    ((1 - l.x - l.width) * pxW).toInt().coerceAtLeast(0),
                    ((1 - l.y - l.height) * pxH).toInt().coerceAtLeast(0),
                )
                val interactive = l.interactive()
                val visualState =
                    l.activeState(
                        ElementSystem.read(ctx, l.source()),
                        false,
                        ElementSystem.toggled(ctx, l.id),
                    )
                val liveTime =
                    l.type in listOf("clock", "date", "analog", "countdown") &&
                        visualState?.imageId == null &&
                        visualState?.text == null
                val clock = liveTime && (l.type == "clock" || l.type == "date")
                val image =
                    (interactive && !liveTime) ||
                        l.type in
                            listOf(
                                "image",
                                "shortcut",
                                "shape",
                                "progress",
                                "battery_ring",
                                "weather",
                                "calendar",
                                "phone",
                            ) ||
                        (l.fontId != null && !clock && l.type != "countdown")
                val special =
                    (liveTime && l.type in listOf("analog", "countdown")) ||
                        (!interactive && l.type == "spacer")
                item.setViewVisibility(
                    R.id.layer_analog,
                    if (liveTime && l.type == "analog") View.VISIBLE else View.GONE,
                )
                item.setViewVisibility(
                    R.id.layer_countdown,
                    if (liveTime && l.type == "countdown") View.VISIBLE else View.GONE,
                )
                if (l.type == "countdown") {
                    item.setChronometer(
                        R.id.layer_countdown,
                        android.os.SystemClock.elapsedRealtime() +
                            (l.targetTime - System.currentTimeMillis()).coerceAtLeast(0),
                        null,
                        l.targetTime > System.currentTimeMillis(),
                    )
                    item.setChronometerCountDown(R.id.layer_countdown, true)
                    item.setTextColor(R.id.layer_countdown, layerColor(l))
                }
                item.setViewVisibility(R.id.layer_clock, if (clock) View.VISIBLE else View.GONE)
                item.setViewVisibility(
                    R.id.layer_text,
                    if (!clock && !image && !special) View.VISIBLE else View.GONE,
                )
                item.setViewVisibility(R.id.layer_image, if (image) View.VISIBLE else View.GONE)
                val fit = (width / 320f).coerceIn(.5f, 1.5f)
                item.setTextColor(R.id.layer_text, layerColor(l))
                item.setTextViewTextSize(
                    R.id.layer_text,
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    l.fontSize * fit,
                )
                item.setTextColor(
                    R.id.layer_clock,
                    layerColor(
                        l.copy(
                            color = visualState?.color ?: l.color,
                            opacity = l.opacity * (visualState?.opacity ?: 1f),
                        )
                    ),
                )
                if (interactive && liveTime) {
                    item.setImageViewBitmap(
                        R.id.layer_element_background,
                        ElementBitmap.render(
                            ctx,
                            l,
                            saved.images,
                            saved.fonts,
                            (pxW * l.width).toInt(),
                            (pxH * l.height).toInt(),
                            false,
                        ),
                    )
                    val padding = (l.elementPadding * density).toInt()
                    item.setViewPadding(R.id.layer_clock, padding, padding, padding, padding)
                }
                item.setTextViewTextSize(
                    R.id.layer_clock,
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    l.fontSize * fit,
                )
                if (clock) {
                    val format = clockPattern(l)
                    item.setString(
                        R.id.layer_clock,
                        "setTimeZone",
                        l.clock.zone.ifBlank { java.util.TimeZone.getDefault().id },
                    )
                    item.setCharSequence(R.id.layer_clock, "setFormat24Hour", format)
                    item.setCharSequence(R.id.layer_clock, "setFormat12Hour", format)
                } else if (image) {
                    val bitmap = runCatching {
                        if (interactive)
                            ElementBitmap.render(
                                ctx,
                                l,
                                saved.images,
                                saved.fonts,
                                (pxW * l.width).toInt(),
                                (pxH * l.height).toInt(),
                            )
                        else
                            when (l.type) {
                                "image" ->
                                    WidgetImageBitmap.image(
                                        saved.images[l.id] ?: error("Imagem ausente"),
                                        (pxW * l.width).toInt(),
                                        (pxH * l.height).toInt(),
                                        l.imageScale,
                                        l.imageZoom,
                                        l.imageOffsetX,
                                        l.imageOffsetY,
                                    )
                                "shortcut" ->
                                    ctx.packageManager
                                        .getApplicationIcon(l.tapPackage!!)
                                        .toBitmap(128, 128)
                                else ->
                                    if (l.type in listOf("weather", "calendar", "phone"))
                                        LiveWidgetBitmap.render(
                                            ctx,
                                            l,
                                            saved.fonts,
                                            (width * l.width).toInt(),
                                            (height * l.height).toInt(),
                                        )
                                    else renderLayer(ctx, l, saved.fonts)
                            }
                    }
                        .getOrNull()
                    if (bitmap != null) item.setImageViewBitmap(R.id.layer_image, bitmap)
                } else
                    item.setTextViewText(
                        R.id.layer_text,
                        if (l.type == "battery")
                            (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                                .coerceIn(0, 100)
                                .toString() + "%"
                        else if (l.type == "note") WidgetNotes.text(ctx, l) else l.text,
                    )
                WidgetActions.pendingIntent(
                        ctx,
                        if (l.tapPackage == null && w.tapPackage != null)
                            l.copy(tapPackage = w.tapPackage)
                        else l,
                    )
                    ?.let { item.setOnClickPendingIntent(R.id.layer_content, it) }
                item.setInt(
                    R.id.layer_image,
                    "setImageAlpha",
                    if (interactive) 255 else (l.opacity * 255).toInt(),
                )
                val gravity =
                    when (l.alignment) {
                        "start" ->
                            android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                        "end" -> android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
                        else -> android.view.Gravity.CENTER
                    }
                item.setInt(R.id.layer_text, "setGravity", gravity)
                item.setInt(R.id.layer_clock, "setGravity", gravity)
                root.addView(R.id.studio_layers, item)
            }
        return root
    }
}

class StudioWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { StudioWidgets.update(context, it) }
        val result = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.withTimeout(24000) { StudioWidgets.refreshWeather(context) }
            } catch (_: Exception) {} finally {
                result.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        StudioWidgets.update(context, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        ids.forEach { StudioWidgets.forget(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "app.homely.PIN_STUDIO_WIDGET") {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val token = intent.getStringExtra("token") ?: return
            if (
                AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ==
                    ComponentName(context, StudioWidgetProvider::class.java)
            )
                StudioWidgets.bind(context, id, token)
        }
    }
}
