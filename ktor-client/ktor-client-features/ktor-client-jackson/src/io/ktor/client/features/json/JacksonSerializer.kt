package io.ktor.client.features.json

import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.*
import kotlin.reflect.*

class JacksonSerializer(block: ObjectMapper.() -> Unit = {}) : JsonSerializer {

    private val backend: ObjectMapper = jacksonObjectMapper().apply(block).copy()

    override fun write(data: Any): String = backend.writeValueAsString(data)

    override fun read(type: KClass<*>, json: String): Any = backend.readValue(json, type.java)
}

fun JsonFeature.Config.jackson(block: ObjectMapper.() -> Unit = {}) {
    serializer = JacksonSerializer(block)
}
