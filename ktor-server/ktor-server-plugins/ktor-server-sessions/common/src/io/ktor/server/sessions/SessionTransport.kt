/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sessions

import io.ktor.server.application.*

/**
 * A session transport used to [receive], [send], or [clear] a session from/to an [ApplicationCall].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SessionTransport)
 */
public interface SessionTransport {
    /**
     * Gets a session information from a [call] and returns a [String] if success or null if failed.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SessionTransport.receive)
     */
    public fun receive(call: ApplicationCall): String?

    /**
     * Sets a session information represented by [value] to a [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SessionTransport.send)
     */
    public fun send(call: ApplicationCall, value: String)

    /**
     * Clears session information from a specific [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.sessions.SessionTransport.clear)
     */
    public fun clear(call: ApplicationCall)
}
