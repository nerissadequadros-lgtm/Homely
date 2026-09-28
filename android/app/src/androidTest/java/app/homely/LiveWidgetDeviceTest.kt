package app.homely

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import app.homely.data.*
import app.homely.platform.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LiveWidgetDeviceTest {
    @Test
    fun phoneClockAndCalendarUseRealAndroidActions() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(
            Intent.ACTION_DIAL,
            WidgetActions.intent(
                    ctx,
                    WidgetLayer(type = "phone", phoneNumber = "+55 42 99999-0000"),
                )!!
                .action,
        )
        assertEquals(
            "tel:+5542999990000",
            android.net.Uri.decode(
                WidgetActions.intent(
                        ctx,
                        WidgetLayer(type = "phone", phoneNumber = "+55 42 99999-0000"),
                    )!!
                    .data
                    .toString()
            ),
        )
        assertEquals(
            android.provider.AlarmClock.ACTION_SHOW_ALARMS,
            WidgetActions.intent(ctx, WidgetLayer(type = "clock"))!!.action,
        )
        assertTrue(
            WidgetActions.intent(ctx, WidgetLayer(type = "calendar"))!!
                .data
                .toString()
                .startsWith("content://com.android.calendar/time/")
        )
        assertNull(WidgetActions.intent(ctx, WidgetLayer(type = "phone", tapAction = "none")))
        assertEquals(
            "00:00:00",
            layerDate(
                WidgetLayer(type = "clock", clock = ClockOptions(seconds = true, zone = "UTC")),
                0,
            ),
        )
        assertEquals(
            "09:00:00",
            layerDate(
                WidgetLayer(
                    type = "clock",
                    clock = ClockOptions(seconds = true, zone = "Asia/Tokyo"),
                ),
                0,
            ),
        )
        assertTrue(calendarText(WidgetLayer(type = "calendar"), 0).contains("31"))
        // Bundled themes can reference AOSP packages on a Google/OEM device.
        listOf("com.android.dialer", "com.android.calendar", "com.android.deskclock").forEach { pkg
            ->
            assertNotNull(
                "Resolve portable app shortcut: $pkg",
                WidgetActions.appIntent(ctx, pkg)?.resolveActivity(ctx.packageManager),
            )
        }
    }

    @Test
    fun realWeatherLoadsFromVpsAndRendersNatively() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val cities = HomelyRepository(ctx).get<WeatherCities>("/v1/weather/cities?q=Ponta%20Grossa")
        assertTrue(cities.items.isNotEmpty())
        val city = cities.items.first()
        val opts =
            WeatherOptions(
                city = city.name,
                latitude = city.latitude,
                longitude = city.longitude,
                showHumidity = true,
                showWind = true,
                style = "forecast",
            )
        val weather = WeatherStore.refresh(ctx, opts, true)
        assertNotNull("Live forecast through Homely VPS", weather)
        assertTrue(weather!!.temperature in -90.0..65.0)
        assertTrue(weather.hourly.isNotEmpty())
        assertEquals("32°F", WeatherStore.temperature(0.0, "f"))
        val layer =
            WidgetLayer(type = "weather", weather = opts, width = 1f, height = 1f, fontSize = 22)
        val spec = WidgetSpec(height = 280, layers = listOf(layer)).validate()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view =
                StudioWidgets.views(ctx, SavedWidget(spec, emptyMap()), 320, 280).apply(ctx, null)
            assertTrue(
                view.findViewById<android.view.View>(R.id.layer_content).hasOnClickListeners()
            )
        }
        assertFalse(WeatherStore.text(ctx, layer).contains("prévia"))
        assertTrue(WeatherStore.text(ctx, layer).contains(city.name))
    }

    @Test
    fun notesAreEditableAndWeatherOptionsRejectBadCoordinates() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val l = WidgetLayer(type = "note", text = "Original")
        WidgetNotes.save(ctx, l.id, "Nota atualizada")
        assertEquals("Nota atualizada", WidgetNotes.text(ctx, l))
        assertTrue(
            runCatching { WeatherOptions(latitude = 999.0, longitude = 0.0).validate() }.isFailure
        )
        assertTrue(runCatching { ClockOptions(zone = "not/a/timezone").validate() }.isFailure)
    }
}
