package org.jetbrains.ktor.application

import org.jetbrains.ktor.util.*

class ApplicationMonitor {
    val applicationStarting = Event<Application>()
    val applicationStarted = Event<Application>()
    val applicationStopping= Event<Application>()
    val applicationStopped = Event<Application>()
}