package org.jetbrains.ktor.util

import java.util.*

public interface ValuesMap {
    companion object {
        val Empty = ValuesMapImpl(mapOf())

        inline fun build(caseInsensitiveKey: Boolean = false, body: ValuesMapImpl.Builder.() -> Unit): ValuesMap = ValuesMapImpl.Builder().apply(body).build(caseInsensitiveKey)
    }

    operator fun get(name: String): String?
    fun getAll(name: String): List<String>?
    fun entries(): Set<Map.Entry<String, List<String>>>
    fun isEmpty(): Boolean
    val caseInsensitiveKey: Boolean
    fun names(): Set<String>

    operator fun contains(name: String): Boolean
    fun contains(name: String, value: String): Boolean
}

public class ValuesMapImpl(map: Map<String, List<String>>, override val caseInsensitiveKey: Boolean = false) : ValuesMap {
    private val map: Map<String, List<String>> = if (caseInsensitiveKey) lowercase(map) else map

    private fun lowercase(map: Map<String, List<String>>): Map<String, List<String>> {
        return map.asSequence().associateBy({ it.key.toLowerCase() }, { it.value })
    }

    private fun makeKey(name: String) = if (caseInsensitiveKey) name.toLowerCase() else name

    override operator fun get(name: String): String? = map[makeKey(name)]?.firstOrNull()
    override fun getAll(name: String): List<String>? = map[makeKey(name)]

    override fun entries(): Set<Map.Entry<String, List<String>>> = map.entries
    override fun isEmpty(): Boolean = map.isEmpty()

    /* TODO: Ideally key case should be preserved for case insensitive map, but what to do with keys
       that are different but case-insensitively equal? */
    override fun names(): Set<String> = map.keys

    override operator fun contains(name: String) = map.containsKey(makeKey(name))
    override fun contains(name: String, value: String) = map[makeKey(name)]?.contains(value) ?: false

    class Builder {
        private val map = linkedMapOf<String, ArrayList<String>>()

        fun appendAll(valuesMap: ValuesMap) {
            for ((key, values) in valuesMap.entries())
                appendAll(key, values)
        }

        fun appendMissing(valuesMap: ValuesMap) {
            for ((key, values) in valuesMap.entries())
                appendMissing(key, values)
        }

        fun appendAll(key: String, values: Iterable<String>) {
            map.getOrPut(key, { arrayListOf() }).addAll(values)
        }

        fun appendMissing(key: String, values: Iterable<String>) {
            map.getOrPut(key, { arrayListOf() }).apply {
                val existing = toHashSet()
                addAll(values.filter { it !in existing })
            }
        }

        fun append(key: String, value: String) {
            map.getOrPut(key, { arrayListOf() }).add(value)
        }

        fun build(caseInsensitiveKey: Boolean = false): ValuesMap = ValuesMapImpl(map, caseInsensitiveKey)
    }
}

fun valuesOf(vararg pairs: Pair<String, List<String>>): ValuesMap {
    return ValuesMapImpl(pairs.asList().toMap(), false)
}

operator fun ValuesMap.plus(other: ValuesMap) = when {
    caseInsensitiveKey == other.caseInsensitiveKey -> ValuesMap.build(caseInsensitiveKey) { appendAll(this@plus); appendAll(other) }
    else -> throw IllegalArgumentException("It is forbidden to concatenate case sensitive and case insensitive maps")
}

fun ValuesMap.flattenEntries(): List<Pair<String, String>> = entries().flatMap { e -> e.value.map { e.key to it } }
