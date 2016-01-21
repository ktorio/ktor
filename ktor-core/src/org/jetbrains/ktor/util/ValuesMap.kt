package org.jetbrains.ktor.util

import java.util.*

public class ValuesMap(map: Map<String, List<String>>, val caseInsensitiveKey: Boolean = false) {
    fun isEmpty(): Boolean = map.isEmpty()

    companion object {
        val Empty = ValuesMap(mapOf())

        inline fun build(caseInsensitiveKey: Boolean = false, body: Builder.() -> Unit): ValuesMap = Builder().apply(body).build(caseInsensitiveKey)
    }

    private val map: Map<String, List<String>> = if (caseInsensitiveKey) lowercase(map) else map

    private fun lowercase(map: Map<String, List<String>>): Map<String, List<String>> {
        return map.asSequence().toMapBy({ it.key.toLowerCase() }, { it.value })
    }

    private fun makeKey(name: String) = if (caseInsensitiveKey) name.toLowerCase() else name

    operator fun get(name: String): String? = map[makeKey(name)]?.firstOrNull()
    fun getAll(name: String): List<String>? = map[makeKey(name)]

    fun entries(): Set<Map.Entry<String, List<String>>> = map.entries

    /* TODO: Ideally key case should be preserved for case insensitive map, but what to do with keys
       that are different but case-insensitively equal? */
    fun names(): Set<String> = map.keys

    operator fun contains(name: String) = map.containsKey(makeKey(name))
    fun contains(name: String, value: String) = map[makeKey(name)]?.contains(value) ?: false

    class Builder {
        private val map = linkedMapOf<String, ArrayList<String>>()

        fun appendAll(valuesMap: ValuesMap) {
            for ((key, values) in valuesMap.map)
                appendAll(key, values)
        }

        fun appendAll(key: String, values: Iterable<String>) {
            map.getOrPut(key, { arrayListOf() }).addAll(values)
        }

        fun append(key: String, value: String) {
            map.getOrPut(key, { arrayListOf() }).add(value)
        }

        fun build(caseInsensitiveKey: Boolean = false): ValuesMap = ValuesMap(map, caseInsensitiveKey)
    }
}

fun valuesOf(vararg pairs: Pair<String, List<String>>): ValuesMap {
    return ValuesMap(pairs.asList().toMap(), false)
}

operator fun ValuesMap.plus(other: ValuesMap) = when {
    caseInsensitiveKey == other.caseInsensitiveKey -> ValuesMap.build(caseInsensitiveKey) { appendAll(this@plus); appendAll(other) }
    else -> throw IllegalArgumentException("It is forbidden to concatenate case sensitive and case insensitive maps")
}

fun ValuesMap.flattenEntries(): List<Pair<String, String>> = entries().flatMap { e -> e.value.map { e.key to it } }
