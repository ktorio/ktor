package io.ktor.client.features.json

import com.google.gson.*
import io.ktor.client.call.*
import io.ktor.http.content.*
import io.ktor.http.*
import kotlinx.io.core.*

/**
 * [JsonSerializer] using [Gson] as backend.
 */
class GsonSerializer(block: GsonBuilder.() -> Unit = {}) : JsonSerializer {

    private val backend: Gson = GsonBuilder().apply(block).create()

    override fun write(data: Any): OutgoingContent = TextContent(backend.toJson(data), ContentType.Application.Json)

    override fun read(type: TypeInfo, body: Input): Any {
        val text = body.readText()
        return backend.fromJson(text, type.reifiedType)
    }
}
