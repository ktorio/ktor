/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.openapi

import kotlinx.serialization.*
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.json.Json.Default.serializersModule
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

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

        /**
         * Encodes the provided item into a [GenericElement] using the specified serializer.
         *
         * @param serializer The serializer to be used for encoding the item.
         * @param item The item to encode into a [GenericElement].
         * @return A [GenericElement] representing the encoded data of the item.
         */
        public fun <T> encodeToElement(serializer: KSerializer<T>, item: T): GenericElement {
            val encoder = GenericElementEncoder(serializer)
            encoder.encodeSerializableValue(serializer, item)
            return encoder.build()
        }
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
     * If this is a map, return a list of key-value entries.
     *
     * @throws IllegalStateException if this is not a map.
     */
    public fun entries(): List<Pair<String, GenericElement>>

    /**
     * If this is a list, return all elements.
     *
     * @throws IllegalStateException if this is not a list.
     */
    public fun items(): List<GenericElement>

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
 * Handles encoding/decoding of GenericElements during deserialization of [GenericElementMap]
 */
public object GenericElementEncodingAdapter : GenericElementSerialAdapter {
    override fun <T> trySerializeToElement(
        encoder: Encoder,
        value: T,
        serializer: KSerializer<T>
    ): GenericElement? {
        return when (encoder) {
            is GenericElementEncoder ->
                GenericElement.encodeToElement(serializer, value)
            else -> null
        }
    }

    override fun tryDeserialize(decoder: Decoder): GenericElement? {
        return when (decoder) {
            is GenericElementDecoder -> decoder.current ?: decoder.asElement()
            else -> null
        }
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
        return GenericElement.encodeToElement(elementSerializer, element).entries()
    }

    override fun items(): List<GenericElement> =
        if (element is Iterable<*>) {
            element.map { item ->
                when (item) {
                    null -> GenericElement.EmptyObject
                    is GenericElement -> item
                    else -> GenericElement(item)
                }
            }
        } else {
            emptyList()
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
        serializer.deserialize(StringDecoder(serializersModule))

    override fun entries(): List<Pair<String, GenericElement>> =
        error("Cannot get entries for a string")

    override fun items(): List<GenericElement> =
        error("Cannot get items for a string")

    override fun isString(): Boolean = true

    @OptIn(ExperimentalSerializationApi::class)
    private inner class StringDecoder(
        override val serializersModule: SerializersModule
    ) : AbstractDecoder() {
        override fun decodeElementIndex(descriptor: SerialDescriptor): Int =
            error("Cannot decode element index for string")
        override fun decodeBoolean(): Boolean =
            element.toBooleanStrict()
        override fun decodeByte(): Byte =
            element.toByte()
        override fun decodeChar(): Char =
            element.singleOrNull() ?: error("Cannot decode char from string '$element'")
        override fun decodeDouble(): Double =
            element.toDouble()
        override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
            enumDescriptor.getElementIndex(element)
        override fun decodeFloat(): Float =
            element.toFloat()
        override fun decodeInt(): Int =
            element.toInt()
        override fun decodeLong(): Long =
            element.toLong()
        override fun decodeShort(): Short =
            element.toShort()
        override fun decodeString(): String =
            element
    }
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
        when (serializer.descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT ->
                serializer.deserialize(GenericElementClassDecoder(element))
            StructureKind.MAP -> serializer.deserialize(GenericElementMapDecoder(element.entries.map { it.toPair() }))
            else -> error("Cannot deserialize map to ${serializer.descriptor.serialName}")
        }

    override fun entries(): List<Pair<String, GenericElement>> =
        element.entries.map { it.key to it.value }

    override fun items(): List<GenericElement> =
        error("Cannot get items for a map")
}

internal class GenericElementList(
    override val element: List<GenericElement>,
    override val elementSerializer: KSerializer<List<GenericElement>> = serializer(),
) : GenericElement {
    override fun isArray(): Boolean = true

    override fun items(): List<GenericElement> = element

    override fun <T> deserialize(serializer: DeserializationStrategy<T>): T =
        serializer.deserialize(GenericElementListDecoder(this))

    override fun entries(): List<Pair<String, GenericElement>> =
        error("Cannot get entries for a list")
}

internal fun GenericElementEncoder(serializer: KSerializer<*>) =
    GenericElementEncoder(serializer.descriptor)

internal fun GenericElementEncoder(descriptor: SerialDescriptor, onClose: () -> Unit = {}) = when (descriptor.kind) {
    StructureKind.LIST -> GenericElementListEncoder(onClose)
    StructureKind.MAP -> GenericElementMapEncoder(onClose)
    // Class, Objects, etc.
    else -> GenericElementEntriesEncoder(onClose)
}

@OptIn(ExperimentalSerializationApi::class)
internal abstract class GenericElementEncoder(
    protected val onClose: () -> Unit
) : AbstractEncoder() {

    override val serializersModule: SerializersModule = EmptySerializersModule()

    protected var currentNestedEncoder: GenericElementEncoder? = null

    abstract fun build(): GenericElement
    protected abstract fun storeElement(element: GenericElement)

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder =
        if (shouldCreateNested()) {
            GenericElementEncoder(descriptor) {
                closeNested()
            }.also {
                currentNestedEncoder = it
            }
        } else {
            this
        }

    protected abstract fun shouldCreateNested(): Boolean

    protected fun closeNested() {
        storeElement(
            currentNestedEncoder?.build() ?: error {
                "No nested encoder to close"
            }
        )
        currentNestedEncoder = null
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        onClose()
    }

    protected fun <T : Any> captureValue(value: T, serializer: KSerializer<T>) {
        storeElement(GenericElementWrapper(value, serializer))
    }

    override fun encodeBoolean(value: Boolean) = captureValue(value, Boolean.serializer())
    override fun encodeByte(value: Byte) = captureValue(value, Byte.serializer())
    override fun encodeShort(value: Short) = captureValue(value, Short.serializer())
    override fun encodeInt(value: Int) = captureValue(value, Int.serializer())
    override fun encodeLong(value: Long) = captureValue(value, Long.serializer())
    override fun encodeFloat(value: Float) = captureValue(value, Float.serializer())
    override fun encodeDouble(value: Double) = captureValue(value, Double.serializer())
    override fun encodeChar(value: Char) = captureValue(value, Char.serializer())
    override fun encodeString(value: String) = captureValue(value, String.serializer())

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        captureValue(enumDescriptor.getElementName(index), String.serializer())
    }
}

/**
 * A custom encoder for populating [GenericElement] key-value pairs from any serializer.
 */
internal class GenericElementEntriesEncoder(
    onClose: () -> Unit,
    internal val entries: MutableList<Pair<String, GenericElement>> = mutableListOf(),
) : GenericElementEncoder(onClose) {
    private var currentElementName: String? = null

    override fun build(): GenericElement = GenericElement(entries)

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        currentElementName = descriptor.getElementName(index)
        return true
    }

    override fun storeElement(element: GenericElement) {
        val name = currentElementName ?: return
        entries += name to element
        currentElementName = null
    }

    override fun encodeNull() {
        currentElementName = null
    }

    override fun shouldCreateNested(): Boolean =
        currentElementName != null
}

internal class GenericElementListEncoder(
    onClose: () -> Unit,
    internal val elements: MutableList<GenericElement> = mutableListOf()
) : GenericElementEncoder(onClose) {
    override fun build(): GenericElement = GenericElementList(elements)

    override fun storeElement(element: GenericElement) {
        elements += element
    }

    override fun shouldCreateNested(): Boolean = true

    override fun encodeNull() {
        // Nulls in lists are currently skipped or not supported by GenericElement wrapper logic for primitives
    }
}

internal class GenericElementMapEncoder(
    onClose: () -> Unit,
    internal val entries: MutableList<Pair<String, GenericElement>> = mutableListOf()
) : GenericElementEncoder(onClose) {
    private var currentKey: String? = null
    private var waitingForKey: Boolean = true

    override fun build(): GenericElement = GenericElementMap(entries.toMap())

    override fun shouldCreateNested(): Boolean = !waitingForKey

    override fun storeElement(element: GenericElement) {
        if (waitingForKey) {
            currentKey = element.element.toString()
            waitingForKey = false
        } else {
            entries += (currentKey ?: error("Map value without key")) to element
            currentKey = null
            waitingForKey = true
        }
    }

    override fun encodeNull() {
        if (waitingForKey) {
            error("Map key cannot be null")
        }
        // Value is null -> skip adding
        currentKey = null
        waitingForKey = !waitingForKey
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

    override fun items(): List<GenericElement> {
        require(element is JsonArray) {
            "$element is not an array"
        }
        return element.map { item ->
            JsonGenericElement(
                item,
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

internal expect val genericElementSerialAdapters: List<GenericElementSerialAdapter>

/**
 * A [GenericElement] serializer that delegates to the first registered [GenericElementSerialAdapter] that can
 * deserialize the element.
 */
public class GenericElementSerializer : KSerializer<GenericElement> {
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
        genericElementSerialAdapters.firstNotNullOfOrNull { it.tryDeserialize(decoder) }
            ?: error("No generic element adapter for ${decoder::class.simpleName}")
}

/**
 * Base decoder for [GenericElement] with common primitive decoding logic.
 */
@OptIn(ExperimentalSerializationApi::class)
internal abstract class GenericElementDecoder : AbstractDecoder() {
    protected var index = 0

    abstract override val serializersModule: SerializersModule

    abstract fun asElement(): GenericElement

    val current: GenericElement? get() =
        if (index > 0) {
            getCurrentElement()
        } else {
            null
        }

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

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        if (index == 0) return this
        val element = getCurrentElement()
        return createNestedDecoder(descriptor, element)
    }

    protected fun createNestedDecoder(descriptor: SerialDescriptor, element: GenericElement): CompositeDecoder {
        return when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> {
                val nestedMap = element.entries().toMap()
                GenericElementClassDecoder(nestedMap, serializersModule)
            }
            StructureKind.MAP -> {
                GenericElementMapDecoder(element.entries(), serializersModule)
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
internal class GenericElementClassDecoder(
    internal val map: Map<String, GenericElement>,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : GenericElementDecoder() {
    private var currentElementName: String? = null

    override fun asElement(): GenericElement =
        GenericElementMap(map)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (index >= descriptor.elementsCount) {
            return CompositeDecoder.DECODE_DONE
        }

        // Find the next property that exists in the map
        while (index < descriptor.elementsCount) {
            val elementName = descriptor.getElementName(index)
            val newIndex = index++

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

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder =
        when (currentElementName) {
            // self reference
            null -> this
            // decode a missing key
            !in map -> GenericElementMapDecoder(emptyList(), serializersModule)
            // decode a nested structure
            else -> createNestedDecoder(descriptor, getCurrentElement())
        }
}

/**
 * A custom decoder for deserializing a [GenericElementMap].
 */
@OptIn(ExperimentalSerializationApi::class)
internal class GenericElementMapDecoder(
    internal val entries: List<Pair<String, GenericElement>>,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : GenericElementDecoder() {
    override fun asElement(): GenericElement =
        GenericElement(entries)

    override fun getCurrentElement(): GenericElement {
        val currentIndex = index - 1
        val isKey = currentIndex % 2 == 0
        val entry = entries[currentIndex / 2]
        return if (isKey) {
            GenericElementString(entry.first)
        } else {
            entry.second
        }
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (index < entries.size * 2) {
            index++
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int =
        entries.size * 2
}

/**
 * A custom decoder for deserializing a list within a [GenericElement].
 */
@OptIn(ExperimentalSerializationApi::class)
internal class GenericElementListDecoder(
    listElement: GenericElement,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : GenericElementDecoder() {
    internal val elements: List<GenericElement> = listElement.items()

    override fun asElement(): GenericElement =
        GenericElementList(elements)

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (index < elements.size) {
            index++
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = elements.size

    override fun getCurrentElement(): GenericElement {
        val index = index - 1
        if (index < 0 || index >= elements.size) {
            throw SerializationException("Index $index out of bounds for list of size ${elements.size}")
        }
        return elements[index]
    }
}
