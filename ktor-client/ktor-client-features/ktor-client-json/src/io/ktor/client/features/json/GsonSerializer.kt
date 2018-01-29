package io.ktor.client.features.json

import com.google.gson.*
import io.ktor.content.*
import io.ktor.http.*
import kotlin.reflect.*


class GsonSerializer(block: GsonBuilder.() -> Unit = {}) : JsonSerializer {

    private val backend: Gson = GsonBuilder().apply(block).create()

    override fun write(data: Any): OutgoingContent = TextContent(backend.toJson(data), ContentType.Application.Json)

    override suspend fun read(type: KClass<*>, content: IncomingContent): Any {
        val text= content.readText()
        return backend.fromJson(text, type.java)
    }
}
