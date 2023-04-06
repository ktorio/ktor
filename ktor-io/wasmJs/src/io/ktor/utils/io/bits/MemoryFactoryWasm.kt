/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.bits

import io.ktor.utils.io.core.internal.*
import kotlin.contracts.*

/**
 * Execute [block] of code providing a temporary instance of [Memory] view of this byte array range
 * starting at the specified [offset] and having the specified bytes [length].
 * By default, if neither [offset] nor [length] specified, the whole array is used.
 * An instance of [Memory] provided into the [block] should be never captured and used outside of lambda.
 */
@OptIn(ExperimentalContracts::class)
public actual inline fun <R> ByteArray.useMemory(offset: Int, length: Int, block: (Memory) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return Memory(ByteArray(length) { this[it + offset] }).let(block)
}

@PublishedApi
internal actual object DefaultAllocator : Allocator {
    override fun alloc(size: Int): Memory = Memory(ByteArray(size))
    override fun alloc(size: Long): Memory = Memory(ByteArray(size.toIntOrFail("size")))
    override fun free(instance: Memory) {
    }
}
