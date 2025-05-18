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
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    override fun decodeNotNullMark(): Boolean =
        map.containsPrefix(currentPath)

    override fun decodeString(): String = map[currentPath]
        ?: throw SerializationException("Property $currentPath not found")

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndex(decodeString())

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val kind = descriptor.kind as StructureKind
        return when (kind) {
            StructureKind.LIST -> ListMapConfigDecoder(map, currentPath, map.listSize(currentPath), serializersModule)
            StructureKind.MAP -> SubMapConfigDecoder(map, currentPath, serializersModule)
            else -> MapConfigDecoder(map, currentPath, serializersModule)
        }
    }
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

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndex(decodeString())

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val currentKey = "$path.${index - 1}"
        val kind = descriptor.kind as StructureKind
        return when (kind) {
            StructureKind.LIST -> ListMapConfigDecoder(map, currentKey, map.listSize(currentKey), serializersModule)
            StructureKind.MAP -> SubMapConfigDecoder(map, currentKey, serializersModule)
            else -> MapConfigDecoder(map, currentKey, serializersModule)
        }
    }
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

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val key = "$path.$currentKey"
        val kind = descriptor.kind as StructureKind
        return when (kind) {
            StructureKind.LIST -> ListMapConfigDecoder(map, key, map.listSize(key), serializersModule)
            StructureKind.MAP -> SubMapConfigDecoder(map, key, serializersModule)
            else -> MapConfigDecoder(map, key, serializersModule)
        }
    }
}

private fun Map<String, String>.listSize(path: String): Int =
    (this["$path.size"] ?: "0").toInt()

internal fun Map<String, String>.containsPrefix(prefix: String): Boolean =
    containsKey(prefix) ||
        containsKey("$prefix.size") ||
        keys.any {
            it.startsWith(prefix) &&
                it.length > prefix.length &&
                it[prefix.length] == '.'
        }
