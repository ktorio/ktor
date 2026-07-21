/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.internal.GeneratedSerializer

internal actual fun typeName(mapping: JsonSchema.Discriminator.Mapping): String? =
    mapping.ref.simpleName

@OptIn(ExperimentalSerializationApi::class)
internal actual fun sealedSubclassComponentNameMapping(serializer: KSerializer<*>?): Map<String, String> {
    val sealedSerializer = serializer?.unwrapNullableSerializer() ?: return emptyMap()
    if (sealedSerializer.descriptor.kind != PolymorphicKind.SEALED || sealedSerializer.descriptor.elementsCount != 2) {
        return emptyMap()
    }

    val sealedElementsDescriptor = sealedSerializer.descriptor.getElementDescriptor(1)
    return (0..<sealedElementsDescriptor.elementsCount).associate { index ->
        val serialName = sealedElementsDescriptor.getElementName(index)
        val subclassDescriptor = sealedElementsDescriptor.getElementDescriptor(index)
        val componentName = subclassComponentName(nestedSerializerAt(sealedSerializer, index))
            ?: fallbackSealedSubclassComponentName(
                descriptorSerialName = sealedSerializer.descriptor.serialName,
                subclassSerialName = subclassDescriptor.serialName,
                serialName = serialName,
            )
        serialName to componentName
    }
}

@OptIn(InternalSerializationApi::class)
internal actual fun nestedSerializerAt(serializer: KSerializer<*>?, index: Int): KSerializer<*>? =
    (serializer?.unwrapNullableSerializer() as? GeneratedSerializer<*>)?.childSerializers()?.getOrNull(index)

internal actual fun subclassComponentName(serializer: KSerializer<*>?): String? =
    serializer
        ?.unwrapNullableSerializer()
        ?.let(::serializerComponentName)

private fun KSerializer<*>.unwrapNullableSerializer(): KSerializer<*> = nestedSerializerAtNullable(this) ?: this

@OptIn(InternalSerializationApi::class)
private fun nestedSerializerAtNullable(serializer: KSerializer<*>): KSerializer<*>? =
    (serializer as? GeneratedSerializer<*>)?.takeIf {
        serializer.descriptor.isNullable
    }?.childSerializers()?.firstOrNull()

private fun serializerComponentName(serializer: KSerializer<*>): String? {
    val renderedClassName = serializer::class.toString()
        .substringAfter("class ", missingDelimiterValue = serializer::class.toString())
        .substringAfter("interface ", missingDelimiterValue = serializer::class.toString())
    return renderedClassName.toComponentName()
        ?: serializer::class.simpleName?.toComponentName()
}

private fun String.toComponentName(): String? =
    takeIf { it.endsWith("\$\$serializer") }
        ?.removeSuffix("\$\$serializer")
        ?.replace('$', '.')
        ?.takeIf { it.isNotBlank() }

private fun fallbackSealedSubclassComponentName(
    descriptorSerialName: String,
    subclassSerialName: String,
    serialName: String,
): String =
    if (subclassSerialName.trimEnd('?') == serialName) {
        "${descriptorSerialName.trimEnd('?')}.$serialName"
    } else {
        subclassSerialName.trimEnd('?')
    }
