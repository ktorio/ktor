package io.ktor.util

import java.util.*

/**
 * Provides data structure for associating a [String] with a [List] of Strings
 */
interface ValuesMap {
    companion object {
        /**
         * Empty [ValuesMap] instance
         */
        val Empty: ValuesMap = ValuesMapImpl()

        /**
         * Builds a [ValuesMap] instance with the given [builder] function
         * @param caseInsensitiveName specifies if map should have case-sensitive or case-insensitive names
         * @param builder specifies a function to build a map
         */
        inline fun build(caseInsensitiveName: Boolean = false, builder: ValuesMapBuilder.() -> Unit): ValuesMap = ValuesMapBuilder(caseInsensitiveName).apply(builder).build()
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

private class ValuesMapSingleImpl(override val caseInsensitiveName: Boolean, val name: String, val values: List<String>) : ValuesMap {
    override fun getAll(name: String): List<String>? = if (this.name.equals(name, caseInsensitiveName)) values else null
    override fun entries(): Set<Map.Entry<String, List<String>>> = setOf(object : Map.Entry<String, List<String>> {
        override val key: String = name
        override val value: List<String> = values
        override fun toString() = "$key=$value"
    })

    override fun isEmpty(): Boolean = false
    override fun names(): Set<String> = setOf(name)

    override fun toString() = "ValuesMap(case=${!caseInsensitiveName}) ${entries()}"
    override fun hashCode() = entriesHashCode(entries(), 31 * caseInsensitiveName.hashCode())
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValuesMap) return false
        if (caseInsensitiveName != other.caseInsensitiveName) return false
        return entriesEquals(entries(), other.entries())
    }

    override fun forEach(body: (String, List<String>) -> Unit) = body(name, values)
    override fun get(name: String): String? = if (name.equals(this.name, caseInsensitiveName)) values.firstOrNull() else null
    override fun contains(name: String): Boolean = name.equals(this.name, caseInsensitiveName)
    override fun contains(name: String, value: String): Boolean = name.equals(this.name, caseInsensitiveName) && values.contains(value)
}

private class ValuesMapImpl(override val caseInsensitiveName: Boolean = false, private val values: Map<String, List<String>> = emptyMap()) : ValuesMap {
    override operator fun get(name: String) = listForKey(name)?.firstOrNull()
    override fun getAll(name: String): List<String>? = listForKey(name)

    override operator fun contains(name: String) = listForKey(name) != null
    override fun contains(name: String, value: String) = listForKey(name)?.contains(value) ?: false

    override fun names(): Set<String> = Collections.unmodifiableSet(values.keys)
    override fun isEmpty() = values.isEmpty()
    override fun entries(): Set<Map.Entry<String, List<String>>> = Collections.unmodifiableSet(values.entries)
    override fun forEach(body: (String, List<String>) -> Unit) = values.forEach(body)

    private fun listForKey(name: String): List<String>? = values[name]
    override fun toString() = "ValuesMap(case=${!caseInsensitiveName}) ${entries()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValuesMap) return false
        if (caseInsensitiveName != other.caseInsensitiveName) return false
        return entriesEquals(entries(), other.entries())
    }

    override fun hashCode() = entriesHashCode(entries(), 31 * caseInsensitiveName.hashCode())
}

class ValuesMapBuilder(val caseInsensitiveKey: Boolean = false, size: Int = 8) {
    private val values: MutableMap<String, MutableList<String>> = if (caseInsensitiveKey) CaseInsensitiveMap(size) else LinkedHashMap(size)
    private var built = false

    fun getAll(name: String): List<String>? = values[name]
    fun contains(name: String, value: String) = values[name]?.contains(value) ?: false

    fun names() = values.keys
    fun isEmpty() = values.isEmpty()
    fun entries(): Set<Map.Entry<String, List<String>>> = Collections.unmodifiableSet(values.entries)

    operator fun set(name: String, value: String) {
        val list = ensureListForKey(name, 1)
        list.clear()
        list.add(value)
    }

    operator fun get(name: String): String? = getAll(name)?.firstOrNull()

    fun append(name: String, value: String) {
        ensureListForKey(name, 1).add(value)
    }

    fun appendAll(valuesMap: ValuesMap) {
        valuesMap.forEach { name, values ->
            appendAll(name, values)
        }
    }

    fun appendMissing(valuesMap: ValuesMap) {
        valuesMap.forEach { name, values ->
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

    fun build(): ValuesMap {
        require(!built) { "ValueMapBuilder can only build a single ValueMap" }
        built = true
        return ValuesMapImpl(caseInsensitiveKey, values)
    }

    private fun ensureListForKey(name: String, size: Int): MutableList<String> {
        return values[name] ?: ArrayList<String>(size).also { values[name] = it }
    }
}

fun valuesOf(vararg pairs: Pair<String, List<String>>, caseInsensitiveKey: Boolean = false): ValuesMap {
    return ValuesMapImpl(caseInsensitiveKey, pairs.asList().toMap())
}

fun valuesOf(pair: Pair<String, List<String>>, caseInsensitiveKey: Boolean = false): ValuesMap {
    return ValuesMapSingleImpl(caseInsensitiveKey, pair.first, pair.second)
}

fun valuesOf(name: String, value: List<String>, caseInsensitiveKey: Boolean = false): ValuesMap {
    return ValuesMapSingleImpl(caseInsensitiveKey, name, value)
}

fun valuesOf(): ValuesMap {
    return ValuesMap.Empty
}

fun valuesOf(map: Map<String, Iterable<String>>, caseInsensitiveKey: Boolean = false): ValuesMap {
    val size = map.size
    if (size == 1) {
        val entry = map.entries.single()
        return ValuesMapSingleImpl(caseInsensitiveKey, entry.key, entry.value.toList())
    }
    val values: MutableMap<String, List<String>> = if (caseInsensitiveKey) CaseInsensitiveMap(size) else LinkedHashMap(size)
    map.entries.forEach { values.put(it.key, it.value.toList()) }
    return ValuesMapImpl(caseInsensitiveKey, values)
}

operator fun ValuesMap.plus(other: ValuesMap) = when {
    caseInsensitiveName == other.caseInsensitiveName -> when {
        this.isEmpty() -> other
        other.isEmpty() -> this
        else -> ValuesMap.build(caseInsensitiveName) { appendAll(this@plus); appendAll(other) }
    }
    else -> throw IllegalArgumentException("It is forbidden to concatenate case sensitive and case insensitive maps")
}

fun ValuesMap.toMap(): Map<String, List<String>> =
        entries().associateByTo(LinkedHashMap(), { it.key }, { it.value.toList() })

fun ValuesMap.flattenEntries(): List<Pair<String, String>> = entries().flatMap { e -> e.value.map { e.key to it } }

fun ValuesMap.filter(keepEmpty: Boolean = false, predicate: (String, String) -> Boolean): ValuesMap {
    val entries = entries()
    val values: MutableMap<String, MutableList<String>> = if (caseInsensitiveName) CaseInsensitiveMap(entries.size) else LinkedHashMap(entries.size)
    entries.forEach { entry ->
        val list = entry.value.filterTo(ArrayList(entry.value.size)) { predicate(entry.key, it) }
        if (keepEmpty || list.isNotEmpty())
            values.put(entry.key, list)
    }

    return ValuesMapImpl(caseInsensitiveName, values)
}

fun ValuesMapBuilder.appendFiltered(source: ValuesMap, keepEmpty: Boolean = false, predicate: (String, String) -> Boolean) {
    source.forEach { name, value ->
        val list = value.filterTo(ArrayList(value.size)) { predicate(name, it) }
        if (keepEmpty || list.isNotEmpty())
            appendAll(name, list)
    }
}

fun ValuesMapBuilder.appendAll(valuesMap: ValuesMapBuilder): ValuesMapBuilder = apply {
    valuesMap.entries().forEach { (name, values) ->
        appendAll(name, values)
    }
}

fun valuesMapBuilderOf(builder: ValuesMapBuilder): ValuesMapBuilder =
        ValuesMapBuilder(builder.caseInsensitiveKey).appendAll(builder)

fun valuesOf(builder: ValuesMapBuilder): ValuesMap = valuesMapBuilderOf(builder).build()

private fun entriesEquals(a: Set<Map.Entry<String, List<String>>>, b: Set<Map.Entry<String, List<String>>>): Boolean {
    return a == b
}

private fun entriesHashCode(entries: Set<Map.Entry<String, List<String>>>, seed: Int): Int {
    return seed * 31 + entries.hashCode()
}
