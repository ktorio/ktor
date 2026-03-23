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
                module.serializerOrNull(type) ?: checkTypeParameters(type, typeInfo, module)
            }
        }
        ?: module.getContextual(typeInfo.type)?.maybeNullable(typeInfo)
        ?: typeInfo.type.serializer().maybeNullable(typeInfo)
}

/**
 * Inspects the type arguments of [type] to detect any that lack a serializer.
 * If found, throws a [SerializationException] with an actionable message naming the problematic arguments.
 * Returns `null` if all arguments have serializers or if a lookup error prevents a definitive result.
 *
 * Note: only checks a single layer of parameterization; nested generics (e.g. `List<List<T>>`)
 * are not recursively validated.
 */
private fun checkTypeParameters(type: KType, typeInfo: TypeInfo, module: SerializersModule): KSerializer<*>? {
    val nonSerializableArgs = type.arguments
        .mapNotNull { arg ->
            try {
                arg.type?.takeIf { module.serializerOrNull(it) == null }
            } catch (_: Exception) {
                // If lookup itself throws, we cannot reliably determine the cause; fall through
                return null
            }
        }

if (nonSerializableArgs.isEmpty()) return null

    // Format message with the failed type parameters
    val argNames = nonSerializableArgs.joinToString {
        when(val clz = it.classifier) {
            is KClass<*> -> "'${clz.simpleName}'"
            else -> "'$it'"
        }
    }
    val (s, be) =
        if (nonSerializableArgs.size == 1) "" to "is"
        else "s" to "are"
    throw SerializationException(
        "Serializer for type argument$s $argNames $be not found for '${typeInfo.type.simpleName}'. " +
            "Ensure that the listed type$s $be marked as '@Serializable'."
    )
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
