/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sessions

import io.ktor.server.application.*

/**
 * A session transport used to [receive], [send], or [clear] a session from/to an [ServerCall].
 */
public interface SessionTransport {
    /**
     * Gets a session information from a [call] and returns a [String] if success or null if failed.
     */
    public fun receive(call: ServerCall): String?

    /**
     * Sets a session information represented by [value] to a [call].
     */
    public fun send(call: ServerCall, value: String)

    /**
     * Clears session information from a specific [call].
     */
    public fun clear(call: ServerCall)
}
