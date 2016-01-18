package org.jetbrains.ktor.application

public enum class ApplicationCallResult {
    /**
     * Indicates [ApplicationCall] was handled
     */
    Handled,

    /**
     * Indicates [ApplicationCall] was not handled
     */
    Unhandled,

    /**
     * Indicates [ApplicationCall] is being handled asynchronously
     *
     * Handling code is responsible to close the call.
     */
    Asynchronous,
}