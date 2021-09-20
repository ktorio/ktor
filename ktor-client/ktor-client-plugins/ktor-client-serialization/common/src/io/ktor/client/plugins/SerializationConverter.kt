/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.shared.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.modules.*

/**
 * [ContentConverter] with kotlinx.serialization.
 */
@OptIn(ExperimentalSerializationApi::class)
public open class SerializationConverter<T : SerialFormat>(
    private val format: T,
    extraSerializerMatchers: Map<KSerializer<*>, (value: Any) -> Boolean> = mutableMapOf()
) : ContentConverter {
    private val extraSerializerMatchers = extraSerializerMatchers.toMutableMap()

    init {
        require(format is BinaryFormat || format is StringFormat) {
            "Only binary and string formats are supported, " +
                "$format is not supported."
        }
    }

    /**
     * Registers extra [matcher] for a given [serializer] to be used when guessing serializers
     */
    public fun match(serializer: KSerializer<*>, matcher: (value: Any) -> Boolean) {
        extraSerializerMatchers[serializer] = matcher
    }

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any
    ): OutgoingContent? {
        val result = try {
            serializerFromResponseType(typeInfo, format.serializersModule)?.let {
                serializeContent(it, format, value, contentType, charset)
            }
        } catch (cause: SerializationException) {
            // can fail due to
            // 1. https://github.com/Kotlin/kotlinx.serialization/issues/1163)
            // 2. mismatching between compile-time and runtime types of the response.
            null
        }
        if (result != null) {
            return result
        }

        val matchedSerializer = extraSerializerMatchers.entries.firstOrNull { (_, matcher) ->
            matcher(value)
        }?.key
        val guessedSearchSerializer = matchedSerializer ?: buildSerializer(value, format.serializersModule)
        return serializeContent(guessedSearchSerializer, format, value, contentType, charset)
    }

    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any? {
        val serializer = format.serializersModule.serializer(typeInfo.kotlinType ?: return null)
        val contentPacket = content.readRemaining()

        return when (format) {
            is StringFormat -> format.decodeFromString(serializer, contentPacket.readText(charset))
            is BinaryFormat -> format.decodeFromByteArray(serializer, contentPacket.readBytes())
            else -> {
                contentPacket.discard()
                error("Unsupported format $format")
            }
        }
    }

    private fun serializerFromResponseType(
        typeInfo: TypeInfo,
        module: SerializersModule
    ): KSerializer<*>? {
        val responseType = typeInfo.kotlinType ?: return null
        return module.serializer(responseType)
    }

    private fun serializeContent(
        serializer: KSerializer<*>,
        format: SerialFormat,
        value: Any,
        contentType: ContentType,
        charset: Charset,
    ): OutgoingContent.ByteArrayContent {
        @Suppress("UNCHECKED_CAST")
        return when (format) {
            is StringFormat -> {
                val content = format.encodeToString(serializer as KSerializer<Any>, value)
                TextContent(content, contentType.withCharset(charset))
            }
            is BinaryFormat -> {
                val content = format.encodeToByteArray(serializer as KSerializer<Any>, value)
                ByteArrayContent(content, contentType)
            }
            else -> error("Unsupported format $format")
        }
    }


    @OptIn(ExperimentalSerializationApi::class)
    private fun Collection<*>.elementSerializer(module: SerializersModule): KSerializer<*> {
        val serializers: List<KSerializer<*>> =
            filterNotNull().map { buildSerializer(it, module) }.distinctBy { it.descriptor.serialName }

        if (serializers.size > 1) {
            error(
                "Serializing collections of different element types is not yet supported. " +
                    "Selected serializers: ${serializers.map { it.descriptor.serialName }}"
            )
        }

        val selected = serializers.singleOrNull() ?: String.serializer()

        if (selected.descriptor.isNullable) {
            return selected
        }

        @Suppress("UNCHECKED_CAST")
        selected as KSerializer<Any>

        if (any { it == null }) {
            return selected.nullable
        }

        return selected
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildSerializer(value: Any, module: SerializersModule): KSerializer<Any> = when (value) {
        is List<*> -> ListSerializer(value.elementSerializer(module))
        is Array<*> -> value.firstOrNull()?.let { buildSerializer(it, module) } ?: ListSerializer(String.serializer())
        is Set<*> -> SetSerializer(value.elementSerializer(module))
        is Map<*, *> -> {
            val keySerializer = value.keys.elementSerializer(module)
            val valueSerializer = value.values.elementSerializer(module)
            MapSerializer(keySerializer, valueSerializer)
        }
        else -> {
            @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
            module.getContextual(value::class) ?: value::class.serializer()
        }
    } as KSerializer<Any>
}
