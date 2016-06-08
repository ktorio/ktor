package org.jetbrains.ktor.config

class MapApplicationConfig : ApplicationConfig {
    private val map: MutableMap<String, String>
    private val path: String

    private constructor(map: MutableMap<String, String>, path: String) {
        this.map = map
        this.path = path
    }

    constructor(vararg values: Pair<String, String>) : this(mutableMapOf(*values), "")
    constructor() : this(mutableMapOf<String, String>(), "")

    fun put(path: String, value: String) {
        map.put(path, value)
    }

    override fun property(path: String): ApplicationConfigValue {
        val key = combine(this.path, path)
        if (!map.containsKey(key))
            throw ApplicationConfigurationException("Property $key not found.")
        return MapApplicationConfigValue(map, key)
    }

    override fun configList(path: String): List<ApplicationConfig> {
        val key = combine(this.path, path)
        val size = map[combine(key, "size")] ?: throw ApplicationConfigurationException("Property $key.size not found.")
        return (0..size.toInt() - 1).map {
            MapApplicationConfig(map, key + it)
        }
    }

    private fun combine(root: String, relative: String): String = if (root.isEmpty()) relative else "$root.$relative"

    override fun propertyOrNull(path: String): ApplicationConfigValue? {
        val key = combine(this.path, path)
        return if (map.containsKey(key)) MapApplicationConfigValue(map, key) else null
    }

    override fun config(path: String): ApplicationConfig = MapApplicationConfig(map, combine(this.path, path))

    private class MapApplicationConfigValue(val map: Map<String, String>, val path: String) : ApplicationConfigValue {
        override fun getString(): String = map[path]!!
        override fun getList(): List<String> {
            throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

    }
}