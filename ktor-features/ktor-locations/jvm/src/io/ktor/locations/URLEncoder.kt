/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.Encoder
import kotlinx.serialization.modules.*
import kotlin.reflect.*

@KtorExperimentalLocationsAPI
internal class URLEncoder(
    override val context: SerialModule,
    private val rootClass: KClass<*>,
    private val conversionService: ConversionService = DefaultConversionService
) : Encoder, CompositeEncoder {
    private val pathParameters = ParametersBuilder()
    private val queryParameters = ParametersBuilder()

    private var pattern: LocationPattern? = null
    private var currentElementName: String? = null

    override fun beginStructure(descriptor: SerialDescriptor, vararg typeSerializers: KSerializer<*>): CompositeEncoder {
        if (descriptor.kind.isClassOrObject() && this.pattern == null) {
            this.pattern = buildLocationPattern(descriptor, rootClass)
        }

        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
    }

    override fun encodeBoolean(value: Boolean) {
        encodeString(value.toString())
    }

    override fun encodeByte(value: Byte) {
        encodeString(value.toString())
    }

    override fun encodeChar(value: Char) {
        encodeString(value.toString())
    }

    override fun encodeDouble(value: Double) {
        encodeString(value.toString())
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        TODO("Not yet implemented")
    }

    override fun encodeFloat(value: Float) {
        encodeString(value.toString())
    }

    override fun encodeInt(value: Int) {
        encodeString(value.toString())
    }

    override fun encodeLong(value: Long) {
        encodeString(value.toString())
    }

    override fun encodeNotNullMark() {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeNull() {
        error("Encoding a primitive to URL is not supported")
    }

    override fun encodeShort(value: Short) {
        encodeString(value.toString())
    }

    override fun encodeString(value: String) {
        val name = currentElementName!!
        val pattern = pattern!!

        if (name in pattern.pathParameterNames) {
            pathParameters.append(name, value)
        } else {
            queryParameters.append(name, value)
        }
    }

    override fun encodeUnit() {
        encodeString("Unit")
    }

    override fun encodeBooleanElement(descriptor: SerialDescriptor, index: Int, value: Boolean) {
        encodeElement(descriptor, index, value.toString())
    }

    override fun encodeByteElement(descriptor: SerialDescriptor, index: Int, value: Byte) {
        encodeElement(descriptor, index, value.toString())
    }

    override fun encodeCharElement(descriptor: SerialDescriptor, index: Int, value: Char) {
        encodeElement(descriptor, index, value.toString())
    }

    override fun encodeDoubleElement(descriptor: SerialDescriptor, index: Int, value: Double) {
        encodeElement(descriptor, index, value.toString())
    }

    override fun encodeFloatElement(descriptor: SerialDescriptor, index: Int, value: Float) {
        encodeElement(descriptor, index, value.toString())
    }

    override fun encodeIntElement(descriptor: SerialDescriptor, index: Int, value: Int) {
        encodeElement(descriptor, index, value.toString())
    }

    override fun encodeLongElement(descriptor: SerialDescriptor, index: Int, value: Long) {
        encodeElement(descriptor, index, value.toString())
    }

    override fun <T : Any> encodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T?
    ) {
        if (value != null) {
            encodeSerializableElement(descriptor, index, serializer, value)
        }
    }

    override fun <T> encodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        serializer: SerializationStrategy<T>,
        value: T
    ) {
        val before = currentElementName
        val name = when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> descriptor.getElementName(index)
            else -> before
        }

        currentElementName = name
        try {
            if (descriptor.kind.isClassOrObject()) {
                val elementDescriptor = descriptor.getElementDescriptor(index)
                if (name != null && elementDescriptor.location == null && elementDescriptor.kind.isClassOrObject()) {
                    try {
                        conversionService.toValues(value).forEach { valueComponent ->
                            encodeElement(name, valueComponent)
                        }

                        return
                    } catch (_: DataConversionException) {
                    }
                }
            }

            serializer.serialize(this, value)
        } finally {
            currentElementName = before
        }
    }

    override fun encodeShortElement(descriptor: SerialDescriptor, index: Int, value: Short) {
        encodeElement(descriptor, index, value.toString())
    }

    override fun encodeStringElement(descriptor: SerialDescriptor, index: Int, value: String) {
        encodeElement(descriptor, index, value)
    }

    override fun encodeUnitElement(descriptor: SerialDescriptor, index: Int) {
        encodeElement(descriptor, index, "")
    }

    private fun encodeElement(desc: SerialDescriptor, index: Int, stringified: String) {
        val name = desc.getElementName(index)
        encodeElement(name, stringified)
    }

    private fun encodeElement(name: String, stringified: String) {
        val pattern = pattern ?: error("No @Location annotation found")

        if (name in pattern.pathParameterNames) {
            pathParameters[name] = stringified
        } else {
            queryParameters.append(name, stringified)
        }
    }

    fun build(): Url {
        val pattern = pattern ?: error("No @Location annotation found.")

        val builder = URLBuilder(
            parameters = queryParameters,
            encodedPath = pattern.format(pathParameters.build())
        )

        return builder.build()
    }

    fun buildTo(builder: URLBuilder) {
        val pattern = pattern ?: error("No @Location annotation found.")

        builder.parameters.appendAll(queryParameters)
        builder.encodedPath = pattern.format(pathParameters.build())
    }
}

