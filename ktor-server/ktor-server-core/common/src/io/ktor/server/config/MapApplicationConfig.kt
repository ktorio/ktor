/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.config

import io.ktor.util.reflect.*
import io.ktor.utils.io.InternalAPI
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.jvm.JvmInline
import io.ktor.server.config.ApplicationConfigValue.Type as ValueType

internal sealed interface Node
@JvmInline
internal value class StrNode(val str: String) : Node
@JvmInline
internal value class ObjectNode(val children: MutableMap<String, Node> = mutableMapOf()) : Node
@JvmInline
internal value class ListNode(val strList: List<String>) : Node

internal fun Node.findChildNode(path: String): Node? {
    var current: Node? = this
    for (key in path.split('.')) {
        if (current !is ObjectNode) {
            return null
        }

        current = current.children[key]
    }

    return current
}

internal fun Node.putNode(path: String, node: Node): ObjectNode? {
    if (this !is ObjectNode) return null
    if (path.isEmpty()) return null
    val keys = path.split('.')

    var parent: ObjectNode = this
    for (i in 0..<keys.size-1) {
        var child = parent.children[keys[i]]

        if (child !is ObjectNode) {
            child = ObjectNode()
            parent.children[keys[i]] = child
        }

        parent = child
    }

    parent.children[keys.last()] = node
    return parent
}

internal fun Node.allPaths(prefix: String = ""): Set<String> {
    val set = mutableSetOf<String>()

    if (this is ObjectNode) {
        for (key in children.keys) {
            val childNode = children[key] ?: continue

            val path = if (prefix.isEmpty()) key else "$prefix.$key"

            if (childNode is ObjectNode) {
                val list = childNode.getSyntheticList()

                if (list == null) {
                    set.addAll(childNode.allPaths(path))
                } else {
                    set.add(path)
                }
            } else {
                set.add(path)
            }
        }
    }
    return set
}

internal fun Node.toMap(): Map<String, Any?> {
    val map = mutableMapOf<String, Any?>()
    if (this !is ObjectNode) return map

    for (key in children.keys) {
        val child = children[key] ?: continue

        when (child) {
            is StrNode -> {
                map[key] = child.str
            }
            is ListNode -> {
                map[key] = child.strList.map { it }
            }
            is ObjectNode -> {
                val list = child.getSyntheticList()

                if (list != null) {
                    map[key] = list.map { it.toMap() }
                } else {
                    map[key] = child.toMap()
                }
            }
        }
    }

    return map
}

private fun Node.getSyntheticList(): List<Node>? {
    if (this !is ObjectNode) return null
    val size = (children["size"] as? StrNode)?.str?.toIntOrNull() ?: return null

    return (0..<size).map { children[it.toString()] ?: throw ApplicationConfigurationException("Missing $it index") }
}


public open class MapApplicationConfig private constructor(
    internal val node: Node
): ApplicationConfig {
    internal constructor(map: MutableMap<String, String>, path: String = ""): this(ObjectNode()) {
        for ((p, value) in map) {
            node.putNode(p, StrNode(value))
        }
    }

    public constructor(values: Collection<Pair<String, String>>) : this(values.toMap().toMutableMap(), "")
    public constructor(vararg values: Pair<String, String>) : this(mutableMapOf(*values), "")
//    public constructor() : this(mutableMapOf<String, String>(), "")

    override fun property(path: String): ApplicationConfigValue {
        return propertyOrNull(path) ?: throw ApplicationConfigurationException(
            "Property $path not found."
        )
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        val child = node.findChildNode(path) ?: return null
        return MapApplicationConfigValue(path, child)
    }

    override fun config(path: String): ApplicationConfig {
        return configOrNull(path) ?: MapApplicationConfig()
    }

    override fun configList(path: String): List<ApplicationConfig> {
        val child = node.findChildNode(path) ?: return emptyList()
        val list = child.getSyntheticList()

        if (list != null) {
            return list.map { MapApplicationConfig(it) }
        }

        // TODO: Actual type
        throw ApplicationConfigurationException("Expected a list of configs at $path")
    }

    private fun configOrNull(path: String): MapApplicationConfig? {
        val child = node.findChildNode(path) ?: return null
        return MapApplicationConfig(child)
    }

    override fun keys(): Set<String> {
        return node.allPaths()
    }

    public fun put(path: String, value: String) {
        node.putNode(path, StrNode(value))
    }

    public fun put(path: String, values: Iterable<String>) {
        node.putNode(path, ListNode(values.toList()))
    }

    override fun toMap(): Map<String, Any?> {
        return node.toMap()
    }

    internal companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun Map<String, Any?>.flatten(prefix: String = ""): Sequence<Pair<String, String>> {
            return sequence {
                for ((key, value) in entries) {
                    val path = if (prefix.isEmpty()) key else "$prefix.$key"
                    when (value) {
                        null -> continue
                        is List<*> -> yieldAll(value.flatten(path))
                        is Map<*, *> -> yieldAll((value as Map<String, Any?>).flatten(path))
                        else -> yield(path to value.toString())
                    }
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        internal fun List<Any?>.flatten(prefix: String): Sequence<Pair<String, String>> {
            return sequence {
                for (i in indices) {
                    val path = if (prefix.isEmpty()) i.toString() else "$prefix.$i"
                    when (val element = get(i)) {
                        null -> continue
                        is List<*> -> yieldAll(element.flatten(path))
                        is Map<*, *> -> yieldAll((element as Map<String, Any?>).flatten(path))
                        else -> yield(path to element.toString())
                    }
                }
                yield((if (prefix.isEmpty()) "size" else "$prefix.size") to size.toString())
            }
        }
    }
}

internal class MapApplicationConfigValue(
    private val path: String,
    internal val node: Node
): ApplicationConfigValue {
    override val type: ValueType = when (node) {
        is StrNode -> ValueType.SINGLE
        is ListNode -> ValueType.LIST
        is ObjectNode -> ValueType.OBJECT
    }

    override fun getString(): String {
        if (node !is StrNode) {
            throw ApplicationConfigurationException("Path $path does not contain a string")
        }

        return node.str
    }

    override fun getList(): List<String> {
        if (node is ListNode) {
            return node.strList
        } else {
            val list = node.getSyntheticList()
            if (list != null) {
                return list.map {
                    if (it is StrNode) it.str else "" // TODO: Test empty string and compare with current behavior
                }
            }
        }

        throw ApplicationConfigurationException("Path $path does not contain a list")
    }

    override fun getMap(): Map<String, Any?> {
        return node.toMap()
    }

    @OptIn(InternalAPI::class)
    override fun getAs(type: TypeInfo): Any? {
        TODO()
//        return type.serializer()
//            .deserialize(ConfigValueDecoder(this))
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal class ConfigValueDecoder(
    private val root: MapApplicationConfigValue,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : AbstractDecoder() {
    override fun decodeInt(): Int = decodeString().toInt()
    override fun decodeLong(): Long = decodeString().toLong()
    override fun decodeFloat(): Float = decodeString().toFloat()
    override fun decodeDouble(): Double = decodeString().toDouble()
    override fun decodeBoolean(): Boolean = decodeString().toBoolean()
    override fun decodeChar(): Char = decodeString().single()
    override fun decodeByte(): Byte = decodeString().toByte()
    override fun decodeShort(): Short = decodeString().toShort()
    override fun decodeString(): String = current.getString()

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val kind = descriptor.kind as? StructureKind ?: error("Expected structure but found ${descriptor.kind}")

        return if (kind is StructureKind.LIST) {
            ListNodeDecoder(current.getList(), serializersModule)
        } else {
            elementIndex = 0
            current = root
            this
        }
    }

    private var elementIndex = 0
    private var current: MapApplicationConfigValue = root

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE

        val idx = elementIndex++
        val name = descriptor.getElementName(idx)

//        current = current.config.property(name) as MapApplicationConfigValue
        return idx
    }

    private class ListNodeDecoder(
        private val items: List<String>,
        override val serializersModule: SerializersModule
    ) : AbstractDecoder() {
        private var index = 0
        private lateinit var current: String

        override fun decodeInt(): Int = decodeString().toInt()
        override fun decodeLong(): Long = decodeString().toLong()
        override fun decodeFloat(): Float = decodeString().toFloat()
        override fun decodeDouble(): Double = decodeString().toDouble()
        override fun decodeBoolean(): Boolean = decodeString().toBoolean()
        override fun decodeChar(): Char = decodeString().single()
        override fun decodeByte(): Byte = decodeString().toByte()
        override fun decodeShort(): Short = decodeString().toShort()
        override fun decodeString(): String = current

        override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = items.size

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            if (index >= items.size) return CompositeDecoder.DECODE_DONE
            val idx = index++
            current = items[idx]
            return idx
        }

//        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
//            return ConfigValueDecoder(current, serializersModule).beginStructure(descriptor)
//        }
    }
}

/**
 * Mutable application config backed by a hash map
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.MapApplicationConfig)
 */
//public open class MapApplicationConfig : ApplicationConfig {
//    internal companion object {
//        @Suppress("UNCHECKED_CAST")
//        internal fun Map<String, Any?>.flatten(prefix: String = ""): Sequence<Pair<String, String>> {
//            return sequence {
//                for ((key, value) in entries) {
//                    val path = combine(prefix, key)
//                    when (value) {
//                        null -> continue
//                        is List<*> -> yieldAll(value.flatten(path))
//                        is Map<*, *> -> yieldAll((value as Map<String, Any?>).flatten(path))
//                        else -> yield(path to value.toString())
//                    }
//                }
//            }
//        }
//
//        @Suppress("UNCHECKED_CAST")
//        internal fun List<Any?>.flatten(prefix: String): Sequence<Pair<String, String>> {
//            return sequence {
//                for (i in indices) {
//                    val path = combine(prefix, i)
//                    when (val element = get(i)) {
//                        null -> continue
//                        is List<*> -> yieldAll(element.flatten(path))
//                        is Map<*, *> -> yieldAll((element as Map<String, Any?>).flatten(path))
//                        else -> yield(path to element.toString())
//                    }
//                }
//                yield(combine(prefix, "size") to size.toString())
//            }
//        }
//    }
//
//    /**
//     * A backing map for this config
//     */
//    protected val map: MutableMap<String, String>
//
//    /**
//     * Config path prefix for this config
//     */
//    protected val path: String
//
//    internal constructor(map: MutableMap<String, String>, path: String = "") {
//        this.map = map
//        this.path = path
//    }
//
//    public constructor(values: Collection<Pair<String, String>>) : this(values.toMap().toMutableMap(), "") {
//        val listElements = mutableMapOf<String, Int>()
//        values.forEach { findListElements(it.first, listElements) }
//        listElements.forEach { (listProperty, size) ->
//            this.map["$listProperty.size"] = "$size"
//        }
//    }
//
//    public constructor(vararg values: Pair<String, String>) : this(mutableMapOf(*values), "")
//    public constructor() : this(mutableMapOf<String, String>(), "")
//
//    /**
//     * Set property value
//     *
//     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.MapApplicationConfig.put)
//     */
//    public fun put(path: String, value: String) {
//        map[combine(this.path, path)] = value
//    }
//
//    /**
//     * Put list property value
//     *
//     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.MapApplicationConfig.put)
//     */
//    public fun put(path: String, values: Iterable<String>) {
//        var size = 0
//        values.forEachIndexed { i, value ->
//            put(combine(path, i), value)
//            size++
//        }
//        put(combine(path, "size"), size.toString())
//    }
//
//    override fun property(path: String): ApplicationConfigValue {
//        return propertyOrNull(path) ?: throw ApplicationConfigurationException(
//            "Property ${combine(this.path, path)} not found."
//        )
//    }
//
//    override fun configList(path: String): List<ApplicationConfig> {
//        val key = combine(this.path, path)
//        val size = map[combine(key, "size")]
//            ?: throw ApplicationConfigurationException("Property $key.size not found.")
//        return (0 until size.toInt()).map {
//            MapApplicationConfig(map, combine(key, it))
//        }
//    }
//
//    override fun propertyOrNull(path: String): ApplicationConfigValue? {
//        val key = combine(this.path, path)
//        return if (map.containsPrefix(key)) {
//            MapApplicationConfigValue(map, key)
//        } else {
//            null
//        }
//    }
//
//    override fun config(path: String): ApplicationConfig = MapApplicationConfig(map, combine(this.path, path))
//
//    override fun keys(): Set<String> {
//        val isTopLevel = path.isEmpty()
//        val keys = if (isTopLevel) map.keys else map.keys.filter { it.startsWith("$path.") }
//        val listEntries = keys.filter { it.contains(".size") }.map { it.substringBefore(".size") }
//        val addedListKeys = mutableSetOf<String>()
//        return keys.mapNotNull { candidate ->
//            val listKey = listEntries.firstOrNull { candidate.startsWith(it) }
//            val key = when {
//                listKey != null && !addedListKeys.contains(listKey) -> {
//                    addedListKeys.add(listKey)
//                    listKey
//                }
//                listKey == null -> candidate
//                else -> null
//            }
//            if (isTopLevel) key else key?.substringAfter("$path.")
//        }.toSet()
//    }
//
//    override fun toMap(): Map<String, Any?> {
//        val keys = map.keys.filter { it.startsWith(path) }
//            .map { it.drop(if (path.isEmpty()) 0 else path.length + 1).split('.').first() }
//            .distinct()
//        return keys.associate { key ->
//            val path = combine(path, key)
//            when {
//                map.containsKey(path) -> key to map[path]
//                map.containsKey(combine(path, "size")) -> when {
//                    map.containsKey(combine(path, "0")) -> key to property(path).getList()
//                    else -> key to configList(key).map { it.toMap() }
//                }
//                else -> key to config(key).toMap()
//            }
//        }
//    }
//}
//
///**
// * A config value implementation backed by this config's map
// * @property map is usually owner's backing map
// * @property path to this value
// */
//internal class MapApplicationConfigValue(
//    private val map: MutableMap<String, String>,
//    private val path: String
//) : ApplicationConfigValue {
//    override val type: ApplicationConfigValue.Type by lazy {
//        when {
//            map.containsKey(path) -> ApplicationConfigValue.Type.SINGLE
//            map.containsKey(combine(path, "size")) -> ApplicationConfigValue.Type.LIST
//            map.containsPrefix(path) -> ApplicationConfigValue.Type.OBJECT
//            else -> ApplicationConfigValue.Type.NULL
//        }
//    }
//    override fun getString(): String = map[path]!!
//    override fun getList(): List<String> {
//        val size =
//            map[combine(path, "size")] ?: throw ApplicationConfigurationException("Property $path.size not found.")
//        return (0 until size.toInt()).map { map[combine(path, it)]!! }
//    }
//
//    override fun getMap(): Map<String, Any?> {
//        return MapApplicationConfig(map, path).toMap()
//    }
//
//    @OptIn(InternalAPI::class)
//    override fun getAs(type: TypeInfo): Any? {
//        return type.serializer()
//            .deserialize(MapConfigDecoder(map, path))
//    }
//}
//
//private fun combine(root: String, relative: Int): String = combine(root, relative.toString())
//private fun combine(root: String, relative: String): String = if (root.isEmpty()) relative else "$root.$relative"
//
//private fun findListElements(input: String, listElements: MutableMap<String, Int>) {
//    var pointBegin = input.indexOf('.')
//    while (pointBegin != input.length) {
//        val pointEnd = input.indexOf('.', pointBegin + 1).let { if (it == -1) input.length else it }
//
//        input.substring(pointBegin + 1, pointEnd).toIntOrNull()?.let { pos ->
//            val element = input.take(pointBegin)
//            val newSize = pos + 1
//            listElements[element] = listElements[element]?.let { maxOf(it, newSize) } ?: newSize
//        }
//        pointBegin = pointEnd
//    }
//}
