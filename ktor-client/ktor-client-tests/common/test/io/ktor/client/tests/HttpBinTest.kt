package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

@Serializable
data class HttpBinResponse(
    val url: String,
    val args: Map<String, String>,
    val headers: Map<String, String>
)

class HttpBinTest {

    @Test
    fun getTest() = clientsTest {
        config {
            testConfiguration()
        }

        test { client ->
            val response = client.get<HttpBinResponse>("https://httpbin.org/get")

            assertEquals("https://httpbin.org/get", response.url)
            assertEquals(emptyMap(), response.args)

            with(response.headers) {
                assertEquals("application/json", get("Accept"))
                assertEquals("httpbin.org", get("Host"))
            }

        }
    }

    @Test
    fun postTest() = clientsTest {
        config {
            testConfiguration()
        }

        test { client ->
            val response = client.post<HttpBinResponse>("https://httpbin.org/post") {
                body = "Hello, bin!"
            }

            assertEquals("https://httpbin.org/post", response.url)
            assertEquals(emptyMap(), response.args)

            with(response.headers) {
                assertEquals("text/plain; charset=UTF-8", get("Content-Type"))
                assertEquals("application/json", get("Accept"))
                assertEquals("11", get("Content-Length"))
                assertEquals("httpbin.org", get("Host"))
            }
        }
    }

    private fun HttpClientConfig<*>.testConfiguration() {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json.nonstrict).apply {
                register(HttpBinResponse.serializer())
            }
        }
    }
}
