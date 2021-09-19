/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.cbor.serializer

import io.ktor.client.features.cbor.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.cbor.*
import kotlinx.serialization.modules.*

/**
 * A [CborSerializer] implemented for kotlinx [Serializable] classes.
 */
@ExperimentalSerializationApi
public class KotlinxSerializer(
    private val cbor: Cbor = DefaultCbor
) : CborSerializer {

    override fun write(data: Any, contentType: ContentType): OutgoingContent {
        @Suppress("UNCHECKED_CAST")
        return ByteArrayContent(writeContent(data), contentType)
    }

    internal fun writeContent(data: Any): ByteArray =
        cbor.encodeToByteArray(buildSerializer(data, cbor.serializersModule), data)

    @OptIn(InternalSerializationApi::class)
    override fun read(type: TypeInfo, body: Input): Any {
        val bytes = body.readBytes()
        val deserializationStrategy = cbor.serializersModule.getContextual(type.type)
        val mapper = deserializationStrategy ?: (type.kotlinType?.let { serializer(it) } ?: type.type.serializer())
        return cbor.decodeFromByteArray(mapper, bytes)!!
    }

    public companion object {

        /**
         * Default [Cbor] configuration for [KotlinxSerializer].
         */
        public val DefaultCbor: Cbor = Cbor {
            ignoreUnknownKeys = false
        }
    }
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
