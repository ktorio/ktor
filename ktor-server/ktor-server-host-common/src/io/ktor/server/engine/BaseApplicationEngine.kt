package io.ktor.server.engine

import io.ktor.application.*

/**
 * Base class for implementing [ApplicationEngine]
 *
 * It creates default engine pipeline, provides [application] property and installs default transformations
 * on respond and receive
 *
 * @param environment instance of [ApplicationEngineEnvironment] for this engine
 * @param pipeline pipeline to use with this engine
 */
abstract class BaseApplicationEngine(override final val environment: ApplicationEngineEnvironment,
                                     val pipeline: EnginePipeline = defaultEnginePipeline(environment)
) : ApplicationEngine {

    open class Configuration : ApplicationEngine.Configuration()

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