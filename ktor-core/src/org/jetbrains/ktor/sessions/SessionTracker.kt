package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*

/**
 * SessionTracker provides ability to track and extract session by the context.
 * For example it could track it by cookie with session id or by IP address (could be good enough for corporate applications)
 */
interface SessionTracker<S : Any> {
    /**
     * Lookup session using the context, call [injectSession] if available and pass execution to the [next] in any case.
     * It is recommended to be async if there is external session store
     */
    fun lookup(context: PipelineContext<ApplicationCall>, injectSession: (S) -> Unit)

    /**
     * Assign session using the context. Override if there is existing session. Could be blocking.
     */
    fun assign(call: ApplicationCall, session: S)

    /**
     * Unassign session if present. Does nothing if no session assigned.
     */
    fun unassign(call: ApplicationCall)
}