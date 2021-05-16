/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE", "NO_EXPLICIT_VISIBILITY_IN_API_MODE")

package io.ktor.util.internal

import io.ktor.util.*

private typealias Node = LinkedListNode
/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual typealias LockFreeLinkedListNode = LinkedListNode

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual typealias LockFreeLinkedListHead = LinkedListHead

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public open class LinkedListNode {
    @PublishedApi internal var _next = this
    @PublishedApi internal var _prev = this
    @PublishedApi internal var _removed: Boolean = false

    public inline val nextNode get() = _next
    public inline val prevNode get() = _prev
    public inline val isRemoved get() = _removed

    public fun addLast(node: Node) {
        val prev = this._prev
        node._next = this
        node._prev = prev
        prev._next = node
        this._prev = node
    }

    public open fun remove(): Boolean {
        if (_removed) return false
        val prev = this._prev
        val next = this._next
        prev._next = next
        next._prev = prev
        _removed = true
        return true
    }

    public fun addOneIfEmpty(node: Node): Boolean {
        if (_next !== this) return false
        addLast(node)
        return true
    }

    public inline fun addLastIf(node: Node, crossinline condition: () -> Boolean): Boolean {
        if (!condition()) return false
        addLast(node)
        return true
    }

    public inline fun addLastIfPrev(node: Node, predicate: (Node) -> Boolean): Boolean {
        if (!predicate(_prev)) return false
        addLast(node)
        return true
    }

    public inline fun addLastIfPrevAndIf(
        node: Node,
        predicate: (Node) -> Boolean, // prev node predicate
        crossinline condition: () -> Boolean // atomically checked condition
    ): Boolean {
        if (!predicate(_prev)) return false
        if (!condition()) return false
        addLast(node)
        return true
    }

    public fun helpRemove() {} // No concurrency on JS -> no removal

    public fun removeFirstOrNull(): Node? {
        val next = _next
        if (next === this) return null
        check(next.remove()) { "Should remove" }
        return next
    }

    public inline fun <reified T> removeFirstIfIsInstanceOfOrPeekIf(predicate: (T) -> Boolean): T? {
        val next = _next
        if (next === this) return null
        if (next !is T) return null
        if (predicate(next)) return next
        check(next.remove()) { "Should remove" }
        return next
    }
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual open class AddLastDesc<T : Node> actual constructor(
    actual val queue: Node,
    actual val node: T
) : AbstractAtomicDesc() {
    override val affectedNode: Node get() = queue._prev
    actual override fun finishPrepare(prepareOp: PrepareOp) {}
    override fun onComplete() = queue.addLast(node)
    actual override fun finishOnSuccess(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode) = Unit
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual open class RemoveFirstDesc<T> actual constructor(
    actual val queue: LockFreeLinkedListNode
) : AbstractAtomicDesc() {
    @Suppress("UNCHECKED_CAST")
    actual val result: T get() = affectedNode as T
    override val affectedNode: Node = queue.nextNode
    actual override fun finishPrepare(prepareOp: PrepareOp) {}
    override fun onComplete() { queue.removeFirstOrNull() }
    actual override fun finishOnSuccess(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode) = Unit
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual abstract class AbstractAtomicDesc : AtomicDesc() {
    protected abstract val affectedNode: Node
    actual abstract fun finishPrepare(prepareOp: PrepareOp)
    protected abstract fun onComplete()

    actual open fun onPrepare(prepareOp: PrepareOp): Any? {
        finishPrepare(prepareOp)
        return null
    }

    actual open fun onRemoved(affected: Node) {}

    actual final override fun prepare(op: AtomicOp<*>): Any? {
        val affected = affectedNode
        val failure = failure(affected)
        if (failure != null) return failure
        @Suppress("UNCHECKED_CAST")
        return onPrepare(PrepareOp(affected, this, op))
    }

    actual final override fun complete(op: AtomicOp<*>, failure: Any?) = onComplete()
    protected actual open fun failure(affected: LockFreeLinkedListNode?): Any? = null // Never fails by default  // must fail on null for unlinked nodes on K/N
    protected actual open fun retry(affected: LockFreeLinkedListNode, next: Any): Boolean = false // Always succeeds
    protected actual abstract fun finishOnSuccess(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode)
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual class PrepareOp(
    actual val affected: LockFreeLinkedListNode,
    actual val desc: AbstractAtomicDesc,
    actual override val atomicOp: AtomicOp<*>
): OpDescriptor() {
    override fun perform(affected: Any?): Any? = null
    actual fun finishPrepare() {}
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public open class LinkedListHead : LinkedListNode() {
    public val isEmpty get() = _next === this

    /**
     * Iterates over all elements in this list of a specified type.
     */
    public inline fun <reified T : Node> forEach(block: (T) -> Unit) {
        var cur: Node = _next
        while (cur != this) {
            if (cur is T) block(cur)
            cur = cur._next
        }
    }

    // just a defensive programming -- makes sure that list head sentinel is never removed
    public final override fun remove(): Boolean = throw UnsupportedOperationException()
}

@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun Any.weakRef(): Any = this

@Suppress("NOTHING_TO_INLINE") // Should be NOP
internal actual inline fun Any?.unweakRef(): Any? = this

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun storeCyclicRef(block: () -> Unit) = block()
