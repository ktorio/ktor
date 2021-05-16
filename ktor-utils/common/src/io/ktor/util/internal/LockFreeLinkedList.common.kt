/*
 * Copyright 2016-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
@file:Suppress("NO_EXPLICIT_VISIBILITY_IN_API_MODE")

package io.ktor.util.internal

import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlin.jvm.*
import kotlin.native.concurrent.*


/**
 * The most abstract operation that can be in process. Other threads observing an instance of this
 * class in the fields of their object shall invoke [perform] to help.
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
@InternalAPI
public abstract class OpDescriptor {
    /**
     * Returns `null` is operation was performed successfully or some other
     * object that indicates the failure reason.
     */
    abstract fun perform(affected: Any?): Any?

    /**
     * Returns reference to atomic operation that this descriptor is a part of or `null`
     * if not a part of any [AtomicOp].
     */
    abstract val atomicOp: AtomicOp<*>?

//    override fun toString(): String = "$classSimpleName@$hexAddress" // debug

    fun isEarlierThan(that: OpDescriptor): Boolean {
        val thisOp = atomicOp ?: return false
        val thatOp = that.atomicOp ?: return false
        return thisOp.opSequence < thatOp.opSequence
    }
}

@SharedImmutable
@JvmField
internal val NO_DECISION: Any = Symbol("NO_DECISION")

/**
 * Descriptor for multi-word atomic operation.
 * Based on paper
 * ["A Practical Multi-Word Compare-and-Swap Operation"](https://www.cl.cam.ac.uk/research/srg/netos/papers/2002-casn.pdf)
 * by Timothy L. Harris, Keir Fraser and Ian A. Pratt.
 *
 * Note: parts of atomic operation must be globally ordered. Otherwise, this implementation will produce
 * `StackOverflowError`.
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
@InternalAPI
public abstract class AtomicOp<in T> : OpDescriptor() {
    private val _consensus = atomic<Any?>(NO_DECISION)

    // Returns NO_DECISION when there is not decision yet
    val consensus: Any? get() = _consensus.value

    val isDecided: Boolean get() = _consensus.value !== NO_DECISION

    /**
     * Sequence number of this multi-word operation for deadlock resolution.
     * An operation with lower number aborts itself with (using [RETRY_ATOMIC] error symbol) if it encounters
     * the need to help the operation with higher sequence number and then restarts
     * (using higher `opSequence` to ensure progress).
     * Simple operations that cannot get into the deadlock always return zero here.
     *
     * See https://github.com/Kotlin/kotlinx.coroutines/issues/504
     */
    open val opSequence: Long get() = 0L

    override val atomicOp: AtomicOp<*> get() = this

    fun decide(decision: Any?): Any? {
//        assert { decision !== NO_DECISION }
        val current = _consensus.value
        if (current !== NO_DECISION) return current
        if (_consensus.compareAndSet(NO_DECISION, decision)) return decision
        return _consensus.value
    }

    abstract fun prepare(affected: T): Any? // `null` if Ok, or failure reason

    abstract fun complete(affected: T, failure: Any?) // failure != null if failed to prepare op

    // returns `null` on success
    @Suppress("UNCHECKED_CAST")
    final override fun perform(affected: Any?): Any? {
        // make decision on status
        var decision = this._consensus.value
        if (decision === NO_DECISION) {
            decision = decide(prepare(affected as T))
        }
        // complete operation
        complete(affected as T, decision)
        return decision
    }
}

/**
 * A part of multi-step atomic operation [AtomicOp].
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
@InternalAPI
public abstract class AtomicDesc {
    lateinit var atomicOp: AtomicOp<*> // the reference to parent atomicOp, init when AtomicOp is created
    abstract fun prepare(op: AtomicOp<*>): Any? // returns `null` if prepared successfully
    abstract fun complete(op: AtomicOp<*>, failure: Any?) // decision == null if success
}

/**
 * It is returned as an error by [AtomicOp] implementations when they detect potential deadlock
 * using [AtomicOp.opSequence] numbers.
 */
@JvmField
@SharedImmutable
internal val RETRY_ATOMIC: Any = Symbol("RETRY_ATOMIC")

/**
 * A symbol class that is used to define unique constants that are self-explanatory in debugger.
 *
 * @suppress **This is unstable API and it is subject to change.**
 */
internal class Symbol(val symbol: String) {
    override fun toString(): String = "<$symbol>"

    @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
    inline fun <T> unbox(value: Any?): T = if (value === this) null as T else value as T
}


/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public expect open class LockFreeLinkedListNode() {
    public val isRemoved: Boolean
    public val nextNode: LockFreeLinkedListNode
    public val prevNode: LockFreeLinkedListNode
    public fun addLast(node: LockFreeLinkedListNode)
    public fun addOneIfEmpty(node: LockFreeLinkedListNode): Boolean
    public inline fun addLastIf(node: LockFreeLinkedListNode, crossinline condition: () -> Boolean): Boolean
    public inline fun addLastIfPrev(
        node: LockFreeLinkedListNode,
        predicate: (LockFreeLinkedListNode) -> Boolean
    ): Boolean

    public inline fun addLastIfPrevAndIf(
        node: LockFreeLinkedListNode,
        predicate: (LockFreeLinkedListNode) -> Boolean, // prev node predicate
        crossinline condition: () -> Boolean // atomically checked condition
    ): Boolean

    public open fun remove(): Boolean

    /**
     * Helps fully finish [remove] operation, must be invoked after [remove] if needed.
     * Ensures that traversing the list via prev pointers sees this node as removed.
     * No-op on JS
     */
    public fun helpRemove()
    public fun removeFirstOrNull(): LockFreeLinkedListNode?
    public inline fun <reified T> removeFirstIfIsInstanceOfOrPeekIf(predicate: (T) -> Boolean): T?
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public expect open class LockFreeLinkedListHead() : LockFreeLinkedListNode {
    public val isEmpty: Boolean
    public inline fun <reified T : LockFreeLinkedListNode> forEach(block: (T) -> Unit)
    public final override fun remove(): Boolean // Actual return type is Nothing, KT-27534
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public expect open class AddLastDesc<T : LockFreeLinkedListNode>(
    queue: LockFreeLinkedListNode,
    node: T
) : AbstractAtomicDesc {
    val queue: LockFreeLinkedListNode
    val node: T
    override fun finishPrepare(prepareOp: PrepareOp)
    override fun finishOnSuccess(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode)
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public expect open class RemoveFirstDesc<T>(queue: LockFreeLinkedListNode): AbstractAtomicDesc {
    val queue: LockFreeLinkedListNode
    public val result: T
    override fun finishPrepare(prepareOp: PrepareOp)
    final override fun finishOnSuccess(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode)
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public expect abstract class AbstractAtomicDesc : AtomicDesc {
    final override fun prepare(op: AtomicOp<*>): Any?
    final override fun complete(op: AtomicOp<*>, failure: Any?)
    protected open fun failure(affected: LockFreeLinkedListNode?): Any?  // must fail on null for unlinked nodes on K/N
    protected open fun retry(affected: LockFreeLinkedListNode, next: Any): Boolean
    public abstract fun finishPrepare(prepareOp: PrepareOp) // non-null on failure
    public open fun onPrepare(prepareOp: PrepareOp): Any? // non-null on failure
    public open fun onRemoved(affected: LockFreeLinkedListNode) // non-null on failure
    protected abstract fun finishOnSuccess(affected: LockFreeLinkedListNode, next: LockFreeLinkedListNode)
}

/** @suppress **This is unstable API and it is subject to change.** */
@InternalAPI
public expect class PrepareOp: OpDescriptor {
    val affected: LockFreeLinkedListNode
    override val atomicOp: AtomicOp<*>
    val desc: AbstractAtomicDesc
    fun finishPrepare()
}

@JvmField
@SharedImmutable
internal val REMOVE_PREPARED: Any = Symbol("REMOVE_PREPARED")

internal expect inline fun storeCyclicRef(block: () -> Unit) // nop on native
internal expect fun Any.weakRef(): Any
internal expect fun Any?.unweakRef(): Any?
