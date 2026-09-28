package app.homely.data

import android.content.Context
import app.homely.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(message: String, val status: Int) : Exception(message)

class HomelyRepository(val context: Context) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
    private val secure = SecureSession(context)
    var token: String? = secure.read()
        private set

    private val prefs = context.getSharedPreferences("homely", Context.MODE_PRIVATE)

    fun url(path: String) = if (path.startsWith("/")) BuildConfig.API_BASE_URL + path else path

    fun setSession(value: String?) {
        token = value
        secure.write(value)
    }

    suspend fun request(path: String, method: String = "GET", body: JsonElement? = null): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url(path))
            token?.let { builder.header("Authorization", "Bearer $it") }
            if (method != "GET")
                builder.method(
                    method,
                    (body?.toString() ?: "{}").toRequestBody("application/json".toMediaType()),
                )
            client.newCall(builder.build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful)
                    throw ApiException(
                        runCatching {
                            json
                                .parseToJsonElement(text)
                                .jsonObject["error"]
                                ?.jsonPrimitive
                                ?.content
                        }
                            .getOrNull() ?: "Falha na conexão (${r.code}).",
                        r.code,
                    )
                text
            }
        }

    suspend inline fun <reified T> get(path: String): T = json.decodeFromString(request(path))

    suspend fun upload(file: File, mime: String): Upload =
        withContext(Dispatchers.IO) {
            val body =
                MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
                    .build()
            val request =
                Request.Builder()
                    .url(url("/v1/media"))
                    .header("Authorization", "Bearer " + (token ?: ""))
                    .post(body)
                    .build()
            client.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful)
                    throw ApiException(
                        runCatching {
                                json
                                    .parseToJsonElement(text)
                                    .jsonObject["error"]!!
                                    .jsonPrimitive
                                    .content
                            }
                            .getOrDefault("Falha ao enviar arquivo."),
                        r.code,
                    )
                json.decodeFromString(text)
            }
        }

    suspend fun download(path: String, file: File): File =
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url(path)).build()).execute().use { r ->
                if (!r.isSuccessful)
                    throw ApiException("Não foi possível baixar o arquivo.", r.code)
                file.outputStream().use { out -> r.body!!.byteStream().use { it.copyTo(out) } }
            }
            file
        }

    fun cachedFeed(): List<ThemePost> = runCatching {
        json.decodeFromString<List<ThemePost>>(File(context.filesDir, "feed.json").readText())
    }
        .getOrDefault(emptyList())

    suspend fun cacheFeed(items: List<ThemePost>) =
        withContext(Dispatchers.IO) {
            File(context.filesDir, "feed.json").writeText(json.encodeToString(items))
        }

    fun draft(): Draft = runCatching {
        json.decodeFromString<Draft>(prefs.getString("draft", null) ?: "")
    }
        .getOrDefault(Draft())

    fun saveDraft(draft: Draft) {
        prefs.edit().putString("draft", json.encodeToString(draft)).apply()
    }

    fun setAppearance(mode: String) {
        prefs.edit().putString("appearance", mode).apply()
    }

    fun appearance() = prefs.getString("appearance", "dark")!!

    fun markDownloaded(id: String) {
        prefs.edit().putStringSet("downloaded", downloaded() + id).apply()
    }

    fun downloaded(): Set<String> = prefs.getStringSet("downloaded", emptySet())!!.toSet()

    fun applied(): String? = prefs.getString("applied", null)

    fun markApplied(id: String) {
        prefs.edit().putString("applied", id).apply()
    }
}
