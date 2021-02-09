/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources.serialisation

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.modules.*

/**
 * Format to (de)serialize resources instances
 */
@OptIn(ExperimentalSerializationApi::class)
public class ResourcesFormat(
    override val serializersModule: SerializersModule = EmptySerializersModule
) : SerialFormat {

    public data class Parameter(
        val name: String,
        val isOptional: Boolean
    )

    public fun <T> encodeToPathPattern(serializer: SerializationStrategy<T>): String {
        val pathBuilder = StringBuilder()

        var current: SerialDescriptor? = serializer.descriptor
        while (current != null) {
            val path = current.annotations.filterIsInstance<Resource>().first().path
            val addSlash = pathBuilder.isNotEmpty() && !pathBuilder.startsWith('/') && !path.endsWith('/')
            if (addSlash) {
                pathBuilder.insert(0, '/')
            }
            pathBuilder.insert(0, path)

            val membersWithAnnotations = current.elementDescriptors.filter { it.annotations.any { it is Resource } }
            if (membersWithAnnotations.size > 1) {
                throw ResourceRoutingException("There are multiple parents for resource ${current.serialName}")
            }
            current = membersWithAnnotations.firstOrNull()
        }

        if (pathBuilder.startsWith('/')) {
            pathBuilder.deleteAt(0)
        }
        return pathBuilder.toString()
    }

    public fun <T> encodeToQueryParameters(serializer: SerializationStrategy<T>): Set<Parameter> {
        val path = encodeToPathPattern(serializer)

        val allParameters = mutableSetOf<Parameter>()
        collectAllParameters(serializer.descriptor, allParameters)

        return allParameters
            .filterNot { (name, _) ->
                path.contains("{$name}") || path.contains("{$name?}") || path.contains("{$name...}")
            }
            .toSet()
    }

    private fun collectAllParameters(descriptor: SerialDescriptor, result: MutableSet<Parameter>) {
        descriptor.elementNames.forEach { name ->
            val index = descriptor.getElementIndex(name)
            val elementDescriptor = descriptor.getElementDescriptor(index)
            if (elementDescriptor.kind is StructureKind.CLASS) {
                collectAllParameters(elementDescriptor, result)
            } else {
                result.add(Parameter(name, descriptor.isElementOptional(index)))
            }
        }
    }

    public fun <T> encodeToParameters(serializer: SerializationStrategy<T>, value: T): Parameters {
        val encoder = ParametersEncoder(serializersModule)
        encoder.encodeSerializableValue(serializer, value)
        return encoder.parameters
    }

    public fun <T> decodeFromParameters(deserializer: DeserializationStrategy<T>, parameters: Parameters): T {
        val input = ParametersDecoder(serializersModule, parameters, emptyList())
        return input.decodeSerializableValue(deserializer)
    }
}
