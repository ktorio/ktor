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
    suspend fun lookup(context: PipelineContext<Unit>, processSession: (S) -> Unit)

    /**
     * Assign session using the context. Override if there is existing session. Could be blocking.
     */
    suspend fun assign(call: ApplicationCall, session: S)

    /**
     * Unassign session if present. Does nothing if no session assigned.
     */
    suspend fun unassign(call: ApplicationCall)
}