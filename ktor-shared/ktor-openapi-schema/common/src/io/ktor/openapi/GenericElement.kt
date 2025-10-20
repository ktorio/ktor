/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

/**
 * A generalized element for deferred deserialization for use as an interoperable stand in for JSON and YAML nodes.
 */
@Serializable(GenericElementSerializer::class)
public interface GenericElement {
    public companion object {
        /**
         * An empty [GenericElement] with no entries.
         */
        public val EmptyObject: GenericElement =
            GenericElementMap(emptyMap(), MapSerializer(String.serializer(), GenericElementSerializer()))

        /**
         * Returns the empty element if the given [element] is null, otherwise returns the element.
         */
        public fun GenericElement?.orEmpty(): GenericElement = this ?: EmptyObject
    }

    /**
     * The serializer used to deserialize the element.
     */
    public val elementSerializer: KSerializer<*>

    /**
     * The underlying element.
     */
    public val element: Any

    /**
     * Returns true if the element is a JSON object.
     */
    public fun isObject(): Boolean =
        elementSerializer.descriptor.kind == StructureKind.OBJECT ||
            elementSerializer.descriptor.kind == StructureKind.MAP

    /**
     * Returns true if the element is a JSON array.
     */
    public fun isArray(): Boolean =
        elementSerializer.descriptor.kind == StructureKind.LIST

    /**
     * Returns true if the element is a JSON primitive and is a string.
     */
    public fun isString(): Boolean =
        elementSerializer.descriptor.kind == PrimitiveKind.STRING

    /**
     * Deserializes the element into the given [serializer].
     */
    public fun <T> deserialize(serializer: DeserializationStrategy<T>): T

    /**
     * Returns a list of key-value pairs for the element.
     */
    public fun entries(): List<Pair<String, GenericElement>>

    /**
     * Merges two elements into a single element with the keys of both.
     */
    public operator fun plus(other: GenericElement): GenericElement =
        GenericElementMap((entries() + other.entries()).toMap())
}

/**
 * Deserialize an element to the given type [T].
 */
public inline fun <reified T> GenericElement.asA(): T =
    deserialize(serializer<T>())

/**
 * Convenience function for creating a [GenericElement] from a value of type [T].
 */
public inline fun <reified T : Any> GenericElement(value: T): GenericElement =
    when (value) {
        is GenericElement -> value
        is String -> GenericElementString(value)
        else -> GenericElementWrapper(value, serializer())
    }

/**
 * Create an object node [GenericElement] from the given [map].
 */
public inline fun <reified T : Any> GenericElement(map: Map<String, T>): GenericElement =
    GenericElement(map.map { it.key to GenericElement(it.value) })

/**
 * Create an object node [GenericElement] from the given [entries].
 */
public fun GenericElement(entries: Collection<Pair<String, GenericElement>>): GenericElement =
    if (!entries.isEmpty() && entries.all { it.second is JsonGenericElement }) {
        val firstEntry = entries.first().second as JsonGenericElement
        JsonGenericElement(
            JsonObject(
                entries.associate { (key, value) ->
                    key to (value as JsonGenericElement).element
                }
            ),
            firstEntry.json,
            firstEntry.elementSerializer
        )
    } else {
        GenericElementMap(entries.toMap())
    }

/**
 * Adapter for custom [GenericElement] types when using different encoders / decoders.
 */
public interface GenericElementSerialAdapter {
    public fun <T> trySerializeToElement(encoder: Encoder, value: T, serializer: KSerializer<T>): GenericElement?
    public fun tryDeserialize(decoder: Decoder): GenericElement?
}

/**
 * [GenericElementSerialAdapter] for standard kotlinx-json.
 */
public object JsonElementSerialAdapter : GenericElementSerialAdapter {
    override fun <T> trySerializeToElement(
        encoder: Encoder,
        value: T,
        serializer: KSerializer<T>
    ): GenericElement? {
        if (encoder !is JsonEncoder) return null
        val jsonElement = encoder.json.encodeToJsonElement(serializer, value)
        return JsonGenericElement(jsonElement, encoder.json, JsonElement.serializer())
    }

    override fun tryDeserialize(decoder: Decoder): GenericElement? {
        if (decoder !is JsonDecoder) return null
        val deserializer = JsonElement.serializer()
        val jsonElement = decoder.decodeSerializableValue(deserializer)
        return JsonGenericElement(jsonElement, decoder.json, deserializer)
    }
}

/**
 * [GenericElementSerialAdapter] for standard kotlinx-json.
 */
public object GenericMapDecoderAdapter : GenericElementSerialAdapter {
    override fun <T> trySerializeToElement(
        encoder: Encoder,
        value: T,
        serializer: KSerializer<T>
    ): GenericElement? {
        if (encoder !is GenericElementEntriesEncoder) return null
        // Nested call to the same encoder - we'll just replace the encoder and serialize to a new object
        val entries = mutableListOf<Pair<String, GenericElement>>()
        GenericElementEntriesEncoder { key, value ->
            entries += key to value
        }
        return GenericElementMap(entries.toMap())
    }

    override fun tryDeserialize(decoder: Decoder): GenericElement? {
        if (decoder !is GenericElementMapDecoder) return null
        return GenericElement(decoder.map)
    }
}

/**
 * A [GenericElement] implementation that wraps a value of type [T].
 */
public class GenericElementWrapper<T : Any>(
    override val element: T,
    override val elementSerializer: KSerializer<T>,
) : GenericElement {

    @Suppress("UNCHECKED_CAST")
    override fun <T> deserialize(serializer: DeserializationStrategy<T>): T =
        element as? T ?: error {
            "Cannot deserialize ${element::class} to ${serializer.descriptor.serialName}"
        }

    override fun entries(): List<Pair<String, GenericElement>> {
        val entries = mutableListOf<Pair<String, GenericElement>>()
        GenericElementEntriesEncoder { key, value ->
            entries += key to value
        }.also {
            it.encodeSerializableValue(elementSerializer, element)
        }
        return entries
    }

    override fun toString(): String =
        "GenericElementWrapper(${elementSerializer.descriptor.serialName}: ${element.toString().take(20)})"
}

/**
 * A [GenericElement] implementation that wraps a string value.
 */
public class GenericElementString(
    override val element: String,
    override val elementSerializer: KSerializer<String> = String.serializer(),
) : GenericElement {
    @Suppress("UNCHECKED_CAST")
    override fun <T> deserialize(serializer: DeserializationStrategy<T>): T =
        element as? T ?: error {
            "Expected String but got ${serializer.descriptor.serialName}"
        }

    override fun entries(): List<Pair<String, GenericElement>> =
        throw UnsupportedOperationException("Cannot get entries for a string")

    override fun isString(): Boolean = true
}

/**
 * A [GenericElement] implementation that wraps a map of [GenericElement]s.
 */
internal class GenericElementMap(
    override val element: Map<String, GenericElement>,
    override val elementSerializer: KSerializer<Map<String, GenericElement>> = serializer(),
) : GenericElement {
    override fun isObject(): Boolean = true

    override fun <T> deserialize(serializer: DeserializationStrategy<T>): T =
        serializer.deserialize(GenericElementMapDecoder(element))

    override fun entries(): List<Pair<String, GenericElement>> =
        element.entries.map { it.key to it.value }
}

/**
 * A custom encoder for populating [GenericElement] key-value pairs from any serializer.
 */
@OptIn(ExperimentalSerializationApi::class)
internal class GenericElementEntriesEncoder(
    private val entryCallback: (String, GenericElement) -> Unit
) : AbstractEncoder() {
    private var currentElementName: String? = null

    override val serializersModule: SerializersModule = EmptySerializersModule()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        // Structure encoding complete
    }

    private fun <T : Any> captureElement(value: T?, serializer: KSerializer<T>) {
        val name = currentElementName ?: return
        if (value != null) {
            entryCallback(name, GenericElementWrapper(value, serializer))
        }
        currentElementName = null
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        currentElementName = descriptor.getElementName(index)
        return true
    }

    override fun encodeNull() {
        currentElementName = null
    }

    override fun encodeBoolean(value: Boolean) {
        captureElement(value, Boolean.serializer())
    }

    override fun encodeByte(value: Byte) {
        captureElement(value, Byte.serializer())
    }

    override fun encodeShort(value: Short) {
        captureElement(value, Short.serializer())
    }

    override fun encodeInt(value: Int) {
        captureElement(value, Int.serializer())
    }

    override fun encodeLong(value: Long) {
        captureElement(value, Long.serializer())
    }

    override fun encodeFloat(value: Float) {
        captureElement(value, Float.serializer())
    }

    override fun encodeDouble(value: Double) {
        captureElement(value, Double.serializer())
    }

    override fun encodeChar(value: Char) {
        captureElement(value, Char.serializer())
    }

    override fun encodeString(value: String) {
        captureElement(value, String.serializer())
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        // For enums, we need to handle them specially
        val name = currentElementName ?: return
        val enumValue = enumDescriptor.getElementName(index)
        entryCallback(name, GenericElementWrapper(enumValue, String.serializer()))
        currentElementName = null
    }
}

/**
 * A [GenericElement] implementation that wraps a JSON element.
 */
internal class JsonGenericElement(
    override val element: JsonElement,
    internal val json: Json = Json,
    override val elementSerializer: KSerializer<JsonElement> = JsonElement.serializer(),
) : GenericElement {
    override fun isObject(): Boolean = element is JsonObject
    override fun isArray(): Boolean = element is JsonArray
    override fun isString(): Boolean = element is JsonPrimitive && element.isString

    override fun <T> deserialize(serializer: DeserializationStrategy<T>): T =
        json.decodeFromJsonElement(serializer, element)

    override fun entries(): List<Pair<String, GenericElement>> {
        require(element is JsonObject) {
            "$element is not an object"
        }
        return element.entries
            .map { (key, value) ->
                key to JsonGenericElement(
                    value,
                    json,
                    elementSerializer
                )
            }
    }

    override fun plus(other: GenericElement): GenericElement {
        require(element is JsonObject) {
            "$element is not an object"
        }
        require(other is JsonGenericElement) {
            "$other is not a JSON element"
        }
        return when (other.element) {
            is JsonObject -> JsonGenericElement(
                JsonObject(element + other.element),
                json,
                elementSerializer
            )
            is JsonNull -> this
            else -> throw IllegalArgumentException("${other.element} is not an object")
        }
    }
}

/**
 * A [GenericElement] serializer that delegates to the first registered [GenericElementSerialAdapter] that can
 * deserialize the element.
 */
public class GenericElementSerializer : KSerializer<GenericElement> {
    private val adapters: List<GenericElementSerialAdapter> = listOf(
        JsonElementSerialAdapter,
        GenericMapDecoderAdapter,
    )

    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor = buildSerialDescriptor(
        "io.ktor.openapi.GenericElement",
        SerialKind.CONTEXTUAL
    )

    @Suppress("UNCHECKED_CAST")
    override fun serialize(encoder: Encoder, value: GenericElement) {
        encoder.encodeSerializableValue(
            value.elementSerializer as KSerializer<Any>,
            value.element
        )
    }

    override fun deserialize(decoder: Decoder): GenericElement =
        adapters.firstNotNullOfOrNull { it.tryDeserialize(decoder) }
            ?: error("No generic element adapter for ${decoder::class.simpleName}")
}

/**
 * Base decoder for [GenericElement] with common primitive decoding logic.
 */
@OptIn(ExperimentalSerializationApi::class)
internal abstract class GenericElementDecoder : AbstractDecoder() {
    abstract override val serializersModule: SerializersModule

    /**
     * Returns the current [GenericElement] to decode.
     */
    protected abstract fun getCurrentElement(): GenericElement

    override fun decodeBoolean(): Boolean = getCurrentElement().deserialize(Boolean.serializer())
    override fun decodeByte(): Byte = getCurrentElement().deserialize(Byte.serializer())
    override fun decodeShort(): Short = getCurrentElement().deserialize(Short.serializer())
    override fun decodeInt(): Int = getCurrentElement().deserialize(Int.serializer())
    override fun decodeLong(): Long = getCurrentElement().deserialize(Long.serializer())
    override fun decodeFloat(): Float = getCurrentElement().deserialize(Float.serializer())
    override fun decodeDouble(): Double = getCurrentElement().deserialize(Double.serializer())
    override fun decodeChar(): Char = getCurrentElement().deserialize(Char.serializer())
    override fun decodeString(): String = getCurrentElement().deserialize(String.serializer())

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        val enumValue = decodeString()
        return enumDescriptor.getElementIndex(enumValue)
    }

    protected fun createNestedDecoder(descriptor: SerialDescriptor, element: GenericElement): CompositeDecoder {
        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT, StructureKind.MAP -> {
                val nestedMap = element.entries().toMap()
                GenericElementMapDecoder(nestedMap, serializersModule)
            }
            StructureKind.LIST -> {
                GenericElementListDecoder(element, serializersModule)
            }
            else -> this
        }
    }
}

/**
 * A custom decoder for deserializing a [GenericElementMap].
 */
@OptIn(ExperimentalSerializationApi::class)
internal class GenericElementMapDecoder(
    internal val map: Map<String, GenericElement>,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : GenericElementDecoder() {
    private var elementIndex = 0
    private var currentElementName: String? = null

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= descriptor.elementsCount) {
            return CompositeDecoder.DECODE_DONE
        }

        // Find the next property that exists in the map
        while (elementIndex < descriptor.elementsCount) {
            val elementName = descriptor.getElementName(elementIndex)
            val newIndex = elementIndex++

            if (elementName in map) {
                currentElementName = elementName
                return newIndex
            }

            // Check if property is optional - if so, skip it
            if (descriptor.isElementOptional(newIndex)) {
                continue
            }

            // Required property not found
            return CompositeDecoder.DECODE_DONE
        }

        return CompositeDecoder.DECODE_DONE
    }

    override fun decodeNotNullMark(): Boolean {
        return currentElementName?.let { it in map } ?: false
    }

    override fun getCurrentElement(): GenericElement {
        val name = currentElementName
            ?: throw SerializationException("No current element - decoder in invalid state")
        return map[name]
            ?: throw SerializationException("Element '$name' not found in map")
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        // For empty structures or when no element is set, create an empty decoder
        if (currentElementName == null || currentElementName !in map) {
            return GenericElementMapDecoder(emptyMap(), serializersModule)
        }

        val element = getCurrentElement()
        return createNestedDecoder(descriptor, element)
    }
}

/**
 * A custom decoder for deserializing a list within a [GenericElement].
 */
@OptIn(ExperimentalSerializationApi::class)
internal class GenericElementListDecoder(
    private val listElement: GenericElement,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : GenericElementDecoder() {
    private val elements: List<GenericElement> by lazy {
        when (listElement) {
            is GenericElementWrapper<*> if listElement.element is List<*> -> {
                listElement.element.map { item ->
                    when (item) {
                        null -> GenericElement.EmptyObject
                        is GenericElement -> item
                        else -> GenericElement(item)
                    }
                }
            }

            is JsonGenericElement if listElement.element is JsonArray -> {
                listElement.element.map { jsonItem ->
                    JsonGenericElement(jsonItem, listElement.json, listElement.elementSerializer)
                }
            }

            else -> emptyList()
        }
    }

    private var currentIndex = 0

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (currentIndex < elements.size) {
            currentIndex++
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = elements.size

    override fun decodeSequentially(): Boolean = true

    override fun getCurrentElement(): GenericElement {
        val index = currentIndex - 1
        if (index < 0 || index >= elements.size) {
            throw SerializationException("Index $index out of bounds for list of size ${elements.size}")
        }
        return elements[index]
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val element = getCurrentElement()
        return createNestedDecoder(descriptor, element)
    }
}
