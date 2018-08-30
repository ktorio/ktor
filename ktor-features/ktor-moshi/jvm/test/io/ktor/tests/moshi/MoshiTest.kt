package io.ktor.tests.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.moshi.moshi
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MoshiTest {
    @Test
    fun `it accepts custom data classes`(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            moshi()
        }
        application.routing {
            get("/") {
                call.respond(Dog(2, "Moshi", "Jake Wharton"))
            }
            post("/") {
                val dog = call.receive<Dog>()
                call.respond(dog.toString())
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.also {
            assertEquals(HttpStatusCode.OK, it.status())
            assertEquals(listOf("""{"id":2,"name":"Moshi","ownerName":"Jake Wharton"}"""), assertNotNull(it.content).lines())
            assertEquals(Charsets.UTF_8, it.contentType().charset())
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Accept", "text/plain")
            addHeader("Content-Type", "application/json")
            setBody("""{"id":2,"name":"Moshi","ownerName":"Jake Wharton"}""")
        }.response.also {
            assertEquals(HttpStatusCode.OK, it.status())
            assertEquals(listOf("""Dog(id=2, name=Moshi, ownerName=Jake Wharton)"""), assertNotNull(it.content).lines())
            assertEquals(Charsets.UTF_8, it.contentType().charset())
        }
    }

    @Test
    fun `it accepts a custom adapter`(): Unit = withTestApplication {
        application.install(ContentNegotiation) {
            moshi {
                add(UUID::class.java, UuidAdapter)
            }
        }

        val organizationId = UUID.randomUUID()
        val ownerId = UUID.randomUUID()
        application.routing {
            get("/") {
                call.respond(Organization(organizationId, Organization.Owner(ownerId)))
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", "application/json")
        }.response.also {
            assertEquals(HttpStatusCode.OK, it.status())
            assertEquals(listOf("""{"id":"$organizationId","owner":{"id":"$ownerId"}}"""), assertNotNull(it.content).lines())
            assertEquals(Charsets.UTF_8, it.contentType().charset())
        }
    }

    object UuidAdapter : JsonAdapter<UUID>() {
        override fun fromJson(reader: JsonReader): UUID? = when(reader.peek()) {
            JsonReader.Token.STRING -> UUID.fromString(reader.nextString())
            JsonReader.Token.NULL -> null
            else -> error("Could not parse UUID from:" + reader.peek())
        }

        override fun toJson(writer: JsonWriter, value: UUID?) {
            writer.value(value?.toString())
        }
    }

    data class Dog(val id: Int, val name: String, val ownerName: String)
    data class Organization(val id: UUID, val owner: Owner) {
        data class Owner(val id: UUID)
    }
}
