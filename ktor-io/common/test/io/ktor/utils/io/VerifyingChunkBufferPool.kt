/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import kotlin.test.*

class VerifyingChunkBufferPool(
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
) : ObjectPool<ChunkBuffer> {
    override val capacity: Int = Int.MAX_VALUE
    private val allocator: Allocator = DefaultAllocator
    private val allocated = mutableSetOf<IdentityWrapper>()

    override fun borrow(): ChunkBuffer {
        val instance = ChunkBuffer(allocator.alloc(bufferSize), null, this)
        check(allocated.add(IdentityWrapper(instance)))
        return instance
    }

    override fun recycle(instance: ChunkBuffer) {
        check(allocated.remove(IdentityWrapper(instance)))
        allocator.free(instance.memory)
    }

    override fun dispose() {
    }

    fun assertEmpty() {
        assertEquals(0, allocated.size, "There are remaining unreleased buffers, ")
    }

    private class IdentityWrapper(private val instance: ChunkBuffer) {
        override fun equals(other: Any?): Boolean {
            if (other !is IdentityWrapper) return false
            return other.instance === this.instance
        }

        override fun hashCode() = identityHashCode(instance)
    }
}
