/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import io.ktor.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*

@KtorExperimentalLocationsAPI
internal class URLDecoder(override val context: SerialModule, val url: Url) : Decoder, CompositeDecoder {
    private var pathParameters: Parameters = Parameters.Empty
    private val pathParameterIndexes = mutableMapOf<String, Int>()

    override val updateMode: UpdateMode = UpdateMode.BANNED

    override fun beginStructure(desc: SerialDescriptor, vararg typeParams: KSerializer<*>): CompositeDecoder {
        if (pathParameters.isEmpty() && desc.getEntityAnnotations().any { it is Location }) {
            pathParameters = buildPathParameters(desc, url.encodedPath)
        }
        return this
    }

    override fun decodeBoolean(): Boolean {
        error("Decoding a primitive from URL is not supported")
    }

    override fun decodeByte(): Byte {
        error("Decoding a primitive from URL is not supported")
    }

    override fun decodeChar(): Char {
        error("Decoding a primitive from URL is not supported")
    }

    override fun decodeDouble(): Double {
        error("Decoding a primitive from URL is not supported")
    }

    override fun decodeEnum(enumDescription: SerialDescriptor): Int {
        TODO("Not yet implemented")
    }

    override fun decodeFloat(): Float {
        error("Decoding a primitive from URL is not supported")
    }

    override fun decodeInt(): Int {
        error("Decoding a primitive from URL is not supported")
    }

    override fun decodeLong(): Long {
        TODO("Not yet implemented")
    }

    override fun decodeNotNullMark(): Boolean {
        error("Decoding a primitive from URL is not supported")
    }

    override fun decodeNull(): Nothing? {
        error("Decoding a primitive from URL is not supported")
    }

    override fun decodeShort(): Short {
        error("Decoding a primitive from URL is not supported")
    }

    override fun decodeString(): String {
        TODO("Not yet implemented")
    }

    override fun decodeUnit() {
        error("Decoding a primitive from URL is not supported")
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
        return deserializer.deserialize(this)
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

        return if (name in pathParameters) {
            pathParameters[name]
        } else {
            val values = url.parameters.getAll(name)
            values?.getOrElse(indexFor(name)) { values.last() }
        } ?: TODO("NPE?")
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

@UseExperimental(KtorExperimentalLocationsAPI::class)
private fun buildPathParameters(desc: SerialDescriptor, actualPath: String): Parameters {
    val pattern = desc.getEntityAnnotations().filterIsInstance<Location>().map { it.path }.single()
    return buildPathParameters(pattern, actualPath)
}

private val pattern = "\\{([a-zA-Z0-9_-]*)(\\?|...)?}".toRegex()

private fun buildPathParameters(pathPattern: String, actualPath: String): Parameters {
    val parsed = RoutingPath.parse(pathPattern)
    if (parsed.parts.none { it.kind == RoutingPathSegmentKind.Parameter }) {
        return Parameters.Empty
    }

    val actualPathSegments = actualPath.split('/')
        .dropWhile { it.isEmpty() }.map { it.decodeURLPart() }

    return Parameters.build {
        parsed.parts.forEachIndexed { index, patternSegment ->
            when (patternSegment.kind) {
                RoutingPathSegmentKind.Constant -> check(actualPathSegments[index] == patternSegment.value)
                RoutingPathSegmentKind.Parameter -> {
                    val matched = pattern.findAll(patternSegment.value).single()
                    val segmentText = actualPathSegments[index]
                    val parameterName = matched.groups[1]?.value ?: ""

                    val suffixLength = patternSegment.value.length - matched.range.last - 1
                    val parameterValue = segmentText.drop(matched.range.first).dropLast(suffixLength)

                    append(parameterName, parameterValue)
                }
            }
        }
    }
}
