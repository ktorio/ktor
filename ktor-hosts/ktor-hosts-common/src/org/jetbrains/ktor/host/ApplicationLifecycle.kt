package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*

/**
 * Represents application lifecycle and provides access to [Application]
 */
interface ApplicationLifecycle {
    /**
     * Running [Application]
     */
    val application : Application

    /**
     * Instance of [ApplicationEnvironment] for this lifecycle
     */
    val environment : ApplicationEnvironment

    /**
     * Starts [ApplicationLifecycle] and creates an application
     */
    fun start()

    /**
     * Stops [ApplicationLifecycle] and destroys any running application
     */
    fun stop()
}

