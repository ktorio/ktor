/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import io.ktor.openapi.GenericElement.Companion.orEmpty
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.collections.mapOf
import kotlin.reflect.KProperty

internal interface Extensible {
    val extensions: ExtensionProperties
}

internal abstract class ExtensibleMixinSerializer<T : Extensible>(
    baseSerializer: KSerializer<T>,
    copy: (T, ExtensionProperties) -> T,
) : SinglePropertyMixinSerializer<T, ExtensionProperties>(
    baseSerializer,
    Extensible::extensions,
    serializer<ExtensionProperties>(),
    { it.startsWith("x-") },
    copy
)

@Suppress("UNCHECKED_CAST")
internal abstract class SinglePropertyMixinSerializer<T, P>(
    baseSerializer: KSerializer<T>,
    property: KProperty<P>,
    propertySerializer: KSerializer<P>,
    propertyKeyMatcher: (String) -> Boolean = propertySerializer.descriptor.elementNames.toSet()::contains,
    copy: (T, P) -> T,
) : DelegateMixinSerializer<T>(
    baseSerializer,
    mapOf(property.name to PropertyDelegateDescriptor(propertySerializer, propertyKeyMatcher)),
    { base, extras ->
        extras.firstOrNull()?.let {
            copy(base, it as P)
        } ?: base
    }
)

@Suppress("UNCHECKED_CAST")
internal abstract class DoublePropertyMixinSerializer<T, P0, P1>(
    baseSerializer: KSerializer<T>,
    property0: KProperty<P0>,
    property0Serializer: KSerializer<P0>,
    property0KeyMatcher: (String) -> Boolean = property0Serializer.descriptor.elementNames.toSet()::contains,
    property1: KProperty<P1>,
    property1Serializer: KSerializer<P1>,
    property1KeyMatcher: (String) -> Boolean = property1Serializer.descriptor.elementNames.toSet()::contains,
    copy: (T, P0, P1) -> T,
) : DelegateMixinSerializer<T>(
    baseSerializer,
    mapOf(
        property0.name to PropertyDelegateDescriptor(property0Serializer, property0KeyMatcher),
        property1.name to PropertyDelegateDescriptor(property1Serializer, property1KeyMatcher),
    ),
    { base, extras ->
        val (property0Value, property1Value) = extras
        copy(base, property0Value as P0, property1Value as P1)
    }
)

internal abstract class DelegateMixinSerializer<T>(
    private val baseSerializer: KSerializer<T>,
    private val properties: Map<String, PropertyDelegateDescriptor>,
    private val copy: (T, List<Any?>) -> T,
) : KSerializer<T> {
    private val reverseLookup: List<Pair<(String) -> Boolean, String>> = properties.entries.map { (key, value) ->
        value.keyMatcher to key
    }

    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = baseSerializer.descriptor

    override fun serialize(encoder: Encoder, value: T) {
        val joinedElement: GenericElement? = genericElementSerialAdapters.firstNotNullOfOrNull {
            it.trySerializeToElement(encoder, value, baseSerializer)
        }
        require(joinedElement != null) {
            "No GenericElementSerialAdapter found for ${encoder::class.simpleName}"
        }

        val dividedElements = joinedElement.extract(properties.keys)
        when (dividedElements.size) {
            1 -> encoder.encodeSerializableValue(baseSerializer, value)
            else -> {
                encoder.encodeSerializableValue(
                    encoder.serializersModule.serializer<GenericElement>(),
                    dividedElements.values.reduce { acc, genericElement ->
                        acc + genericElement
                    }
                )
            }
        }
    }

    override fun deserialize(decoder: Decoder): T {
        val joinedElement = decoder.decodeSerializableValue(GenericElement.serializer())
        val dividedElements = joinedElement.extract(reverseLookup)
        val baseElement = dividedElements[ElementKey.Root].orEmpty()
        val baseObject = baseElement.deserialize(baseSerializer)
        val extras = properties.map { (key, property) ->
            dividedElements[ElementKey.Property(key)]?.deserialize(property.serializer)
        }
        return copy(baseObject, extras)
    }
}

internal data class PropertyDelegateDescriptor(
    val serializer: KSerializer<*>,
    val keyMatcher: (String) -> Boolean,
)

/**
 * Given a set of keys, extracts a new [GenericElement] from a root object for each key.
 */
internal fun GenericElement.extract(keys: Collection<String>): Map<ElementKey, GenericElement> {
    val (root, separated) = entries().partition { it.first !in keys }
    return buildMap {
        put(ElementKey.Root, GenericElement(root))
        for ((key, value) in separated) {
            put(ElementKey.Property(key), value)
        }
    }
}

/**
 * Extracts new [GenericElement]s through associated properties.
 */
internal fun GenericElement.extract(
    reverseLookup: List<Pair<(String) -> Boolean, String>>
): Map<ElementKey, GenericElement> {
    return entries().groupBy { entry ->
        reverseLookup.firstOrNull { (matcher, _) ->
            matcher(entry.first)
        }?.let { ElementKey.Property(it.second) } ?: ElementKey.Root
    }.mapValues { (_, entries) ->
        GenericElement(entries)
    }
}

internal sealed interface ElementKey {
    object Root : ElementKey
    data class Property(val key: String) : ElementKey
}
