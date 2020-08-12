/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

@OptIn(ExperimentalStdlibApi::class)
internal fun serializerByTypeInfo(type: KType): KSerializer<*> {
    val classifierClass = type.classifier as? KClass<*>
    if (classifierClass != null && classifierClass.java.isArray) {
        return arraySerializer(type)
    }

    return serializer(type)
}

// NOTE: this should be removed once kotlinx.serialization serializer get support of arrays that is blocked by KT-32839
private fun arraySerializer(type: KType): KSerializer<*> {
    val elementType = type.arguments[0].type ?: error("Array<*> is not supported")
    val elementSerializer = serializerByTypeInfo(elementType)


    @Suppress("UNCHECKED_CAST")
    return ArraySerializer(
        elementType.jvmErasure as KClass<Any>,
        elementSerializer as KSerializer<Any>
    )
}

@OptIn(UnsafeSerializationApi::class)
internal fun serializerForSending(value: Any, module: SerializersModule): KSerializer<*> = when (value) {
    is JsonElement -> JsonElementSerializer
    is List<*> -> ListSerializer(value.elementSerializer(module))
    is Set<*> -> SetSerializer(value.elementSerializer(module))
    is Map<*, *> -> MapSerializer(value.keys.elementSerializer(module), value.values.elementSerializer(module))
    is Map.Entry<*, *> -> MapEntrySerializer(
        serializerForSending(value.key ?: error("Map.Entry(null, ...) is not supported"), module),
        serializerForSending(value.value ?: error("Map.Entry(..., null) is not supported)"), module)
    )
    is Array<*> -> {
        val componentType = value.javaClass.componentType.kotlin.starProjectedType
        val componentClass =
            componentType.classifier as? KClass<*> ?: error("Unsupported component type $componentType")

        @Suppress("UNCHECKED_CAST")
        ArraySerializer(
            componentClass as KClass<Any>,
            serializerByTypeInfo(componentType) as KSerializer<Any>
        )
    }
    else -> module.getContextual(value::class) ?: value::class.serializer()
}

@Suppress("EXPERIMENTAL_API_USAGE_ERROR")
private fun Collection<*>.elementSerializer(module: SerializersModule): KSerializer<*> {
    val serializers = mapNotNull { value ->
        value?.let { serializerForSending(it, module) }
    }.distinctBy { it.descriptor.serialName }

    if (serializers.size > 1) {
        val message = "Serializing collections of different element types is not yet supported. " +
            "Selected serializers: ${serializers.map { it.descriptor.serialName }}"
        error(message)
    }

    val selected: KSerializer<*> = serializers.singleOrNull() ?: String.serializer()
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
