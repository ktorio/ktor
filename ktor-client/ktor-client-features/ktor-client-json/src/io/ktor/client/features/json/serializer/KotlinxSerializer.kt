package io.ktor.client.features.json.serializer

import io.ktor.client.call.*
import io.ktor.client.features.json.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.reflect.KClass

internal expect fun serializerForPlatform(type: TypeInfo): KSerializer<*>

typealias SerializerMapper<T> = () -> KSerializer<T>

class KotlinxSerializer(
    val json: JSON = JSON(
        unquoted = false,
        indented = false,
        nonstrict = false,
        context = SerialContext()
    ),
    block: SerialContext.() -> Unit = {}
) : JsonSerializer {
    init {
        json.context?.apply {
            block()
        }
    }

    private val serializerMap = mutableMapOf<KClass<*>, MutableList<SerializerMapper<*>>>()
    //TODO: Use Type instead of KClass once TypeInfo is accessible in write
    fun <T : Any> serializer(type: KClass<T>, block: SerializerMapper<T>) {
        serializerMap.getOrPut(type) { mutableListOf() } += block
    }

    override fun write(data: Any): OutgoingContent {
        @Suppress("UNCHECKED_CAST")
        val clazz: KClass<Any> = data::class as KClass<Any>
        var serializer: KSerializer<Any>? = null
        val serializers = serializerMap[clazz] ?: mutableListOf()
        for(serializerFunction in serializers) {
            serializer = try {
                @Suppress("UNCHECKED_CAST")
                serializerFunction() as KSerializer<Any>
            } catch(e: Exception) {
                continue
            }
            break
        }
        if(serializer == null) serializer = clazz.serializer()
        return TextContent(json.stringify(serializer, data), ContentType.Application.Json)
    }

    override suspend fun read(type: TypeInfo, response: HttpResponse): Any {
        var serializer: KSerializer<*>? = null
        val serializers = serializerMap[type.type] ?: mutableListOf()
        for(serializerFunction in serializers) {
            serializer = try {
                serializerFunction()
            } catch(e: Exception) {
                continue
            }
            break
        }
        if(serializer == null) serializer = serializerForPlatform(type)
        return json.parse(serializer, response.readText())!!
    }
}