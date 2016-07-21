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

    fun onBeforeInitializeApplication(initializer: Application.() -> Unit)

    /**
     * Stops an application and frees any resources associated with it
     */
    fun dispose()

    fun ensureApplication() {
        application
    }
}