package app.homely.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.homely.data.WidgetLayer
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarContent(layer: WidgetLayer, font: FontFamily, now: Long) {
    val zone =
        if (layer.clock.zone.isBlank()) ZoneId.systemDefault() else ZoneId.of(layer.clock.zone)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val month = YearMonth.from(today).plusMonths(layer.monthOffset.toLong())
    val first = month.atDay(1).dayOfWeek.value % 7
    val rows = (first + month.lengthOfMonth() + 6) / 7
    val tint = color(layer.color)
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR"))),
            color = tint,
            fontSize = layer.fontSize.sp,
            fontFamily = font,
            fontWeight = FontWeight(layer.weight),
            maxLines = 1,
        )
        Row(Modifier.fillMaxWidth()) {
            listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        it,
                        color = tint.copy(alpha = .55f),
                        fontSize = (layer.fontSize * .75f).sp,
                        fontFamily = font,
                    )
                }
            }
        }
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                repeat(7) { col ->
                    val day = row * 7 + col - first + 1
                    val isToday = YearMonth.from(today) == month && day == today.dayOfMonth
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (day in 1..month.lengthOfMonth()) {
                            Text(
                                day.toString(),
                                Modifier.background(
                                        if (isToday) tint.copy(alpha = .16f)
                                        else androidx.compose.ui.graphics.Color.Transparent,
                                        CircleShape,
                                    )
                                    .padding(4.dp),
                                color = tint,
                                fontFamily = font,
                                fontSize = layer.fontSize.sp,
                                fontWeight = FontWeight(if (isToday) 700 else layer.weight),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
