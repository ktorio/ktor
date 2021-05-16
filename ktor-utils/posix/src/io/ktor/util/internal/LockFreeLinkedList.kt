/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("NO_EXPLICIT_VISIBILITY_IN_API_MODE", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package io.ktor.util.internal

import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.internal.*
import kotlin.jvm.*
import kotlin.native.concurrent.*
import kotlin.native.ref.*

private typealias Node = LockFreeLinkedListNode

@PublishedApi
internal const val UNDECIDED: Int = 0

@PublishedApi
internal const val SUCCESS: Int = 1

@PublishedApi
internal const val FAILURE: Int = 2

@PublishedApi
@SharedImmutable
internal val CONDITION_FALSE: Any = Symbol("CONDITION_FALSE")

@PublishedApi
@SharedImmutable
internal val LIST_EMPTY: Any = Symbol("LIST_EMPTY")

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual typealias RemoveFirstDesc<T> = LockFreeLinkedListNode.RemoveFirstDesc<T>

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual typealias AddLastDesc<T> = LockFreeLinkedListNode.AddLastDesc<T>

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual typealias AbstractAtomicDesc = LockFreeLinkedListNode.AbstractAtomicDesc

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public actual typealias PrepareOp = LockFreeLinkedListNode.PrepareOp

/**
 * Doubly-linked concurrent list node with remove support.
 * Based on paper
 * ["Lock-Free and Practical Doubly Linked List-Based Deques Using Single-Word Compare-and-Swap"](https://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.140.4693&rep=rep1&type=pdf)
 * by Sundell and Tsigas with considerable changes.
 *
 * The core idea of the algorithm is to maintain a doubly-linked list with an ever-present sentinel node (it is
 * never removed) that serves both as a list head and tail and to linearize all operations (both insert and remove) on
 * the update of the next pointer. Removed nodes have their next pointer marked with [Removed] class.
 *
 * Important notes:
 * * There are no operations to add items to left side of the list, only to the end (right side), because we cannot
 *   efficiently linearize them with atomic multi-step head-removal operations. In short,
 *   support for [describeRemoveFirst] operation precludes ability to add items at the beginning.
 * * Previous pointers are not marked for removal. We don't support linearizable backwards traversal.
 * * Remove-helping logic is simplified and consolidated in [correctPrev] method.
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
@Suppress("LeakingThis")
@InternalAPI
public actual open class LockFreeLinkedListNode {
    // those _next & _prev refs can be null on Kotlin/Native when doubly-linked list is unlinked
    private val _next = atomic<Any?>(this) // Node | Removed | OpDescriptor
    private val _prev = atomic<Node?>(this) // Node to the left (cannot be marked as removed)
    private val _removedRef = atomic<Removed?>(null) // lazily cached removed ref to this

    private fun removed(): Removed =
        _removedRef.value ?: Removed(this).also {
            storeCyclicRef { _removedRef.lazySet(it) }
        }

    @PublishedApi
    internal abstract class CondAddOp(
        val newNode: Node
    ) : AtomicOp<Node>() {
        private val _oldNext = atomic<Node?>(null)

        var oldNext: Node?
            get() = _oldNext.value
            set(value) { _oldNext.value = value }

        override fun complete(affected: Node, failure: Any?) {
            val success = failure == null
            val update = if (success) newNode else oldNext
            if (update != null && affected._next.compareAndSet( this, update)) {
                // only the thread the makes this update actually finishes add operation
                if (success) newNode.finishAdd(oldNext!!)
            }
        }
    }

    @PublishedApi
    internal inline fun makeCondAddOp(node: Node, crossinline condition: () -> Boolean): CondAddOp =
        object : CondAddOp(node) {
            override fun prepare(affected: Node): Any? = if (condition()) null else CONDITION_FALSE
        }

    public actual open val isRemoved: Boolean get() = next is Removed

    // LINEARIZABLE. Returns Node | Removed
    // Returns null for the unlinked node on Kotlin/Native
    public val next: Any? get() {
        _next.loop { next ->
            if (next !is OpDescriptor) return next
            next.perform(this)
        }
    }

    // LINEARIZABLE. Returns next non-removed Node
    public actual val nextNode: Node get() = next?.unwrap() ?: this // it could have been unlinked on Kotlin/Native

    // LINEARIZABLE WHEN THIS NODE IS NOT REMOVED:
    // Returns prev non-removed Node, makes sure prev is correct (prev.next === this)
    // NOTE: if this node is removed, then returns non-removed previous node without applying
    // prev.next correction, which does not provide linearizable backwards iteration, but can be used to
    // resume forward iteration when current node was removed.
    // NOTE: It could have been unlinked on Kotlin/Native. In this case `this` is returned.
    public actual val prevNode: Node get() = prevOrNull ?: this
    
    // Just line prevNode, but return nulls for unlinked nodes on Kotlin/Native
    @PublishedApi
    internal val prevOrNull: Node?
        get() {
            return correctPrev(null) ?: findPrevNonRemoved(_prev.value ?: return null)
        }

    private tailrec fun findPrevNonRemoved(current: Node): Node? {
        if (!current.isRemoved) return current
        return findPrevNonRemoved(current._prev.value ?: return null)
    }

    // ------ addOneIfEmpty ------

    public actual fun addOneIfEmpty(node: Node): Boolean {
        node._prev.lazySet(this)
        node._next.lazySet(this)
        while (true) {
            val next = next
            if (next !== this) return false // this is not an empty list!
            if (_next.compareAndSet(this, node)) {
                // added successfully (linearized add) -- fixup the list
                node.finishAdd(this)
                return true
            }
        }
    }

    // ------ addLastXXX ------

    /**
     * Adds last item to this list.
     */
    public actual fun addLast(node: Node) {
        while (true) { // lock-free loop on prev.next
            val prev = prevOrNull ?: return // can be unlinked on Kotlin/Native
            if (prev.addNext(node, this)) return
        }
    }

    public fun <T : Node> describeAddLast(node: T): AddLastDesc<T> = AddLastDesc(this, node)

    /**
     * Adds last item to this list atomically if the [condition] is true.
     */
    public actual inline fun addLastIf(node: Node, crossinline condition: () -> Boolean): Boolean {
        val condAdd = makeCondAddOp(node, condition)
        while (true) { // lock-free loop on prev.next
            // sentinel node is never removed, so prev is always defined, but can be concurrently unlinked on Kotlin/Native
            val prev = prevOrNull ?: return false
            when (prev.tryCondAddNext(node, this, condAdd)) {
                SUCCESS -> return true
                FAILURE -> return false
            }
        }
    }

    public actual inline fun addLastIfPrev(node: Node, predicate: (Node) -> Boolean): Boolean {
        while (true) { // lock-free loop on prev.next
            // sentinel node is never removed, so prev is always defined, but can be unlinked on Kotlin/Native
            val prev = prevOrNull ?: return false
            if (!predicate(prev)) return false
            if (prev.addNext(node, this)) return true
        }
    }

    public actual inline fun addLastIfPrevAndIf(
            node: Node,
            predicate: (Node) -> Boolean, // prev node predicate
            crossinline condition: () -> Boolean // atomically checked condition
    ): Boolean {
        val condAdd = makeCondAddOp(node, condition)
        while (true) { // lock-free loop on prev.next
            // sentinel node is never removed, so prev is always defined, but can be unlinked on Kotlin/Native
            val prev = prevOrNull ?: return false
            if (!predicate(prev)) return false
            when (prev.tryCondAddNext(node, this, condAdd)) {
                SUCCESS -> return true
                FAILURE -> return false
            }
        }
    }

    // ------ addXXX util ------

    /**
     * Given:
     * ```
     *                +-----------------------+
     *          this  |         node          V  next
     *          +---+---+     +---+---+     +---+---+
     *  ... <-- | P | N |     | P | N |     | P | N | --> ....
     *          +---+---+     +---+---+     +---+---+
     *                ^                       |
     *                +-----------------------+
     * ```
     * Produces:
     * ```
     *          this            node             next
     *          +---+---+     +---+---+     +---+---+
     *  ... <-- | P | N | ==> | P | N | --> | P | N | --> ....
     *          +---+---+     +---+---+     +---+---+
     *                ^         |   ^         |
     *                +---------+   +---------+
     * ```
     *  Where `==>` denotes linearization point.
     *  Returns `false` if `next` was not following `this` node.
     */
    @PublishedApi
    internal fun addNext(node: Node, next: Node): Boolean {
        node._prev.lazySet(this)
        node._next.lazySet(next)
        if (!_next.compareAndSet(next, node)) return false
        // added successfully (linearized add) -- fixup the list
        node.finishAdd(next)
        return true
    }

    // returns UNDECIDED, SUCCESS or FAILURE
    @PublishedApi
    internal fun tryCondAddNext(node: Node, next: Node, condAdd: CondAddOp): Int {
        node._prev.lazySet(this)
        node._next.lazySet(next)
        condAdd.oldNext = next
        if (!_next.compareAndSet(next, condAdd)) return UNDECIDED
        // added operation successfully (linearized) -- complete it & fixup the list
        return if (condAdd.perform(this) == null) SUCCESS else FAILURE
    }

    // ------ removeXXX ------

    /**
     * Removes this node from the list. Returns `true` when removed successfully, or `false` if the node was already
     * removed or if it was not added to any list in the first place.
     *
     * **Note**: Invocation of this operation does not guarantee that remove was actually complete if result was `false`.
     * In particular, invoking [nextNode].[prevNode] might still return this node even though it is "already removed".
     * Invoke [helpRemove] to make sure that remove was completed.
     */
    public actual open fun remove(): Boolean =
        removeOrNext() == null

    // returns null if removed successfully or next node if this node is already removed or unlinked on Kotlin/Native
    @PublishedApi
    internal fun removeOrNext(): Node? {
        while (true) { // lock-free loop on next
            val next = this.next ?: return null // abort when unlinked on Kotlin/Native
            if (next is Removed) return next.ref // was already removed -- don't try to help (original thread will take care)
            if (next === this) {
                _prev.value = null // Unlink this node from itself to avoid cycle on Kotlin/Native
                return next // was not even added
            }
            val removed = (next as Node).removed()
            if (_next.compareAndSet(next, removed)) {
                // was removed successfully (linearized remove) -- fixup the list
                next.correctPrev(null)
                return null
            }
        }
    }

    // Helps with removal of this node
    public actual fun helpRemove() {
        // Note: this node must be already removed
        (next as Removed?)?.ref?.correctPrev(null)
    }

    // Helps with removal of nodes that are previous to this
    @PublishedApi
    internal fun helpRemovePrev() {
        // We need to call correctPrev on a non-removed node to ensure progress, since correctPrev bails out when
        // called on a removed node. There's always at least one non-removed node (list head).
        var node = this
        while (true) {
            val next = node.next ?: return // abort when unlinked on Kotlin/Native
            if (next !is Removed) break
            node = next.ref ?: return // abort when unlinked on Kotlin/Native
        }
        // Found a non-removed node
        node.correctPrev(null)
    }

    public actual fun removeFirstOrNull(): Node? {
        while (true) { // try to linearize
            val first = next as Node
            if (first === this) return null
            if (first.remove()) return first
            first.helpRemove() // must help remove to ensure lock-freedom
        }
    }

    public fun describeRemoveFirst(): RemoveFirstDesc<Node> = RemoveFirstDesc(this)

    // just peek at item when predicate is true
    public actual inline fun <reified T> removeFirstIfIsInstanceOfOrPeekIf(predicate: (T) -> Boolean): T? {
        while (true) {
            val first = this.next as Node
            if (first === this) return null // got list head -- nothing to remove
            if (first !is T) return null
            if (predicate(first)) {
                // check for removal of the current node to make sure "peek" operation is linearizable
                if (!first.isRemoved) return first
            }
            val next = first.removeOrNext()
            if (next === null) return first // removed successfully -- return it
            // help and start from the beginning
            next.helpRemovePrev()
        }
    }

    // ------ multi-word atomic operations helpers ------

    public open class AddLastDesc<T : Node> constructor(
        val queue: Node,
        val node: T
    ) : AbstractAtomicDesc() {
//        init {
            // require freshly allocated node here
//            assert { node._next.value === node && node._prev.value === node }
//        }

        // Returns null when atomic op got into deadlock trying to help operation that started later (RETRY_ATOMIC)
        final override fun takeAffectedNode(op: OpDescriptor): Node? =
            queue.correctPrev(op) // queue head is never removed, so null result can only mean RETRY_ATOMIC

        private val _affectedNode = atomic<Node?>(null)
        final override val affectedNode: Node? get() = _affectedNode.value
        final override val originalNext: Node get() = queue

        override fun retry(affected: Node, next: Any): Boolean = next !== queue

        override fun finishPrepare(prepareOp: PrepareOp) {
            // Note: onPrepare must use CAS to make sure the stale invocation is not
            // going to overwrite the previous decision on successful preparation.
            // Result of CAS is irrelevant, but we must ensure that it is set when invoker completes
            _affectedNode.compareAndSet(null, prepareOp.affected)
        }

        override fun updatedNext(affected: Node, next: Node): Any {
            // it is invoked only on successfully completion of operation, but this invocation can be stale,
            // so we must use CAS to set both prev & next pointers
            node._prev.compareAndSet(node, affected)
            node._next.compareAndSet(node, queue)
            return node
        }

        override fun finishOnSuccess(affected: Node, next: Node) {
            node.finishAdd(queue)
        }
    }

    public open class RemoveFirstDesc<T>(
        val queue: Node
    ) : AbstractAtomicDesc() {
        private val _affectedNode = atomic<Node?>(null)
        private val _originalNext = atomic<Node?>(null)

        @Suppress("UNCHECKED_CAST")
        public val result: T get() = affectedNode!! as T

        final override fun takeAffectedNode(op: OpDescriptor): Node? {
            queue._next.loop { next ->
                if (next is OpDescriptor) {
                    if (op.isEarlierThan(next))
                        return null // RETRY_ATOMIC
                    next.perform(queue)
                } else {
                    return next as Node
                }
            }
        }

        final override val affectedNode: Node? get() = _affectedNode.value
        final override val originalNext: Node? get() = _originalNext.value

        // check node predicates here, must signal failure if affect is not of type T
        protected override fun failure(affected: Node?): Any? =
            if (affected === queue || affected == null) LIST_EMPTY else null // must fail on null for unlinked nodes on K/N

        final override fun retry(affected: Node, next: Any): Boolean {
            if (next !is Removed) return false
            val nextRef = next.ref ?: return false // abort when unlinked on Kotlin/Native
            nextRef.helpRemovePrev() // must help delete to ensure lock-freedom
            return true
        }

        override fun finishPrepare(prepareOp: PrepareOp) {
            // Note: finishPrepare must use CAS to make sure the stale invocation is not
            // going to overwrite the previous decision on successful preparation.
            // Result of CAS is irrelevant, but we must ensure that it is set when invoker completes
            _affectedNode.compareAndSet(null, prepareOp.affected)
            _originalNext.compareAndSet(null, prepareOp.next)
        }

        final override fun updatedNext(affected: Node, next: Node): Any = next.removed()

        final override fun finishOnSuccess(affected: Node, next: Node) {
            // Complete removal operation here. It bails out if next node is also removed. It becomes
            // responsibility of the next's removes to call correctPrev which would help fix all the links.
            next.correctPrev(null)
        }
    }

    // This is Harris's RDCSS (Restricted Double-Compare Single Swap) operation
    // It inserts "op" descriptor of when "op" status is still undecided (rolls back otherwise)
    public class PrepareOp(
        val affected: Node,
        val next: Node,
        val desc: AbstractAtomicDesc
    ) : OpDescriptor() {
        override val atomicOp: AtomicOp<*> get() = desc.atomicOp

        // Returns REMOVE_PREPARED or null (it makes decision on any failure)
        override fun perform(affected: Any?): Any? {
//            assert { affected === this.affected }
            affected as Node // type assertion
            val decision = desc.onPrepare(this)
            if (decision === REMOVE_PREPARED) {
                // remove element on failure -- do not mark as decided, will try another one
                val next = this.next
                val removed = next.removed()
                if (affected._next.compareAndSet(this, removed)) {
                    // The element was actually removed
                    desc.onRemoved(affected)
                    // Complete removal operation here. It bails out if next node is also removed and it becomes
                    // responsibility of the next's removes to call correctPrev which would help fix all the links.
                    next.correctPrev(null)
                }
                return REMOVE_PREPARED
            }
            // We need to ensure progress even if it operation result consensus was already decided
            val consensus = if (decision != null) {
                // some other logic failure, including RETRY_ATOMIC -- reach consensus on decision fail reason ASAP
                atomicOp.decide(decision)
            } else {
                atomicOp.consensus // consult with current decision status like in Harris DCSS
            }
            val update: Any = when {
                consensus === NO_DECISION -> atomicOp // desc.onPrepare returned null -> start doing atomic op
                consensus == null -> desc.updatedNext(affected, next) // move forward if consensus on success
                else -> next // roll back if consensus if failure
            }
            affected._next.compareAndSet(this, update)
            return null
        }

        public fun finishPrepare(): Unit = desc.finishPrepare(this)

        override fun toString(): String = "PrepareOp(op=$atomicOp)"
    }

    public abstract class AbstractAtomicDesc : AtomicDesc() {
        protected abstract val affectedNode: Node?
        protected abstract val originalNext: Node?
        protected open fun takeAffectedNode(op: OpDescriptor): Node? = affectedNode!! // null for RETRY_ATOMIC
        protected open fun failure(affected: Node?): Any? = null // next: Node | Removed // must fail on null for unlinked nodes on K/N
        protected open fun retry(affected: Node, next: Any): Boolean = false // next: Node | Removed
        protected abstract fun finishOnSuccess(affected: Node, next: Node)

        public abstract fun updatedNext(affected: Node, next: Node): Any

        public abstract fun finishPrepare(prepareOp: PrepareOp)

        // non-null on failure
        public open fun onPrepare(prepareOp: PrepareOp): Any? {
            finishPrepare(prepareOp)
            return null
        }

        public open fun onRemoved(affected: Node) {} // called once when node was prepared & later removed

        @Suppress("UNCHECKED_CAST")
        final override fun prepare(op: AtomicOp<*>): Any? {
            while (true) { // lock free loop on next
                val affected = takeAffectedNode(op) ?: return RETRY_ATOMIC
                // read its original next pointer first
                val next = affected._next.value
                // then see if already reached consensus on overall operation
                if (next === op) return null // already in process of operation -- all is good
                if (op.isDecided) return null // already decided this operation -- go to next desc
                if (next is OpDescriptor) {
                    // some other operation is in process
                    // if operation in progress (preparing or prepared) has higher sequence number -- abort our preparations
                    if (op.isEarlierThan(next))
                        return RETRY_ATOMIC
                    next.perform(affected)
                    continue // and retry
                }
                // on Kotlin/Native next can be already unlinked
                // next: Node | Removed
                val failure = failure(affected) // must fail on null for unlinked nodes on K/N
                if (failure != null) return failure // signal failure
                next as Any // should have failed if was null
                if (retry(affected, next)) continue // retry operation
                val prepareOp = PrepareOp(affected, next as Node, this)
                if (affected._next.compareAndSet(next, prepareOp)) {
                    // prepared -- complete preparations
                    try {
                        val prepFail = prepareOp.perform(affected)
                        if (prepFail === REMOVE_PREPARED) continue // retry
//                        assert { prepFail == null }
                        return null
                    } catch (e: Throwable) {
                        // Crashed during preparation (for example IllegalStateExpception) -- undo & rethrow
                        affected._next.compareAndSet(prepareOp, next)
                        throw e
                    }
                }
            }
        }

        final override fun complete(op: AtomicOp<*>, failure: Any?) {
            val success = failure == null
            val affectedNode = affectedNode ?: run { /*assert { !success };*/ return }
            val originalNext = originalNext ?: run { /*assert { !success };*/ return }
            val update = if (success) updatedNext(affectedNode, originalNext) else originalNext
            if (affectedNode._next.compareAndSet(op, update)) {
                if (success) finishOnSuccess(affectedNode, originalNext)
            }
        }
    }

    // ------ other helpers ------

    /**
     * Given:
     * ```
     *
     *          prev            this             next
     *          +---+---+     +---+---+     +---+---+
     *  ... <-- | P | N | --> | P | N | --> | P | N | --> ....
     *          +---+---+     +---+---+     +---+---+
     *              ^ ^         |             |
     *              | +---------+             |
     *              +-------------------------+
     * ```
     * Produces:
     * ```
     *          prev            this             next
     *          +---+---+     +---+---+     +---+---+
     *  ... <-- | P | N | --> | P | N | --> | P | N | --> ....
     *          +---+---+     +---+---+     +---+---+
     *                ^         |   ^         |
     *                +---------+   +---------+
     * ```
     */
    private fun finishAdd(next: Node) {
        next._prev.loop { nextPrev ->
            if (this.next !== next) return // this or next was removed or another node added, remover/adder fixes up links
            if (next._prev.compareAndSet(nextPrev, this)) {
                // This newly added node could have been removed, and the above CAS would have added it physically again.
                // Let us double-check for this situation and correct if needed
                if (isRemoved) next.correctPrev(null)
                return
            }
        }
    }

    protected open fun nextIfRemoved(): Node? = (next as? Removed)?.ref

    /**
     * Returns the corrected value of the previous node while also correcting the `prev` pointer
     * (so that `this.prev.next === this`) and helps complete node removals to the left ot this node.
     *
     * It returns `null` in special cases:
     *
     * * When this node is removed. In this case there is no need to waste time on corrections, because
     *   remover of this node will ultimately call [correctPrev] on the next node and that will fix all
     *   the links from this node, too.
     * * When [op] descriptor is not `null` and operation descriptor that is [OpDescriptor.isEarlierThan]
     *   that current [op] is found while traversing the list. This `null` result will be translated
     *   by callers to [RETRY_ATOMIC].
     * * When list is unlinked on Kotlin/Native.
     */
    private tailrec fun correctPrev(op: OpDescriptor?): Node? {
        val oldPrev = _prev.value
        var prev: Node = oldPrev ?: return null // abort when unlinked on Kotlin/Native
        var last: Node? = null // will be set so that last.next === prev
        while (true) { // move the left until first non-removed node
            val prevNext: Any = prev._next.value ?: return null // abort when unlinked on Kotlin/Native
            when {
                // fast path to find quickly find prev node when everything is properly linked
                prevNext === this -> {
                    if (oldPrev === prev) return prev // nothing to update -- all is fine, prev found
                    // otherwise need to update prev
                    if (!this._prev.compareAndSet(oldPrev, prev)) {
                        // Note: retry from scratch on failure to update prev
                        return correctPrev(op)
                    }
                    return prev // return the correct prev
                }
                // slow path when we need to help remove operations
                this.isRemoved -> return null // nothing to do, this node was removed, bail out asap to save time
                prevNext === op -> return prev // part of the same op -- don't recurse, didn't correct prev
                prevNext is OpDescriptor -> { // help & retry
                    if (op != null && op.isEarlierThan(prevNext))
                        return null // RETRY_ATOMIC
                    prevNext.perform(prev)
                    return correctPrev(op) // retry from scratch
                }
                prevNext is Removed -> {
                    if (last !== null) {
                        // newly added (prev) node is already removed, correct last.next around it
                        if (!last._next.compareAndSet(prev, prevNext.ref)) {
                            return correctPrev(op) // retry from scratch on failure to update next
                        }
                        prev = last
                        last = null
                    } else {
                        prev = prev._prev.value ?: return null // abort when unlinked on Kotlin/Native
                    }
                }
                else -> { // prevNext is a regular node, but not this -- help delete
                    last = prev
                    prev = prevNext as Node
                }
            }
        }
    }

//    internal fun validateNode(prev: Node, next: Node) {
//        assert { prev === this._prev.value }
//        assert { next === this._next.value }
//    }

    /**
     * Only needed on Kotlin/Native to unlink cyclic data structure. See [disposeLockFreeLinkedList].
     */
    internal fun unlinkRefs(last: Boolean) {
        if (last) _next.value = null
        _prev.value = null
    }

//    override fun toString(): String = "$classSimpleName@$hexAddress"
}

private class Removed(ref: Node) {
    private val wRef: Any = ref.weakRef()
    val ref: Node? get() = wRef.unweakRef() as Node?
    override fun toString(): String = "Removed[$ref]"
}

@PublishedApi
internal fun Any.unwrap(): Node = (this as? Removed)?.ref ?: this as Node

/**
 * Head (sentinel) item of the linked list that is never removed.
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
@InternalAPI
public actual open class LockFreeLinkedListHead : LockFreeLinkedListNode() {
    public actual val isEmpty: Boolean get() = next === this

    /**
     * Iterates over all elements in this list of a specified type.
     */
    public actual inline fun <reified T : Node> forEach(block: (T) -> Unit) {
        var cur: Node? = next as Node?
        while (cur != this && cur != null) {
            if (cur is T) block(cur)
            cur = cur.next?.unwrap()
        }
    }

    // just a defensive programming -- makes sure that list head sentinel is never removed
    public actual final override fun remove(): Boolean = error("head cannot be removed")

    // optimization: because head is never removed, we don't have to read _next.value to check these:
    override val isRemoved: Boolean get() = false
    override fun nextIfRemoved(): Node? = null

//    internal fun validate() {
//        var prev: Node = this
//        var cur: Node = next as Node
//        while (cur != this) {
//            val next = cur.nextNode
//            cur.validateNode(prev, next)
//            prev = cur
//            cur = next
//        }
//        validateNode(prev, next as Node)
//    }
}


@Suppress("NOTHING_TO_INLINE")
internal actual inline fun Any.weakRef(): Any = WeakReference(this)

internal actual fun Any?.unweakRef(): Any? = (this as WeakReference<*>?)?.get()

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun storeCyclicRef(block: () -> Unit) {} // nop on native
