package app.homely.platform

import android.content.*
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import app.homely.WidgetActionActivity
import app.homely.data.*
import kotlinx.serialization.encodeToString

object WidgetActions {
    fun appIntent(ctx: Context, pkg: String): Intent? =
        ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: when {
                pkg.contains("dialer") || pkg.endsWith(".phone") ->
                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:"))
                pkg.contains("calendar") ->
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.withAppendedPath(
                            CalendarContract.CONTENT_URI,
                            "time/" + System.currentTimeMillis(),
                        ),
                    )
                pkg.contains("clock") -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
                pkg.contains("camera") ->
                    Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                pkg.contains("messaging") ->
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
                else -> null
            }

    fun launchPackage(ctx: Context, pkg: String) {
        launch(
            ctx,
            WidgetLayer(type = "shortcut", tapPackage = pkg, text = pkg.substringAfterLast('.')),
        )
    }

    fun intent(ctx: Context, l: WidgetLayer): Intent? {
        if (l.tapAction == "none") return null
        if (l.tapAction !in listOf("auto", "app")) return detail(ctx, l)
        if (l.tapPackage != null)
            appIntent(ctx, l.tapPackage)?.let {
                return it
            }
        return when (l.type) {
            "phone" ->
                Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse(
                        "tel:" + Uri.encode(l.phoneNumber.filter { it.isDigit() || it in "+*#" })
                    ),
                )
            "clock",
            "analog" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "date",
            "calendar" ->
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.withAppendedPath(
                        CalendarContract.CONTENT_URI,
                        "time/" + System.currentTimeMillis(),
                    ),
                )
            "weather",
            "note" -> detail(ctx, l)
            else -> if (l.type == "shortcut") detail(ctx, l) else null
        }
    }

    fun detail(ctx: Context, l: WidgetLayer) =
        Intent(ctx, WidgetActionActivity::class.java)
            .setData(Uri.parse("homely://item/" + l.id))
            .putExtra("layer", HomelyRepository(ctx).json.encodeToString(l))

    fun launch(ctx: Context, l: WidgetLayer) {
        if (l.tapAction !in listOf("auto", "app", "none")) {
            ElementSystem.execute(ctx, l.tapAction, l)
            return
        }
        val intent = intent(ctx, l) ?: return
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: RuntimeException) {
            ctx.startActivity(detail(ctx, l).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun pendingIntent(ctx: Context, l: WidgetLayer): android.app.PendingIntent? {
        if (l.tapAction == "none" && l.states.none { it.action !in listOf("inherit", "none") })
            return null
        // Use our dispatcher so missing apps have a visible fallback and remain editable.
        val intent = detail(ctx, l).putExtra("dispatch", true)
        return android.app.PendingIntent.getActivity(
            ctx,
            l.id.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

object WidgetNotes {
    val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)

    fun text(ctx: Context, l: WidgetLayer) =
        ctx.getSharedPreferences("widget-notes", 0).getString(l.id, l.text).orEmpty()

    fun save(ctx: Context, id: String, text: String) {
        ctx.getSharedPreferences("widget-notes", 0).edit().putString(id, text.take(300)).apply()
        revision.value++
    }
}
