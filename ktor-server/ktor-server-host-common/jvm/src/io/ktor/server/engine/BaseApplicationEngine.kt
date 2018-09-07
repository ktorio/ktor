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
@EngineAPI
abstract class BaseApplicationEngine(
    final override val environment: ApplicationEngineEnvironment,
    val pipeline: EnginePipeline = defaultEnginePipeline(environment)
) : ApplicationEngine {

    /**
     * Configuration for the [BaseApplicationEngine]
     */
    open class Configuration : ApplicationEngine.Configuration()

    /**
     * Currently running application instance
     */
    val application: Application get() = environment.application

    init {
        BaseApplicationResponse.setupSendPipeline(pipeline.sendPipeline)
        environment.monitor.subscribe(ApplicationStarting) {
            it.receivePipeline.merge(pipeline.receivePipeline)
            it.sendPipeline.merge(pipeline.sendPipeline)
            it.receivePipeline.installDefaultTransformations()
            it.sendPipeline.installDefaultTransformations()
        }
        environment.monitor.subscribe(ApplicationStarted) {
            environment.connectors.forEach {
                environment.log.info("Responding at ${it.type.name.toLowerCase()}://${it.host}:${it.port}")
            }
        }
    }
}
