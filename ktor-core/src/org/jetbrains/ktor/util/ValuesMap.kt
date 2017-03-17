package org.jetbrains.ktor.util

import java.util.*

interface ValuesMap {
    companion object {
        val Empty: ValuesMap = ValuesMapImpl()

        inline fun build(caseInsensitiveKey: Boolean = false, body: ValuesMapBuilder.() -> Unit): ValuesMap = ValuesMapBuilder(caseInsensitiveKey).apply(body).build()
    }

    operator fun get(name: String): String? = getAll(name)?.firstOrNull()
    fun getAll(name: String): List<String>?
    fun entries(): Set<Map.Entry<String, List<String>>>
    fun isEmpty(): Boolean
    val caseInsensitiveKey: Boolean
    fun names(): Set<String>

    operator fun contains(name: String): Boolean = getAll(name) != null
    fun contains(name: String, value: String): Boolean = getAll(name)?.contains(value) ?: false
}

private class ValuesMapSingleImpl(override val caseInsensitiveKey: Boolean, val name: String, val values: List<String>) : ValuesMap {
    override fun getAll(name: String): List<String>? = if (this.name.equals(name, caseInsensitiveKey)) values else null
    override fun entries(): Set<Map.Entry<String, List<String>>> = setOf(object : Map.Entry<String, List<String>> {
        override val key: String = name
        override val value: List<String> = values
        override fun toString() = "$key=$value"
    })

    override fun isEmpty(): Boolean = false
    override fun names(): Set<String> = setOf(name)
    override fun toString() = "ValuesMap(case=${!caseInsensitiveKey}) ${entries()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValuesMap) return false
        if (caseInsensitiveKey != other.caseInsensitiveKey) return false
        return entriesEquals(entries(), other.entries())
    }

    override fun hashCode() = entriesHashCode(entries(), 31 * caseInsensitiveKey.hashCode())
}

private class ValuesMapImpl(override val caseInsensitiveKey: Boolean = false, private val values: Map<String, List<String>> = emptyMap()) : ValuesMap {
    override operator fun get(name: String) = listForKey(name)?.firstOrNull()
    override fun getAll(name: String): List<String>? = listForKey(name)

    override operator fun contains(name: String) = listForKey(name) != null
    override fun contains(name: String, value: String) = listForKey(name)?.contains(value) ?: false

    override fun names(): Set<String> = Collections.unmodifiableSet(values.keys)
    override fun isEmpty() = values.isEmpty()
    override fun entries(): Set<Map.Entry<String, List<String>>> = Collections.unmodifiableSet(values.entries)

    private fun listForKey(key: String): List<String>? = values[key]
    override fun toString() = "ValuesMap(case=${!caseInsensitiveKey}) ${entries()}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValuesMap) return false
        if (caseInsensitiveKey != other.caseInsensitiveKey) return false
        return entriesEquals(entries(), other.entries())
    }

    override fun hashCode() = entriesHashCode(entries(), 31 * caseInsensitiveKey.hashCode())
}

class ValuesMapBuilder(val caseInsensitiveKey: Boolean = false, size: Int = 8) {
    private val values: MutableMap<String, MutableList<String>> = if (caseInsensitiveKey) CaseInsensitiveMap(size) else LinkedHashMap(size)
    private var built = false

    fun getAll(name: String): List<String>? = listForKey(name)
    fun contains(name: String, value: String) = listForKey(name)?.contains(value) ?: false

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
        for ((key, values) in valuesMap.entries())
            appendAll(key, values)
    }

    fun appendMissing(valuesMap: ValuesMap) {
        for ((key, values) in valuesMap.entries())
            appendMissing(key, values)
    }

    fun appendAll(key: String, values: Iterable<String>) {
        ensureListForKey(key, (values as? Collection)?.size ?: 2).addAll(values)
    }

    fun appendMissing(key: String, values: Iterable<String>) {
        val existing = listForKey(key)?.toSet() ?: emptySet()

        appendAll(key, values.filter { it !in existing })
    }

    fun remove(name: String) {
        values.remove(name)
    }

    fun removeKeysWithNoEntries() {
        for ((k, _) in values.filter { it.value.isEmpty() }) {
            remove(k)
        }
    }

    fun remove(name: String, value: String) = listForKey(name)?.remove(value) ?: false

    fun clear() {
        values.clear()
    }

    fun build(): ValuesMap {
        require(!built) { "ValueMapBuilder can only build single ValueMap" }
        built = true
        return ValuesMapImpl(caseInsensitiveKey, values)
    }

    private fun ensureListForKey(key: String, size: Int): MutableList<String> {
        val existing = listForKey(key)
        if (existing != null) {
            return existing
        }

        appendNewKey(key, size)
        return ensureListForKey(key, size)
    }

    private fun appendNewKey(key: String, size: Int) {
        values[key] = ArrayList(size)
    }

    private fun listForKey(key: String): MutableList<String>? = values[key]
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
    caseInsensitiveKey == other.caseInsensitiveKey -> when {
        this.isEmpty() -> other
        other.isEmpty() -> this
        else -> ValuesMap.build(caseInsensitiveKey) { appendAll(this@plus); appendAll(other) }
    }
    else -> throw IllegalArgumentException("It is forbidden to concatenate case sensitive and case insensitive maps")
}

fun ValuesMap.toMap(): Map<String, List<String>> =
        entries().associateByTo(LinkedHashMap<String, List<String>>(), { it.key }, { it.value.toList() })

fun ValuesMap.flattenEntries(): List<Pair<String, String>> = entries().flatMap { e -> e.value.map { e.key to it } }

fun ValuesMap.filter(keepEmpty: Boolean = false, predicate: (String, String) -> Boolean): ValuesMap {
    val entries = entries()
    val values: MutableMap<String, MutableList<String>> = if (caseInsensitiveKey) CaseInsensitiveMap(entries.size) else LinkedHashMap(entries.size)
    entries.forEach { entry ->
        val list = entry.value.filterTo(ArrayList(entry.value.size)) { predicate(entry.key, it) }
        if (keepEmpty || list.isNotEmpty())
            values.put(entry.key, list)
    }

    return ValuesMapImpl(caseInsensitiveKey, values)
}

fun ValuesMapBuilder.appendFiltered(source: ValuesMap, keepEmpty: Boolean = false, predicate: (String, String) -> Boolean) {
    source.entries().forEach { entry ->
        val list = entry.value.filterTo(ArrayList(entry.value.size)) { predicate(entry.key, it) }
        if (keepEmpty || list.isNotEmpty())
            appendAll(entry.key, list)
    }
}

private fun entriesEquals(a: Set<Map.Entry<String, List<String>>>, b: Set<Map.Entry<String, List<String>>>): Boolean {
    return a == b
}

private fun entriesHashCode(entries: Set<Map.Entry<String, List<String>>>, seed: Int): Int {
    return seed * 31 + entries.hashCode()
}