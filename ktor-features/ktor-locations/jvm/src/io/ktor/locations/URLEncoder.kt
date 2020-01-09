/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import io.ktor.routing.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*

@KtorExperimentalLocationsAPI
internal class URLEncoder(
    override val context: SerialModule
) : Encoder, CompositeEncoder {
    private val builder = URLBuilder()
    private val pathParameters = ParametersBuilder()

    private var path: String? = null

    override fun beginStructure(desc: SerialDescriptor, vararg typeParams: KSerializer<*>): CompositeEncoder {
        if (path == null) {
            check(this.path == null)
            this.path = buildPathParameters(desc, pathParameters)
        }
        return this
    }

    override fun encodeBoolean(value: Boolean) {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeByte(value: Byte) {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeChar(value: Char) {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeDouble(value: Double) {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeEnum(enumDescription: SerialDescriptor, ordinal: Int) {
        TODO("Not yet implemented")
    }

    override fun encodeFloat(value: Float) {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeInt(value: Int) {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeLong(value: Long) {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeNotNullMark() {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeNull() {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeShort(value: Short) {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeString(value: String) {
        error("Encoding a String to URL is not supported")
    }

    override fun encodeUnit() {
        error("Encoding a Unit to URL is not supported")
    }

    override fun encodeBooleanElement(desc: SerialDescriptor, index: Int, value: Boolean) {
        encodeElement(desc, index, value.toString())
    }

    override fun encodeByteElement(desc: SerialDescriptor, index: Int, value: Byte) {
        encodeElement(desc, index, value.toString())
    }

    override fun encodeCharElement(desc: SerialDescriptor, index: Int, value: Char) {
        encodeElement(desc, index, value.toString())
    }

    override fun encodeDoubleElement(desc: SerialDescriptor, index: Int, value: Double) {
        encodeElement(desc, index, value.toString())
    }

    override fun encodeFloatElement(desc: SerialDescriptor, index: Int, value: Float) {
        encodeElement(desc, index, value.toString())
    }

    override fun encodeIntElement(desc: SerialDescriptor, index: Int, value: Int) {
        encodeElement(desc, index, value.toString())
    }

    override fun encodeLongElement(desc: SerialDescriptor, index: Int, value: Long) {
        encodeElement(desc, index, value.toString())
    }

    override fun encodeNonSerializableElement(desc: SerialDescriptor, index: Int, value: Any) {
        TODO("Not yet implemented")
    }

    override fun <T : Any> encodeNullableSerializableElement(
        desc: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?
    ) {
        if (value != null) {
            serializer.serialize(this, value)
        }
    }

    override fun <T> encodeSerializableElement(
        desc: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        serializer.serialize(this, value)
    }

    override fun encodeShortElement(desc: SerialDescriptor, index: Int, value: Short) {
        encodeElement(desc, index, value.toString())
    }

    override fun encodeStringElement(desc: SerialDescriptor, index: Int, value: String) {
        encodeElement(desc, index, value)
    }

    override fun encodeUnitElement(desc: SerialDescriptor, index: Int) {
        encodeElement(desc, index, "")
    }

    private fun encodeElement(desc: SerialDescriptor, index: Int, stringified: String) {
        val name = desc.getElementName(index)
        if (name in pathParameters) {
            pathParameters[name] = stringified
        } else {
            builder.parameters.append(name, stringified)
        }
    }

    fun build(): Url {
        check(pathParameters.entries().none { it.value.single() === UnspecifiedParameterValue })
        val parsed = RoutingPath.parse(path ?: "/")
        val pattern = "\\{([a-zA-Z0-9_-]*)(\\?|...)?}".toRegex()
        val suffix = when {
            path?.lastOrNull() == '/' -> "/"
            else -> ""
        }
        val actualPath =
            parsed.parts.asSequence().map { part ->
                when (part.kind) {
                    RoutingPathSegmentKind.Constant -> part.value.encodeURLPath()
                    RoutingPathSegmentKind.Parameter -> pattern.replace(part.value) { result ->
                        val parameterName = result.groups[1]?.value ?: return@replace ""
                        val value = pathParameters[parameterName] ?: return@replace ""
                        value.encodeURLPath()
                    }
                }
            }.filter { it.isNotEmpty() }.joinToString(separator = "/", prefix = "/", postfix = suffix)

        builder.encodedPath = actualPath

        return builder.build()
    }
}

@Serializable
private data class C(val b: String)

@KtorExperimentalLocationsAPI
@Serializable
@Location("/path/{a}/")
private data class Xy(val a: String, val b: String, val c: C)

/**
 * Test entry point
 */
@KtorExperimentalLocationsAPI
@UseExperimental(ImplicitReflectionSerializer::class)
fun main() {
    val serializer = Xy::class.serializer()
    val encoder = URLEncoder(EmptyModule)

    val valueBefore = Xy("111", "222", C("333"))
    println("Value before: $valueBefore")

    serializer.serialize(encoder, valueBefore)

    val url = encoder.build()
    println("URL: $url")

    val decoder = URLDecoder(EmptyModule, url)
    val valueAfter = serializer.deserialize(decoder)

    println("Value after: $valueAfter")
}

internal val UnspecifiedParameterValue = String()

@UseExperimental(KtorExperimentalLocationsAPI::class)
private fun buildPathParameters(desc: SerialDescriptor, pathParameters: ParametersBuilder): String {
    val path = desc.getEntityAnnotations().filterIsInstance<Location>().map { it.path }.single()
    buildPathParameters(path, pathParameters)
    return path
}

private fun buildPathParameters(path: String, pathParameters: ParametersBuilder) {
    pathParameters.clear()

    val parsed = RoutingPath.parse(path)
    val pattern = "\\{([a-zA-Z0-9_-]*)(\\?|...)?}".toRegex()
    parsed.parts.asSequence().filter { it.kind == RoutingPathSegmentKind.Parameter }.flatMap {
        pattern.findAll(it.value).mapNotNull { parameter -> parameter.groups[1]?.value }
    }.forEach { parameterName ->
        pathParameters.append(parameterName, UnspecifiedParameterValue)
    }
}
