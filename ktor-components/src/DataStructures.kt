package org.jetbrains.container

import java.util.*

public fun topologicalSort<T>(items: Iterable<T>, dependencies: (T) -> Iterable<T>): List<T> {
    val itemsInProgress = HashSet<T>();
    val completedItems = HashSet<T>();
    val result = ArrayList<T>()

    fun DfsVisit(item: T) {
        if (completedItems.contains(item))
            return;

        if (itemsInProgress.contains(item))
            throw CycleInTopoSortException();

        itemsInProgress.add(item);

        for (dependency in dependencies(item)) {
            DfsVisit(dependency);
        }

        itemsInProgress.remove(item);
        completedItems.add(item);
        result.add(item);
    }

    for (item in items)
        DfsVisit(item)

    return result.reverse();
}

public class CycleInTopoSortException : Exception()

class Multimap<K, V> : Iterable<Map.Entry<K, V>> {
    val map = LinkedHashMap<K, MutableSet<V>>()

    class Entry<K, V>(key: K, value: V) : Map.Entry<K, V> {
        private val _key = key
        private val _value = value
        override fun getKey(): K = _key
        override fun getValue(): V = _value
    }

    override fun iterator(): Iterator<Map.Entry<K, V>> = entries().iterator()

    fun put(key: K, value: V) {
        val list = map.getOrPut(key) { LinkedHashSet<V>() }
        list.add(value)
    }

    fun containsKey(key: K): Boolean = map.containsKey(key)
    fun containsValue(value: V): Boolean = values().flatMap { it }.any { it == value }
    fun entries(): List<Map.Entry<K, V>> = map.entrySet().flatMap { entry -> entry.value.map { Entry(entry.key, it) } }
    fun get(key: K): Set<V> = map.get(key) ?: emptySet()
    fun isEmpty(): Boolean = map.isEmpty()
    fun keys(): Set<K> = map.keySet()
    fun size(): Int = map.size()
    fun values(): Collection<Set<V>> = map.values()

}