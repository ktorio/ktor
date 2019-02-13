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
class KotlinxSerializer(private val json: Json = Json.plain) : JsonSerializer {
    @Suppress("UNCHECKED_CAST")
    private val mappers: MutableMap<KClass<*>, KSerializer<*>> = mutableMapOf()
    private val listMappers: MutableMap<KClass<*>, KSerializer<*>> = mutableMapOf()

    /**
     * Set mapping from [type] to generated [KSerializer].
     */
    fun <T : Any> setMapper(type: KClass<T>, serializer: KSerializer<T>) {
        @Suppress("UNCHECKED_CAST")
        mappers[type as KClass<Any>] = serializer as KSerializer<Any>
    }

    /**
     * Set mapping from [type] to generated [KSerializer].
     */
    fun <T : Any> setListMapper(type: KClass<T>, serializer: KSerializer<T>) {
        @Suppress("UNCHECKED_CAST")
        listMappers[type] = serializer.list as KSerializer<List<Any>>
    }

    /** Set the mapping from [T] to [mapper]. */
    inline fun <reified T : Any> register(mapper: KSerializer<T>) {
        setMapper(T::class, mapper)
    }

    /** Set the mapping from [List<T>] to [mapper]. */
    inline fun <reified T : Any> registerList(mapper: KSerializer<T>) {
        setListMapper(T::class, mapper)
    }

    /**
     * Set the mapping from [T] to it's [KSerializer]. This method only works for non-parameterized types.
     */
    inline fun <reified T : Any> register() {
        register(T::class.serializer())
    }

    /**
     * Set the mapping from [List<T>] to it's [KSerializer]. This method only works for non-parameterized types.
     */
    inline fun <reified T : Any> registerList() {
        registerList(T::class.serializer())
    }

    override fun write(data: Any): OutgoingContent {
        val serializer = lookupSerializerByData(data)

        @Suppress("UNCHECKED_CAST")
        val content = json.stringify(serializer as KSerializer<Any>, data)

        return TextContent(content, ContentType.Application.Json)
    }

    override suspend fun read(type: TypeInfo, response: HttpResponse): Any {
        val mapper = lookupSerializerByType(type.type)
        val text = response.readText()

        @Suppress("UNCHECKED_CAST")
        return json.parse(mapper as KSerializer<Any>, text)
    }

    private fun lookupSerializerByData(data: Any): KSerializer<*> {
        if (data is List<*>) {
            val item = data.find { it != null }
            return item?.let { listMappers[item::class] } ?: EMPTY_LIST_SERIALIZER
        }

        val type = data::class
        mappers[type]?.let { return it }
        return (type.defaultSerializer() ?: type.serializer())
    }

    private fun lookupSerializerByType(type: KClass<*>): KSerializer<*> {
        mappers[type]?.let { return it }
        return (type.defaultSerializer() ?: type.serializer())
    }

    companion object {
        private val EMPTY_LIST_SERIALIZER = String.serializer().list
    }
}
