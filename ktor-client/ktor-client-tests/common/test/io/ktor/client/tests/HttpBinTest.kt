package io.ktor.client.tests

import io.ktor.client.*
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
            installJson()
        }

        test { client ->
            val response = client.get<HttpBinResponse>("http://httpbin.org/get")
            val expected = HttpBinResponse(
                "http://httpbin.org/get",
                emptyMap(),
                mapOf(
                    "Accept" to "application/json",
                    "Connection" to "close",
                    "Host" to "httpbin.org"
                )
            )

            assertEquals(expected, response)
        }
    }

    @Test
    fun postTest() = clientsTest {
        config {
            installJson()
        }

        test { client ->
            val response = client.post<HttpBinResponse>("http://httpbin.org/post") {
                body = "Hello, bin!"
            }

            val expected = HttpBinResponse(
                "http://httpbin.org/post",
                emptyMap(),
                mapOf(
                    "Content-Type" to "text/plain; charset=UTF-8",
                    "Accept" to "application/json",
                    "Content-Length" to "11",
                    "Connection" to "close",
                    "Host" to "httpbin.org"
                )
            )

            assertEquals(expected, response)
        }
    }

    private fun HttpClientConfig<*>.installJson() {
        install(JsonFeature) {
            serializer = KotlinxSerializer(Json.nonstrict).apply {
                register(HttpBinResponse.serializer())
            }
        }
    }
}
