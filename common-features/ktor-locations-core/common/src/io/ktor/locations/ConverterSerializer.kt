/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.*

public class ConverterSerializer<T>(
    private val type: KType,
    private val conversionService: ConversionService
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = ConverterSerialDescriptor(type.toString())

    @OptIn(ExperimentalStdlibApi::class)
    override fun deserialize(decoder: Decoder): T {
        val values = buildList<String> {
            decoder.decodeStructure(descriptor) {
                repeat(decodeCollectionSize(descriptor)) { index ->
                    add(decodeStringElement(descriptor, index))
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        return conversionService.fromValues(values, type) as T
    }

    override fun serialize(encoder: Encoder, value: T) {
        val converted = conversionService.toValues(value)
        val elementSerializer = String.serializer()

        encoder.beginCollection(descriptor, converted.size).apply {
            converted.forEachIndexed { index, component ->
                encodeSerializableElement(descriptor, index, elementSerializer, component)
            }

            endStructure(descriptor)
        }
    }

    private class ConverterSerialDescriptor(override val serialName: String) : SerialDescriptor {
        override val elementsCount: Int
            get() = 1

        override val kind: SerialKind
            get() = StructureKind.LIST

        override fun getElementAnnotations(index: Int): List<Annotation> {
            return emptyList()
        }

        override fun getElementDescriptor(index: Int): SerialDescriptor {
            return String.serializer().descriptor
        }

        override fun getElementIndex(name: String): Int {
            return name.toIntOrNull() ?: CompositeDecoder.UNKNOWN_NAME
        }

        override fun getElementName(index: Int): String {
            return index.toString()
        }

        override fun isElementOptional(index: Int): Boolean {
            return false
        }

    }
}
