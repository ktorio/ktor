package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.transform.*
import java.util.concurrent.*

interface ApplicationHost {
    val environment: ApplicationHostEnvironment

    fun start(wait: Boolean = false): ApplicationHost
    fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit)
}

abstract class BaseApplicationHost(override final val environment: ApplicationHostEnvironment) : ApplicationHost {
    val application: Application get() = environment.application
    val pipeline = createHostPipeline()

    protected open fun createHostPipeline() = defaultHostPipeline(environment)

    init {
        environment.monitor.applicationStarting += {
            it.install(ApplicationTransform).registerDefaultHandlers()
        }
    }

}