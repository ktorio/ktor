/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.selector

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*

/**
 * A thread-safe map that manages suspensions for different [SelectInterest] types.
 *
 * This class is used internally by the selector to track coroutine continuations
 * waiting for specific I/O operations (read, write, accept, connect) to become ready.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap)
 */
public class InterestSuspensionsMap {
    private val readHandlerReference = atomic<CancellableContinuation<Unit>?>(null)
    private val writeHandlerReference = atomic<CancellableContinuation<Unit>?>(null)
    private val connectHandlerReference = atomic<CancellableContinuation<Unit>?>(null)
    private val acceptHandlerReference = atomic<CancellableContinuation<Unit>?>(null)

    /**
     * Registers a suspension [continuation] for the specified [interest].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.addSuspension)
     *
     * @param interest the type of I/O operation to wait for
     * @param continuation the coroutine continuation to resume when the operation is ready
     * @throws IllegalStateException if a handler for this interest is already registered
     */
    public fun addSuspension(interest: SelectInterest, continuation: CancellableContinuation<Unit>) {
        val isAdded = when (interest) {
            SelectInterest.READ -> readHandlerReference.compareAndSet(null, continuation)
            SelectInterest.WRITE -> writeHandlerReference.compareAndSet(null, continuation)
            SelectInterest.ACCEPT -> acceptHandlerReference.compareAndSet(null, continuation)
            SelectInterest.CONNECT -> connectHandlerReference.compareAndSet(null, continuation)
        }

        if (!isAdded) {
            error("Handler for ${interest.name} is already registered")
        }
    }

    /**
     * Invokes the given [block] for each registered suspension whose interest matches the [readyOps] flags.
     * The matching suspensions are removed from the map before invoking the block.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.invokeForEachPresent)
     *
     * @param readyOps a bitmask of ready operations (from NIO selection key)
     * @param block the action to perform on each matching continuation
     */
    public inline fun invokeForEachPresent(readyOps: Int, block: CancellableContinuation<Unit>.() -> Unit) {
        val flags = SelectInterest.flags

        for (ordinal in flags.indices) {
            if (flags[ordinal] and readyOps != 0) {
                removeSuspension(ordinal)?.block()
            }
        }
    }

    /**
     * Invokes the given [block] for each registered suspension, regardless of ready state.
     * The suspensions are removed from the map before invoking the block.
     * This is typically used for cleanup or cancellation.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.invokeForEachPresent)
     *
     * @param block the action to perform on each continuation with its associated interest
     */
    public inline fun invokeForEachPresent(block: CancellableContinuation<Unit>.(SelectInterest) -> Unit) {
        for (interest in SelectInterest.AllInterests) {
            removeSuspension(interest)?.run { block(interest) }
        }
    }

    /**
     * Removes and returns the suspension registered for the specified [interest].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.removeSuspension)
     *
     * @param interest the type of I/O operation
     * @return the removed continuation, or null if no suspension was registered
     */
    public fun removeSuspension(interest: SelectInterest): CancellableContinuation<Unit>? = when (interest) {
        SelectInterest.READ -> readHandlerReference.getAndSet(null)
        SelectInterest.WRITE -> writeHandlerReference.getAndSet(null)
        SelectInterest.ACCEPT -> acceptHandlerReference.getAndSet(null)
        SelectInterest.CONNECT -> connectHandlerReference.getAndSet(null)
    }

    /**
     * Removes and returns the suspension registered for the interest at the given ordinal index.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.network.selector.InterestSuspensionsMap.removeSuspension)
     *
     * @param interestOrdinal the ordinal index of the [SelectInterest]
     * @return the removed continuation, or null if no suspension was registered
     */
    public fun removeSuspension(interestOrdinal: Int): CancellableContinuation<Unit>? =
        removeSuspension(SelectInterest.AllInterests[interestOrdinal])

    override fun toString(): String {
        return "R ${readHandlerReference.value} W ${writeHandlerReference.value} " +
            "C ${connectHandlerReference.value} A ${acceptHandlerReference.value}"
    }

    public companion object
}
