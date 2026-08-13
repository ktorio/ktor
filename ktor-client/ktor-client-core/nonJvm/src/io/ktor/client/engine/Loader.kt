/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine

import io.ktor.utils.io.*
import kotlinx.atomicfu.*

private typealias EngineFactory = HttpClientEngineFactory<HttpClientEngineConfig>

/**
 * An entry in the [engines] registry that pairs an engine [factory] with its [priority].
 *
 * @property factory the engine factory
 * @property priority determines selection order when [io.ktor.client.HttpClient] is called with no explicit engine.
 *   Higher values are preferred. When multiple factories share the same highest priority an exception is thrown;
 *   assign distinct priorities to avoid ambiguity.
 */
@InternalAPI
public class EngineEntry(
    public val factory: EngineFactory,
    public val priority: Double = 1.0,
) {
    init {
        require(!priority.isNaN()) { "Priority cannot be NaN" }
    }
}

/**
 * Shared engines collection for.
 * Use [append] to enable engine auto discover in [HttpClient()].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.engines)
 */
@InternalAPI
public object engines : Iterable<EngineFactory> {
    private val head = atomic<Node?>(null)

    /**
     * Add engine to head with the default priority of 1.0.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.engines.append)
     */
    public fun append(item: EngineFactory) {
        append(item, 1.0)
    }

    /**
     * Add engine to head with the given [priority].
     * Higher priority values are preferred during auto-discovery.
     * An exception is thrown at selection time if multiple engines share the same highest priority.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.engines.append)
     */
    public fun append(item: EngineFactory, priority: Double) {
        while (true) {
            val current = head.value
            val new = Node(EngineEntry(item, priority), current)

            if (head.compareAndSet(current, new)) break
        }
    }

    /**
     * Returns all registered entries (factory + priority pairs).
     */
    public fun entries(): List<EngineEntry> {
        val result = mutableListOf<EngineEntry>()
        var current = head.value
        while (current != null) {
            result.add(current.entry)
            current = current.next
        }
        return result
    }

    /**
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.engines.iterator)
     *
     * @return unfrozen collection iterator.
     */
    override fun iterator(): Iterator<EngineFactory> = object : Iterator<EngineFactory> {
        var current = head.value

        override fun next(): EngineFactory {
            val result = current!!
            current = result.next
            return result.entry.factory
        }

        override fun hasNext(): Boolean = (null != current)
    }

    private class Node(
        val entry: EngineEntry,
        val next: Node?
    )
}
