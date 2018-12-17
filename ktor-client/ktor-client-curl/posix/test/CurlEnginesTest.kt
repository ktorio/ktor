package io.ktor.client.test

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.request.*
import kotlinx.io.core.*
import kotlinx.serialization.*
import kotlin.test.*
import kotlinx.coroutines.*

@Serializable
data class HttpBinResponse(
    val url: String,
    val args: Map<String, String>,
    val headers: Map<String, String>,
    val origin: String
)

class CurlEnginesTest {

    @Test
    fun getTest(): Unit = clientTest { client ->
        val response = client.get<HttpBinResponse>("http://httpbin.org/get")
        println(response)
    }

    @Test
    fun postTest() {
    }

    private fun clientTest(block: suspend (HttpClient) -> Unit): Unit = runBlocking {
        HttpClient(Curl) {
            install(JsonFeature) {
                serializer = KotlinxSerializer().apply {
                    register(HttpBinResponse.serializer())
                }
            }
        }.use { block(it) }
    }
}
