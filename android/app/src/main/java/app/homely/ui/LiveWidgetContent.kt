package app.homely.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.homely.data.*
import app.homely.platform.*
import kotlinx.coroutines.*

@Composable
fun WeatherContent(
    layer: WidgetLayer,
    modifier: Modifier = Modifier,
    fonts: Map<String, String> = emptyMap(),
) {
    val ctx = LocalContext.current
    val w = layer.weather
    val revision by WeatherStore.revision.collectAsState()
    var loading by remember(w.latitude, w.longitude) { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(w.latitude, w.longitude, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            loading = true
            WeatherStore.refresh(ctx, w)
            loading = false
            while (true) {
                delay(1200000)
                WeatherStore.refresh(ctx, w)
            }
        }
    }
    val data = remember(revision, w.latitude, w.longitude) { WeatherStore.cached(ctx, w) }
    val tint = color(layer.color)
    if (w.latitude == null || data == null) {
        Column(
            modifier.padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Cloud, null, tint = tint, modifier = Modifier.size(30.dp))
            Text(
                if (w.latitude == null) "Escolha uma cidade"
                else if (loading) "Buscando previsão…" else "Sem previsão disponível",
                color = tint,
                fontSize = layer.fontSize.coerceAtMost(16).sp,
            )
            if (w.latitude != null && !loading)
                Text(
                    "Toque para tentar novamente",
                    color = tint.copy(alpha = .65f),
                    fontSize = 10.sp,
                )
        }
        return
    }
    val icon =
        when {
            data.symbol.contains("thunder") -> Icons.Outlined.Thunderstorm
            data.symbol.contains("snow") -> Icons.Outlined.AcUnit
            data.symbol.contains("rain") -> Icons.Outlined.WaterDrop
            data.symbol.contains("clearsky") && data.symbol.contains("night") ->
                Icons.Outlined.DarkMode
            data.symbol.contains("clearsky") -> Icons.Outlined.WbSunny
            else -> Icons.Outlined.Cloud
        }
    Column(
        modifier.padding(8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment =
            when (layer.alignment) {
                "start" -> Alignment.Start
                "end" -> Alignment.End
                else -> Alignment.CenterHorizontally
            },
    ) {
        if (w.showCity)
            Text(
                w.city,
                color = tint.copy(alpha = .72f),
                fontSize = (layer.fontSize * .7f).coerceIn(9f, 18f).sp,
                maxLines = 1,
            )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                WeatherStore.temperature(data.temperature, w.unit),
                color = tint,
                fontSize =
                    (layer.fontSize * if (w.style == "compact") 1.2f else 1.8f)
                        .coerceIn(16f, 64f)
                        .sp,
                fontWeight = FontWeight(layer.weight),
                fontFamily =
                    layer.fontId
                        ?.let { fonts[it] }
                        ?.let {
                            runCatching {
                                androidx.compose.ui.text.font.FontFamily(
                                    androidx.compose.ui.text.font.Font(java.io.File(it))
                                )
                            }
                                .getOrNull()
                        } ?: studioFont(layer.fontFamily),
                modifier = Modifier.weight(1f, false),
            )
            if (w.showIcon)
                Icon(
                    icon,
                    null,
                    tint = tint,
                    modifier = Modifier.size((layer.fontSize * 1.6f).coerceIn(20f, 48f).dp),
                )
        }
        if (w.showCondition)
            Text(
                WeatherStore.description(data.symbol),
                color = tint,
                fontSize = (layer.fontSize * .7f).coerceIn(9f, 18f).sp,
            )
        if (w.showRange)
            Text(
                "↓ ${WeatherStore.temperature(data.minimum,w.unit)}   ↑ ${WeatherStore.temperature(data.maximum,w.unit)}",
                color = tint.copy(alpha = .7f),
                fontSize = (layer.fontSize * .6f).coerceIn(9f, 16f).sp,
            )
        if (w.showWind || w.showHumidity)
            Text(
                listOfNotNull(
                        if (w.showHumidity) "${data.humidity?.toInt()?:"—"}% umidade" else null,
                        if (w.showWind) "${data.wind.toInt()} km/h" else null,
                    )
                    .joinToString(" · "),
                color = tint.copy(alpha = .7f),
                fontSize = 10.sp,
            )
        if (w.style == "forecast")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                data.hourly.take(4).forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            java.text
                                .SimpleDateFormat("HH'h'", java.util.Locale.getDefault())
                                .format(java.util.Date(p.time)),
                            color = tint.copy(alpha = .6f),
                            fontSize = 9.sp,
                        )
                        Text(
                            WeatherStore.temperature(p.temperature, w.unit),
                            color = tint,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        if (data.stale || System.currentTimeMillis() - data.fetchedAt > 7200000)
            Text(
                "Dados anteriores · toque para atualizar",
                color = tint.copy(alpha = .7f),
                fontSize = 9.sp,
            )
    }
}

@Composable
fun WeatherProperties(vm: HomelyViewModel, l: WidgetLayer, onChange: (WidgetLayer) -> Unit) {
    val w = l.weather
    var search by remember(l.id) { mutableStateOf(w.city) }
    var results by remember { mutableStateOf<List<WeatherCity>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Text("Previsão de verdade", style = MaterialTheme.typography.titleMedium)
    Text(
        "Escolha a cidade do widget. Não precisamos acessar seu GPS.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Field(search, { search = it }, "Cidade · ex.: Ponta Grossa")
    OutlinedButton(
        {
            loading = true
            error = null
            vm.action {
                try {
                    results =
                        vm.repo
                            .get<WeatherCities>(
                                "/v1/weather/cities?q=" +
                                    java.net.URLEncoder.encode(search, "UTF-8")
                            )
                            .items
                    if (results.isEmpty())
                        error = "Cidade não encontrada. Tente uma cidade próxima."
                } catch (e: Exception) {
                    error = "Não foi possível buscar cidades."
                } finally {
                    loading = false
                }
            }
        },
        enabled = search.trim().length >= 2 && !loading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (loading) "Buscando…" else "Buscar cidade")
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    results.forEach { c ->
        TextButton({
            onChange(
                l.copy(
                    weather =
                        w.copy(
                            city = "${c.name}, ${c.country}",
                            latitude = c.latitude,
                            longitude = c.longitude,
                        )
                )
            )
            results = emptyList()
            search = c.name
        }) {
            Text("${c.name} · ${c.country}")
        }
    }
    if (w.latitude != null)
        Text("Local do widget: ${w.city}", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("Celsius", w.unit == "c") { onChange(l.copy(weather = w.copy(unit = "c"))) }
        Pill("Fahrenheit", w.unit == "f") { onChange(l.copy(weather = w.copy(unit = "f"))) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("editorial" to "Editorial", "compact" to "Compacto", "forecast" to "Por hora")
            .forEach { (id, name) ->
                Pill(name, w.style == id) { onChange(l.copy(weather = w.copy(style = id))) }
            }
    }
    listOf(
            Triple("Nome da cidade", w.showCity, { v: Boolean -> w.copy(showCity = v) }),
            Triple("Condição", w.showCondition, { v: Boolean -> w.copy(showCondition = v) }),
            Triple("Ícone do clima", w.showIcon, { v: Boolean -> w.copy(showIcon = v) }),
            Triple(
                "Mínima e máxima · próximas 24 h",
                w.showRange,
                { v: Boolean -> w.copy(showRange = v) },
            ),
            Triple("Umidade", w.showHumidity, { v: Boolean -> w.copy(showHumidity = v) }),
            Triple("Vento", w.showWind, { v: Boolean -> w.copy(showWind = v) }),
        )
        .forEach { (title, value, change) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(value, { onChange(l.copy(weather = change(it))) })
            }
        }
    Text(
        "Previsões MET Norway · cidades GeoNames · CC BY 4.0. A cidade escolhida acompanha o tema publicado.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun ClockProperties(l: WidgetLayer, onChange: (WidgetLayer) -> Unit) {
    val c = l.clock
    if (l.type != "date" && l.type != "calendar") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("24 horas", c.format == "24") { onChange(l.copy(clock = c.copy(format = "24"))) }
            Pill("12 horas", c.format == "12") { onChange(l.copy(clock = c.copy(format = "12"))) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mostrar segundos", Modifier.weight(1f))
            Switch(c.seconds, { onChange(l.copy(clock = c.copy(seconds = it))) })
        }
    }
    if (l.type == "date")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("full" to "Extensa", "short" to "Curta", "weekday" to "Dia").forEach { (id, name)
                ->
                Pill(name, c.dateStyle == id) { onChange(l.copy(clock = c.copy(dateStyle = id))) }
            }
        }
    var zone by remember(l.id) { mutableStateOf(c.zone) }
    Field(zone, { zone = it }, "Fuso · vazio usa o aparelho")
    Text(
        "Ex.: America/Sao_Paulo, Europe/Lisbon, Asia/Tokyo",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedButton(
        {
            if (zone.isBlank() || zone in java.util.TimeZone.getAvailableIDs())
                onChange(l.copy(clock = c.copy(zone = zone)))
        },
        enabled = zone.isBlank() || zone in java.util.TimeZone.getAvailableIDs(),
    ) {
        Text("Usar fuso")
    }
}
