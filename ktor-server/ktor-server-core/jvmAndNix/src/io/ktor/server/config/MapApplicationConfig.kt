/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.config

/**
 * Mutable application config backed by a hash map
 */
public open class MapApplicationConfig : ApplicationConfig {
    /**
     * A backing map for this config
     */
    protected val map: MutableMap<String, String>

    /**
     * Config path prefix for this config
     */
    protected val path: String

    private constructor(map: MutableMap<String, String>, path: String) {
        this.map = map
        this.path = path
    }

    public constructor(values: List<Pair<String, String>>) : this(values.toMap().toMutableMap(), "")
    public constructor(vararg values: Pair<String, String>) : this(mutableMapOf(*values), "")
    public constructor() : this(mutableMapOf<String, String>(), "")

    /**
     * Set property value
     */
    public fun put(path: String, value: String) {
        map[combine(this.path, path)] = value
    }

    /**
     * Put list property value
     */
    public fun put(path: String, values: Iterable<String>) {
        var size = 0
        values.forEachIndexed { i, value ->
            put(combine(path, i.toString()), value)
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
        val size = map[combine(key, "size")] ?: throw ApplicationConfigurationException("Property $key.size not found.")
        return (0 until size.toInt()).map {
            MapApplicationConfig(map, combine(key, it.toString()))
        }
    }

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        val key = combine(this.path, path)
        return if (!map.containsKey(key) && !map.containsKey(combine(key, "size"))) {
            null
        } else {
            MapApplicationConfigValue(map, key)
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

    /**
     * A config value implementation backed by this config's map
     * @property map is usually owner's backing map
     * @property path to this value
     */
    protected class MapApplicationConfigValue(
        public val map: Map<String, String>,
        public val path: String
    ) : ApplicationConfigValue {
        override fun getString(): String = map[path]!!
        override fun getList(): List<String> {
            val size =
                map[combine(path, "size")] ?: throw ApplicationConfigurationException("Property $path.size not found.")
            return (0 until size.toInt()).map { map[combine(path, it.toString())]!! }
        }
    }
}

private fun combine(root: String, relative: String): String = if (root.isEmpty()) relative else "$root.$relative"
