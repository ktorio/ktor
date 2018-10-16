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

    /**
     * Set the mapping from [T] to it's [KSerializer].
     */
    inline fun <reified T : Any> register() {
        setMapper(T::class, T::class.serializer())
    }

    private fun getMapper(type: KClass<*>): KSerializer<Any> {
        return mappers[type] ?: throw UnsupportedOperationException("No mapping set for $type")
    }

    override fun write(data: Any): OutgoingContent {
        val content = JSON.stringify(getMapper(data::class), data)
        return TextContent(content, ContentType.Application.Json)
    }

    override suspend fun read(type: TypeInfo, response: HttpResponse): Any {
        val mapper = getMapper(type.type)
        val text = response.readText()
        return JSON.parse(mapper, text)
    }
}
