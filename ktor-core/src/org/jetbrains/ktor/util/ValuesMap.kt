package org.jetbrains.ktor.util

import java.util.*

public class ValuesMap(private val map: Map<String, List<String>>) {
    companion object {
        val Empty = ValuesMap(mapOf())

        inline fun build(body: Builder.() -> Unit): ValuesMap = Builder().apply(body).build()
    }

    operator fun get(name: String): String? = map[name]?.singleOrNull()
    fun getAll(name: String): List<String>? = map[name]

    fun entries(): Set<Map.Entry<String, List<String>>> = map.entrySet()
    fun names(): Set<String> = map.keySet()

    operator fun contains(name: String) = map.containsKey(name)
    fun contains(name: String, value: String) = map[name]?.contains(value) ?: false

    class Builder {
        private val map = linkedMapOf<String, ArrayList<String>>()

        fun appendAll(valuesMap: ValuesMap) {
            for ((key, values) in valuesMap.map)
                map.getOrPut(key, { arrayListOf() }).addAll(values)
        }

        fun appendAll(key: String, values: Iterable<String>) {
            map.getOrPut(key, { arrayListOf() }).addAll(values)
        }

        fun append(key: String, value: String) {
            map.getOrPut(key, { arrayListOf() }).add(value)
        }

        fun build(): ValuesMap = ValuesMap(map)
    }
}

fun valuesOf(vararg pairs: Pair<String, Iterable<String>>): ValuesMap {
    return ValuesMap.build {
        for ((key, values) in pairs)
            appendAll(key, values)
    }
}