package io.ktor.config

import io.ktor.util.*

/**
 * Mutable application config backed by a hash map
 */
@KtorExperimentalAPI
class MapApplicationConfig : ApplicationConfig {
    private val map: MutableMap<String, String>
    private val path: String

    private constructor(map: MutableMap<String, String>, path: String) {
        this.map = map
        this.path = path
    }

    constructor(vararg values: Pair<String, String>) : this(mutableMapOf(*values), "")
    constructor() : this(mutableMapOf<String, String>(), "")

    /**
     * Set property value
     */
    fun put(path: String, value: String) {
        map[path] = value
    }

    /**
     * Put list property value
     */
    fun put(path: String, values: Iterable<String>) {
        var size = 0
        values.forEachIndexed { i, value ->
            put(combine(path, i.toString()), value)
            size++
        }
        put(combine(path, "size"), size.toString())
    }

    override fun property(path: String): ApplicationConfigValue {
        return propertyOrNull(path) ?: throw ApplicationConfigurationException("Property ${combine(this.path, path)} not found.")
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

    private class MapApplicationConfigValue(val map: Map<String, String>, val path: String) : ApplicationConfigValue {
        override fun getString(): String = map[path]!!
        override fun getList(): List<String> {
            val size = map[combine(path, "size")] ?: throw ApplicationConfigurationException("Property $path.size not found.")
            return (0 until size.toInt()).map { map[combine(path, it.toString())]!! }
        }
    }
}

private fun combine(root: String, relative: String): String = if (root.isEmpty()) relative else "$root.$relative"
