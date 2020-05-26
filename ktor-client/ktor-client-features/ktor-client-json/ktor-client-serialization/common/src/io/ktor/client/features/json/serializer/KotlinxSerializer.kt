/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.json.serializer

import io.ktor.client.call.*
import io.ktor.client.features.json.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*

/**
 * A [JsonSerializer] implemented for kotlinx [Serializable] classes.
 */
@OptIn(
    ImplicitReflectionSerializer::class, UnstableDefault::class
)
class KotlinxSerializer(
    private val json: Json = Json(DefaultJsonConfiguration)
) : JsonSerializer {

    override fun write(data: Any, contentType: ContentType): OutgoingContent {
        @Suppress("UNCHECKED_CAST")
        val content = json.stringify(buildSerializer(data) as KSerializer<Any>, data)
        return TextContent(content, contentType)
    }

    override fun read(type: TypeInfo, body: Input): Any {
        val text = body.readText()
        val deserializationStrategy = json.context.getContextual(type.type)
        val mapper = deserializationStrategy ?: (type.kotlinType?.let { serializer(it) } ?: type.type.serializer())
        return json.parse(mapper, text)!!
    }

    public companion object {
        /**
         * Default [Json] configuration for [KotlinxSerializer].
         */
        val DefaultJsonConfiguration: JsonConfiguration = JsonConfiguration(
            isLenient = true,
            ignoreUnknownKeys = false,
            serializeSpecialFloatingPointValues = true,
            useArrayPolymorphism = false
        )
    }
}

@Suppress("UNCHECKED_CAST")
@OptIn(ImplicitReflectionSerializer::class)
private fun buildSerializer(value: Any): KSerializer<*> = when (value) {
    is JsonElement -> JsonElementSerializer
    is List<*> -> value.elementSerializer().list
    is Array<*> -> value.firstOrNull()?.let { buildSerializer(it) } ?: String.serializer().list
    is Set<*> -> value.elementSerializer().set
    is Map<*, *> -> {
        val keySerializer = value.keys.elementSerializer() as KSerializer<Any>
        val valueSerializer = value.values.elementSerializer() as KSerializer<Any>
        MapSerializer(keySerializer, valueSerializer)
    }
    else -> value::class.serializer()
}

@OptIn(ImplicitReflectionSerializer::class)
private fun Collection<*>.elementSerializer(): KSerializer<*> {
    val serializers = filterNotNull().map { buildSerializer(it) }.distinctBy { it.descriptor.serialName }

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
