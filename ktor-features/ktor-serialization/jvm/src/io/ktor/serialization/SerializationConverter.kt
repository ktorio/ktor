/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.json.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

/**
 * Json [ContentConverter] with kotlinx.serialization.
 *
 * Installation:
 * ```kotlin
 * install(ContentNegotiation) {
 *    register(ContentType.Application.Json, SerializationConverter())
 * }
 *
 * install(ContentNegotiation) {
 *     serialization(json = Json.nonstrict)
 * }
 * ```
 */
@UseExperimental(ImplicitReflectionSerializer::class)
class SerializationConverter(private val json: Json = Json(DefaultJsonConfiguration)) : ContentConverter {
    override suspend fun convertForSend(
        context: PipelineContext<Any, ApplicationCall>,
        contentType: ContentType,
        value: Any
    ): Any? {
        @Suppress("UNCHECKED_CAST")
        val content = json.stringify(serializerForSending(value) as KSerializer<Any>, value)
        return TextContent(content, contentType.withCharset(context.call.suitableCharset()))
    }

    override suspend fun convertForReceive(context: PipelineContext<ApplicationReceiveRequest, ApplicationCall>): Any? {
        val request = context.subject
        val channel = request.value as? ByteReadChannel ?: return null
        val charset = context.call.request.contentCharset() ?: Charsets.UTF_8

        val content = channel.readRemaining().readText(charset)
        val serializer = serializerByTypeInfo(request.typeInfo)

        return json.parse(serializer, content)
    }

}

/**
 * Register kotlinx.serialization converter into [ContentNegotiation] feature
 */
fun ContentNegotiation.Configuration.serialization(
    contentType: ContentType = ContentType.Application.Json,
    json: Json = Json(DefaultJsonConfiguration)
) {
    val converter = SerializationConverter(json)
    register(contentType, converter)
}

/**
 * The default json configuration used in [SerializationConverter]. The settings are:
 * - defaults are serialized
 * - mode is not strict so extra json fields are ignored
 * - pretty printing is disabled
 * - array polymorphism is enabled
 * - keys and values are quoted, non-quoted are not allowed
 *
 * See [JsonConfiguration] for more details.
 */
val DefaultJsonConfiguration: JsonConfiguration = JsonConfiguration.Stable.copy(
    encodeDefaults = true,
    strictMode = false,
    unquoted = false,
    prettyPrint = false,
    useArrayPolymorphism = true
)

// NOTE: this should be removed once kotlinx.serialization get proper typeOf support
@UseExperimental(ImplicitReflectionSerializer::class, ExperimentalStdlibApi::class)
private fun serializerByTypeInfo(type: KType): KSerializer<*> {
    if (type.arguments.isEmpty()) {
        return type.jvmErasure.serializer()
    }
    val classifierClass = type.classifier as? KClass<*>
    if (classifierClass != null) {
        if (classifierClass.java.isArray) {
            val elementType = type.arguments[0].type ?: error("Array<*> is not supported")
            val elementSerializer = serializerByTypeInfo(elementType)

            @Suppress("UNCHECKED_CAST")
            return ReferenceArraySerializer(
                elementType.jvmErasure as KClass<Any>,
                elementSerializer as KSerializer<Any>
            )
        }

        return when (classifierClass) {
            List::class, ArrayList::class -> {
                val elementSerializer = type.argumentSerializer(0, "List<*> is not supported")

                ArrayListSerializer(elementSerializer)
            }
            Set::class, HashSet::class -> {
                val elementSerializer = type.argumentSerializer(0, "Set<*> is not supported")

                HashSetSerializer(elementSerializer)
            }
            LinkedHashSet::class -> {
                val elementSerializer = type.argumentSerializer(0, "Set<*> is not supported")

                LinkedHashSetSerializer(elementSerializer)
            }
            Map::class, HashMap::class -> {
                val keySerializer = type.argumentSerializer(0, "Map<*, V> is not supported")
                val valueSerializer = type.argumentSerializer(1, "Map<K, *> is not supported")

                HashMapSerializer(keySerializer, valueSerializer)
            }
            LinkedHashMap::class -> {
                val keySerializer = type.argumentSerializer(0, "Map<*, V> is not supported")
                val valueSerializer = type.argumentSerializer(1, "Map<K, *> is not supported")

                LinkedHashMapSerializer(keySerializer, valueSerializer)
            }
            Map.Entry::class -> {
                val keySerializer = type.argumentSerializer(0, "Map.Entry<*, V> is not supported")
                val valueSerializer = type.argumentSerializer(1, "Map.Entry<K, *> is not supported")

                MapEntrySerializer(keySerializer, valueSerializer)
            }
            else -> error("Unsupported classifier $classifierClass of type $type")
        }
    }

    error("Unsupported type $type")
}

private fun KType.argumentSerializer(position: Int, message: String): KSerializer<*> {
    val type = arguments[position].type ?: error(message)
    return serializerByTypeInfo(type)
}

@UseExperimental(ImplicitReflectionSerializer::class)
private fun serializerForSending(value: Any): KSerializer<*> {
    if (value is List<*>) {
        return ArrayListSerializer(value.elementSerializer())
    }
    if (value is Set<*>) {
        return HashSetSerializer(value.elementSerializer())
    }
    if (value is Map<*, *>) {
        return HashMapSerializer(value.keys.elementSerializer(), value.values.elementSerializer())
    }
    if (value is Map.Entry<*, *>) {
        return MapEntrySerializer(
            serializerForSending(value.key ?: error("Map.Entry(null, ...) is not supported")),
            serializerForSending(value.value ?: error("Map.Entry(..., null) is not supported)"))
        )
    }
    if (value is Array<*>) {
        val componentType = value.javaClass.componentType.kotlin.starProjectedType
        val componentClass =
            componentType.classifier as? KClass<*> ?: error("Unsupported component type $componentType")
        @Suppress("UNCHECKED_CAST")
        return ReferenceArraySerializer(
            componentClass as KClass<Any>,
            serializerByTypeInfo(componentType) as KSerializer<Any>
        )
    }
    return value::class.serializer()
}

@UseExperimental(ImplicitReflectionSerializer::class)
private fun Collection<*>.elementSerializer() = firstOrNull { it != null }?.let { first ->
    serializerByTypeInfo(first.javaClass.kotlin.starProjectedType)
} ?: String::class.serializer()
