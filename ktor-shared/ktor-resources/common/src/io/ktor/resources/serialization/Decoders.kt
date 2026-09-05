/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.resources.serialization

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.utils.io.*
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.internal.*
import kotlinx.serialization.modules.*

/**
 * Marks an exception as coming from a `@Resource` class's own constructor, so it survives any number of
 * intervening [decodeElementOrWrap] calls without being re-categorized as a decode failure.
 *
 * Only exposed by [ResourcesFormat.decodeFromParametersOrThrowConstructionFailure], not the public
 * [ResourcesFormat.decodeFromParameters]; callers distinguishing decode failures from constructor failures
 * must use that method and unwrap [original] themselves, since it may coincidentally be a
 * [SerializationException].
 */
@InternalAPI
public class ResourceConstructionFailure(public val original: Throwable) : Exception(original.message, original)

/**
 * Wraps any decode failure (primitive conversion, or a non-resource serializer's own exception, including a
 * plain [SerializationException]) into a [ResourceSerializationException], preserving the original as
 * [ResourceSerializationException.cause]. [ResourcesFormat.decodeFromParameters] unwraps that cause again
 * before returning, so direct callers still see the original exception type; the [ResourceSerializationException]
 * wrapper only exists to be unambiguously recognized as a decode failure by [decodeNestedResourceOrMark].
 * [ResourceConstructionFailure] and [MissingRequiredParameterException] are passed through as-is, since they're
 * already known to be a constructor failure or a decode failure respectively.
 */
@OptIn(ExperimentalSerializationApi::class, InternalAPI::class)
private inline fun <T> decodeElementOrWrap(currentName: String, block: () -> T): T {
    try {
        return block()
    } catch (cause: ResourceConstructionFailure) {
        throw cause
    } catch (cause: ResourceSerializationException) {
        throw cause
    } catch (cause: MissingRequiredParameterException) {
        throw cause
    } catch (cause: Throwable) {
        throw ResourceSerializationException("Failed to decode value for '$currentName'", cause)
    }
}

/**
 * Marks any failure as a [ResourceConstructionFailure], except one already known to be a decode failure
 * ([ResourceSerializationException] or [MissingRequiredParameterException]), so a nested `@Resource` class's
 * own constructor exception survives with its original type.
 */
@OptIn(ExperimentalSerializationApi::class, InternalAPI::class)
private inline fun <T> decodeNestedResourceOrMark(block: () -> T): T {
    try {
        return block()
    } catch (cause: ResourceConstructionFailure) {
        throw cause
    } catch (cause: ResourceSerializationException) {
        throw cause
    } catch (cause: MissingRequiredParameterException) {
        throw cause
    } catch (cause: Throwable) {
        throw ResourceConstructionFailure(cause)
    }
}

/**
 * Whether [deserializer] is the compiler-generated serializer for a `@Resource` class, meaning any exception
 * from its [DeserializationStrategy.deserialize] can only be that class's own constructor throwing. The
 * [Resource] annotation alone isn't enough, since a custom/delegating serializer can also decode such a class
 * (e.g. via `Route.handle(serializer, ...)`) while adding its own validation, which isn't the constructor;
 * requiring [GeneratedSerializer] rules those out.
 *
 * Gotcha for a wrapper delegating to the generated serializer: this only works if it decodes via
 * `decoder.decodeSerializableValue(delegate)`. Calling `delegate.deserialize(decoder)` directly bypasses this
 * check, so a constructor exception from the delegate is then indistinguishable from the wrapper's own logic
 * and gets reported as a decode failure instead.
 */
@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
private fun isResourceClass(deserializer: DeserializationStrategy<*>): Boolean =
    deserializer is GeneratedSerializer<*> && deserializer.descriptor.annotations.any { it is Resource }

@OptIn(ExperimentalSerializationApi::class)
internal class ParametersDecoder(
    override val serializersModule: SerializersModule,
    private val parameters: Parameters,
    elementNames: Iterable<String>
) : AbstractDecoder() {

    private val parameterNames = elementNames.iterator()
    private lateinit var currentName: String

    // decodeSerializableValue can run before decodeElementIndex ever sets currentName, for a root type that
    // isn't itself a structure member.
    private val currentNameOrRoot: String
        get() = if (::currentName.isInitialized) currentName else "<root>"

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (!parameterNames.hasNext()) {
            return CompositeDecoder.DECODE_DONE
        }
        while (parameterNames.hasNext()) {
            currentName = parameterNames.next()
            val elementIndex = descriptor.getElementIndex(currentName)
            val elementDescriptorKind = descriptor.getElementDescriptor(elementIndex).kind
            val isPrimitive = elementDescriptorKind is PrimitiveKind
            val isEnum = elementDescriptorKind is SerialKind.ENUM
            if (!(isPrimitive || isEnum) || parameters.contains(currentName)) {
                return elementIndex
            }
            // Report a missing required parameter ourselves rather than let kotlinx.serialization's own
            // MissingFieldException check fire later: that type is indistinguishable from one a resource's
            // own constructor might throw directly.
            if (!descriptor.isElementOptional(elementIndex)) {
                throw MissingRequiredParameterException(
                    "Missing required parameter '$currentName' for '${descriptor.serialName}'"
                )
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

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        if (isResourceClass(deserializer)) {
            return decodeNestedResourceOrMark { super.decodeSerializableValue(deserializer) }
        }
        return decodeElementOrWrap(currentNameOrRoot) { super.decodeSerializableValue(deserializer) }
    }

    override fun decodeBoolean(): Boolean = decodeElementOrWrap(currentNameOrRoot) { decodeString().toBoolean() }

    override fun decodeByte(): Byte = decodeElementOrWrap(currentNameOrRoot) { decodeString().toByte() }

    override fun decodeChar(): Char = decodeElementOrWrap(currentNameOrRoot) { decodeString()[0] }

    override fun decodeDouble(): Double = decodeElementOrWrap(currentNameOrRoot) { decodeString().toDouble() }

    override fun decodeFloat(): Float = decodeElementOrWrap(currentNameOrRoot) { decodeString().toFloat() }

    override fun decodeInt(): Int = decodeElementOrWrap(currentNameOrRoot) { decodeString().toInt() }

    override fun decodeLong(): Long = decodeElementOrWrap(currentNameOrRoot) { decodeString().toLong() }

    override fun decodeShort(): Short = decodeElementOrWrap(currentNameOrRoot) { decodeString().toShort() }

    override fun decodeString(): String {
        return parameters[currentName]!!
    }

    override fun decodeNotNullMark(): Boolean {
        return parameters.contains(currentName)
    }

    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = decodeElementOrWrap(currentNameOrRoot) {
        val enumName = decodeString()
        val index = enumDescriptor.getElementIndex(enumName)
        if (index == CompositeDecoder.UNKNOWN_NAME) {
            throw ResourceSerializationException(
                "${enumDescriptor.serialName} does not contain element with name '$enumName'"
            )
        }
        index
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

    override fun <T> decodeSerializableValue(deserializer: DeserializationStrategy<T>): T {
        if (isResourceClass(deserializer)) {
            return decodeNestedResourceOrMark { super.decodeSerializableValue(deserializer) }
        }
        return decodeElementOrWrap(parameterName) { super.decodeSerializableValue(deserializer) }
    }

    override fun decodeBoolean(): Boolean = decodeElementOrWrap(parameterName) { decodeString().toBoolean() }

    override fun decodeByte(): Byte = decodeElementOrWrap(parameterName) { decodeString().toByte() }

    override fun decodeChar(): Char = decodeElementOrWrap(parameterName) { decodeString()[0] }

    override fun decodeDouble(): Double = decodeElementOrWrap(parameterName) { decodeString().toDouble() }

    override fun decodeFloat(): Float = decodeElementOrWrap(parameterName) { decodeString().toFloat() }

    override fun decodeInt(): Int = decodeElementOrWrap(parameterName) { decodeString().toInt() }

    override fun decodeLong(): Long = decodeElementOrWrap(parameterName) { decodeString().toLong() }

    override fun decodeShort(): Short = decodeElementOrWrap(parameterName) { decodeString().toShort() }

    override fun decodeString(): String {
        return parameters.getAll(parameterName)!![currentIndex]
    }

    override fun decodeNotNullMark(): Boolean {
        return parameters.contains(parameterName)
    }

    override fun decodeNull(): Nothing? {
        return null
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int = decodeElementOrWrap(parameterName) {
        val enumName = decodeString()
        val index = enumDescriptor.getElementIndex(enumName)
        if (index == CompositeDecoder.UNKNOWN_NAME) {
            throw ResourceSerializationException(
                "${enumDescriptor.serialName} does not contain element with name '$enumName'"
            )
        }
        index
    }
}
