/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.config

import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Mutable application config backed by a hash map
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.MapApplicationConfig)
 */
public open class MapApplicationConfig : ApplicationConfig {
    internal companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun Map<String, Any?>.flatten(prefix: String = ""): Sequence<Pair<String, String>> {
            return sequence {
                for ((key, value) in entries) {
                    val path = combine(prefix, key)
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
                    val path = combine(prefix, i)
                    when (val element = get(i)) {
                        null -> continue
                        is List<*> -> yieldAll(element.flatten(path))
                        is Map<*, *> -> yieldAll((element as Map<String, Any?>).flatten(path))
                        else -> yield(path to element.toString())
                    }
                }
                yield(combine(prefix, "size") to size.toString())
            }
        }
    }

    /**
     * A backing map for this config
     */
    protected val map: MutableMap<String, String>

    /**
     * Config path prefix for this config
     */
    protected val path: String

    internal constructor(map: MutableMap<String, String>, path: String = "") {
        this.map = map
        this.path = path
    }

    public constructor(values: Collection<Pair<String, String>>) : this(values.toMap().toMutableMap(), "") {
        val listElements = mutableMapOf<String, Int>()
        values.forEach { findListElements(it.first, listElements) }
        listElements.forEach { (listProperty, size) ->
            this.map["$listProperty.size"] = "$size"
        }
    }

    public constructor(vararg values: Pair<String, String>) : this(mutableMapOf(*values), "")
    public constructor() : this(mutableMapOf<String, String>(), "")

    /**
     * Set property value
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.MapApplicationConfig.put)
     */
    public fun put(path: String, value: String) {
        map[combine(this.path, path)] = value
    }

    /**
     * Put list property value
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.config.MapApplicationConfig.put)
     */
    public fun put(path: String, values: Iterable<String>) {
        var size = 0
        values.forEachIndexed { i, value ->
            put(combine(path, i), value)
            size++
        }
        put(combine(path, "size"), size.toString())
    }

    override fun property(path: String): ApplicationConfigValue {
        return propertyOrNull(path) ?: throw ApplicationConfigurationException(
            "Property ${combine(this.path, path)} not found."
        )
    }

    override fun configList(path: String): List<ApplicationConfig> {
        val key = combine(this.path, path)
        val size = map[combine(key, "size")]
            ?: throw ApplicationConfigurationException("Property $key.size not found.")
        return (0 until size.toInt()).map {
            MapApplicationConfig(map, combine(key, it))
        }
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        val key = combine(this.path, path)
        return if (map.containsPrefix(key)) {
            MapApplicationConfigValue(map, key)
        } else {
            null
        }
    }

    override fun config(path: String): ApplicationConfig = MapApplicationConfig(map, combine(this.path, path))

    override fun keys(): Set<String> {
        val isTopLevel = path.isEmpty()
        val keys = if (isTopLevel) map.keys else map.keys.filter { it.startsWith("$path.") }
        val listEntries = keys.filter { it.contains(".size") }.map { it.substringBefore(".size") }
        val addedListKeys = mutableSetOf<String>()
        return keys.mapNotNull { candidate ->
            val listKey = listEntries.firstOrNull { candidate.startsWith(it) }
            val key = when {
                listKey != null && !addedListKeys.contains(listKey) -> {
                    addedListKeys.add(listKey)
                    listKey
                }
                listKey == null -> candidate
                else -> null
            }
            if (isTopLevel) key else key?.substringAfter("$path.")
        }.toSet()
    }

    override fun toMap(): Map<String, Any?> {
        val keys = map.keys.filter { it.startsWith(path) }
            .map { it.drop(if (path.isEmpty()) 0 else path.length + 1).split('.').first() }
            .distinct()
        return keys.associate { key ->
            val path = combine(path, key)
            when {
                map.containsKey(path) -> key to map[path]
                map.containsKey(combine(path, "size")) -> when {
                    map.containsKey(combine(path, "0")) -> key to property(path).getList()
                    else -> key to configList(key).map { it.toMap() }
                }
                else -> key to config(key).toMap()
            }
        }
    }
}

/**
 * A config value implementation backed by this config's map
 * @property map is usually owner's backing map
 * @property path to this value
 */
internal class MapApplicationConfigValue(
    private val map: MutableMap<String, String>,
    private val path: String
) : ApplicationConfigValue {
    override val type: ApplicationConfigValue.Type by lazy {
        when {
            map.containsKey(path) -> ApplicationConfigValue.Type.SINGLE
            map.containsKey(combine(path, "size")) -> ApplicationConfigValue.Type.LIST
            map.containsPrefix(path) -> ApplicationConfigValue.Type.OBJECT
            else -> ApplicationConfigValue.Type.NULL
        }
    }
    override fun getString(): String = map[path]!!
    override fun getList(): List<String> {
        val size =
            map[combine(path, "size")] ?: throw ApplicationConfigurationException("Property $path.size not found.")
        return (0 until size.toInt()).map { map[combine(path, it)]!! }
    }

    override fun getMap(): Map<String, Any?> {
        return MapApplicationConfig(map, path).toMap()
    }

    @OptIn(InternalAPI::class)
    override fun getAs(type: TypeInfo): Any? {
        return type.serializer()
            .deserialize(MapConfigDecoder(map, path))
    }
}

private fun combine(root: String, relative: Int): String = combine(root, relative.toString())
private fun combine(root: String, relative: String): String = if (root.isEmpty()) relative else "$root.$relative"

private fun findListElements(input: String, listElements: MutableMap<String, Int>) {
    var pointBegin = input.indexOf('.')
    while (pointBegin != input.length) {
        val pointEnd = input.indexOf('.', pointBegin + 1).let { if (it == -1) input.length else it }

        input.substring(pointBegin + 1, pointEnd).toIntOrNull()?.let { pos ->
            val element = input.take(pointBegin)
            val newSize = pos + 1
            listElements[element] = listElements[element]?.let { maxOf(it, newSize) } ?: newSize
        }
        pointBegin = pointEnd
    }
}
