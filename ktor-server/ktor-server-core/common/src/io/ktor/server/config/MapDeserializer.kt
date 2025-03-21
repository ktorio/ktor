/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialFormat
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

internal class MapDeserializer(override val serializersModule: SerializersModule) : SerialFormat {

    companion object {
        val DEFAULT = MapDeserializer(EmptySerializersModule())
    }

    fun <T> decodeFromMap(deserializer: DeserializationStrategy<T>, map: Map<String, Any?>): T {
        val decoder = MapDecoder(map, serializersModule)
        return deserializer.deserialize(decoder)
    }

    private class MapDecoder(
        private val map: Map<String, Any?>,
        override val serializersModule: SerializersModule
    ) : AbstractDecoder() {
        private var currentIndex = -1
        private var currentKey: String? = null
        private lateinit var descriptor: SerialDescriptor
        private var listItemIndex = 0
        private var listItems: List<Any?>? = null

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
            this.descriptor = descriptor

            val key = currentKey
            if (key != null) {
                val value = map[key]

                when (descriptor.kind) {
                    StructureKind.CLASS -> {
                        if (value is Map<*, *>) {
                            @Suppress("UNCHECKED_CAST")
                            return MapDecoder(value as Map<String, Any?>, serializersModule).apply {
                                this.descriptor = descriptor
                            }
                        }
                    }

                    StructureKind.LIST -> {
                        if (value is List<*>) {
                            listItems = value
                            listItemIndex = 0
                            return this
                        }
                    }

                    else -> Unit // Other structure kinds not handled specially
                }
            }

            return this
        }

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            if (descriptor.kind == StructureKind.LIST) {
                val items = listItems
                if (items != null && listItemIndex < items.size) {
                    return listItemIndex++
                }
                return CompositeDecoder.DECODE_DONE
            }

            currentIndex++
            if (currentIndex >= descriptor.elementsCount) {
                return CompositeDecoder.DECODE_DONE
            }

            val name = descriptor.getElementName(currentIndex)
            currentKey = name

            return if (map.containsKey(name)) {
                currentIndex
            } else {
                decodeElementIndex(descriptor)
            }
        }

        override fun decodeNotNullMark(): Boolean {
            val key = currentKey ?: return false
            return map.containsKey(key) && map[key] != null
        }

        override fun decodeNull(): Nothing? =null

        override fun decodeString(): String {
            val key = currentKey
            val value = when {
                descriptor.kind == StructureKind.LIST -> listItems?.getOrNull(listItemIndex - 1)
                key != null -> map[key]
                else -> null
            }

            return when (value) {
                null -> ""
                is String -> value
                else -> value.toString()
            }
        }

        override fun decodeInt(): Int {
            val key = currentKey
            val value = when {
                descriptor.kind == StructureKind.LIST -> listItems?.getOrNull(listItemIndex - 1)
                key != null -> map[key]
                else -> null
            }

            return when (value) {
                is Int -> value
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                    ?: throw SerializationException("Expected Int for key '$key', but got '$value'")

                null -> throw SerializationException("Expected Int for key '$key', but got null")
                else -> throw SerializationException("Expected Int for key '$key', but got '${value::class.simpleName}'")
            }
        }

        override fun decodeLong(): Long {
            val key = currentKey
            val value = when {
                descriptor.kind == StructureKind.LIST -> listItems?.getOrNull(listItemIndex - 1)
                key != null -> map[key]
                else -> null
            }

            return when (value) {
                is Long -> value
                is Number -> value.toLong()
                is String -> value.toLongOrNull()
                    ?: throw SerializationException("Expected Long for key '$key', but got '$value'")

                null -> throw SerializationException("Expected Long for key '$key', but got null")
                else -> throw SerializationException("Expected Long for key '$key', but got '${value::class.simpleName}'")
            }
        }

        override fun decodeFloat(): Float {
            val key = currentKey
            val value = when {
                descriptor.kind == StructureKind.LIST -> listItems?.getOrNull(listItemIndex - 1)
                key != null -> map[key]
                else -> null
            }

            return when (value) {
                is Float -> value
                is Number -> value.toFloat()
                is String -> value.toFloatOrNull()
                    ?: throw SerializationException("Expected Float for key '$key', but got '$value'")

                null -> throw SerializationException("Expected Float for key '$key', but got null")
                else -> throw SerializationException("Expected Float for key '$key', but got '${value::class.simpleName}'")
            }
        }

        override fun decodeDouble(): Double {
            val key = currentKey
            val value = when {
                descriptor.kind == StructureKind.LIST -> listItems?.getOrNull(listItemIndex - 1)
                key != null -> map[key]
                else -> null
            }

            return when (value) {
                is Double -> value
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                    ?: throw SerializationException("Expected Double for key '$key', but got '$value'")

                null -> throw SerializationException("Expected Double for key '$key', but got null")
                else -> throw SerializationException("Expected Double for key '$key', but got '${value::class.simpleName}'")
            }
        }

        override fun decodeBoolean(): Boolean {
            val key = currentKey
            val value = when {
                descriptor.kind == StructureKind.LIST -> listItems?.getOrNull(listItemIndex - 1)
                key != null -> map[key]
                else -> null
            }

            return when (value) {
                is Boolean -> value
                is String -> value.toBooleanStrictOrNull()
                    ?: throw SerializationException("Expected Boolean for key '$key', but got '$value'")

                is Number -> throw SerializationException("Expected Boolean for key '$key', but got '$value'")
                null -> throw SerializationException("Expected Boolean for key '$key', but got null")
                else -> throw SerializationException("Expected Boolean for key '$key', but got '${value::class.simpleName}'")
            }
        }

        override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
            val key = currentKey
            val enumValue = when {
                descriptor.kind == StructureKind.LIST -> listItems?.getOrNull(listItemIndex - 1)
                key != null -> map[key]
                else -> null
            }

            val enumString = enumValue.toString()

            for (i in 0 until enumDescriptor.elementsCount) {
                if (enumDescriptor.getElementName(i).equals(enumString, ignoreCase = true)) {
                    return i
                }
            }

            val validValues = (0 until enumDescriptor.elementsCount).map { enumDescriptor.getElementName(it) }
            throw SerializationException("Expected one of ${validValues.joinToString()} for key '$key', but got '$enumString'")
        }

        override fun <T> decodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            deserializer: DeserializationStrategy<T>,
            previousValue: T?
        ): T {
            if (this.descriptor.kind == StructureKind.LIST) {
                val value = listItems?.getOrNull(listItemIndex - 1)

                if (value is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val mapValue = value as Map<String, Any?>
                    return MapDecoder(mapValue, serializersModule).decodeSerializableValue(deserializer)
                }
            }

            return super.decodeSerializableElement(descriptor, index, deserializer, previousValue)
        }
    }
}
