package io.ktor.client.features.json.tests

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
import kotlinx.serialization.*
import kotlin.test.*

/** Base class for JSON tests. */
abstract class JsonTest: TestWithKtor() {
    val widget = Widget("Foo", 1000, listOf("bar", "baz", "qux"))
    val users = listOf(
        User("vasya", 10),
        User("foo", 45)
    )
    
    override val server: ApplicationEngine = embeddedServer(Jetty, serverPort) {
        install(ContentNegotiation) {
            gson()
        }
        routing {
            createRoutes(this)
        }
    }
    
    protected open fun createRoutes(routing: Routing): Unit = with(routing) {
        post("/widget") {
            val received = call.receive<Widget>()
            assertEquals(widget, received)
            call.respond(widget)
        }
        get("/users") {
            call.respond(Response(true, users))
        }
    }
    
    protected abstract val serializerImpl: JsonSerializer?
    
    protected fun TestClientBuilder<*>.configClient() {
        config {
            install(JsonFeature) {
                serializer = serializerImpl
            }
        }
    }
    
    @Test
    fun testSerializeSimple() = clientTest(CIO) {
        configClient()
        
        test { client ->
            val result = client.post<Widget>(body = widget, path = "/widget", port = serverPort) {
                contentType(ContentType.Application.Json)
            }
            
            assertEquals(widget, result)
        }
    }
    
    @Test
    fun testSerializeNested() = clientTest(CIO) {
        configClient()
        
        test { client ->
            val result = client.get<Response<List<User>>>(path = "/users", port = serverPort)
            
            assertTrue(result.ok)
            assertNotNull(result.result)
            assertEquals(users, result.result)
        }
    }
    
    @Serializable
    data class Response<T>(
        val ok: Boolean,
        val result: T?
    )
    
    @Serializable
    data class Widget(
        val name: String,
        val value: Int,
        val tags: List<String> = emptyList()
    )
    
    @Serializable
    data class User(
        val name: String,
        val age: Int
    )
}
