package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*

/**
 * SessionTracker provides ability to track and extract session by the context.
 * For example it could track it by cookie with session id or by IP address (could be good enough for corporate applications)
 */
interface SessionTracker<S : Any> {
    /**
     * Lookup session using the context, call [processSession] if available
     * It is recommended to be async if there is external session store
     */
    fun lookup(context: PipelineContext<ApplicationCall>, processSession: (S) -> Unit) : Nothing

    /**
     * Assign session using the context. Override if there is existing session. Could be blocking.
     */
    fun assign(call: ApplicationCall, session: S)

    /**
     * Unassign session if present. Does nothing if no session assigned.
     */
    fun unassign(call: ApplicationCall)
}