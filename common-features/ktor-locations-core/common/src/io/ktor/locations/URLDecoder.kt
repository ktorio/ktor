/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.locations

import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.*
import kotlinx.serialization.modules.*
import kotlin.reflect.*

@InternalAPI
public class URLDecoder(
    override val context: SerialModule,
    private val encodedPath: String?,
    private val queryParameters: Parameters,
    private val rootClass: KClass<*>?
) : Decoder, CompositeDecoder {
    public constructor(context: SerialModule, url: Url, rootClass: KClass<*>?) : this(
        context,
        url.encodedPath,
        url.parameters,
        rootClass
    )

    private var pathParameters: Parameters = Parameters.Empty
    private var pattern: LocationPattern? = null
    private val pathParameterIndexes = mutableMapOf<String, Int>()

    override val updateMode: UpdateMode = UpdateMode.BANNED

    private var currentElementName: String? = null

    private val iteratorIndexMap = HashMap<SerialDescriptor, Int>()

    override fun beginStructure(descriptor: SerialDescriptor, vararg typeParams: KSerializer<*>): CompositeDecoder {
        if (descriptor.kind.isClassOrObject() && this.pattern == null) {
            this.pattern = buildLocationPattern(descriptor, rootClass)

            try {
                this.pathParameters = encodedPath
                    ?.let { path -> this.pattern!!.parse(path) }
                    ?: queryParameters
            } catch (cause: Throwable) {
                throw URLDecodingException("Failed to parse path $encodedPath", cause)
            }
        }

        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
    }

    private inline fun <T> decodeOrFail(type: String, block: (String) -> T): T {
        return try {
            block(decodeString())
        } catch (cause: Exception) {
            throw ParameterConversionException(currentElementName ?: "?", type, cause)
        }
    }

    private inline fun <T> decodeElementOrFail(
        descriptor: SerialDescriptor, index: Int, block: (String) -> T
    ): T {
        return try {
            block(decodeElement(descriptor, index))
        } catch (cause: Exception) {
            throw ParameterConversionException(
                descriptor.getElementName(index),
                descriptor.getElementDescriptor(index).toString(), cause
            )
        }
    }

    override fun decodeBoolean(): Boolean {
        return decodeOrFail("Boolean") { it.toBoolean() }
    }

    override fun decodeByte(): Byte {
        return decodeOrFail("Byte") { it.toByte() }
    }

    override fun decodeChar(): Char {
        return decodeOrFail("Char") { it.single() }
    }

    override fun decodeDouble(): Double {
        return decodeOrFail("Double") { it.toDouble() }
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val value = decodeOrFail(enumDescriptor.serialName) { it }
        val index = enumDescriptor.getElementIndex(value)

        if (index < 0) {
            throw ParameterConversionException(
                currentElementName ?: "?",
                enumDescriptor.serialName,
                IllegalArgumentException("Illegal enum value $value")
            )
        }

        return index
    }

    override fun decodeFloat(): Float {
        return decodeOrFail("Float") { it.toFloat() }
    }

    override fun decodeInt(): Int {
        return decodeOrFail("Int") { it.toInt() }
    }

    override fun decodeLong(): Long {
        return decodeOrFail("Long") { it.toLong() }
    }

    override fun decodeNotNullMark(): Boolean {
        return hasNextString()
    }

    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeShort(): Short {
        return decodeOrFail("Short") { it.toShort() }
    }

    private fun elementsForDecoding(): List<String>? {
        val pattern = pattern ?: return null
        val parameterName = currentElementName ?: return null

        val values = if (parameterName in pattern.pathParameterNames) {
            pathParameters.getAll(parameterName)
        } else {
            queryParameters.getAll(parameterName)
        } ?: error("No value for parameter $parameterName")

        if (values.isNotEmpty()) {
            return values
        }

        return null
    }

    private fun hasNextString(): Boolean {
        return elementsForDecoding() != null
    }

    override fun decodeString(): String {
        val parameterName = currentElementName
        val values = elementsForDecoding()

        if (parameterName != null && values != null) {
            return values.getOrElse(indexFor(parameterName)) { values.last() }
        }

        throw ParameterConversionException(parameterName ?: "", "String", null)
    }

    override fun decodeUnit() {
        decodeOrFail("Unit") {}
    }

    override fun decodeBooleanElement(descriptor: SerialDescriptor, index: Int): Boolean {
        return decodeElementOrFail(descriptor, index) { it == "" || it == "true" }
    }

    override fun decodeByteElement(descriptor: SerialDescriptor, index: Int): Byte {
        return decodeElementOrFail(descriptor, index) { it.toByte() }
    }

    override fun decodeCharElement(descriptor: SerialDescriptor, index: Int): Char {
        return decodeElementOrFail(descriptor, index) { it.single() }
    }

    override fun decodeDoubleElement(descriptor: SerialDescriptor, index: Int): Double {
        return decodeElementOrFail(descriptor, index) { it.toDouble() }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val currentElementName = currentElementName
        val isCollection = descriptor.kind == StructureKind.LIST
        val count = when {
            isCollection && currentElementName != null -> pathParameters.getAll(currentElementName)?.size
                ?: queryParameters.getAll(currentElementName)?.size ?: 0
            else -> descriptor.elementsCount
        }

        var index = iteratorIndexMap.getOrElse(descriptor) { -1 } + 1
        while (index < count) {
            if (!isCollection &&
                descriptor.isElementOptional(index)
                && descriptor.getElementName(index).let { name ->
                    name !in pathParameters && name !in queryParameters
                }
            ) {
                index++
                continue
            }

            iteratorIndexMap[descriptor] = index
            return index
        }
        return CompositeDecoder.READ_DONE
    }

    override fun decodeFloatElement(descriptor: SerialDescriptor, index: Int): Float {
        return decodeElementOrFail(descriptor, index) { it.toFloat() }
    }

    override fun decodeIntElement(descriptor: SerialDescriptor, index: Int): Int {
        return decodeElementOrFail(descriptor, index) { it.toInt() }
    }

    override fun decodeLongElement(descriptor: SerialDescriptor, index: Int): Long {
        return decodeElementOrFail(descriptor, index) { it.toLong() }
    }

    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>
    ): T? {
        if (!descriptor.kind.isClassOrObject()) {
            return deserializer.deserialize(this)
        }

        val before = currentElementName
        currentElementName = descriptor.getElementName(index)
        return try {
            deserializer.deserialize(this)
        } finally {
            currentElementName = before
        }
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>
    ): T {
        if (descriptor.kind != StructureKind.CLASS) {
            return deserializer.deserialize(this)
        }

        val before = currentElementName
        currentElementName = descriptor.getElementName(index)
        return try {
            deserializer.deserialize(this)
        } finally {
            currentElementName = before
        }
    }

    override fun decodeShortElement(descriptor: SerialDescriptor, index: Int): Short {
        return decodeElementOrFail(descriptor, index) { it.toShort() }
    }

    override fun decodeStringElement(descriptor: SerialDescriptor, index: Int): String {
        return decodeElementOrFail(descriptor, index) { it }
    }

    override fun decodeUnitElement(descriptor: SerialDescriptor, index: Int) {
        decodeElementOrFail(descriptor, index) {}
    }

    private fun decodeElement(desc: SerialDescriptor, index: Int): String {
        val name = desc.getElementName(index)

        return if (name in pattern!!.pathParameterNames) {
            pathParameters[name]
        } else {
            val values = queryParameters.getAll(name)
            values?.getOrElse(indexFor(name)) { values.last() }
        } ?: ""
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        val pattern = pattern
        val parameterName = currentElementName

        if (pattern != null && parameterName != null) {
            if (parameterName in pattern.pathParameterNames) {
                return pathParameters.getAll(parameterName)?.size ?: 0
            }
            return queryParameters.getAll(parameterName)?.size ?: 0
        }

        return super.decodeCollectionSize(descriptor)
    }

    private fun indexFor(name: String): Int {
        val existingIndex = pathParameterIndexes[name]
        val index = existingIndex ?: 0

        pathParameterIndexes[name] = index + 1
        return index
    }

    override fun <T : Any> updateNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        old: T?
    ): T? {
        error("Update is not supported")
    }

    override fun <T> updateSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        old: T
    ): T {
        error("Update is not supported")
    }
}

/**
 * Thrown when URL decoder failed.
 */
public open class URLDecodingException(
    public override val message: String,
    public override val cause: Throwable?
) : SerializationException(message, cause)

/**
 * Thrown when URL decoder failed to decode a property with name [propertyName] of
 * type [typeName] due to an optionally provided [cause].
 *
 * @property propertyName that was not decoded
 * @property typeName of the property
 * @property cause of failure (optional)
 */
public class ParameterConversionException(
    public val propertyName: String,
    public val typeName: String,
    override val cause: Throwable?
) : URLDecodingException("Failed to convert property $propertyName: $typeName", cause)

/**
 * Thrown when a required parameter [propertyName] of
 * type [typeName] is missing (not provided in URL).
 *
 * @property propertyName that was not decoded
 * @property typeName of the property
 */
public class MissingParameterException(
    public val propertyName: String,
    public val typeName: String
) : URLDecodingException("Missing property $propertyName: $typeName", null)

