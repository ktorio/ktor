package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*

/**
 * SessionTracker provides ability to track and extract session from the call context.
 * For example it could track it by cookie with session id or by IP address (could be good enough for corporate applications)
 */
interface SessionTracker {
    /**
     * Lookup session using the [context]
     *
     * It is recommended to perform lookup asynchronously if there is an external session store
     * @return session instance or null if session was not found
     */
    suspend fun lookup(context: PipelineContext<Unit>, cookieSettings: SessionCookiesSettings): Any?

    /**
     * Assign session using the context. Override if there is existing session. Could be blocking.
     */
    suspend fun assign(call: ApplicationCall, session: Any, cookieSettings: SessionCookiesSettings)

    /**
     * Unassign session if present. Does nothing if no session assigned.
     */
    suspend fun unassign(call: ApplicationCall)

    fun validate(value: Any)
}