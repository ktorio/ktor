package io.ktor.config

import com.typesafe.config.ConfigFactory
import io.ktor.util.*

/**
 * Mutable application config backed by a hash map
 */
@KtorExperimentalAPI
open class MapApplicationConfig : ApplicationConfig {
    /**
     * A backing map for this config
     */
    protected val map: MutableMap<String, Any>

    /**
     * Config path prefix for this config
     */
    protected val path: String

    private constructor(map: MutableMap<String, Any>, path: String) {
        this.map = map
        this.path = path
    }

    constructor(vararg values: Pair<String, Any>) : this(mutableMapOf(*values), "")
    constructor() : this(mutableMapOf<String, Any>(), "")

    /**
     * Set property value
     */
    fun <T : Any> put(path: String, value: T) {
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

    override fun property(path: String): HoconApplicationConfigValue {
        return propertyOrNull(path)
            ?: throw ApplicationConfigurationException("Property ${combine(this.path, path)} not found.")
    }

    override fun configList(path: String): List<ApplicationConfig> {
        val key = combine(this.path, path)

        val rawSize = map[combine(key, "size")]
        val size = when (rawSize) {
            is Int -> rawSize
            is String -> rawSize.toInt()
            null -> throw ApplicationConfigurationException("Property $key.size not found.")
            else -> throw ApplicationConfigurationException("Property $key.size is of unhandled type '${rawSize.javaClass}' (should be 'Int' or 'String').")
        }

        return (0 until size).map {
            MapApplicationConfig(map, combine(key, it.toString()))
        }
    }

    override fun propertyOrNull(path: String): HoconApplicationConfigValue? {
        val key = combine(this.path, path)
        return if (!map.containsKey(key) && !map.containsKey(combine(key, "size"))) {
            null
        } else {
            HoconApplicationConfigValue(ConfigFactory.parseMap(map), key)
        }
    }

    override fun config(path: String): ApplicationConfig = MapApplicationConfig(map, combine(this.path, path))
}

private fun combine(root: String, relative: String): String = if (root.isEmpty()) relative else "$root.$relative"
