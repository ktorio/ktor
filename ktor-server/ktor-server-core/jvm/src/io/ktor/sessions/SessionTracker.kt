package io.ktor.sessions

import io.ktor.application.*

/**
 * SessionTracker provides ability to track and extract session from the call context.
 */
interface SessionTracker {
    /**
     * Load session value from [transport] string for the specified [call]
     *
     * It is recommended to perform lookup asynchronously if there is an external session store
     * @return session instance or null if session was not found
     */
    suspend fun load(call: ApplicationCall, transport: String?): Any?

    /**
     * Store session [value] and return respective transport string for the specified [call].
     *
     * Override if there is existing session.
     */
    suspend fun store(call: ApplicationCall, value: Any): String

    /**
     * Clear session information
     */
    suspend fun clear(call: ApplicationCall)

    /**
     * Validate session information
     */
    fun validate(value: Any)
}