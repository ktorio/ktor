package io.ktor.client.features.json.test

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.module.kotlin.*
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
            get("/users") {
                call.respond(Response(true, arrayOf(User("vasya", 10))))
            }
            post("/jackson") {
                val data = jacksonObjectMapper().readTree(call.receiveText())
                assertFalse(data.at("/object").has("ignoredValue"))
                assertFalse(data.at("/list/0").has("ignoredValue"))
                call.respond(Jackson("response", "not_ignored")) // encoded with GsonConverter
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

    @Test
    fun testGeneric() = clientTest(CIO) {
        config {
            install(JsonFeature) {
                serializer = GsonSerializer()
            }
        }

        test { client ->
            val customResponse = client.get<Response<Array<User>>>(path = "users", port = serverPort)

            assertTrue(customResponse.ok)
            val users = customResponse.result!!
            assertEquals(1, users.size)
            assertEquals(User("vasya", 10), users[0])
        }
    }

    data class Jackson(val value: String, @JsonIgnore val ignoredValue: String?)

    @Test
    fun testJackson() = clientTest(CIO) {
        config {
            install(JsonFeature) {
                serializer = JacksonSerializer()
            }
        }

        test { client ->
            val body = mapOf(Pair("object", Jackson("request", "ignored")), Pair("list", listOf(Jackson("request", "ignored"))))
            val response = client.post<Jackson>(port = serverPort, path = "jackson",  body = body) {
                contentType(ContentType.Application.Json)
            }

            assertEquals(Jackson("response", "not_ignored"), response) // encoded with GsonConverter
        }
    }
}
