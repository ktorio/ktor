package io.ktor.client.engine.js.test

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.JSON
import kotlin.js.*
import kotlin.test.*

@Serializable
data class GithubProfile(
    val login: String,
    val id: Int,
    val name: String
)

class RequestTest {
    @Test
    fun testReceiveFromGithub(): Promise<Unit> = GlobalScope.promise {
        val client = HttpClient {
            install(JsonFeature) {
                serializer = KotlinxSerializer(JSON.nonstrict)
            }
        }

        val e5l = GithubProfile("e5l", 4290035, "Leonid Stashevsky")
        assertEquals(e5l, client.get("http://cors-anywhere.herokuapp.com/https://api.github.com/users/e5l"))
    }
}
