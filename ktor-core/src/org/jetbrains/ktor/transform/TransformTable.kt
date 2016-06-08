package org.jetbrains.ktor.transform

import java.util.*
import kotlin.reflect.*

class TransformTable {
    private val root = Entry(Any::class.java, null)
    private val cache = HashMap<Class<*>, MutableList<Entry<*>>>(1)

    init {
        cache[Any::class.java] = mutableListOf<Entry<*>>(root)
    }

    inline fun <reified T : Any> register(noinline predicate: (T) -> Boolean = { true }, noinline handler: (T) -> Any) {
        register(T::class, predicate, handler)
    }

    fun <T : Any> register(type: KClass<T>, predicate: (T) -> Boolean, handler: (T) -> Any) {
        @Suppress("UNCHECKED_CAST")
        registerImpl(type.java, root as Entry<T>, Handler(predicate, handler))
    }

    fun transform(obj: Any): Any = transformImpl(obj)

    private fun <T : Any> registerImpl(type: Class<T>, node: Entry<T>, handler: Handler<T>): Int {
        if (node.type === type) {
            node.handlers.add(handler)
            return 1
        } else if (node.type.isAssignableFrom(type)) {
            val installed = node.leafs.map { registerImpl(type, it, handler) }.sum()
            if (installed == 0) {
                val entry = insertEntry(type, node)
                cache.getOrPut(type) { ArrayList() }.add(entry)
                return registerImpl(type, entry, handler)
            }

            return installed
        }

        return 0
    }

    private fun <T : Any> insertEntry(type: Class<T>, node: Entry<T>): Entry<T> {
        val entry = Entry(type, node)

        for (leaf in node.leafs) {
            if (type in leaf.type.superTypes()) {
                node.leafs.remove(leaf)
                leaf.parent = entry
                entry.leafs.add(leaf)
                break
            }
        }

        node.leafs.add(entry)
        return entry
    }

    tailrec
    private fun <T : Any> transformImpl(obj: T, handlers: List<Handler<T>> = collect(obj.javaClass), visited: MutableSet<Handler<*>> = HashSet()): Any {
        for (handler in handlers) {
            if (handler !in visited && handler.predicate(obj)) {
                val result = handler.handler(obj)

                if (result === obj) {
                    continue
                }

                visited.add(handler)
                if (result.javaClass === obj.javaClass) {
                    @Suppress("UNCHECKED_CAST")
                    return transformImpl(result as T, handlers, visited)
                } else {
                    return transformImpl(result, collect(result.javaClass), visited)
                }
            }
        }

        return obj
    }

    private fun <T : Any> collect(type: Class<T>) =
            collectCached(type) ?:
                    ArrayList<Handler<T>>().apply {
                        collectImpl(type, mutableListOf<Entry<*>>(root), this)
                    }.asReversed()

    tailrec
    private fun <T : Any> collectImpl(type: Class<T>, nodes: MutableList<Entry<*>>, result: ArrayList<Handler<T>>) {
        val current = nodes.lookup(type) ?: return

        result.addAll(current.handlers)
        nodes.addAll(current.leafs)

        collectImpl(type, nodes, result)
    }

    private fun <T : Any> collectCached(type: Class<T>): List<Handler<T>>? {
        val exactNodes = cache[type]?.mapNotNull { it.castOrNull(type) }
        if (exactNodes == null || exactNodes.isEmpty()) {
            return null
        }

        val collected = ArrayList<Handler<T>>()

        for (root in exactNodes) {
            collected.addAll(root.handlers)
            var current = root.parent
            while (current != null) {
                collected.addAll(current.handlers)
                current = current.parent
            }
        }

        return collected
    }

    tailrec
    private fun <T : Any> MutableList<Entry<*>>.lookup(type: Class<T>): Entry<T>? {
        if (isEmpty()) return null

        return removeAt(lastIndex).castOrNull(type) ?: lookup(type)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> Entry<*>.castOrNull(type: Class<T>) = if (this.type.isAssignableFrom(type)) this as Entry<T> else null

    private class Entry<T : Any>(val type: Class<T>, var parent: Entry<in T>?) {
        val handlers = ArrayList<Handler<T>>()
        val leafs = ArrayList<Entry<T>>()

        override fun toString() = "Entry(${type.name})"
    }

    private class Handler<in T>(val predicate: (T) -> Boolean, val handler: (T) -> Any) {
        override fun toString() = handler.toString()
    }

    private fun <T> Class<T>.superTypes(): Sequence<Class<*>> {
        var current = listOf<Class<*>>(this)
        val visited = HashSet<Class<*>>()

        return generateSequence {
            val next = current.filter { it !in visited }.map {
                val a = it.interfaces.orEmpty().toList()
                val b = it.superclass

                if (b == null) a else a + b
            }.flatMap { it }

            visited.addAll(current)
            current = next

            if (next.isEmpty()) null else next
        }.flatMap { it.asSequence() }
    }
}