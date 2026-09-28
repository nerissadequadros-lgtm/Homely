package app.homely.platform

import android.Manifest
import android.app.ActivityManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.*
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.homely.data.*
import kotlinx.coroutines.flow.MutableStateFlow

/** Public Android APIs only. Missing permission/data is an explicit unavailable reading. */
object ElementSystem {
    val revision = MutableStateFlow(0L)
    private var torchRegistered = false
    private var torchId: String? = null
    private var torch: Boolean? = null
    private var cpuTime = android.os.Process.getElapsedCpuTime()
    private var wallTime = SystemClock.elapsedRealtime()
    private var cpuValue: Float? = null

    @Synchronized
    fun observeTorch(ctx: Context) {
        if (torchRegistered) return
        val camera = ctx.applicationContext.getSystemService(CameraManager::class.java)
        runCatching {
            torchId =
                camera.cameraIdList.firstOrNull {
                    camera
                        .getCameraCharacteristics(it)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
            camera.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == torchId) {
                            torch = enabled
                            revision.value++
                        }
                    }

                    override fun onTorchModeUnavailable(cameraId: String) {
                        if (cameraId == torchId) {
                            torch = null
                            revision.value++
                        }
                    }
                },
                Handler(Looper.getMainLooper()),
            )
            torchRegistered = true
        }
    }

    @Synchronized
    fun read(ctx: Context, source: String): ElementReading = runCatching {
        fun percent(value: Float) =
            ElementReading(value.coerceIn(0f, 100f), "enabled", "${value.toInt()}%")
        val connectivity = ctx.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        when (source) {
            "none",
            "auto" -> ElementReading(status = "enabled", label = "")
            "battery" ->
                (ctx.getSystemService(BatteryManager::class.java)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                    .let { if (it in 0..100) percent(it.toFloat()) else ElementReading() }
            "ram" ->
                ActivityManager.MemoryInfo().let {
                    ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
                    percent((1f - it.availMem.toFloat() / it.totalMem) * 100)
                }
            "storage" ->
                StatFs(ctx.filesDir.path).let {
                    percent((1f - it.availableBytes.toFloat() / it.totalBytes) * 100)
                }
            "volume" ->
                ctx.getSystemService(AudioManager::class.java).let {
                    percent(
                        it.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f /
                            it.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    )
                }
            "wifi" -> {
                val enabled =
                    ctx.applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled
                val connected = network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                ElementReading(
                    if (enabled) 100f else 0f,
                    if (!enabled) "disabled" else if (connected) "connected" else "disconnected",
                    if (!enabled) "Desligado" else if (connected) "Conectado" else "Sem conexão",
                )
            }
            "lan" ->
                (network?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true).let {
                    ElementReading(
                        if (it) 100f else 0f,
                        if (it) "connected" else "disconnected",
                        if (it) "Conectada" else "Sem conexão",
                    )
                }
            "bluetooth" -> {
                if (
                    Build.VERSION.SDK_INT >= 31 &&
                        ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.BLUETOOTH_CONNECT,
                        ) != PackageManager.PERMISSION_GRANTED
                )
                    ElementReading(label = "Autorize o Bluetooth")
                else {
                    val adapter = ctx.getSystemService(BluetoothManager::class.java).adapter
                    if (adapter == null) ElementReading()
                    else {
                        val on = adapter.isEnabled
                        val connected =
                            on &&
                                listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET).any {
                                    adapter.getProfileConnectionState(it) ==
                                        android.bluetooth.BluetoothAdapter.STATE_CONNECTED
                                }
                        ElementReading(
                            if (on) 100f else 0f,
                            if (!on) "disabled" else if (connected) "connected" else "disconnected",
                            if (!on) "Desligado"
                            else if (connected) "Áudio conectado" else "Sem áudio conectado",
                        )
                    }
                }
            }
            "flashlight" -> {
                observeTorch(ctx)
                torch?.let {
                    ElementReading(
                        if (it) 100f else 0f,
                        if (it) "enabled" else "disabled",
                        if (it) "Ligada" else "Desligada",
                    )
                } ?: ElementReading()
            }
            "signal" -> {
                val rssi =
                    ctx.applicationContext
                        .getSystemService(WifiManager::class.java)
                        .connectionInfo
                        .rssi
                if (
                    network?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true ||
                        rssi <= -127
                )
                    ElementReading()
                else percent(WifiManager.calculateSignalLevel(rssi, 101).toFloat())
            }
            "cpu" -> {
                val elapsed = SystemClock.elapsedRealtime()
                val used = android.os.Process.getElapsedCpuTime()
                if (elapsed - wallTime >= 1000) {
                    cpuValue =
                        ((used - cpuTime) * 100f /
                                (elapsed - wallTime) /
                                Runtime.getRuntime().availableProcessors())
                            .coerceIn(0f, 100f)
                    cpuTime = used
                    wallTime = elapsed
                }
                cpuValue?.let(::percent) ?: ElementReading(label = "Medindo CPU do Homely")
            }
            else -> ElementReading()
        }
    }
        .getOrElse { ElementReading() }

    fun toggled(ctx: Context, id: String) =
        ctx.getSharedPreferences("element-states", 0).getBoolean(id, false)

    fun execute(ctx: Context, action: String, layer: WidgetLayer) {
        try {
            when (action) {
                "none" -> return
                "toggle_state" -> {
                    ctx.getSharedPreferences("element-states", 0)
                        .edit()
                        .putBoolean(layer.id, !toggled(ctx, layer.id))
                        .apply()
                    revision.value++
                    StudioWidgets.updateAll(ctx)
                }
                "animate" -> {
                    revision.value++
                }
                "flashlight" -> {
                    if (
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) !=
                            PackageManager.PERMISSION_GRANTED
                    )
                        ctx.startActivity(
                            WidgetActions.detail(ctx, layer)
                                .putExtra("requestTorch", true)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    else toggleTorch(ctx)
                }
                "volume_up",
                "volume_down" -> {
                    ctx.getSystemService(AudioManager::class.java)
                        .adjustStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            if (action == "volume_up") AudioManager.ADJUST_RAISE
                            else AudioManager.ADJUST_LOWER,
                            AudioManager.FLAG_SHOW_UI,
                        )
                    revision.value++
                }
                "auto" -> {
                    val target =
                        when (layer.source()) {
                            "wifi",
                            "bluetooth",
                            "flashlight",
                            "volume",
                            "storage" -> layer.source()
                            "ram",
                            "cpu",
                            "lan",
                            "signal" -> "settings"
                            else -> null
                        }
                    if (target != null) execute(ctx, target, layer)
                    else WidgetActions.launch(ctx, layer.copy(tapAction = "auto"))
                }
                "app" -> WidgetActions.launch(ctx, layer.copy(tapAction = action))
                else -> {
                    val target =
                        when (action) {
                            "wifi" ->
                                if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI
                                else Settings.ACTION_WIFI_SETTINGS
                            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                            "volume" ->
                                if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_VOLUME
                                else Settings.ACTION_SOUND_SETTINGS
                            "alarm" -> android.provider.AlarmClock.ACTION_SHOW_ALARMS
                            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
                            "calendar" -> null
                            else -> Settings.ACTION_SETTINGS
                        }
                    val intent =
                        if (target != null) Intent(target)
                        else
                            Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "content://com.android.calendar/time/${System.currentTimeMillis()}"
                                ),
                            )
                    ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                    ctx,
                    "Esta ação não está disponível neste aparelho.",
                    android.widget.Toast.LENGTH_SHORT,
                )
                .show()
        }
    }

    fun toggleTorch(ctx: Context) {
        observeTorch(ctx)
        val id = torchId ?: error("Lanterna indisponível")
        ctx.getSystemService(CameraManager::class.java).setTorchMode(id, torch != true)
    }
}
