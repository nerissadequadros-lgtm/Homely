package app.homely.platform

import android.content.*
import android.content.pm.*
import android.graphics.drawable.Icon
import app.homely.data.DraftIcon

class UsageModes(context: Context) {
    private val p = context.getSharedPreferences("usage-modes", 0)
    var icons: Boolean
        get() = p.getBoolean("icons", true)
        set(v) {
            p.edit().putBoolean("icons", v).apply()
        }

    var floating: Boolean
        get() = p.getBoolean("floating", false)
        set(v) {
            p.edit().putBoolean("floating", v).apply()
        }

    var home: Boolean
        get() = p.getBoolean("home", false)
        set(v) {
            p.edit().putBoolean("home", v).apply()
        }

    var chosen: Boolean
        get() = p.getBoolean("chosen", false)
        set(v) {
            p.edit().putBoolean("chosen", v).apply()
        }
}

data class LauncherSupport(
    val name: String,
    val packageName: String,
    val shortcuts: Boolean,
    val iconPacks: String,
)

object LauncherCapabilities {
    fun inspect(ctx: Context): LauncherSupport {
        val app =
            ctx.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        val pkg = app?.activityInfo?.packageName.orEmpty()
        val known =
            setOf(
                "com.teslacoilsw.launcher",
                "app.lawnchair",
                "ch.deletescape.lawnchair.plah",
                "com.microsoft.launcher",
                "com.actionlauncher.playstore",
                "ginlemon.flowerfree",
            )
        val packs =
            when {
                pkg == ctx.packageName ->
                    "Os ícones do Studio podem ser usados diretamente no Homely Home."
                pkg in known ->
                    "Este launcher tem suporte conhecido a icon packs. Nesta versão, o Homely oferece atalhos fixados; exportação de packs está no roadmap."
                pkg in
                    setOf(
                        "com.google.android.apps.nexuslauncher",
                        "com.sec.android.app.launcher",
                        "com.android.launcher3",
                    ) ->
                    "Este launcher não oferece integração confirmada com packs do Homely. Use atalhos fixados."
                else ->
                    "Compatibilidade com icon packs não confirmada. O Android não oferece uma API universal para essa detecção."
            }
        return LauncherSupport(
            app?.loadLabel(ctx.packageManager)?.toString() ?: "Launcher não definido",
            pkg,
            ctx.getSystemService(ShortcutManager::class.java).isRequestPinShortcutSupported,
            packs,
        )
    }

    fun pin(ctx: Context, icon: DraftIcon) {
        val manager = ctx.getSystemService(ShortcutManager::class.java)
        check(manager.isRequestPinShortcutSupported) {
            "Este launcher não aceita fixação de atalhos."
        }
        val intent =
            WidgetActions.appIntent(ctx, icon.packageName) ?: error("Aplicativo não instalado.")
        val shortcut =
            ShortcutInfo.Builder(ctx, "homely-app-" + icon.packageName)
                .setShortLabel(icon.label.take(40))
                .setIcon(Icon.createWithBitmap(IconRenderer.decode(icon.path, 256)))
                .setIntent(intent)
                .build()
        manager.updateShortcuts(listOf(shortcut))
        check(manager.requestPinShortcut(shortcut, null)) {
            "O launcher não aceitou a solicitação."
        }
    }
}
