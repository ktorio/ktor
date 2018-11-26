package io.ktor.client.engine.js.test

import io.ktor.client.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
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

    @Test
    fun testCustomFormBody(): Promise<Unit> = GlobalScope.promise {
        val client = HttpClient {
            install(JsonFeature)
        }

        val data = {
            formData {
                append("name", "hello")
                append("content") {
                    writeStringUtf8("123456789")
                }
                append("file", "urlencoded_name.jpg") {
                    for (i in 1..4096) {
                        writeByte(i.toByte())
                    }
                }
                append("hello", 5)
            }
        }

        try {
            client.submitFormWithBinaryData<String>(url = "upload", formData = data())
        } catch (cause: Throwable) {
            assertEquals("Failed to fetch", cause.message)
        }

        return@promise
    }
}
