/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*

@KtorExperimentalLocationsAPI
internal class URLDecoder(override val context: SerialModule, private val url: Url) : Decoder, CompositeDecoder {
    private var pathParameters: Parameters = Parameters.Empty
    private var pattern: LocationPattern? = null
    private val pathParameterIndexes = mutableMapOf<String, Int>()

    override val updateMode: UpdateMode = UpdateMode.BANNED

    private var currentElementName: String? = null

    override fun beginStructure(desc: SerialDescriptor, vararg typeParams: KSerializer<*>): CompositeDecoder {
        if (desc.kind != StructureKind.CLASS) {
            return this
        }

        val location = desc.getEntityAnnotations().firstOrNull { it is Location } as? Location

        if (location != null && this.pattern == null) {
            val children = desc.elementDescriptors().mapNotNull { child ->
                child.getEntityAnnotations().firstOrNull { it is Location } as? Location
            }

            // TODO make recursion
            this.pattern = when (children.size) {
                0 -> LocationPattern(location.path)
                1 -> LocationPattern(children[0].path) + LocationPattern(location.path)
                else -> error("...")
            }

            try {
                this.pathParameters = this.pattern!!.parse(url.encodedPath)
            } catch (cause: Throwable) {
                cause.printStackTrace()
            }
        }

        return this
    }

    override fun decodeBoolean(): Boolean {
        return decodeString().toBoolean()
    }

    override fun decodeByte(): Byte {
        return decodeString().toByte()
    }

    override fun decodeChar(): Char {
        return decodeString().single()
    }

    override fun decodeDouble(): Double {
        return decodeString().toDouble()
    }

    override fun decodeEnum(enumDescription: SerialDescriptor): Int {
        TODO("Not yet implemented")
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

    override fun decodeNotNullMark(): Boolean {
        TODO("NotNull marks are not supported")
    }

    override fun decodeNull(): Nothing? {
        TODO("Decoding Nulls is not supported")
    }

    override fun decodeShort(): Short {
        return decodeString().toShort()
    }

    override fun decodeString(): String {
        val pattern = pattern
        val parameterName = currentElementName

        if (pattern != null && parameterName != null) {
            val values = if (parameterName in pattern.pathParameterNames) {
                pathParameters.getAll(parameterName)
            } else {
                url.parameters.getAll(parameterName)
            } ?: error("No value for parameter $parameterName")

            return values.getOrElse(indexFor(parameterName)) { values.last() }
        }

        error("Unable to decode a String.")
    }

    override fun decodeUnit() {
        decodeString()
    }

    override fun decodeBooleanElement(desc: SerialDescriptor, index: Int): Boolean {
        return decodeElement(desc, index).let { it == "" || it == "true" }
    }

    override fun decodeByteElement(desc: SerialDescriptor, index: Int): Byte {
        return decodeElement(desc, index).toByte()
    }

    override fun decodeCharElement(desc: SerialDescriptor, index: Int): Char {
        return decodeElement(desc, index).single()
    }

    override fun decodeDoubleElement(desc: SerialDescriptor, index: Int): Double {
        return decodeElement(desc, index).toDouble()
    }

    override fun decodeElementIndex(desc: SerialDescriptor): Int {
        return CompositeDecoder.READ_ALL
    }

    override fun decodeFloatElement(desc: SerialDescriptor, index: Int): Float {
        return decodeElement(desc, index).toFloat()
    }

    override fun decodeIntElement(desc: SerialDescriptor, index: Int): Int {
        return decodeElement(desc, index).toInt()
    }

    override fun decodeLongElement(desc: SerialDescriptor, index: Int): Long {
        return decodeElement(desc, index).toLong()
    }

    override fun <T : Any> decodeNullableSerializableElement(
        desc: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>
    ): T? {
        return deserializer.deserialize(this)
    }

    override fun <T> decodeSerializableElement(
        desc: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>
    ): T {
        if (desc.kind != StructureKind.CLASS) {
            return deserializer.deserialize(this)
        }

        val before = currentElementName
        currentElementName = desc.getElementName(index)
        return try {
            deserializer.deserialize(this)
        } finally {
            currentElementName = before
        }
    }

    override fun decodeShortElement(desc: SerialDescriptor, index: Int): Short {
        return decodeElement(desc, index).toShort()
    }

    override fun decodeStringElement(desc: SerialDescriptor, index: Int): String {
        return decodeElement(desc, index)
    }

    override fun decodeUnitElement(desc: SerialDescriptor, index: Int) {
        check(decodeElement(desc, index) == "")
    }

    private fun decodeElement(desc: SerialDescriptor, index: Int): String {
        val name = desc.getElementName(index)

        return if (name in pattern!!.pathParameterNames) {
            pathParameters[name]
        } else {
            val values = url.parameters.getAll(name)
            values?.getOrElse(indexFor(name)) { values.last() }
        } ?: run {
            TODO("NPE?")
        }
    }

    override fun decodeCollectionSize(desc: SerialDescriptor): Int {
        val pattern = pattern
        val parameterName = currentElementName

        if (pattern != null && parameterName != null) {
            if (parameterName in pattern.pathParameterNames) {
                return pathParameters.getAll(parameterName)?.size ?: 0
            }
            return url.parameters.getAll(parameterName)?.size ?: 0
        }

        return super.decodeCollectionSize(desc)
    }

    private fun indexFor(name: String): Int {
        val existingIndex = pathParameterIndexes[name]
        val index = existingIndex ?: 0

        pathParameterIndexes[name] = index + 1
        return index
    }

    override fun <T : Any> updateNullableSerializableElement(
        desc: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        old: T?
    ): T? {
        TODO("Not yet implemented")
    }

    override fun <T> updateSerializableElement(
        desc: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        old: T
    ): T {
        TODO("Not yet implemented")
    }
}
