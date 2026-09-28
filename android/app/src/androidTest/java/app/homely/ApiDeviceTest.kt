package app.homely

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.homely.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApiDeviceTest {
    @Test
    fun httpsCatalogAndEncryptedSessionWorkOnAndroid() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = HomelyRepository(context)
        val page = repo.get<ThemePage>("/v1/themes")
        assertTrue("Seed catalog reachable with trusted TLS", page.items.size >= 16)
        val id = System.currentTimeMillis().toString()
        val result =
            repo.json.decodeFromString<AuthResponse>(
                repo.request(
                    "/v1/auth/register",
                    "POST",
                    buildJsonObject {
                        put("email", "androidqa" + id + "@example.com")
                        put("password", java.util.UUID.randomUUID().toString())
                        put("username", "androidqa" + id)
                        put("displayName", "Homely Android QA")
                    },
                )
            )
        repo.setSession(result.token)
        try {
            assertEquals(result.token, SecureSession(context).read())
            assertEquals(result.user.id, repo.get<Profile>("/v1/me").id)
            val theme = page.items.first()
            repo.request(
                "/v1/themes/" + theme.id + "/like",
                "PUT",
                buildJsonObject { put("active", true) },
            )
            assertTrue(repo.get<ThemePost>("/v1/themes/" + theme.id).liked)
            repo.request(
                "/v1/themes/" + theme.id + "/save",
                "PUT",
                buildJsonObject { put("active", true) },
            )
            assertTrue(repo.get<ThemePage>("/v1/themes?saved=true").items.any { it.id == theme.id })
            assertTrue(
                repo.get<ThemePage>("/v1/themes/" + theme.id + "/related").items.none {
                    it.id == theme.id
                }
            )
        } finally {
            repo.request("/v1/me", "DELETE")
            repo.setSession(null)
        }
    }
}
