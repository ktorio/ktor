/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.sse.*
import io.ktor.utils.io.core.*

/**
 * Policy that controls how an SSE diagnostic body snapshot is captured while reading a stream.
 *
 * The snapshot represents already processed data (no re-reading from the network).
 * It is intended for logging and error analysis when failures happen.
 */
public interface BodySnapshotPolicy {
    /**
     * Disable body snapshot capture.
     */
    public data object Off : BodySnapshotPolicy

    /**
     * Keep the last [count] completed SSE events* in the snapshot.
     *
     * The session appends an event when it encounters an empty line (SSE event boundary).
     */
    public data class LastEvents(val count: Int) : BodySnapshotPolicy {
        init {
            require(count > 0) { "count must be > 0" }
        }
    }

    /**
     * Keep the last [count] text lines of the stream in the snapshot
     * Includes blank lines that delimit SSE events, comment lines, etc.).
     */
    public data class LastLines(val count: Int) : BodySnapshotPolicy {
        init {
            require(count > 0) { "count must be > 0" }
        }
    }

    /**
     * Keep only the last completed event.
     * Shorthand for `LastEvents(1)`.
     */
    public data object LastEvent : BodySnapshotPolicy

    /**
     * Keep everything that has been processed so far.
     *
     * Note: Use with care for long-lived streams.
     */
    public data object All : BodySnapshotPolicy
}

internal interface BodySnapshot {
    fun appendLine(line: String)

    fun appendEvent(event: ServerSentEvent)

    fun getSnapshot(): ByteArray
}

internal class EventsBodySnapshot(private val number: Int) : BodySnapshot {
    private val events = ArrayDeque<ServerSentEvent>()

    override fun appendLine(line: String) {
        // no-op
    }

    override fun appendEvent(event: ServerSentEvent) {
        if (events.size == number) {
            events.removeFirst()
        }
        events.addLast(event)
    }

    override fun getSnapshot(): ByteArray {
        return events.joinToString(NEWLINE) { it.toString() }.toByteArray()
    }
}

internal class LinesBodySnapshot(private val number: Int) : BodySnapshot {
    private val lines = ArrayDeque<String>()

    override fun appendLine(line: String) {
        if (lines.size == number) {
            lines.removeFirst()
        }
        lines.addLast(line)
    }

    override fun appendEvent(event: ServerSentEvent) {
        // no-op
    }

    override fun getSnapshot(): ByteArray {
        return lines.joinToString(NEWLINE).toByteArray()
    }
}

private const val NEWLINE: String = "\r\n"
