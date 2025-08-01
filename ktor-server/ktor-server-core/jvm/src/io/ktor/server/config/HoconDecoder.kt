/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.config

import com.typesafe.config.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

@OptIn(ExperimentalSerializationApi::class)
internal open class HoconDecoder(
    private val config: Config,
    private val path: String = "",
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : AbstractDecoder() {
    protected var elementIndex = 0
    protected var currentPath = path

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE

        val newIndex = elementIndex++
        val name = descriptor.getElementName(newIndex)
        val fullPath = if (path.isEmpty()) name else "$path.$name"

        return if (config.hasPath(fullPath)) {
            currentPath = fullPath
            newIndex
        } else if (descriptor.isElementOptional(newIndex)) {
            decodeElementIndex(descriptor)
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    override fun decodeNotNullMark(): Boolean {
        return config.hasPath(currentPath) && !config.getIsNull(currentPath)
    }

    override fun decodeBoolean(): Boolean = config.getBoolean(currentPath)

    override fun decodeByte(): Byte = config.getInt(currentPath).toByte()

    override fun decodeShort(): Short = config.getInt(currentPath).toShort()

    override fun decodeInt(): Int = config.getInt(currentPath)

    override fun decodeLong(): Long = config.getLong(currentPath)

    override fun decodeFloat(): Float = config.getDouble(currentPath).toFloat()

    override fun decodeDouble(): Double = config.getDouble(currentPath)

    override fun decodeChar(): Char = config.getString(currentPath)[0]

    override fun decodeString(): String = config.getString(currentPath)

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndex(decodeString())

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return when (descriptor.kind as StructureKind) {
            StructureKind.LIST ->
                HoconListDecoder(
                    config.getList(currentPath),
                    serializersModule = serializersModule
                )
            StructureKind.MAP ->
                HoconMapDecoder(
                    config.getObject(currentPath).toConfig(),
                    serializersModule = serializersModule
                )
            StructureKind.CLASS,
            StructureKind.OBJECT ->
                HoconDecoder(config, currentPath, serializersModule)
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal class HoconListDecoder(
    private val list: ConfigList,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : AbstractDecoder() {
    private var elementIndex = 0
    private val currentElement: ConfigValue get() = list[elementIndex - 1]

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (elementIndex < list.size) elementIndex++ else CompositeDecoder.DECODE_DONE
    }

    override fun decodeValue(): Any =
        currentElement.unwrapped()

    override fun decodeString(): String =
        currentElement.unwrapped().toString()

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndex(decodeString())

    override fun decodeLong(): Long {
        return when (val value = currentElement.unwrapped()) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw SerializationException("Cannot decode Long from $value")
        }
    }

    override fun decodeDouble(): Double {
        return when (val value = list[elementIndex - 1].unwrapped()) {
            is Int -> value.toDouble()
            is Long -> value.toDouble()
            is Double -> value
            else -> throw SerializationException("Cannot decode Double from $value")
        }
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        return when (descriptor.kind as StructureKind) {
            StructureKind.LIST ->
                HoconListDecoder(
                    currentElement as? ConfigList
                        ?: throw SerializationException("Expected a ConfigList but got $currentElement"),
                    serializersModule = serializersModule
                )
            StructureKind.MAP ->
                HoconMapDecoder(
                    (currentElement as? ConfigObject)?.toConfig()
                        ?: throw SerializationException("Expected a ConfigObject but got $currentElement"),
                    serializersModule = serializersModule
                )
            StructureKind.CLASS,
            StructureKind.OBJECT ->
                HoconDecoder(
                    (currentElement as? ConfigObject)?.toConfig()
                        ?: throw SerializationException("Expected a ConfigObject but got $currentElement"),
                    "",
                    serializersModule
                )
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal class HoconMapDecoder(
    private val config: Config,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : HoconDecoder(config, "", serializersModule) {

    private val keys = config.root().keys.toList()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= keys.size * 2) return CompositeDecoder.DECODE_DONE

        // For maps, we alternate between keys and values
        // Even indexes are for keys, odd indexes are for values
        val result = elementIndex

        // If this is a key, store it to use for the next value
        if (elementIndex % 2 == 0) {
            currentPath = keys[elementIndex / 2]
        }

        elementIndex++
        return result
    }

    override fun decodeString(): String {
        return if (elementIndex % 2 == 1) {
            // We're decoding a key
            currentPath
        } else {
            // We're decoding a value
            config.getString(currentPath)
        }
    }
}
