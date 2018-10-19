package io.ktor.tests.serializable

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serializable.*
import io.ktor.server.testing.*
import kotlinx.serialization.*
import org.junit.Test
import kotlin.test.*

@Serializable
data class MyEntity(
    @SerialId(1)
    val id: Int,
    @SerialId(2)
    val name: String,
    @SerialId(3)
    val children: List<ChildEntity>
)

@Serializable
data class ChildEntity(
    @SerialId(1)
    val item: String,
    @SerialId(2)
    val quantity: Int
)

abstract class SerializableTest {
    abstract val contentType: ContentType
    abstract val contentConverter: SerializableConverter

    abstract fun parseResponse(response: TestApplicationResponse): MyEntity
    abstract fun createRequest(entity: MyEntity, request: TestApplicationRequest)

    @Test
    fun testEntity() = withTestApplication {
        val uc = "\u0422"
        application.install(ContentNegotiation) {
            register(contentType, contentConverter.apply {
                register(MyEntity.serializer())
            })
        }

        val model = MyEntity(777, "Cargo", listOf(ChildEntity("Qube", 1), ChildEntity("Sphere", 2), ChildEntity(uc, 3)))

        application.routing {
            get("/") {
                call.respond(model)
            }
            post("/") {
                val entity = call.receive<MyEntity>()
                assertEquals(model, entity)
                call.respond(entity.toString())
            }
        }

        handleRequest(HttpMethod.Get, "/") {
            addHeader("Accept", contentType.toString())
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(model, parseResponse(response))
            assertEquals(contentType, response.contentType().withoutParameters())
        }

        handleRequest(HttpMethod.Post, "/") {
            addHeader("Content-Type", contentType.toString())
            createRequest(model, this)
        }.response.let { response ->
            assertEquals(HttpStatusCode.OK, response.status())
            assertNotNull(response.content)
            assertEquals(
                listOf("""MyEntity(id=777, name=Cargo, children=[ChildEntity(item=Qube, quantity=1), ChildEntity(item=Sphere, quantity=2), ChildEntity(item=$uc, quantity=3)])"""),
                response.content!!.lines()
            )
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), response.contentType())
        }
    }
}
