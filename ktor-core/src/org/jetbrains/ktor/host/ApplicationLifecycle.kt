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
     * Stops an application and frees any resources associated with it
     */
    fun dispose()
}