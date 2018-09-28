package io.ktor.client.features.json.serializer

import io.ktor.client.call.*
import io.ktor.client.features.json.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.reflect.*

class KotlinxSerializer : JsonSerializer {
    private val mappers = mutableMapOf<KClass<Any>, KSerializer<Any>>()

    /**
     * Set mapping from [type] to generated [KSerializer].
     */
    fun <T : Any> setMapper(type: KClass<T>, serializer: KSerializer<T>) {
        @Suppress("UNCHECKED_CAST")
        mappers[type as KClass<Any>] = serializer as KSerializer<Any>
    }

    override fun write(data: Any): OutgoingContent {
        @Suppress("UNCHECKED_CAST")
        val content = JSON.stringify(mappers[data::class]!!, data)
        return TextContent(content, ContentType.Application.Json)
    }

    override suspend fun read(type: TypeInfo, response: HttpResponse): Any {
        val mapper = mappers[type.type]!!
        val text = response.readText()
        return JSON.parse(mapper, text)
    }
}
