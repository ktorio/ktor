/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.openapi

import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.openapi.*
import io.ktor.openapi.reflect.ReflectionJsonSchemaInference
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.openapi.*
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.serializersModuleOf
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAPITest {

    private val sampleBook = Book(
        "The Hitchhiker's Guide to the Galaxy",
        "Douglas Adams"
    )
    private val sampleAuthor = Author(
        "Douglas Adams",
        ZonedDateTime.parse("1974-04-25T00:00:00Z")
    )
    private val descriptions = listOf(
        "List all books",
        "Get a book by id",
        "Create a book",
        "Update a book"
    )

    @Test
    fun `resolves from file and routing sources`() = testApplication {
        install(ContentNegotiation) {
            json()
        }
        routing {
            @OptIn(ExperimentalKtorApi::class)
            route("/api") {
                route("/books") {
                    get {
                        call.respond(listOf(sampleBook))
                    }.describe {
                        summary = descriptions[0]
                    }
                    get("/{id}") {
                        call.respond(sampleBook)
                    }.describe {
                        summary = descriptions[1]
                    }
                    post {
                        call.respond(HttpStatusCode.Created)
                    }.describe {
                        summary = descriptions[2]
                    }
                    put("/{id}") {
                        call.respond(HttpStatusCode.NoContent)
                    }.describe {
                        summary = descriptions[3]
                    }
                }
            }

            // Use the default documentation.yaml file
            route("/default") {
                openAPI("docs") {
                    outputPath = "docs/files"
                }
            }

            // Use the routing tree
            route("/routes") {
                openAPI("docs") {
                    outputPath = "docs/routes"
                    info = OpenApiInfo("Books API from routes", "1.0.0")
                    source = OpenApiDocSource.Routing(
                        contentType = ContentType.Application.Json,
                        schemaInference = ReflectionJsonSchemaInference.Default,
                    )
                }
            }
        }

        client.get("/default/docs").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Books API from file")
            for (description in descriptions) {
                assertContains(responseText, description, message = "Response should contain '$description'")
            }
        }

        client.get("/routes/docs").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val responseText = response.bodyAsText()
            assertContains(responseText, "Books API from routes")
            assertFalse("/routes/docs" in responseText)
            for (description in descriptions) {
                assertContains(responseText, description, message = "Response should contain '$description'")
            }
        }
    }

    @OptIn(ExperimentalKtorApi::class)
    @Test
    fun `uses custom serializers module`() = testApplication {
        val externalModule = serializersModuleOf(ZonedDateTime::class, ZonedDateTimeAsStringSerializer)
        val internalModule = serializersModuleOf(ZonedDateTime::class, ZonedDateTimeAsStructSerializer)

        // The schema inference call is the same for each endpoint,
        // but implemented differently from the serializers module.
        fun Route.authorsEndpoint(module: SerializersModule) = route("/authors") {
            install(ContentNegotiation) {
                json(Json { serializersModule = module })
            }
            get {
                call.respond(listOf(sampleAuthor))
            }.describe {
                responses {
                    HttpStatusCode.OK {
                        description = "List of authors"
                        schema = jsonSchema<List<Author>>()
                    }
                }
            }
            get("/{id}") {
                call.respond(sampleAuthor)
            }.describe {
                responses {
                    HttpStatusCode.OK {
                        description = "Author by id"
                        schema = jsonSchema<Author>()
                    }
                }
            }
            post {
                call.respond(HttpStatusCode.Created)
            }
            put("/{id}") {
                call.respond(HttpStatusCode.NoContent)
            }
        }

        routing {
            route("/api") {
                route("/internal") {
                    authorsEndpoint(internalModule)
                }
                route("/external") {
                    authorsEndpoint(externalModule)
                }
            }

            route("/routes") {
                install(ContentNegotiation) {
                    json()
                }
                get {
                    call.respond(
                        OpenApiDoc(info = OpenApiInfo("Internal API", "1.0.0")) +
                            call.application.routingRoot.descendants()
                    )
                }.hide()
            }
        }

        val httpClient = client.config {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        val openApiDoc = httpClient.get("/routes").let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            response.body<OpenApiDoc>()
        }

        val internalSchema = openApiDoc.authorDateOfBirthSchema("/api/internal/authors/{id}")
        val externalSchema = openApiDoc.authorDateOfBirthSchema("/api/external/authors/{id}")

        // The internal API documents ZonedDateTime as a structured object with year/month/etc.
        assertEquals(JsonType.OBJECT, internalSchema.type)
        val properties = assertNotNull(internalSchema.properties)
        assertTrue("year" in properties, "Expected 'year' property in $properties")
        assertTrue("month" in properties, "Expected 'month' property in $properties")
        assertTrue("zoneId" in properties, "Expected 'zoneId' property in $properties")

        // The external API documents ZonedDateTime as a single ISO string.
        assertEquals(JsonType.STRING, externalSchema.type)

        // The two routes must produce distinct schemas for the same Kotlin type.
        assertNotEquals(internalSchema, externalSchema)
    }

    /**
     * Returns the first descendant route whose full path matches [path]. Used to scope OpenAPI
     * document generation to a subtree of the routing graph.
     */
    private fun Route.findRoute(path: String): Route =
        assertNotNull(
            descendants().firstOrNull { (it as? RoutingNode)?.path() == path },
            "Could not find route $path"
        )

    /**
     * Resolves the schema for the `dateOfBirth` property of the `Author` returned by the
     * `GET <path>` operation, dereferencing component references as needed.
     */
    private fun OpenApiDoc.authorDateOfBirthSchema(path: String): JsonSchema {
        val pathItem = assertNotNull(paths[path]?.valueOrNull(), "Missing path $path")
        val operation = assertNotNull(pathItem.get, "Missing GET on $path")
        val response = assertNotNull(operation.responses?.responses?.get(HttpStatusCode.OK.value)?.valueOrNull())
        val mediaType = assertNotNull(response.content?.get(ContentType.Application.Json))
        val authorSchema = assertNotNull(mediaType.schema?.dereference(this))
        val dateOfBirth = assertNotNull(authorSchema.properties?.get("dateOfBirth"))
        return assertNotNull(dateOfBirth.dereference(this))
    }

    private fun ReferenceOr<JsonSchema>.dereference(doc: OpenApiDoc): JsonSchema? = when (this) {
        is ReferenceOr.Value -> value

        is ReferenceOr.Reference -> {
            val name = ref.removePrefix("#/components/schemas/")
            doc.components?.schemas?.get(name)
        }
    }
}

data class Book(
    val title: String,
    val author: String
)

@Serializable
data class Author(
    val name: String,
    val dateOfBirth: @Contextual ZonedDateTime,
)

/**
 * Serializes a [ZonedDateTime] as a single ISO-8601 string, like the kotlinx-datetime defaults.
 * This is the representation typically expected by external (third-party) clients.
 */
private object ZonedDateTimeAsStringSerializer : KSerializer<ZonedDateTime> {
    override val descriptor: SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
            "java.time.ZonedDateTime",
            kotlinx.serialization.descriptors.PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: ZonedDateTime) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): ZonedDateTime =
        ZonedDateTime.parse(decoder.decodeString())
}

/**
 * Serializes a [ZonedDateTime] as a structured object with year/month/day/hour/minute/second/nano/zoneId fields.
 * This is convenient for internal callers that prefer to avoid date-time parsing.
 */
private object ZonedDateTimeAsStructSerializer : KSerializer<ZonedDateTime> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("java.time.ZonedDateTime") {
            element<Int>("year")
            element<Int>("month")
            element<Int>("day")
            element<Int>("hour")
            element<Int>("minute")
            element<Int>("second")
            element<Int>("nano")
            element<String>("zoneId")
        }

    override fun serialize(encoder: Encoder, value: ZonedDateTime) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.year)
            encodeIntElement(descriptor, 1, value.monthValue)
            encodeIntElement(descriptor, 2, value.dayOfMonth)
            encodeIntElement(descriptor, 3, value.hour)
            encodeIntElement(descriptor, 4, value.minute)
            encodeIntElement(descriptor, 5, value.second)
            encodeIntElement(descriptor, 6, value.nano)
            encodeStringElement(descriptor, 7, value.zone.id)
        }
    }

    override fun deserialize(decoder: Decoder): ZonedDateTime =
        decoder.decodeStructure(descriptor) {
            var year = 0
            var month = 1
            var day = 1
            var hour = 0
            var minute = 0
            var second = 0
            var nano = 0
            var zoneId = "UTC"
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> year = decodeIntElement(descriptor, 0)
                    1 -> month = decodeIntElement(descriptor, 1)
                    2 -> day = decodeIntElement(descriptor, 2)
                    3 -> hour = decodeIntElement(descriptor, 3)
                    4 -> minute = decodeIntElement(descriptor, 4)
                    5 -> second = decodeIntElement(descriptor, 5)
                    6 -> nano = decodeIntElement(descriptor, 6)
                    7 -> zoneId = decodeStringElement(descriptor, 7)
                    kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            ZonedDateTime.of(year, month, day, hour, minute, second, nano, ZoneId.of(zoneId))
        }
}
