package io.ktor.client.features.json

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import io.ktor.client.call.*
import io.ktor.client.response.*
import io.ktor.content.*
import io.ktor.http.*

class JacksonSerializer(block: ObjectMapper.() -> Unit = {}) : JsonSerializer {

    private val backend = jacksonObjectMapper().apply(block)

    override fun write(data: Any) = TextContent(backend.writeValueAsString(data), ContentType.Application.Json)

    override suspend fun read(type: TypeInfo, response: HttpResponse): Any {
        return backend.readValue(response.readText(), backend.typeFactory.constructType(type.reifiedType))
    }
}
