package io.ktor.util

import java.util.*

/**
 * Calculates a list of all superclasses for the given class
 */
fun Class<*>.findAllSupertypes(): List<Class<*>> {
    val result = LinkedHashSet<Class<*>>()
    findAllSupertypes(mutableListOf(Pair(this, supertypes())), mutableSetOf(this), result)
    return result.toList()
}

private tailrec fun findAllSupertypes(nodes: MutableList<Pair<Class<*>, MutableList<Class<*>>>>, path: MutableSet<Class<*>>, visited: MutableSet<Class<*>>) {
    if (nodes.isEmpty()) return

    val (current, children) = nodes[nodes.lastIndex]
    if (children.isEmpty()) {
        visited.add(current)
        path.remove(current)
        nodes.removeLast()
    } else {
        val next = children.removeLast()
        if (path.add(next)) {
            nodes.add(Pair(next, next.supertypes()))
        }
    }

    findAllSupertypes(nodes, path, visited)
}

private fun Class<*>.supertypes(): MutableList<Class<*>> = when {
    superclass == null -> interfaces?.toMutableList() ?: mutableListOf<Class<*>>()
    interfaces == null || interfaces.isEmpty() -> mutableListOf(superclass)
    else -> ArrayList<Class<*>>(interfaces.size + 1).apply {
        interfaces.toCollection(this@apply)
        add(superclass)
    }
}

private fun <T> MutableList<T>.removeLast(): T = removeAt(lastIndex)