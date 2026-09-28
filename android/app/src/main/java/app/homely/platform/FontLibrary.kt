package app.homely.platform

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import app.homely.data.ThemeFont
import java.io.File
import java.util.UUID

object FontLibrary {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Typeface>()

    fun typeface(path: String) = cache.getOrPut(path) { Typeface.createFromFile(path) }

    fun import(ctx: Context, uri: Uri): Pair<ThemeFont, String> {
        val name =
            ctx.contentResolver
                .query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Minha fonte.ttf"
        val bytes =
            ctx.contentResolver.openInputStream(uri)!!.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= 5 * 1024 * 1024) { "A fonte deve ter até 5 MB." }
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
        require(bytes.size >= 12) { "Arquivo de fonte inválido." }
        val otf = bytes.take(4).toByteArray().decodeToString() == "OTTO"
        val ttf =
            bytes[0] == 0.toByte() &&
                bytes[1] == 1.toByte() &&
                bytes[2] == 0.toByte() &&
                bytes[3] == 0.toByte()
        require(otf || ttf) { "Escolha uma fonte TTF ou OTF." }
        val id = UUID.randomUUID().toString()
        val file = File(ctx.filesDir, "font-$id." + if (otf) "otf" else "ttf")
        try {
            file.writeBytes(bytes)
            typeface(file.path)
        } catch (e: Exception) {
            file.delete()
            throw IllegalArgumentException("Não foi possível ler a fonte.", e)
        }
        return ThemeFont(id, name.substringBeforeLast('.').take(60)) to file.path
    }
}
