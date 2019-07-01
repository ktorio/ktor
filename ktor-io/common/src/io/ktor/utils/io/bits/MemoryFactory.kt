package io.ktor.utils.io.bits

import io.ktor.utils.io.core.internal.*
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

// TODO: length default argument should be this.size - offset but it doesn't work due to KT-29920
/**
 * Execute [block] of code providing a temporary instance of [Memory] view of this byte array range
 * starting at the specified [offset] and having the specified bytes [length].
 * By default, if neither [offset] nor [length] specified, the whole array is used.
 * An instance of [Memory] provided into the [block] should be never captured and used outside of lambda.
 */
expect fun <R> ByteArray.useMemory(offset: Int = 0, length: Int, block: (Memory) -> R): R

/**
 * Invoke [block] function with a temporary [Memory] instance of the specified [size] in bytes.
 * The provided instance shouldn't be captured and used outside of the [block] otherwise an undefined behaviour
 * may occur including crash and/or data corruption.
 */
inline fun <R> withMemory(size: Int, block: (Memory) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    return withMemory(size.toLong(), block)
}

/**
 * Invoke [block] function with a temporary [Memory] instance of the specified [size] in bytes.
 * The provided instance shouldn't be captured and used outside of the [block] otherwise an undefined behaviour
 * may occur including crash and/or data corruption.
 */
inline fun <R> withMemory(size: Long, block: (Memory) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val allocator = DefaultAllocator
    val memory = allocator.alloc(size)
    return try {
        block(memory)
    } finally {
        allocator.free(memory)
    }
}

@PublishedApi
internal expect object DefaultAllocator : Allocator

@DangerousInternalIoApi
internal interface Allocator {
    fun alloc(size: Int): Memory

    fun alloc(size: Long): Memory

    fun free(instance: Memory)
}
