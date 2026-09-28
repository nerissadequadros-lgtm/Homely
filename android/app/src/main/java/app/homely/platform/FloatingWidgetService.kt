package app.homely.platform

import android.app.*
import android.app.usage.*
import android.content.*
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import app.homely.MainActivity
import app.homely.R
import java.util.Calendar
import kotlinx.coroutines.*

/** Visible only while a known HOME activity is foreground. No usage data leaves this device. */
class FloatingWidgetService : Service() {
    private val weatherScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var window: View? = null
    private lateinit var manager: WindowManager
    private var snapshot: SavedWidget? = null
    private var foreground: String? = null
    private var lastEvent = 0L
    private var token: String? = null
    private var px = 24
    private var py = 240
    private val prefs
        get() = getSharedPreferences("floating-widget", 0)

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(WindowManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "pause") {
            val until =
                Calendar.getInstance()
                    .apply {
                        add(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    .timeInMillis
            prefs.edit().putLong("pausedUntil", until).apply()
            UsageModes(this).floating = false
            stopSelf()
            return START_NOT_STICKY
        }
        token = intent?.getStringExtra("token") ?: prefs.getString("token", null)
        snapshot = token?.let { StudioWidgets.read(this, it) }
        if (snapshot == null || !Settings.canDrawOverlays(this) || !hasUsageAccess(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        prefs.edit().putString("token", token).putLong("pausedUntil", 0).apply()
        UsageModes(this).floating = true
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(
                "floating",
                "Widgets flutuantes",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val pause =
            PendingIntent.getService(
                this,
                90,
                Intent(this, FloatingWidgetService::class.java).setAction("pause"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val open =
            PendingIntent.getActivity(
                this,
                91,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(this, "floating")
                .setSmallIcon(R.drawable.ic_homely)
                .setContentTitle("Widget Homely ativo")
                .setContentText("Visível somente na tela inicial. Arraste pela alça para mover.")
                .setOngoing(true)
                .setContentIntent(open)
                .addAction(0, "Pausar por hoje", pause)
                .build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(
                45,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        else startForeground(45, notification)
        px = prefs.getInt("x", 24)
        py = prefs.getInt("y", 240)
        lastEvent = System.currentTimeMillis() - 60000
        handler.removeCallbacksAndMessages(null)
        handler.post(tick)
        return START_NOT_STICKY
    }

    private val tick =
        object : Runnable {
            override fun run() {
                try {
                    if (
                        !Settings.canDrawOverlays(this@FloatingWidgetService) ||
                            !hasUsageAccess(this@FloatingWidgetService)
                    ) {
                        stopSelf()
                        return
                    }
                    val now = System.currentTimeMillis()
                    val events =
                        getSystemService(UsageStatsManager::class.java).queryEvents(lastEvent, now)
                    val event = UsageEvents.Event()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED)
                            foreground = event.packageName
                    }
                    lastEvent = now
                    val home =
                        packageManager
                            .resolveActivity(
                                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
                            )
                            ?.activityInfo
                            ?.packageName
                    val visible =
                        foreground == home &&
                            home != null &&
                            home != "android" &&
                            getSystemService(PowerManager::class.java).isInteractive &&
                            !getSystemService(KeyguardManager::class.java).isKeyguardLocked
                    if (visible) show() else hide()
                } catch (_: Exception) {
                    hide()
                }
                handler.postDelayed(this, 600)
            }
        }
    private var lastRendered = 0L

    private fun show() {
        val saved = snapshot ?: return
        if (window != null) {
            if (System.currentTimeMillis() - lastRendered > 30000) {
                hide()
            } else return
        }
        weatherScope.launch {
            saved.spec.layers
                .filter { it.type == "weather" }
                .forEach { WeatherStore.refresh(this@FloatingWidgetService, it.weather) }
        }
        val density = resources.displayMetrics.density
        val width =
            (resources.displayMetrics.widthPixels / density * .85f).toInt().coerceAtMost(400)
        val outer =
            android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
            }
        val handle =
            android.widget.TextView(this).apply {
                text = "⠿  Homely · arraste para mover"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(0xCC191725.toInt())
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(8, 8, 8, 8)
            }
        outer.addView(handle, android.widget.LinearLayout.LayoutParams(-1, (28 * density).toInt()))
        outer.addView(
            StudioWidgets.views(this, saved, width, saved.spec.height).apply(this, outer),
            android.widget.LinearLayout.LayoutParams(-1, (saved.spec.height * density).toInt()),
        )
        val params =
            WindowManager.LayoutParams(
                    (width * density).toInt(),
                    ((saved.spec.height + 28) * density).toInt(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                )
                .apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = px
                    y = py
                }
        var downX = 0f
        var downY = 0f
        var initialX = 0
        var initialY = 0
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    initialX = params.x
                    initialY = params.y
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x =
                        (initialX + event.rawX - downX)
                            .toInt()
                            .coerceIn(
                                0,
                                (resources.displayMetrics.widthPixels - params.width).coerceAtLeast(
                                    0
                                ),
                            )
                    params.y =
                        (initialY + event.rawY - downY)
                            .toInt()
                            .coerceIn(
                                0,
                                (resources.displayMetrics.heightPixels - params.height - 60)
                                    .coerceAtLeast(0),
                            )
                    manager.updateViewLayout(outer, params)
                }
                MotionEvent.ACTION_UP -> {
                    px = params.x
                    py = params.y
                    prefs.edit().putInt("x", px).putInt("y", py).apply()
                }
            }
            true
        }
        manager.addView(outer, params)
        window = outer
        lastRendered = System.currentTimeMillis()
    }

    private fun hide() {
        window?.let { runCatching { manager.removeView(it) } }
        window = null
    }

    override fun onDestroy() {
        weatherScope.cancel()
        handler.removeCallbacksAndMessages(null)
        hide()
        UsageModes(this).floating = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        fun hasUsageAccess(ctx: Context): Boolean =
            ctx.getSystemService(AppOpsManager::class.java)
                .checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    ctx.packageName,
                ) == AppOpsManager.MODE_ALLOWED

        fun start(ctx: Context, token: String) {
            check(Settings.canDrawOverlays(ctx) && hasUsageAccess(ctx)) {
                "Autorize a sobreposição e o acesso de uso nas configurações."
            }
            ctx.startForegroundService(
                Intent(ctx, FloatingWidgetService::class.java).putExtra("token", token)
            )
        }
    }
}
