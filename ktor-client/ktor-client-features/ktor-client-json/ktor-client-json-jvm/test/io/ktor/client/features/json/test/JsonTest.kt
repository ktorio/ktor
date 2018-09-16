package io.ktor.client.features.json.test

import io.ktor.application.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
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
import kotlinx.serialization.*
import java.util.*
import kotlin.test.*

@Serializable
data class Widget(
    val name: String,
    val value: Int,
    val tags: List<String> = emptyList()
)

class JsonTest : TestWithKtor() {
    val widget = Widget("Foo", 1000, listOf("bar", "baz", "qux"))
    val usersArray = arrayOf(User("vasya", 10))
    val usersList = listOf(User("vasya", 10))
    val usersSet = setOf(User("vasya", 10))
    val userMap = mapOf("baz" to User("vasya", 10))

    @Serializable
    data class Response<T>(val ok: Boolean, val result: T?)

    @Serializable
    data class User(val name: String, val age: Int)

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
            get("/usersArray") {
                call.respond(usersArray)
            }
            get("/usersList") {
                call.respond(usersList)
            }
            get("/usersSet") {
                call.respond(usersSet)
            }
            get("/userMap") {
                call.respond(userMap)
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

    @Test
    fun testKotlinxSerialization() = clientTest(CIO) {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer().apply {
                    serializer(Widget::class) {
                        json.context?.getSerializerByClass(Widget::class)!!
                    }
                }
            }
        }

        test { client ->
            val result = client.post<Widget>(body = widget, port = serverPort) {
                contentType(ContentType.Application.Json)
            }

            assertEquals(widget, result)
        }
    }

    @Test
    fun testKotlinxSerializationArray() = clientTest(CIO) {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            val result = client.get<Array<User>>(path = "/usersArray", body = widget, port = serverPort) {
                contentType(ContentType.Application.Json)
            }

//            Arrays.deepEquals(usersArray, result)
            assert(Arrays.deepEquals(usersArray, result))
        }
    }

    @Test
    fun testKotlinxSerializationList() = clientTest(CIO) {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            val result = client.get<List<User>>(path = "/usersList", body = widget, port = serverPort) {
                contentType(ContentType.Application.Json)
            }

            assertEquals(usersList, result)
        }
    }

    @Test
    fun testKotlinxSerializationSet() = clientTest(CIO) {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            val result = client.get<Set<User>>(path = "/usersSet", body = widget, port = serverPort) {
                contentType(ContentType.Application.Json)
            }

            assertEquals(usersSet, result)
        }
    }

    @Test
    fun testKotlinxSerializationMap() = clientTest(CIO) {
        config {
            install(JsonFeature) {
                serializer = KotlinxSerializer()
            }
        }

        test { client ->
            val result = client.get<Map<String, User>>(path = "/userMap", body = widget, port = serverPort) {
                contentType(ContentType.Application.Json)
            }

            assertEquals(userMap, result)
        }
    }
}
