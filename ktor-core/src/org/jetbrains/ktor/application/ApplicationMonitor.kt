package org.jetbrains.ktor.application

import org.jetbrains.ktor.util.*

/**
 * Provides events for [Application] lifecycle
 */
class ApplicationMonitor {
    /**
     * Event that fires when application is about to start
     */
    val applicationStarting = Event<Application>()

    /**
     * Event that fires when application has been started
     */
    val applicationStarted = Event<Application>()

    /**
     * Event that fires when application is about to stop
     */
    val applicationStopping = Event<Application>()

    /**
     * Event that fires when application has been stopped
     */
    val applicationStopped = Event<Application>()
}