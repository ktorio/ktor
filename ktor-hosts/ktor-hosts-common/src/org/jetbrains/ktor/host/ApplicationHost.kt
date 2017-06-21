package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import java.util.concurrent.*

interface ApplicationHost {
    val environment: ApplicationHostEnvironment

    fun start(wait: Boolean = false): ApplicationHost
    fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit)
}

abstract class BaseApplicationHost(override final val environment: ApplicationHostEnvironment,
                                   val pipeline: HostPipeline = defaultHostPipeline(environment)
) : ApplicationHost {

    val application: Application get() = environment.application

    init {
        environment.monitor.applicationStarting += {
            it.receivePipeline.installDefaultTransformations()
            it.sendPipeline.installDefaultTransformations()
        }
    }

}