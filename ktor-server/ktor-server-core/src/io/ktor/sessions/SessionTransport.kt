package io.ktor.sessions

import io.ktor.application.*

/**
 * SessionTransport [receive], [send] or [clear] a session from/to an [ApplicationCall].
 */
interface SessionTransport {
    /**
     * Will get session information from a [call] and will return a String if success or null if couldn't.
     */
    fun receive(call: ApplicationCall): String?

    /**
     * Will set session information represented by [value] to a [call].
     */
    fun send(call: ApplicationCall, value: String)

    /**
     * Will clear session information from a specific [call].
     */
    fun clear(call: ApplicationCall)
}

