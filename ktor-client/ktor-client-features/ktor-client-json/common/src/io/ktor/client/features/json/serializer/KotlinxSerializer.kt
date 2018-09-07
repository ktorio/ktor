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
@UseExperimental(ImplicitReflectionSerializer::class)
class KotlinxSerializer(private val json: JSON = JSON.plain) : JsonSerializer {
    @Suppress("UNCHECKED_CAST")
    private val mappers: MutableMap<KClass<Any>, KSerializer<Any>> = mutableMapOf()

    /**
     * Set mapping from [type] to generated [KSerializer].
     */
    fun <T : Any> setMapper(type: KClass<T>, serializer: KSerializer<T>) {
        @Suppress("UNCHECKED_CAST")
        mappers[type as KClass<Any>] = serializer as KSerializer<Any>
    }

    /** Set the mapping from [T] to [mapper]. */
    inline fun <reified T : Any> register(mapper: KSerializer<T>) {
        setMapper(T::class, mapper)
    }

    /**
     * Set the mapping from [T] to it's [KSerializer]. This method only works for non-parameterized types.
     */
    inline fun <reified T : Any> register() {
        register(T::class.serializer())
    }


    override fun write(data: Any): OutgoingContent {
        val content = json.stringify(lookupSerializer(data::class), data)
        return TextContent(content, ContentType.Application.Json)
    }

    override suspend fun read(type: TypeInfo, response: HttpResponse): Any {
        val mapper = lookupSerializer(type.type)
        val text = response.readText()
        return json.parse(mapper, text)
    }

    private fun lookupSerializer(type: KClass<*>): KSerializer<Any> {
        mappers[type]?.let { return it }

        @Suppress("UNCHECKED_CAST")
        return (type.defaultSerializer() ?: type.serializer()) as KSerializer<Any>
    }
}
