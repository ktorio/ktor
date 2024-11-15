/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.dataformat.smile.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.serialization.test.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.flow.*
import java.nio.charset.*
import kotlin.test.*

class ServerJacksonTest : AbstractServerSerializationTest() {
    private val objectMapper = jacksonObjectMapper()
    override val defaultContentType: ContentType = ContentType.Application.Json
    override val customContentType: ContentType = ContentType.parse("application/x-json")

    override fun ContentNegotiationConfig.configureContentNegotiation(contentType: ContentType) {
        register(contentType, JacksonConverter(objectMapper, false))
    }

    override fun simpleDeserialize(t: ByteArray): MyEntity {
        return objectMapper.readValue(String(t), jacksonTypeRef<MyEntity>())
    }

    override fun simpleDeserializeList(t: ByteArray, charset: Charset): List<MyEntity> {
        return objectMapper.readValue(String(t, charset), jacksonTypeRef<List<MyEntity>>())
    }

    override fun simpleSerialize(any: MyEntity): ByteArray {
        return objectMapper.writeValueAsBytes(any)
    }

    @Test
    fun testWithUTF16() = testApplication {
        val uc = "\u0422"
        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter())
        }
        routing {
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

        client.get("/") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.AcceptCharset, "UTF-16")
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"id":1,"title":"Hello, World!","unicode":"$uc"}""", response.bodyAsText())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Application.Json.withCharset(Charsets.UTF_16), ContentType.parse(contentTypeText))
        }

        client.post("/") {
            header(HttpHeaders.Accept, "text/plain")
            header(HttpHeaders.ContentType, "application/json; charset=UTF-16")
            setBody("""{"id":1,"title":"Hello, World!","unicode":"$uc"}""".toByteArray(charset = Charsets.UTF_16))
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""id=1, title=Hello, World!, unicode=$uc""", response.bodyAsText())
            val contentTypeText = assertNotNull(response.headers[HttpHeaders.ContentType])
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), ContentType.parse(contentTypeText))
        }
    }

    @Test
    fun testPrettyPrinter() = testApplication {
        install(ContentNegotiation) {
            jackson {
                configure(SerializationFeature.INDENT_OUTPUT, true)
            }
        }

        routing {
            get("/") {
                call.respond(mapOf("a" to 1, "b" to 2))
            }
        }

        client.get("/") {
            header(HttpHeaders.Accept, "application/json")
        }.let { response ->
            assertEquals("{\n  \"a\" : 1,\n  \"b\" : 2\n}", response.bodyAsText())
        }
    }

    @Test
    fun testCustomKotlinModule() = testApplication {
        install(ContentNegotiation) {
            jackson {
                registerModule(
                    KotlinModule.Builder()
                        .withReflectionCacheSize(512)
                        .configure(KotlinFeature.NullToEmptyCollection, enabled = false)
                        .configure(KotlinFeature.NullToEmptyMap, enabled = false)
                        .configure(KotlinFeature.NullIsSameAsDefault, enabled = true)
                        .configure(KotlinFeature.SingletonSupport, enabled = false)
                        .configure(KotlinFeature.StrictNullChecks, enabled = false)
                        .build()
                )
            }
        }

        routing {
            post("/") {
                call.respond(call.receive<WithDefaultValueEntity>())
            }
        }

        client.post("/") {
            header(HttpHeaders.Accept, "application/json")
            header(HttpHeaders.ContentType, "application/json")
            setBody("""{"value":null}""")
        }.let { response ->
            assertEquals("""{"value":"asd"}""", response.bodyAsText())
        }
    }

    @Test
    fun testSmileEncoding() = testApplication {
        val uc = "\u0422"

        val smileContentType = ContentType.parse("application/x-jackson-smile")
        val smileMapper = ObjectMapper(SmileFactory())

        install(ContentNegotiation) {
            register(smileContentType, JacksonConverter(smileMapper))
        }
        routing {
            post("/") {
                val map = call.receive<Map<*, *>>()
                val text = map.entries.joinToString { "${it.key}=${it.value}" }
                call.respond(text)
            }
        }

        val data = mapOf(
            "id" to 1,
            "title" to "Hello, World!",
            "unicode" to uc
        )

        client.post("/") {
            header(HttpHeaders.Accept, "application/json")
            contentType(smileContentType)
            setBody(smileMapper.writeValueAsBytes(data))
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""id=1, title=Hello, World!, unicode=$uc""", response.bodyAsText())
        }
    }

    @Test
    fun testStreamingSmileEncoding() = testApplication {
        val smileContentType = ContentType.parse("application/x-jackson-smile")
        val smileMapper = ObjectMapper(SmileFactory()).apply {
            registerKotlinModule()
        }

        install(ContentNegotiation) {
            register(smileContentType, JacksonConverter(smileMapper))
        }
        routing {
            get("/smile-stream") {
                val testEntities = flowOf(
                    MyEntity(111, "ൠ", listOf(ChildEntity("item1", 1), ChildEntity("item2", 2))),
                    MyEntity(222, "ൠ1", listOf(ChildEntity("item3", 3), ChildEntity("item4", 5)))
                )

                call.respond(testEntities)
            }
        }

        client.get("/smile-stream") {
            header(HttpHeaders.Accept, smileContentType)
        }.let { response ->
            assertEquals(HttpStatusCode.OK, response.status)
            val bytes = response.bodyAsBytes()
            val entities = smileMapper.readValue<List<MyEntity>>(bytes)
            assertEquals(2, entities.size)
        }
    }
}

data class WithDefaultValueEntity(val value: String = "asd")
