package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.config.*
import org.jetbrains.ktor.logging.*
import java.util.concurrent.*

class ApplicationHostEnvironmentStatic(application: Application,
                                       override val classLoader: ClassLoader,
                                       override val log: ApplicationLog,
                                       override val config: ApplicationConfig,
                                       override val connectors: List<HostConnectorConfig>,
                                       override val executor: ScheduledExecutorService = DefaultExecutorServiceBuilder()
                                       ) : ApplicationHostEnvironment {

    override val monitor = ApplicationMonitor().logEvents()

    private var running = false
    private val _application = application

    override val application: Application
        get() {
            if (!running)
                throw IllegalStateException("Application is not currently running")
            return _application
        }

    override fun start() {
        running = true
        monitor.applicationStart(application)
    }

    override fun stop() {
        monitor.applicationStop(application)
        application.dispose()
        running = false
    }
}