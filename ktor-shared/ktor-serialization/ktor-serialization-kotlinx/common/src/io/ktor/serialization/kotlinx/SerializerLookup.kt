/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.serialization.kotlinx

import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*

@InternalSerializationApi
@ExperimentalSerializationApi
/**
 * Attempts to create a serializer for the given [typeInfo]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.serialization.kotlinx.serializerForTypeInfo)
 */
public fun SerializersModule.serializerForTypeInfo(typeInfo: TypeInfo): KSerializer<*> {
    val module = this
    return typeInfo.kotlinType
        ?.let { type ->
            if (type.arguments.isEmpty()) {
                null // fallback to a simple case because of
                // https://github.com/Kotlin/kotlinx.serialization/issues/1870
            } else {
                module.serializerOrNull(type) ?: run {
                    var lookupFailed = false
                    val nonSerializableArgs = type.arguments
                        .mapNotNull { it.type }
                        .filter { argType ->
                            try {
                                module.serializerOrNull(argType) == null
                            } catch (_: Throwable) {
                                lookupFailed = true
                                false
                            }
                        }
                    if (lookupFailed) return@run null
                    if (nonSerializableArgs.isNotEmpty()) {
                        val argNames = nonSerializableArgs.joinToString {
                            val classifier = it.classifier
                            if (classifier is KClass<*>) "'${classifier.simpleName}'" else "'$it'"
                        }
                        throw SerializationException(
                            "Serializer for type " +
                                (if (nonSerializableArgs.size == 1) "argument $argNames is" else "arguments $argNames are") +
                                " not found for '${typeInfo.type.simpleName}'. " +
                                "Ensure that the listed " +
                                (if (nonSerializableArgs.size == 1) "type is" else "types are") +
                                " marked as '@Serializable'."
                        )
                    }
                    null
                }
            }
        }
        ?: module.getContextual(typeInfo.type)?.maybeNullable(typeInfo)
        ?: typeInfo.type.serializer().maybeNullable(typeInfo)
}

private fun <T : Any> KSerializer<T>.maybeNullable(typeInfo: TypeInfo): KSerializer<*> {
    return if (typeInfo.kotlinType?.isMarkedNullable == true) this.nullable else this
}

@Suppress("UNCHECKED_CAST")
@InternalAPI
public fun guessSerializer(value: Any?, module: SerializersModule): KSerializer<Any> = when (value) {
    null -> String.serializer().nullable
    is List<*> -> ListSerializer(value.elementSerializer(module))
    is Array<*> -> value.firstOrNull()?.let { guessSerializer(it, module) } ?: ListSerializer(String.serializer())
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

@OptIn(ExperimentalSerializationApi::class, InternalAPI::class)
private fun Collection<*>.elementSerializer(module: SerializersModule): KSerializer<*> {
    val serializers: List<KSerializer<*>> =
        filterNotNull().map { guessSerializer(it, module) }.distinctBy { it.descriptor.serialName }

    if (serializers.size > 1) {
        error(
            "Serializing collections of different element types is not yet supported. " +
                "Selected serializers: ${serializers.map { it.descriptor.serialName }}",
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
