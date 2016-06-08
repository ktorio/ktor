package org.jetbrains.ktor.transform

import java.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.*

class TransformTable {
    private val entries = ArrayList<Entry<*>>()

    inline fun <reified T : Any> register(noinline predicate: (T) -> Boolean = { true }, noinline handler: (T) -> Any) {
        register(T::class, predicate, handler)
    }

    fun <T : Any> register(type: KClass<T>, predicate: (T) -> Boolean, handler: (T) -> Any) {
        entries.add(Entry(type, predicate, handler))
    }

    fun transform(obj: Any): Any = transform(obj, HashSet(entries.size * 2))

    tailrec
    private fun transform(obj: Any, visited: MutableSet<Entry<*>>): Any {
        val entries = entries(obj).filter { it !in visited }
        var current = obj

        for (entry in entries) {
            val result = entry.handler(current)
            current = result

            if (result === obj) {
                continue
            } else {
                visited.add(entry)
                return transform(current, visited)
            }
        }

        return current
    }

    fun entries(obj: Any) = entries(obj, entries)

    private fun entries(obj: Any, entries: List<Entry<*>>) = entries.mapNotNull { castOrNull(it, obj) }
            .filter { it.predicate(obj) }
            .sortedWith(Comparator { a, b ->
                val typeA = a.type.java
                val typeB = b.type.java

                when {
                    typeA === typeB -> 0
                    typeA.isAssignableFrom(typeB) -> 1
                    else -> -1
                }
            })

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> castOrNull(e: Entry<*>, obj: T) = if (e.type.java.isInstance(obj)) e as Entry<T> else null

    class Entry<T : Any>(val type: KClass<T>, val predicate: (T) -> Boolean, val handler: (T) -> Any) {
        override fun toString() = "Entry(${type.jvmName})"
    }
}