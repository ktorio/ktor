/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources.serialisation

import io.ktor.http.*
import io.ktor.resources.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.modules.*

@OptIn(ExperimentalSerializationApi::class)
internal class ParametersDecoder(
    override val serializersModule: SerializersModule,
    private val parameters: Parameters,
    elementNames: Iterable<String>
) : AbstractDecoder() {

    private val parameterNames = elementNames.iterator()
    private lateinit var currentName: String

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (!parameterNames.hasNext()) {
            return CompositeDecoder.DECODE_DONE
        }
        while (parameterNames.hasNext()) {
            currentName = parameterNames.next()
            val elementIndex = descriptor.getElementIndex(currentName)
            val isPrimitive = descriptor.getElementDescriptor(elementIndex).kind is PrimitiveKind
            if (!isPrimitive || parameters.contains(currentName)) {
                return elementIndex
            }
        }
        return CompositeDecoder.DECODE_DONE
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (descriptor.kind == StructureKind.LIST) {
            return ListLikeDecoder(serializersModule, parameters, currentName)
        }
        return ParametersDecoder(serializersModule, parameters, descriptor.elementNames)
    }

    override fun decodeBoolean(): Boolean {
        return decodeString().toBoolean()
    }

    override fun decodeByte(): Byte {
        return decodeString().toByte()
    }

    override fun decodeChar(): Char {
        return decodeString()[0]
    }

    override fun decodeDouble(): Double {
        return decodeString().toDouble()
    }

    override fun decodeFloat(): Float {
        return decodeString().toFloat()
    }

    override fun decodeInt(): Int {
        return decodeString().toInt()
    }

    override fun decodeLong(): Long {
        return decodeString().toLong()
    }

    override fun decodeShort(): Short {
        return decodeString().toShort()
    }

    override fun decodeString(): String {
        return parameters[currentName]!!
    }

    override fun decodeNotNullMark(): Boolean {
        return parameters.contains(currentName)
    }

    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val enumName = decodeString()
        val index = enumDescriptor.getElementIndex(enumName)
        if (index == CompositeDecoder.UNKNOWN_NAME) {
            throw ResourceRoutingException(
                "${enumDescriptor.serialName} does not contain element with name '$enumName'"
            )
        }
        return index
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class ListLikeDecoder(
    override val serializersModule: SerializersModule,
    private val parameters: Parameters,
    private val parameterName: String
) : AbstractDecoder() {

    private var currentIndex = -1

    private val elementsCount = parameters.getAll(parameterName)?.size ?: 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (++currentIndex == elementsCount) {
            return CompositeDecoder.DECODE_DONE
        }
        return currentIndex
    }

    override fun decodeBoolean(): Boolean {
        return decodeString().toBoolean()
    }

    override fun decodeByte(): Byte {
        return decodeString().toByte()
    }

    override fun decodeChar(): Char {
        return decodeString()[0]
    }

    override fun decodeDouble(): Double {
        return decodeString().toDouble()
    }

    override fun decodeFloat(): Float {
        return decodeString().toFloat()
    }

    override fun decodeInt(): Int {
        return decodeString().toInt()
    }

    override fun decodeLong(): Long {
        return decodeString().toLong()
    }

    override fun decodeShort(): Short {
        return decodeString().toShort()
    }

    override fun decodeString(): String {
        return parameters.getAll(parameterName)!![currentIndex]
    }

    override fun decodeNotNullMark(): Boolean {
        return parameters.contains(parameterName)
    }

    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val enumName = decodeString()
        val index = enumDescriptor.getElementIndex(enumName)
        if (index == CompositeDecoder.UNKNOWN_NAME) {
            throw ResourceRoutingException(
                "${enumDescriptor.serialName} does not contain element with name '$enumName'"
            )
        }
        return index
    }
}
