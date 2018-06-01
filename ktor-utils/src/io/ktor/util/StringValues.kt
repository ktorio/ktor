package io.ktor.util

import io.ktor.compat.*


@Deprecated("ValuesMap was split into Headers and Parameters, please choose type appropriate for the context", level = DeprecationLevel.ERROR)
interface ValuesMap

/**
 * Provides data structure for associating a [String] with a [List] of Strings
 */
interface StringValues {
    companion object {
        /**
         * Empty [StringValues] instance
         */
        val Empty: StringValues = StringValuesImpl()

        /**
         * Builds a [StringValues] instance with the given [builder] function
         * @param caseInsensitiveName specifies if map should have case-sensitive or case-insensitive names
         * @param builder specifies a function to build a map
         */
        inline fun build(caseInsensitiveName: Boolean = false, builder: StringValuesBuilder.() -> Unit): StringValues = StringValuesBuilder(caseInsensitiveName).apply(builder).build()
    }

    /**
     * Specifies if map has case-sensitive or case-insensitive names
     */
    val caseInsensitiveName: Boolean

    /**
     * Gets first value from the list of values associated with a [name], or null if the name is not present
     */
    operator fun get(name: String): String? = getAll(name)?.firstOrNull()

    /**
     * Gets all values associated with the [name], or null if the name is not present
     */
    fun getAll(name: String): List<String>?


    /**
     * Gets all names from the map
     */
    fun names(): Set<String>

    /**
     * Gets all entries from the map
     */
    fun entries(): Set<Map.Entry<String, List<String>>>

    /**
     * Checks if the given [name] exists in the map
     */
    operator fun contains(name: String): Boolean = getAll(name) != null

    /**
     * Checks if the given [name] and [value] pair exists in the map
     */
    fun contains(name: String, value: String): Boolean = getAll(name)?.contains(value) ?: false

    /**
     * Iterates over all entries in this map and calls [body] for each pair
     *
     * Can be optimized in implementations
     */
    fun forEach(body: (String, List<String>) -> Unit) = entries().forEach { (k, v) -> body(k, v) }

    /**
     * Checks if this map is empty
     */
    fun isEmpty(): Boolean
}

open class StringValuesSingleImpl(override val caseInsensitiveName: Boolean, val name: String, val values: List<String>) : StringValues {
    override fun getAll(name: String): List<String>? = if (this.name.equals(name, caseInsensitiveName)) values else null
    override fun entries(): Set<Map.Entry<String, List<String>>> = setOf(object : Map.Entry<String, List<String>> {
        override val key: String = name
        override val value: List<String> = values
        override fun toString() = "$key=$value"
    })

    override fun isEmpty(): Boolean = false
    override fun names(): Set<String> = setOf(name)

    override fun toString() = "StringValues(case=${!caseInsensitiveName}) ${entries()}"
    override fun hashCode() = entriesHashCode(entries(), 31 * caseInsensitiveName.hashCode())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StringValues) return false
        if (caseInsensitiveName != other.caseInsensitiveName) return false
        return entriesEquals(entries(), other.entries())
    }

    override fun forEach(body: (String, List<String>) -> Unit) = body(name, values)
    override fun get(name: String): String? = if (name.equals(this.name, caseInsensitiveName)) values.firstOrNull() else null
    override fun contains(name: String): Boolean = name.equals(this.name, caseInsensitiveName)
    override fun contains(name: String, value: String): Boolean = name.equals(this.name, caseInsensitiveName) && values.contains(value)
}

open class StringValuesImpl(override val caseInsensitiveName: Boolean = false, values: Map<String, List<String>> = emptyMap()) : StringValues {
    protected val values: Map<String, List<String>> by lazy {
        if (caseInsensitiveName) caseInsensitiveMap<List<String>>(values.size).apply { putAll(values) } else values.toMap()
    }
    override operator fun get(name: String) = listForKey(name)?.firstOrNull()
    override fun getAll(name: String): List<String>? = listForKey(name)

    override operator fun contains(name: String) = listForKey(name) != null
    override fun contains(name: String, value: String) = listForKey(name)?.contains(value) ?: false

    override fun names(): Set<String> = values.keys.unmodifiable()
    override fun isEmpty(): Boolean = values.isEmpty()
    override fun entries(): Set<Map.Entry<String, List<String>>> = values.entries.unmodifiable()
    override fun forEach(body: (String, List<String>) -> Unit) = values.forEach(body)

    private fun listForKey(name: String): List<String>? = values[name]
    override fun toString() = "StringValues(case=${!caseInsensitiveName}) ${entries()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StringValues) return false
        if (caseInsensitiveName != other.caseInsensitiveName) return false
        return entriesEquals(entries(), other.entries())
    }

    override fun hashCode() = entriesHashCode(entries(), 31 * caseInsensitiveName.hashCode())
}

open class StringValuesBuilder(val caseInsensitiveName: Boolean = false, size: Int = 8) {
    protected val values: MutableMap<String, MutableList<String>> = if (caseInsensitiveName) caseInsensitiveMap(size) else LinkedHashMap(size)
    protected var built = false

    fun getAll(name: String): List<String>? = values[name]
    fun contains(name: String, value: String) = values[name]?.contains(value) ?: false

    fun names() = values.keys
    fun isEmpty() = values.isEmpty()
    fun entries(): Set<Map.Entry<String, List<String>>> = values.entries.unmodifiable()

    operator fun set(name: String, value: String) {
        val list = ensureListForKey(name, 1)
        list.clear()
        list.add(value)
    }

    operator fun get(name: String): String? = getAll(name)?.firstOrNull()

    fun append(name: String, value: String) {
        ensureListForKey(name, 1).add(value)
    }

    fun appendAll(stringValues: StringValues) {
        stringValues.forEach { name, values ->
            appendAll(name, values)
        }
    }

    fun appendMissing(stringValues: StringValues) {
        stringValues.forEach { name, values ->
            appendMissing(name, values)
        }
    }

    fun appendAll(name: String, values: Iterable<String>) {
        ensureListForKey(name, (values as? Collection)?.size ?: 2).addAll(values)
    }

    fun appendMissing(name: String, values: Iterable<String>) {
        val existing = this.values[name]?.toSet() ?: emptySet()

        appendAll(name, values.filter { it !in existing })
    }

    fun remove(name: String) {
        values.remove(name)
    }

    fun removeKeysWithNoEntries() {
        for ((k, _) in values.filter { it.value.isEmpty() }) {
            remove(k)
        }
    }

    fun remove(name: String, value: String) = values[name]?.remove(value) ?: false

    fun clear() {
        values.clear()
    }

    open fun build(): StringValues {
        require(!built) { "ValueMapBuilder can only build a single ValueMap" }
        built = true
        return StringValuesImpl(caseInsensitiveName, values)
    }

    private fun ensureListForKey(name: String, size: Int): MutableList<String> {
        if (built)
            throw IllegalStateException("Cannot modify a builder when final structure has already been built")
        return values[name] ?: ArrayList<String>(size).also { values[name] = it }
    }
}

fun valuesOf(vararg pairs: Pair<String, List<String>>, caseInsensitiveKey: Boolean = false): StringValues {
    return StringValuesImpl(caseInsensitiveKey, pairs.asList().toMap())
}

fun valuesOf(name: String, value: String, caseInsensitiveKey: Boolean = false): StringValues {
    return StringValuesSingleImpl(caseInsensitiveKey, name, listOf(value))
}

fun valuesOf(name: String, values: List<String>, caseInsensitiveKey: Boolean = false): StringValues {
    return StringValuesSingleImpl(caseInsensitiveKey, name, values)
}

fun valuesOf() = StringValues.Empty

fun valuesOf(map: Map<String, Iterable<String>>, caseInsensitiveKey: Boolean = false): StringValues {
    val size = map.size
    if (size == 1) {
        val entry = map.entries.single()
        return StringValuesSingleImpl(caseInsensitiveKey, entry.key, entry.value.toList())
    }
    val values: MutableMap<String, List<String>> = if (caseInsensitiveKey) caseInsensitiveMap(size) else LinkedHashMap(size)
    map.entries.forEach { values.put(it.key, it.value.toList()) }
    return StringValuesImpl(caseInsensitiveKey, values)
}

fun StringValues.toMap(): Map<String, List<String>> =
        entries().associateByTo(LinkedHashMap(), { it.key }, { it.value.toList() })

fun StringValues.flattenEntries(): List<Pair<String, String>> = entries().flatMap { e -> e.value.map { e.key to it } }

fun StringValues.flattenForEach(block: (String, String) -> Unit) = forEach { name, items ->
    items.forEach { block(name, it) }
}

fun StringValues.filter(keepEmpty: Boolean = false, predicate: (String, String) -> Boolean): StringValues {
    val entries = entries()
    val values: MutableMap<String, MutableList<String>> = if (caseInsensitiveName) caseInsensitiveMap(entries.size) else LinkedHashMap(entries.size)
    entries.forEach { entry ->
        val list = entry.value.filterTo(ArrayList(entry.value.size)) { predicate(entry.key, it) }
        if (keepEmpty || list.isNotEmpty())
            values.put(entry.key, list)
    }

    return StringValuesImpl(caseInsensitiveName, values)
}

fun StringValuesBuilder.appendFiltered(source: StringValues, keepEmpty: Boolean = false, predicate: (String, String) -> Boolean) {
    source.forEach { name, value ->
        val list = value.filterTo(ArrayList(value.size)) { predicate(name, it) }
        if (keepEmpty || list.isNotEmpty())
            appendAll(name, list)
    }
}

fun StringValuesBuilder.appendAll(builder: StringValuesBuilder): StringValuesBuilder = apply {
    builder.entries().forEach { (name, values) ->
        appendAll(name, values)
    }
}

private fun entriesEquals(a: Set<Map.Entry<String, List<String>>>, b: Set<Map.Entry<String, List<String>>>): Boolean {
    return a == b
}

private fun entriesHashCode(entries: Set<Map.Entry<String, List<String>>>, seed: Int): Int {
    return seed * 31 + entries.hashCode()
}
