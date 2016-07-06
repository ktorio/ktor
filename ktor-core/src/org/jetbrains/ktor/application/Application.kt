package org.jetbrains.ktor.application

import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.transform.*
import java.util.concurrent.*

/**
 * Represents configured and running web application, capable of handling requests
 */
open class Application(val environment: ApplicationEnvironment) : ApplicationCallPipeline() {
    val executor: ScheduledExecutorService = environment.executorServiceBuilder()

    init {
        if (environment.config.propertyOrNull("ktor.test.doNotSetupDefaultPages")?.getString() != "true") {
            setupDefaultHostPages()
        }
        install(TransformationSupport).registerDefaultHandlers()
    }

    /**
     * Called by host when [Application] is terminated
     */
    open fun dispose() {
        executor.shutdown()
        if (!executor.awaitTermination(10L, TimeUnit.SECONDS)) {
            environment.log.warning("Failed to stop application executor service")
            executor.shutdownNow()
        }
    }
}
