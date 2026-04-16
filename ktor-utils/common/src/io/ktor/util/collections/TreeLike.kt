/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util.collections

/**
 * Represents a tree-like hierarchical structure, where each node has a parent and a collection of child nodes.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.TreeLike)
 *
 * @param T The type parameter representing the node type, which must also implement [TreeLike].
 */
public interface TreeLike<out T : TreeLike<T>> {
    public val parent: T?
    public val children: Iterable<T>

    /**
     * Returns a sequence of nodes from the current node up to the root.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.TreeLike.lineage)
     */
    @Suppress("UNCHECKED_CAST")
    public fun lineage(): Sequence<T> =
        generateSequence(this as? T) { it.parent }

    /**
     * Returns a sequence of nodes from the current node and its descendants.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.TreeLike.descendants)
     */
    public fun descendants(): Sequence<T> =
        children.asSequence()
            .flatMap {
                sequenceOf(it) + it.descendants()
            }

    /**
     * Returns `true` if the current node is a root node.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.TreeLike.isRoot)
     */
    public fun isRoot(): Boolean =
        parent == null

    /**
     * Returns `true` if the current node has no children.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.collections.TreeLike.isLeaf)
     */
    public fun isLeaf(): Boolean =
        !children.iterator().hasNext()
}
