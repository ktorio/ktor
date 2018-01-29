package io.ktor.client.features.json.test

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.experimental.*
import org.junit.Test
import kotlin.test.*

data class Widget(
        val name: String,
        val value: Int,
        val tags: List<String> = emptyList()
)

class JsonTest : TestWithKtor() {
    val widget = Widget("Foo", 1000, listOf("bar", "baz", "qux"))

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        install(ContentNegotiation) {
            register(ContentType.Application.Json, GsonConverter())
        }
        routing {
            post("/") {
                val received = call.receive<Widget>()
                assertEquals(widget, received)
                call.respond(received)
            }
        }
    }

    @Test
    fun testSerialize() = runBlocking {
        val client = HttpClient(Apache) {
            install(JsonFeature)
        }

        val result = client.post<Widget>(body = widget, port = serverPort) {
            contentType(ContentType.Application.Json)
        }

        assertEquals(widget, result)
    }
}
