/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal abstract class AbstractMapConfigDecoder(
    protected val map: Map<String, String>,
    protected val path: String = "",
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : AbstractDecoder() {
    override fun decodeInt(): Int = decodeString().toInt()
    override fun decodeLong(): Long = decodeString().toLong()
    override fun decodeFloat(): Float = decodeString().toFloat()
    override fun decodeDouble(): Double = decodeString().toDouble()
    override fun decodeBoolean(): Boolean = decodeString().toBoolean()
    override fun decodeChar(): Char = decodeString().single()
    override fun decodeByte(): Byte = decodeString().toByte()
    override fun decodeShort(): Short = decodeString().toShort()
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndex(decodeString())

    protected fun beginStructure(descriptor: SerialDescriptor, path: String): CompositeDecoder {
        val kind = descriptor.kind as? StructureKind ?: error("Expected structure but found ${descriptor.kind}")
        return when (kind) {
            StructureKind.LIST -> ListMapConfigDecoder(map, path, map.listSize(path), serializersModule)
            StructureKind.MAP -> SubMapConfigDecoder(map, path, serializersModule)
            else -> MapConfigDecoder(map, path, serializersModule)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal class MapConfigDecoder(
    map: Map<String, String>,
    path: String = "",
    serializersModule: SerializersModule = EmptySerializersModule()
) : AbstractMapConfigDecoder(map, path, serializersModule) {
    private var elementIndex = 0
    private var currentPath = path

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE

        val newIndex = elementIndex++
        val name = descriptor.getElementName(newIndex)
        val fullPath = if (path.isEmpty()) name else "$path.$name"

        return if (map.containsPrefix(fullPath)) {
            currentPath = fullPath
            newIndex
        } else if (descriptor.isElementOptional(newIndex)) {
            decodeElementIndex(descriptor)
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    override fun decodeNotNullMark(): Boolean =
        map.containsPrefix(currentPath)

    override fun decodeString(): String = map[currentPath]
        ?: throw SerializationException("Property $currentPath not found")

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        beginStructure(descriptor, currentPath)
}

@OptIn(ExperimentalSerializationApi::class)
private class ListMapConfigDecoder(
    map: Map<String, String>,
    path: String,
    private val size: Int,
    override val serializersModule: SerializersModule
) : AbstractMapConfigDecoder(map, path, serializersModule) {
    private var index = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (index < size) index++ else CompositeDecoder.DECODE_DONE
    }

    override fun decodeString(): String {
        val key = "$path.${index - 1}"
        return map[key] ?: throw SerializationException("Missing list element at \"$key\"")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        beginStructure(descriptor, "$path.${index - 1}")
}

@OptIn(ExperimentalSerializationApi::class)
private class SubMapConfigDecoder(
    map: Map<String, String>,
    path: String,
    override val serializersModule: SerializersModule
) : AbstractMapConfigDecoder(map, path, serializersModule) {
    private var index = 0
    private val keys = map.keys
        .filter { it.startsWith("$path.") }
        .map { it.substringAfter("$path.").split('.').first() }
        .distinct()

    private val currentKeyIndex: Int get() =
        if (index % 2 == 1) {
            (index - 1) / 2
        } else {
            (index - 2) / 2
        }

    private val currentKey: String get() =
        keys[currentKeyIndex]

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (index < keys.size * 2) index++ else CompositeDecoder.DECODE_DONE
    }

    override fun decodeString(): String {
        return if (index % 2 == 1) {
            // Decode key
            keys[(index - 1) / 2]
        } else {
            // Decode value
            val key = "$path.${keys[(index - 2) / 2]}"
            map[key] ?: throw SerializationException("Missing map value at $key")
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        beginStructure(descriptor, "$path.$currentKey")
}

private fun Map<String, String>.listSize(path: String): Int =
    (this["$path.size"] ?: "0").toInt()

internal fun Map<String, String>.containsPrefix(prefix: String): Boolean =
    prefix.isEmpty() ||
        containsKey(prefix) ||
        containsKey("$prefix.size") ||
        keys.any {
            it.startsWith(prefix) &&
                it.length > prefix.length &&
                it[prefix.length] == '.'
        }
