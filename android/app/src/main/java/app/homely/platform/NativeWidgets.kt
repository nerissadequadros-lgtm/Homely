package app.homely.platform

import android.app.PendingIntent
import android.appwidget.*
import android.content.*
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.widget.RemoteViews
import app.homely.R

object NativeWidgets {
    fun component(context: Context, id: String): ComponentName =
        ComponentName(
            context,
            when (id) {
                "clock" -> HomelyClockWidget::class.java
                "editorial" -> HomelyEditorialClockWidget::class.java
                "date" -> HomelyDateWidget::class.java
                "pastel" -> HomelyPastelDateWidget::class.java
                "battery" -> HomelyBatteryWidget::class.java
                "neon" -> HomelyNeonBatteryWidget::class.java
                else -> error("Widget desconhecido.")
            },
        )

    fun views(context: Context, id: String): RemoteViews {
        val layout =
            when (id) {
                "clock" -> R.layout.widget_clock
                "editorial" -> R.layout.widget_editorial
                "date",
                "pastel" -> R.layout.widget_date
                else -> R.layout.widget_battery
            }
        return RemoteViews(context.packageName, layout).apply {
            if (id == "pastel") {
                setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_pastel)
                setTextColor(R.id.widget_day, Color.parseColor("#422C57"))
                setTextColor(R.id.widget_month, Color.parseColor("#6D5286"))
                setTextColor(R.id.widget_weekday, Color.parseColor("#6D5286"))
            }
            if (id in listOf("battery", "neon")) {
                val battery =
                    (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        .coerceIn(0, 100)
                setTextViewText(R.id.widget_battery_value, battery.toString() + "%")
                setProgressBar(R.id.widget_battery_progress, 100, battery, false)
                if (id == "neon") {
                    setInt(R.id.widget_root, "setBackgroundResource", R.drawable.widget_neon)
                    setTextColor(R.id.widget_battery_value, Color.parseColor("#B7F67B"))
                }
            }
        }
    }

    fun pin(context: Context, id: String): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val extras =
            Bundle().apply {
                putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, views(context, id))
            }
        return manager.requestPinAppWidget(component(context, id), extras, null)
    }
}

abstract class HomelyWidgetProvider(private val kind: String) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = NativeWidgets.views(context, kind)
            val intent =
                Intent(context, javaClass)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(id))
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getBroadcast(
                    context,
                    id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            manager.updateAppWidget(id, views)
        }
    }
}

class HomelyEditorialClockWidget : HomelyWidgetProvider("editorial")

class HomelyDateWidget : HomelyWidgetProvider("date")

class HomelyPastelDateWidget : HomelyWidgetProvider("pastel")

class HomelyBatteryWidget : HomelyWidgetProvider("battery")

class HomelyNeonBatteryWidget : HomelyWidgetProvider("neon")
