/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.util.*
import kotlin.native.concurrent.*

private typealias T = HttpClientEngineFactory<HttpClientEngineConfig>

@InternalAPI
@Suppress("KDocMissingDocumentation")
/**
 * Shared engines collection for.
 * Use [append] to enable engine auto discover in [HttpClient()].
 */
object engines : Iterable<T> {
    private val head = AtomicReference<Node?>(null)

    /**
     * Add engine to head.
     */
    fun append(item: T) {
        while (true) {
            val current = head.value
            val new = Node(item, current)

            if (head.compareAndSet(current, new)) break
        }
    }

    /**
     * @return unfrozen collection iterator.
     */
    override fun iterator(): Iterator<T> = object : Iterator<T> {
        var current = head.value

        override fun next(): T {
            val result = current!!
            current = result.next
            return result.item
        }

        override fun hasNext(): Boolean = (null != current)
    }

    private class Node(
        val item: T, val next: Node?
    ) {
        init {
            freeze()
        }
    }
}
