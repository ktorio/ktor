package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*

/**
 * Base class for implementing [ApplicationHost]
 *
 * It creates default host pipeline, provides [application] property and installs default transformations
 * on respond and receive
 *
 * @param environment instance of [ApplicationHostEnvironment] for this host
 * @param pipeline pipeline to use with this host
 */
abstract class BaseApplicationHost(override final val environment: ApplicationHostEnvironment,
                                   val pipeline: HostPipeline = defaultHostPipeline(environment)
) : ApplicationHost {

    /**
     * Currently running application instance
     */
    val application: Application get() = environment.application

    init {
        environment.monitor.subscribe(ApplicationStarting) {
            it.receivePipeline.installDefaultTransformations()
            it.sendPipeline.installDefaultTransformations()
        }
    }

}