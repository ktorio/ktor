package io.ktor.tests.jackson

import com.fasterxml.jackson.databind.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.junit.Test
import kotlin.test.*

class JacksonTest {
    @Test
    fun testMap() = withTestApplication {
        val uc = "\u0422"
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter())
        }
        application.routing {
            val model = mapOf("id" to 1, "title" to "Hello, World!", "unicode" to uc)
            get("/") {
                call.respond(model)
            }
            post("/") {
                val map = call.receive<Map<*, *>>()
                val text = map.entries.joinToString { "${it.key}=${it.value}" }
                call.respond(text)
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""{"id":1,"title":"Hello, World!","unicode":"$uc"}"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Accept", "text/plain")
            addHeader("Content-Type", "application/json")
            setBody("""{"id":1,"title":"Hello, World!","unicode":"$uc"}""")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""id=1, title=Hello, World!, unicode=$uc"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testEntity() = withTestApplication {
        val uc = "\u0422"
        application.install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter())
        }

        application.routing {
            val model = MyEntity(777, "Cargo", listOf(ChildEntity("Qube", 1), ChildEntity("Sphere", 2), ChildEntity(uc, 3)))

            get("/") {
                call.respond(model)
            }
            post("/") {
                val entity = call.receive<MyEntity>()
                call.respond(entity.toString())
            }

        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""{"id":777,"name":"Cargo","children":[{"item":"Qube","quantity":1},{"item":"Sphere","quantity":2},{"item":"$uc","quantity":3}]}"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", "application/json")
            setBody("""{"id":777,"name":"Cargo","children":[{"item":"Qube","quantity":1},{"item":"Sphere","quantity":2},{"item":"$uc","quantity":3}]}""")
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(listOf("""MyEntity(id=777, name=Cargo, children=[ChildEntity(item=Qube, quantity=1), ChildEntity(item=Sphere, quantity=2), ChildEntity(item=$uc, quantity=3)])"""), response.content!!.lines())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }

    }

    @Test
    fun testPrettyPrinter() = withTestApplication {
        application.install(ContentNegotiation) {
            jackson {
                configure(SerializationFeature.INDENT_OUTPUT, true)
            }
        }

        application.routing {
            get("/") {
                call.respond(mapOf("a" to 1, "b" to 2))
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader(HttpHeaders.Accept, "application/json")
        }.response.let { response ->
            assertEquals("{\n  \"a\" : 1,\n  \"b\" : 2\n}", response.content)
        }
    }
}


data class MyEntity(val id: Int, val name: String, val children: List<ChildEntity>)
data class ChildEntity(val item: String, val quantity: Int)

