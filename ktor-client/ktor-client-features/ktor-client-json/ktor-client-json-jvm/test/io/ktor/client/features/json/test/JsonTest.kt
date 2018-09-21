package io.ktor.client.features.json.test

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlin.test.*

data class Widget(
    val name: String,
    val value: Int,
    val tags: List<String> = emptyList()
)

class JsonTest : TestWithKtor() {

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
//        Disabled until server supports null
//        install(ContentNegotiation) {
//            register(ContentType.Application.Json, GsonConverter())
//        }
        routing {
            post("/") {
                val received = call.receiveText()
                call.respondWrite(ContentType.Application.Json, HttpStatusCode.OK) {
                    append(received)
                }
            }
        }
    }

    private inline fun <T, reified R : JsonContent<T>> testJson(jsonValue: T) = clientTest(CIO) {
        config {
            install(JsonFeature)
        }

        test { client ->
            val result = client.jsonPost<R>(body = jsonValue, port = serverPort)

            assertEquals(jsonValue, result.value)
        }
    }

    @Test
    fun testSerializeObject() = testJson(Widget("Foo", 1000, listOf("bar", "baz", "qux")))

    @Test
    fun testSerializeNull() = testJson<Widget?, JsonContent<Widget?>>(null)

    @Test
    fun testSerializeFalse() = testJson(false)

    @Test
    fun testSerializeString() = testJson("JsonJSONJsON")

    @Test
    fun testSerializeNumber() = testJson(42)

    @Test
    fun testManuallySerializedJson() = clientTest(CIO) {
        config {
            install(JsonFeature)
        }

        test { client ->
            val result = client.post<JsonContent<String>>(body = TextContent("\"String\"", ContentType.Application.Json), port = serverPort)

            assertEquals("String", result.value)
        }
    }

    @Test
    fun testReceiveJsonAsStringForManualDeserialization() = clientTest(CIO) {
        config {
            install(JsonFeature)
        }

        test { client ->
            val result = client.jsonPost<String>(body = "Str", port = serverPort)

            assertEquals("\"Str\"", result)
        }
    }

}

private suspend inline fun <reified T> HttpClient.jsonPost(
        scheme: String = "http", host: String = "localhost", port: Int = 80,
        path: String = "/",
        body: Any? = null,
        block: HttpRequestBuilder.() -> Unit = {}
): T = post(scheme, host, port, path, JsonContent(body)){
    contentType(ContentType.Application.Json)
    block()
}
