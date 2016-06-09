package org.jetbrains.ktor.transform

import java.util.*

internal inline fun <reified T : Any> dfs(): List<Class<*>> = dfs(T::class.java)

internal fun dfs(type: Class<*>): List<Class<*>> = dfs(type, ::supertypes)

internal fun <T> dfs(root: T, parent: (T) -> List<T>): List<T> {
    val result = LinkedHashSet<T>()
    dfs(mutableListOf(Pair(root, parent(root).toMutableList())), parent, mutableSetOf(root), result)

    return result.toList()
}

tailrec
internal fun <T> dfs(nodes: MutableList<Pair<T, MutableList<T>>>, parent: (T) -> List<T>, path: MutableSet<T>, visited: MutableSet<T>) {
    if (nodes.isEmpty()) return

    val (current, children) = nodes.last()
    if (children.isEmpty()) {
        visited.add(current)
        path.remove(current)
        nodes.removeLast()
    } else {
        val next = children.removeLast()
        if (path.add(next)) {
            nodes.add(Pair(next, parent(next).toMutableList()))
        }
    }

    dfs(nodes, parent, path, visited)
}


private fun supertypes(clazz: Class<*>): List<Class<*>> = clazz.superclass?.let { clazz.interfaces.orEmpty().toList() + it } ?: clazz.interfaces.orEmpty().toList()
internal fun <T> MutableList<T>.removeLast(): T = removeAt(lastIndex)