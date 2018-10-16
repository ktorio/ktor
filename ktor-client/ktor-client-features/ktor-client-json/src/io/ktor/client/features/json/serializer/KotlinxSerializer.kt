package io.ktor.client.features.json.serializer

import io.ktor.client.call.*
import io.ktor.client.features.json.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.json.*
import kotlin.reflect.*

/**
 * A [JsonSerializer] implemented for kotlinx [Serializable] classes. Since serializers are determined statically, you
 * must set the mapping for each Serializable class to it's [KSerializer] manually, using [setMapper] or [register].
 *
 * ```
 * KotlinxSerializer().apply {
 *     register<MySerializable>()
 * }
 * ```
 */
class KotlinxSerializer : JsonSerializer {
    @Suppress("UNCHECKED_CAST")
    private val mappers = mutableMapOf(
            Unit::class as KClass<Any> to UnitSerializer as KSerializer<Any>,
            Boolean::class as KClass<Any> to BooleanSerializer as KSerializer<Any>,
            Byte::class as KClass<Any> to ByteSerializer as KSerializer<Any>,
            Short::class as KClass<Any> to ShortSerializer as KSerializer<Any>,
            Int::class as KClass<Any> to IntSerializer as KSerializer<Any>,
            Long::class as KClass<Any> to LongSerializer as KSerializer<Any>,
            Float::class as KClass<Any> to FloatSerializer as KSerializer<Any>,
            Double::class as KClass<Any> to DoubleSerializer as KSerializer<Any>,
            Char::class as KClass<Any> to CharSerializer as KSerializer<Any>,
            String::class as KClass<Any> to StringSerializer as KSerializer<Any>
    )

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
