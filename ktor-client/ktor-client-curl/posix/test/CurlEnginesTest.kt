package io.ktor.client.test

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import kotlinx.io.core.*
import kotlinx.serialization.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

@Serializable
data class HttpBinResponse(
    val url: String,
    val args: Map<String, String>,
    val headers: Map<String, String>
)

class CurlEnginesTest {

    @Test
    @Ignore
    fun getTest(): Unit = clientTest { client ->
        val response = client.get<HttpBinResponse>("https://httpbin.org/get")
        val expected = HttpBinResponse(
            "https://httpbin.org/get",
            emptyMap(),
            mapOf(
                "Accept" to "application/json",
                "Host" to "httpbin.org"
            )
        )

        assertEquals(expected, response)
    }

    @Test
    @Ignore
    fun postTest() = clientTest { client ->
        val response = client.post<HttpBinResponse>("https://httpbin.org/post") {
            body = "Hello, bin!"
        }

        val expected = HttpBinResponse(
            "https://httpbin.org/post",
            emptyMap(),
            mapOf(
                "Content-Type" to "text/plain; charset=UTF-8",
                "Accept" to "application/json",
                "Content-Length" to "11",
                "Host" to "httpbin.org"
            )
        )

        assertEquals(expected, response)
    }

    private fun clientTest(block: suspend (HttpClient) -> Unit): Unit = runBlocking {
        HttpClient(Curl) {
            install(JsonFeature) {
                serializer = KotlinxSerializer(JSON.nonstrict).apply {
                    register(HttpBinResponse.serializer())
                }
            }
        }.use { block(it) }
    }
}
