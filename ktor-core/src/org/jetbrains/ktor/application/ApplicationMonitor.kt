package org.jetbrains.ktor.application

import org.jetbrains.ktor.util.*

class ApplicationMonitor {
    val applicationStart = Event<Application>()
    val applicationStop = Event<Application>()
}

fun ApplicationMonitor.logEvents(): ApplicationMonitor {
    applicationStart += { it.log.info("Application started: $it") }
    applicationStop += { it.log.info("Application stopped: $it") }
    return this
}