package org.jetbrains.ktor.application

import java.util.*

public class ValuesMap(val map: Map<String, List<String>>) {
    companion object {
        val Empty = ValuesMap(mapOf())

        inline fun build(body: Builder.() -> Unit): ValuesMap = Builder().apply(body).build()
    }

    fun get(name: String): List<String>? = map[name]

    fun contains(name: String) = map.containsKey(name)
    fun contains(name: String, value: String) = map[name]?.contains(value) ?: false

    class Builder {
        private val map = hashMapOf<String, ArrayList<String>>()

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