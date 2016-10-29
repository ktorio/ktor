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

class ValuesMapSingleImpl(override val caseInsensitiveKey: Boolean, val name: String, val values: List<String>) : ValuesMap {
    override fun getAll(name: String): List<String>? = if (this.name.equals(name, caseInsensitiveKey)) values else null
    override fun entries(): Set<Map.Entry<String, List<String>>> = setOf(object : Map.Entry<String, List<String>> {
        override val key: String = name
        override val value: List<String> = values
    })

    override fun isEmpty(): Boolean = false
    override fun names(): Set<String> = setOf(name)
}

private class ValuesMapImpl(override val caseInsensitiveKey: Boolean = false, source: Map<String, Iterable<String>> = emptyMap()) : ValuesMap {
    private val values: MutableMap<String, List<String>> = if (caseInsensitiveKey) CaseInsensitiveMap(source.size) else LinkedHashMap(source.size)

    init {
        if (source.isNotEmpty()) {
            values.putAll(source.entries.map { it.key to it.value.toList() })
        }
    }

    override operator fun get(name: String) = listForKey(name)?.firstOrNull()
    override fun getAll(name: String): List<String>? = listForKey(name)

    override operator fun contains(name: String) = listForKey(name) != null
    override fun contains(name: String, value: String) = listForKey(name)?.contains(value) ?: false

    override fun names() = Collections.unmodifiableSet(values.keys)
    override fun isEmpty() = values.isEmpty()
    override fun entries() = Collections.unmodifiableSet(values.entries)

    private fun listForKey(key: String): List<String>? = values[key]
}

class ValuesMapBuilder(val caseInsensitiveKey: Boolean = false, size: Int = 8) {
    private val values: MutableMap<String, MutableList<String>> = if (caseInsensitiveKey) CaseInsensitiveMap(size) else LinkedHashMap(size)

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
        for ((k, v) in values.filter { it.value.isEmpty() }) {
            remove(k)
        }
    }

    fun remove(name: String, value: String) = listForKey(name)?.remove(value) ?: false

    fun clear() {
        values.clear()
    }

    fun build(): ValuesMap = ValuesMapImpl(caseInsensitiveKey, values)

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

fun valuesOf(vararg pairs: Pair<String, List<String>>): ValuesMap {
    return ValuesMapImpl(false, pairs.asList().toMap())
}

fun valuesOf(pair: Pair<String, List<String>>): ValuesMap {
    return ValuesMapSingleImpl(false, pair.first, pair.second)
}

fun valuesOf(map: Map<String, Iterable<String>>, caseInsensitiveKey: Boolean = false): ValuesMap = ValuesMapImpl(caseInsensitiveKey, map)

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
fun ValuesMap.filter(keepEmpty: Boolean = false, predicate: (String, String) -> Boolean) = valuesOf(
        entries().map { e -> e.key to e.value.filter { predicate(e.key, it) } }
                .filter { keepEmpty || it.second.isNotEmpty() }
                .toMap(),
        this.caseInsensitiveKey
)