package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*

class ApplicationLifecycleStatic(override val environment: ApplicationEnvironment,
                                 override val application: Application) : ApplicationLifecycle {
    override fun start() {
        environment.monitor.applicationStart(application)
    }

    override fun stop() {
        environment.monitor.applicationStop(application)
        application.dispose()
    }
}