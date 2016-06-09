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

    fun copy(): TransformTable {
        val newInstance = TransformTable()
        newInstance.root.leafs.addAll(root.leafs.map { it.copy() })
        newInstance.root.leafs.forEach { newInstance.cacheChildren(it) }

        return newInstance
    }

    private fun cacheChildren(node: Entry<*>) {
        dfs(node) { it.leafs }.filter { !it.type.isInterface }.forEach {
            cache.getOrPut(it.type) { ArrayList() }.add(it)
        }
    }

    private fun <T : Any> registerImpl(type: Class<T>, node: Entry<T>, handler: Handler<T>): Int {
        if (node.type === type) {
            node.handlers.add(handler)
            return 1
        } else if (node.type.isAssignableFrom(type)) {
            val installed = node.leafs.map { registerImpl(type, it, handler) }.sum()
            if (installed == 0) {
                val entry = insertEntry(type, node)
                if (!type.isInterface) {
                    cache.getOrPut(type) { ArrayList() }.add(entry)
                }
                return registerImpl(type, entry, handler)
            }

            return installed
        }

        return 0
    }

    private fun <T : Any> insertEntry(type: Class<T>, parent: Entry<T>): Entry<T> {
        val newTypeEntry = Entry(type, parent)
        val parentType = parent.type
        val superTypes = type.superTypes().takeLastWhile { it !== parentType }

        for (leaf in parent.leafs) {
            val leafSuperTypes = leaf.type.superTypes().takeLastWhile { it !== parentType }.dropWhile { it !== type }
            var common: Class<*>? = null
            for (idx in 0 .. Math.min(superTypes.size, leafSuperTypes.size) - 1) {
                if (superTypes[idx] === leafSuperTypes[idx]) {
                    common = superTypes[idx]
                } else {
                    break
                }
            }

            if (common == type) {
                parent.leafs.remove(leaf)
                parent.leafs.add(newTypeEntry)
                newTypeEntry.leafs.add(leaf)
                leaf.parent = newTypeEntry

                return newTypeEntry
            } else if (common != null) {
                @Suppress("UNCHECKED_CAST")
                val nodeForCommon = Entry(common as Class<T>, parent)
                parent.leafs.remove(leaf)
                nodeForCommon.leafs.add(leaf)
                leaf.parent = nodeForCommon

                if (!common.isInterface) {
                    cache.getOrPut(common) { ArrayList() }.add(nodeForCommon)
                }

                newTypeEntry.parent = nodeForCommon
                newTypeEntry.leafs.add(leaf)
                return newTypeEntry
            }
        }

        newTypeEntry.leafs.addAll(findSubTypes(parent, type).mapNotNull { it.castOrNull(type)?.copy() })
        newTypeEntry.leafs.forEach { cacheChildren(it) }
        parent.leafs.add(newTypeEntry)

        return newTypeEntry
    }

    private fun findSubTypes(node: Entry<*>, type: Class<*>): List<Entry<*>> = ArrayList<Entry<*>>().apply {
        findSubTypes(mutableListOf(node), type, this)
    }.distinctBy { it.type }

    private fun findSubTypes(nodes: MutableList<Entry<*>>, type: Class<*>, good: MutableList<Entry<*>>) {
        if (nodes.isEmpty()) return
        val node = nodes.removeLast()

        if (type.isAssignableFrom(node.type)) {
            good.add(node)
        } else {
            nodes.addAll(node.leafs)
        }

        findSubTypes(nodes, type, good)
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

    private fun <T : Any> Entry<T>.copy(): Entry<T> {
        val newInstance = Entry(type, parent)
        newInstance.handlers.addAll(handlers)
        val newLeafs = leafs.map { it.copy() }
        newLeafs.forEach { it.parent = newInstance }
        newInstance.leafs.addAll(newLeafs)

        return newInstance
    }

    private class Handler<in T>(val predicate: (T) -> Boolean, val handler: (T) -> Any) {
        override fun toString() = handler.toString()
    }

    private fun <T> Class<T>.superTypes() = dfs(this)
}