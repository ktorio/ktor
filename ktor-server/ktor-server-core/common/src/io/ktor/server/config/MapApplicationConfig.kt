/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.config

import io.ktor.util.reflect.*
import io.ktor.utils.io.InternalAPI
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlin.collections.toMap
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
    for (i in 0..<keys.size - 1) {
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
                val list = childNode.getSyntheticListOrNull()

                if (list != null) {
                    set.add(path)
                } else {
                    set.addAll(childNode.allPaths(path))
                }
            } else {
                set.add(path)
            }
        }
    }
    return set
}

internal fun Node.toPrimitive(): Any {
    return when (this) {
        is StrNode -> str
        is ListNode -> strList
        is ObjectNode -> {
            val list = getSyntheticListOrNull()

            if (list == null) {
                val map = mutableMapOf<String, Any?>()
                for (key in children.keys) {
                    val child = children[key] ?: continue
                    map[key] = child.toPrimitive()
                }
                map
            } else {
                list.map { it.toPrimitive() }
            }
        }
    }
}

internal fun Node.toMap(): Map<String, Any?> {
    if (this !is ObjectNode) return mapOf()

    val result = toPrimitive()

    return if (result is Map<*, *>) { // It may be a synthetic list
        @Suppress("UNCHECKED_CAST")
        result as Map<String, Any?>
    } else {
        mapOf()
    }
}

private fun Node.getSize(): Int? {
    if (this !is ObjectNode) return null
    val size = (children["size"] as? StrNode)?.str?.toIntOrNull()

    return if (size != null && size >= 0) {
        size
    } else {
        null
    }
}

private fun Node.getSyntheticListOrNull(): List<Node>? {
    if (this !is ObjectNode) return null
    val size = getSize() ?: return null

    return (0..<size).map { children[it.toString()] ?: return null }
}

internal fun Node.typeName(): String {
    return when (this) {
        is StrNode -> "string value"
        is ListNode -> "list of string values"
        is ObjectNode -> {
            if (getSyntheticListOrNull() == null) {
                "config object"
            } else {
                "list of config objects"
            }
        }
    }
}

public open class MapApplicationConfig private constructor(
    internal val node: Node
) : ApplicationConfig {
    internal constructor(map: MutableMap<String, String>, path: String = "") : this(ObjectNode()) {
        for ((p, value) in map) {
            node.putNode(p, StrNode(value))
        }
    }

    public constructor(values: Collection<Pair<String, String>>) : this(values.toMap().toMutableMap(), "")
    public constructor(vararg values: Pair<String, String>) : this(mutableMapOf(*values), "")

    override fun property(path: String): ApplicationConfigValue {
        return propertyOrNull(path) ?: throw ApplicationConfigurationException(
            "Property at \"$path\" not found."
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
        val child = node.findChildNode(path) ?: throw ApplicationConfigurationException(
            "Property at \"$path\" not found."
        )

        val size = child.getSize()

        if (size != null && child is ObjectNode) {
            return (0..<size).map {
                if (child.children.containsKey(it.toString())) {
                    MapApplicationConfig(child.children[it.toString()]!!)
                } else {
                    MapApplicationConfig(ObjectNode())
                }
            }
        }

        throw ApplicationConfigurationException("Expected a list of configs at \"$path\", got ${child.typeName()}")
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
) : ApplicationConfigValue {
    override val type: ValueType = when (node) {
        is StrNode -> ValueType.SINGLE
        is ListNode -> ValueType.LIST
        is ObjectNode -> ValueType.OBJECT
    }

    override fun getString(): String {
        if (node !is StrNode) {
            throw ApplicationConfigurationException("Expected string value at \"$path\", got ${node.typeName()}")
        }

        return node.str
    }

    override fun getList(): List<String> {
        if (node is ListNode) {
            return node.strList
        } else if (node is ObjectNode) {
            val list = node.getSyntheticListOrNull()

            if (list != null && list.all { it is StrNode }) {
                return list.map { (it as StrNode).str }
            }
        }

        throw ApplicationConfigurationException("Expected list of string values at \"$path\", got ${node.typeName()}")
    }

    override fun getMap(): Map<String, Any?> {
        return node.toMap()
    }

    @OptIn(InternalAPI::class)
    override fun getAs(type: TypeInfo): Any? {
        return type.serializer()
            .deserialize(ConfigValueDecoder(node, path))
    }
}

@OptIn(ExperimentalSerializationApi::class)
internal abstract class AbstractConfigValueDecoder(
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
    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int =
        enumDescriptor.getElementIndex(decodeString())
}

@OptIn(ExperimentalSerializationApi::class)
internal class ConfigValueDecoder(
    private val root: Node,
    private val path: String,
    override val serializersModule: SerializersModule = EmptySerializersModule()
) : AbstractConfigValueDecoder(serializersModule) {

    override fun decodeString(): String = (current as? StrNode)?.str ?: ""

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val kind = descriptor.kind as? StructureKind ?: error("Expected structure but found ${descriptor.kind}")

        return when (kind) {
            is StructureKind.LIST -> {
                ListDecoder(current, currentPath, serializersModule)
            }

            is StructureKind.MAP -> {
                MapDecoder(current, currentPath, serializersModule)
            }

            else -> {
                elementIndex = 0
                current = root
                this
            }
        }
    }

    private var elementIndex = 0
    private var current: Node = root
    private var currentPath: String = path

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex >= descriptor.elementsCount) return CompositeDecoder.DECODE_DONE

        val idx = elementIndex++
        val name = descriptor.getElementName(idx)
        currentPath = "$path.$name"

        return if (descriptor.isElementOptional(idx)) {
            decodeElementIndex(descriptor)
        } else if (root is ObjectNode) {
            val childNode = root.children[name] ?: return CompositeDecoder.DECODE_DONE
            current = childNode
            idx
        } else {
            CompositeDecoder.DECODE_DONE
        }
    }

    private class ListDecoder(
        root: Node,
        private val path: String,
        override val serializersModule: SerializersModule
    ) : AbstractConfigValueDecoder(serializersModule) {
        private var index = 0
        private var nodeList: List<Node> = when (root) {
            is ListNode -> {
                root.strList.map { StrNode(it) }
            }
            is ObjectNode -> {
                val size = root.getSize()

                if (size != null) {
                    (0..<size).map { idx ->
                        root.children[idx.toString()]
                            ?: throw SerializationException("Missing list element at \"$path.$idx\"")
                    }
                } else {
                    emptyList()
                }
            }
            else -> {
                null
            }
        } ?: emptyList()
        private lateinit var current: Node
        private var currentPath: String = path

        override fun decodeString(): String = (current as? StrNode)?.str ?: ""
        override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = nodeList.size

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            if (index >= nodeList.size) return CompositeDecoder.DECODE_DONE
            val idx = index++
            current = nodeList[idx]
            currentPath = "$path.$idx"
            return idx
        }

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
            return ConfigValueDecoder(current, currentPath, serializersModule).beginStructure(descriptor)
        }
    }

    private class MapDecoder(
        root: Node,
        private val path: String,
        override val serializersModule: SerializersModule
    ) : AbstractConfigValueDecoder(serializersModule) {
        private var elementIndex = 0

        private val entries: List<Pair<String, Node>> = when (root) {
            is ObjectNode -> root.children.entries.map { it.key to it.value }
            is ListNode -> listOf("size" to StrNode(root.strList.size.toString())) +
                root.strList.mapIndexed { i, s -> i.toString() to StrNode(s) }
            else -> emptyList()
        }

        private lateinit var current: Node
        private var currentPath: String = path

        override fun decodeCollectionSize(descriptor: SerialDescriptor): Int = entries.size

        override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
            val pairCount = entries.size
            val totalElements = pairCount * 2
            if (elementIndex >= totalElements) return CompositeDecoder.DECODE_DONE

            val idx = elementIndex++
            val pair = entries[idx / 2]

            current = if (idx % 2 == 0) {
                currentPath = "$path.${pair.first}"
                StrNode(pair.first)
            } else {
                pair.second
            }

            return idx
        }

        override fun decodeString(): String = (current as? StrNode)?.str ?: ""

        override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
            return ConfigValueDecoder(current, currentPath, serializersModule).beginStructure(descriptor)
        }
    }
}
