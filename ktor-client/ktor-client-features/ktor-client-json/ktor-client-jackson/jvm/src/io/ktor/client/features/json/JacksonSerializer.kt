package io.ktor.client.features.json

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.client.call.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.io.core.*

class JacksonSerializer(block: ObjectMapper.() -> Unit = {}) : JsonSerializer {

    private val backend = jacksonObjectMapper().apply(block)

    override fun write(data: Any) = TextContent(backend.writeValueAsString(data), ContentType.Application.Json)

    override fun read(type: TypeInfo, body: Input): Any {
        val reader = body.asStream().reader()
        return backend.readValue(reader, backend.typeFactory.constructType(type.reifiedType))
    }
}
