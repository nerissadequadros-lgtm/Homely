package app.homely.platform

import android.content.Context
import app.homely.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class WeatherPoint(
    val time: Long,
    val temperature: Double,
    val humidity: Double? = null,
    val wind: Double = 0.0,
    val symbol: String = "cloudy",
    val rain: Double = 0.0,
)

@Serializable
data class WeatherData(
    val time: Long,
    val temperature: Double,
    val humidity: Double? = null,
    val wind: Double = 0.0,
    val symbol: String = "cloudy",
    val rain: Double = 0.0,
    val minimum: Double,
    val maximum: Double,
    val hourly: List<WeatherPoint> = emptyList(),
    val updatedAt: Long,
    val fetchedAt: Long,
    val stale: Boolean = false,
    val attribution: String = "MET Norway · CC BY 4.0",
)

@Serializable
data class WeatherCity(
    val id: String,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "",
)

@Serializable data class WeatherCities(val items: List<WeatherCity>)

object WeatherStore {
    val revision = MutableStateFlow(0L)
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val failures = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun key(w: WeatherOptions) = "${w.latitude},${w.longitude}"

    fun cached(ctx: Context, w: WeatherOptions): WeatherData? = runCatching {
        HomelyRepository(ctx)
            .json
            .decodeFromString<WeatherData>(
                ctx.getSharedPreferences("weather-cache", 0).getString(key(w), null) ?: return null
            )
    }
        .getOrNull()

    suspend fun refresh(ctx: Context, w: WeatherOptions, force: Boolean = false): WeatherData? {
        if (w.latitude == null || w.longitude == null) return null
        val key = key(w)
        return locks
            .getOrPut(key) { Mutex() }
            .withLock {
                val old = cached(ctx, w)
                if (!force && old != null && System.currentTimeMillis() - old.fetchedAt < 1200000)
                    return@withLock old
                if (!force && (failures[key] ?: 0) > System.currentTimeMillis()) return@withLock old
                try {
                    val data =
                        withTimeout(20000) {
                            HomelyRepository(ctx)
                                .get<WeatherData>(
                                    "/v1/weather?lat=${w.latitude}&lon=${w.longitude}"
                                )
                        }
                    ctx.getSharedPreferences("weather-cache", 0)
                        .edit()
                        .putString(key, HomelyRepository(ctx).json.encodeToString(data))
                        .apply()
                    failures.remove(key)
                    revision.value++
                    data
                } catch (e: Exception) {
                    if (e is CancellationException && e !is TimeoutCancellationException) throw e
                    failures[key] = System.currentTimeMillis() + 300000
                    old
                }
            }
    }

    fun temperature(value: Double, unit: String) =
        "${kotlin.math.round(if(unit=="f")value*1.8+32 else value).toInt()}°${unit.uppercase()}"

    fun description(symbol: String) =
        when {
            symbol.contains("thunder") -> "Trovoadas"
            symbol.contains("snow") -> "Neve"
            symbol.contains("sleet") -> "Chuva e neve"
            symbol.contains("rain") -> "Chuva"
            symbol.contains("fog") -> "Neblina"
            symbol.contains("partlycloudy") -> "Parcialmente nublado"
            symbol.contains("fair") -> "Poucas nuvens"
            symbol.contains("clearsky") -> "Céu limpo"
            else -> "Nublado"
        }

    fun symbol(symbol: String) =
        when {
            symbol.contains("thunder") -> "ϟ"
            symbol.contains("snow") -> "❄"
            symbol.contains("rain") -> "☂"
            symbol.contains("clearsky") && symbol.contains("night") -> "☾"
            symbol.contains("clearsky") -> "☀"
            else -> "☁"
        }

    fun text(ctx: Context, l: WidgetLayer): String {
        val w = l.weather
        if (w.latitude == null) return "Escolha uma cidade"
        val d = cached(ctx, w) ?: return "Sem previsão\nToque para atualizar"
        return buildList {
                if (w.showCity) add(w.city)
                add(
                    (if (w.showIcon) symbol(d.symbol) + "  " else "") +
                        temperature(d.temperature, w.unit)
                )
                if (w.showCondition) add(description(d.symbol))
                if (w.showRange)
                    add("↓ ${temperature(d.minimum,w.unit)}  ↑ ${temperature(d.maximum,w.unit)}")
                if (w.showHumidity) add("Umidade ${d.humidity?.toInt() ?: "—"}%")
                if (w.showWind) add("Vento ${d.wind.toInt()} km/h")
                if (d.stale || System.currentTimeMillis() - d.fetchedAt > 7200000)
                    add("Dados anteriores")
            }
            .joinToString("\n")
    }
}

fun clockPattern(l: WidgetLayer) =
    if (l.type == "date")
        when (l.clock.dateStyle) {
            "short" -> "dd/MM/yyyy"
            "weekday" -> "EEEE"
            else -> "EEE, d MMM"
        }
    else
        (if (l.clock.format == "12") "hh:mm" else "HH:mm") +
            (if (l.clock.seconds) ":ss" else "") +
            (if (l.clock.format == "12") " a" else "")

fun layerDate(l: WidgetLayer, now: Long) =
    java.text
        .SimpleDateFormat(clockPattern(l), java.util.Locale.getDefault())
        .apply {
            if (l.clock.zone.isNotBlank()) timeZone = java.util.TimeZone.getTimeZone(l.clock.zone)
        }
        .format(java.util.Date(now))

fun calendarText(l: WidgetLayer, now: Long = System.currentTimeMillis()): String {
    val date =
        java.time.Instant.ofEpochMilli(now)
            .atZone(
                if (l.clock.zone.isBlank()) java.time.ZoneId.systemDefault()
                else java.time.ZoneId.of(l.clock.zone)
            )
            .toLocalDate()
            .plusMonths(l.monthOffset.toLong())
    val month = java.time.YearMonth.from(date)
    val title =
        month.format(
            java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.getDefault())
        )
    val cells =
        List(month.atDay(1).dayOfWeek.value % 7) { "  " } +
            (1..month.lengthOfMonth()).map { it.toString().padStart(2, ' ') }
    return title +
        "\nD  S  T  Q  Q  S  S\n" +
        cells.chunked(7).joinToString("\n") { it.joinToString(" ") }
}
