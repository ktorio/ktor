package io.ktor.client.features.json.test

import io.ktor.application.*
import io.ktor.client.engine.cio.*
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
import kotlin.test.*

data class Widget(
    val name: String,
    val value: Int,
    val tags: List<String> = emptyList()
)

class GsonTest : TestWithKtor() {
    val widget = Widget("Foo", 1000, listOf("bar", "baz", "qux"))

    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        install(ContentNegotiation) {
            gson()
        }
        routing {
            post("/") {
                val received = call.receive<Widget>()
                assertEquals(widget, received)
                call.respond(received)
            }
            get("/users") {
                call.respond(Response(true, arrayOf(User("vasya", 10))))
            }
        }
    }

    @Test
    fun testSerialize() = clientTest(CIO) {
        config {
            install(JsonFeature)
        }

        test { client ->
            val result = client.post<Widget>(body = widget, port = serverPort) {
                contentType(ContentType.Application.Json)
            }

            assertEquals(widget, result)
        }
    }

    data class Response<T>(val ok: Boolean, val result: T?)
    data class User(val name: String, val age: Int)
}
