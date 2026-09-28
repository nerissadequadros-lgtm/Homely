package app.homely.platform

import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import app.homely.data.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val label: String)

data class AppShortcutInfo(val id: String, val label: String, val intent: Intent?)

object NativeActions {
    fun apps(context: Context): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(intent, 0)
            .map {
                InstalledApp(
                    it.activityInfo.packageName,
                    it.loadLabel(context.packageManager).toString(),
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    /** Query dynamic and manifest shortcuts for a given package, Samsung-style. */
    fun shortcuts(context: Context, packageName: String): List<AppShortcutInfo> {
        return try {
            val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
            val userHandle = android.os.Process.myUserHandle()
            val query =
                android.content.pm.LauncherApps.ShortcutQuery()
                    .setPackage(packageName)
                    .setQueryFlags(
                        android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                    )
            launcherApps.getShortcuts(query, userHandle)?.take(4)?.map { shortcut ->
                AppShortcutInfo(
                    id = shortcut.id,
                    label = shortcut.shortLabel?.toString() ?: shortcut.id,
                    intent = shortcut.intent,
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun asset(repo: HomelyRepository, asset: Asset): File =
        withContext(Dispatchers.IO) {
            val f =
                File(
                    repo.context.cacheDir,
                    asset.id + "." + if (asset.mime == "video/mp4") "mp4" else "img",
                )
            repo.download(asset.url, f)
            val digest = MessageDigest.getInstance("SHA-256")
            f.inputStream().use { input ->
                val b = ByteArray(8192)
                while (true) {
                    val n = input.read(b)
                    if (n < 0) break
                    digest.update(b, 0, n)
                }
            }
            check(digest.digest().joinToString("") { "%02x".format(it) } == asset.sha256) {
                "O arquivo recebido falhou na verificação de integridade."
            }
            f
        }

    suspend fun wallpaper(repo: HomelyRepository, asset: Asset, which: Int) =
        withContext(Dispatchers.IO) {
            val manager = WallpaperManager.getInstance(repo.context)
            check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed) {
                "Este aparelho não permite alterar o wallpaper."
            }
            val f = asset(repo, asset)
            f.inputStream().use { manager.setStream(it, null, true, which) }
        }

    suspend fun prepareLive(repo: HomelyRepository, asset: Asset): Intent =
        withContext(Dispatchers.IO) {
            val f = asset(repo, asset)
            val permanent = File(repo.context.filesDir, "motion.mp4")
            f.copyTo(permanent, true)
            Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(repo.context, HomelyWallpaperService::class.java),
                )
        }

    suspend fun shortcut(repo: HomelyRepository, asset: Asset) {
        val context = repo.context
        val manager = context.getSystemService(ShortcutManager::class.java)
        check(manager.isRequestPinShortcutSupported) { "Seu launcher não permite fixar atalhos." }
        val intent =
            WidgetActions.appIntent(context, asset.packageName ?: error("Ícone sem aplicativo."))
                ?: error("Instale " + asset.name + " antes de criar o atalho.")
        val f = asset(repo, asset)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, options)
        var sample = 1
        while (options.outWidth / sample > 256 || options.outHeight / sample > 256) sample *= 2
        val bitmap =
            BitmapFactory.decodeFile(
                f.path,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: error("Imagem inválida.")
        val shortcut =
            ShortcutInfo.Builder(context, "homely-" + asset.id)
                .setShortLabel(asset.name.take(40))
                .setIcon(Icon.createWithBitmap(bitmap))
                .setIntent(intent)
                .build()
        check(manager.requestPinShortcut(shortcut, null)) { "Seu launcher não aceitou o atalho." }
    }

    fun pinWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(
                ComponentName(context, HomelyClockWidget::class.java),
                null,
                null,
            )
    }

    suspend fun saveToUri(repo: HomelyRepository, path: String, uri: Uri) =
        withContext(Dispatchers.IO) {
            val tmp = File.createTempFile("homely-download", ".tmp", repo.context.cacheDir)
            try {
                repo.download(path, tmp)
                repo.context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
            } finally {
                tmp.delete()
            }
        }
}
