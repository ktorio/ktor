/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.sse.*

/**
 * Policy that controls how an SSE diagnostic buffer is captured while reading a stream.
 *
 * The buffer represents already processed data (no re-reading from the network).
 * It is intended for logging and error analysis when failures happen.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEBufferPolicy)
 */
public sealed interface SSEBufferPolicy {
    /**
     * Disable buffer capture.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEBufferPolicy.Off)
     */
    public data object Off : SSEBufferPolicy

    /**
     * Keep the last [count] completed SSE events in the diagnostic buffer.
     *
     * The session appends an event when it encounters an empty line (SSE event boundary).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEBufferPolicy.LastEvents)
     */
    public data class LastEvents(val count: Int) : SSEBufferPolicy {
        init {
            require(count > 0) { "Count must be > 0" }
        }
    }

    /**
     * Keep the last [count] text lines of the stream in the buffer.
     * Includes blank lines that delimit SSE events, comment lines, etc.).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEBufferPolicy.LastLines)
     */
    public data class LastLines(val count: Int) : SSEBufferPolicy {
        init {
            require(count > 0) { "Count must be > 0" }
        }
    }

    /**
     * Keep only the last completed event.
     * Shorthand for `LastEvents(1)`.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEBufferPolicy.LastEvent)
     */
    public data object LastEvent : SSEBufferPolicy

    /**
     * Keep everything that has been processed so far.
     *
     * Note: Use with care for long-lived streams.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEBufferPolicy.All)
     */
    public data object All : SSEBufferPolicy
}

internal fun SSEBufferPolicy.toBodyBuffer(): BodyBuffer = when (this) {
    is SSEBufferPolicy.Off -> BodyBuffer.Empty
    is SSEBufferPolicy.LastEvent -> BodyBuffer.Events(1)
    is SSEBufferPolicy.LastEvents -> BodyBuffer.Events(count)
    is SSEBufferPolicy.LastLines -> BodyBuffer.Lines(count)
    is SSEBufferPolicy.All -> BodyBuffer.Lines(Int.MAX_VALUE)
}

internal sealed interface BodyBuffer {
    fun appendLine(line: String) {}

    fun appendEvent(event: ServerSentEvent) {}

    fun toByteArray(): ByteArray = EMPTY

    class Events(private val capacity: Int) : BodyBuffer {
        private val events = ArrayDeque<ServerSentEvent>()

        override fun appendEvent(event: ServerSentEvent) {
            if (events.size == capacity) {
                events.removeFirst()
            }
            events.addLast(event)
        }

        override fun toByteArray(): ByteArray {
            return toByteArray(events)
        }
    }

    class Lines(private val capacity: Int) : BodyBuffer {
        private val lines = ArrayDeque<String>()

        override fun appendLine(line: String) {
            if (lines.size == capacity) {
                lines.removeFirst()
            }
            lines.addLast(line)
        }

        override fun toByteArray(): ByteArray {
            return toByteArray(lines)
        }
    }

    object Empty : BodyBuffer
}

private fun toByteArray(array: ArrayDeque<*>): ByteArray = array.joinToString(NEWLINE).encodeToByteArray()

private const val NEWLINE: String = "\r\n"

internal val EMPTY = ByteArray(0)
