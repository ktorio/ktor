/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization

import kotlinx.serialization.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.json.*
import kotlin.reflect.*
import kotlin.reflect.full.*
import kotlin.reflect.jvm.*

@UseExperimental(ImplicitReflectionSerializer::class, ExperimentalStdlibApi::class)
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
    return ReferenceArraySerializer(
        elementType.jvmErasure as KClass<Any>,
        elementSerializer as KSerializer<Any>
    )
}

@UseExperimental(ImplicitReflectionSerializer::class)
internal fun serializerForSending(value: Any): KSerializer<*> {
    if (value is JsonElement) {
        return JsonElementSerializer
    }
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
private fun Collection<*>.elementSerializer(): KSerializer<*> {
    @Suppress("DEPRECATION_ERROR")
    val serializers = mapNotNull { value ->
        value?.let { serializerForSending(it) }
    }.distinctBy { it.descriptor.name }

    if (serializers.size > 1) {
        @Suppress("DEPRECATION_ERROR")
        error("Serializing collections of different element types is not yet supported. " +
            "Selected serializers: ${serializers.map { it.descriptor.name }}"
        )
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
