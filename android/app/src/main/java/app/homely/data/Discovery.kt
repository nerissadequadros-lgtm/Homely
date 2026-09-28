package app.homely.data

import android.content.Context
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

@Serializable
data class DiscoveryNotice(
    val id: String,
    val title: String,
    val themeIds: List<String>,
    val createdAt: String,
    val read: Boolean = false,
)

@Serializable
data class DiscoveryInbox(val items: List<DiscoveryNotice> = emptyList(), val unread: Int = 0)

class DiscoveryClient(private val ctx: Context) {
    private val repo = HomelyRepository(ctx)
    private val prefs = ctx.getSharedPreferences("discovery", 0)

    private fun owner() =
        repo.token?.let {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(it.toByteArray())
                .take(8)
                .joinToString("") { b -> "%02x".format(b) }
        } ?: "guest"

    fun enabled() = prefs.getBoolean("enabled-${owner()}", true)

    fun enabled(value: Boolean) {
        prefs.edit().putBoolean("enabled-${owner()}", value).apply()
    }

    fun inbox(): DiscoveryInbox = runCatching {
        repo.json.decodeFromString<DiscoveryInbox>(prefs.getString("inbox-${owner()}", null) ?: "")
    }.getOrDefault(DiscoveryInbox())

    fun enqueue(kind: String, themeId: String? = null, context: String? = null, duration: Int = 0) {
        if (repo.token == null) return
        val event = buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("kind", kind)
            themeId?.let { put("themeId", it) }
            context?.let { put("context", it) }
            put("duration", duration.coerceIn(0, 300))
        }
        synchronized(lock) {
            val key = "events-${owner()}"
            val queue = runCatching {
                repo.json.parseToJsonElement(prefs.getString(key, "[]")!!).jsonArray.toList()
            }.getOrDefault(emptyList())
            prefs.edit().putString(key, JsonArray((queue + event).takeLast(100)).toString()).apply()
        }
    }

    suspend fun flush() {
        if (repo.token == null) return
        val key = "events-${owner()}"
        val batch =
            synchronized(lock) {
                runCatching {
                        repo.json
                            .parseToJsonElement(prefs.getString(key, "[]")!!)
                            .jsonArray
                            .take(30)
                    }
                    .getOrDefault(emptyList())
            }
        if (batch.isEmpty()) return
        repo.request("/v1/behavior", "POST", buildJsonObject { put("events", JsonArray(batch)) })
        synchronized(lock) {
            val current = repo.json.parseToJsonElement(prefs.getString(key, "[]")!!).jsonArray
            val sent = batch.map { it.jsonObject["id"] }.toSet()
            prefs
                .edit()
                .putString(
                    key,
                    JsonArray(current.filter { it.jsonObject["id"] !in sent }).toString(),
                )
                .apply()
        }
    }

    suspend fun sync(): DiscoveryInbox {
        if (repo.token == null) return DiscoveryInbox()
        flush()
        repo.request(
            "/v1/discovery/preferences",
            "PUT",
            buildJsonObject {
                put("timezone", java.util.TimeZone.getDefault().id)
                put("enabled", enabled())
            },
        )
        val result = repo.get<DiscoveryInbox>("/v1/notifications")
        prefs.edit().putString("inbox-${owner()}", repo.json.encodeToString(result)).apply()
        return result
    }

    companion object {
        private val lock = Any()
    }
}
